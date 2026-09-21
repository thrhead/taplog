package io.github.thrhead.taplog.data.persistence

import io.github.thrhead.taplog.core.domain.Behavior
import io.github.thrhead.taplog.core.domain.BindingId
import io.github.thrhead.taplog.core.domain.BindingLifecycle
import io.github.thrhead.taplog.core.domain.BindingStatus
import io.github.thrhead.taplog.core.domain.DatasetGeneration
import io.github.thrhead.taplog.core.domain.DisplaySnapshot
import io.github.thrhead.taplog.core.domain.DurationStatus
import io.github.thrhead.taplog.core.domain.EpochMillis
import io.github.thrhead.taplog.core.domain.Event
import io.github.thrhead.taplog.core.domain.EventId
import io.github.thrhead.taplog.core.domain.EventPayload
import io.github.thrhead.taplog.core.domain.EventSnapshot
import io.github.thrhead.taplog.core.domain.Lifecycle
import io.github.thrhead.taplog.core.domain.Quantity
import io.github.thrhead.taplog.core.domain.Record
import io.github.thrhead.taplog.core.domain.RecordId
import io.github.thrhead.taplog.core.domain.RecordTarget
import io.github.thrhead.taplog.core.domain.ReceiptId
import io.github.thrhead.taplog.core.domain.ResultReason
import io.github.thrhead.taplog.core.domain.Revision
import io.github.thrhead.taplog.core.domain.Sequence
import io.github.thrhead.taplog.core.domain.Source
import io.github.thrhead.taplog.core.domain.StateGroup
import io.github.thrhead.taplog.core.domain.StateGroupId
import io.github.thrhead.taplog.core.domain.Target
import io.github.thrhead.taplog.core.domain.TargetId
import io.github.thrhead.taplog.core.domain.UndoInvalidation
import io.github.thrhead.taplog.core.domain.UnitName
import io.github.thrhead.taplog.core.engine.CommitOperation
import io.github.thrhead.taplog.core.engine.DeletionScope
import io.github.thrhead.taplog.core.engine.DomainState
import io.github.thrhead.taplog.core.engine.EngineResult
import io.github.thrhead.taplog.core.engine.EventEngine
import io.github.thrhead.taplog.core.engine.ScopeContext
import io.github.thrhead.taplog.core.engine.StateScope
import io.github.thrhead.taplog.core.engine.UndoReceipt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RepositoryContractTest {
    @Test
    fun coreConfirmedPairDeletionPersistsOnlyTheSelectedScopeAndUndoHistory() {
        val boundary = InMemoryLocalPersistence(aggregate())
        val engine = EventEngine(boundary, TestClock)
        val impact = DeletionScope.preview(boundary.read(), RecordId("counter"), TargetId("alpha"))

        assertEquals(listOf(EventId("counter-alpha")), impact.eventIds)
        assertTrue(engine.deleteScope(RecordId("counter"), TargetId("alpha")) is EngineResult.NeedsConfirmation)
        assertTrue(engine.deleteScope(RecordId("counter"), TargetId("alpha"), confirmed = true) is EngineResult.Applied)

        val actual = boundary.read()
        assertEquals(aggregate().targets, actual.targets)
        assertTrue(actual.records.containsKey(RecordId("counter")))
        assertFalse(actual.events.any { it.id == EventId("counter-alpha") })
        assertTrue(actual.events.any { it.id == EventId("duration-no-target-open") })
        assertFalse(actual.relationships.containsKey(RecordId("counter") to TargetId("alpha")))
        assertFalse(actual.bindings.containsKey(BindingId("binding-alpha")))
        assertTrue(actual.bindings.containsKey(BindingId("binding-no-target")))
        assertTrue(actual.undoReceipts.containsKey(ReceiptId("receipt")))
    }

    @Test
    fun coreConfirmedRecordDeletionRemovesNoTargetHistoryAndItsUndoReceipts() {
        val before = aggregate()
        val boundary = InMemoryLocalPersistence(before)
        val engine = EventEngine(boundary, TestClock)

        assertTrue(engine.deleteScope(RecordId("duration"), confirmed = true) is EngineResult.Applied)

        val actual = boundary.read()
        assertFalse(actual.records.containsKey(RecordId("duration")))
        assertFalse(actual.events.any { it.recordId == RecordId("duration") })
        assertFalse(actual.relationships.keys.any { it.first == RecordId("duration") })
        assertFalse(actual.bindings.values.any { it.recordId == RecordId("duration") })
        assertEquals(before.targets, actual.targets)
        assertTrue(actual.events.any { it.id == EventId("counter-alpha") })
        assertTrue(actual.bindings.containsKey(BindingId("binding-alpha")))
    }

    @Test
    fun readAfterWritePreservesEveryAggregateFamilyAndMetadata() {
        val persistence = InMemoryLocalPersistence()
        val expected = aggregate()

        assertTrue(persistence.commit(CommitOperation(persistence.read(), expected)))

        val actual = persistence.read()
        assertEquals(expected, actual)
        assertEquals(7L, actual.generation.value)
        assertEquals(20L, actual.nextSequence.value)
        assertEquals(3L, actual.records.getValue(RecordId("counter")).revision.value)
        assertEquals(5L, actual.targets.getValue(TargetId("alpha")).revision.value)
        assertEquals(7L, actual.relationships.getValue(RecordId("counter") to TargetId("alpha")).revision.value)
        assertEquals(2L, actual.events.single { it.id == EventId("counter-alpha") }.revision.value)
        assertEquals(7, actual.records.size)
        assertEquals(2, actual.targets.size)
        assertEquals(2, actual.relationships.size)
        assertEquals(1, actual.stateGroups.size)
        assertEquals(2, actual.stateGenerations.size)
        assertEquals(2, actual.currentStates.size)
        assertEquals(2, actual.bindings.size)
        assertEquals(1, actual.undoReceipts.size)
    }

    @Test
    fun eventRowsUseOneTypedRowPerEventAndRetainImmutableSnapshotsAndNullableScopes() {
        val actual = committedPersistence().read()
        val rows = PersistenceMapper.toRows(actual)

        assertEquals(actual.events.size, rows.events.size)
        assertEquals(rows.events.size, rows.events.map { it.eventId }.toSet().size)
        assertEquals(setOf("MOMENT", "COUNTER", "DURATION", "STATE"), rows.events.map { it.behavior }.toSet())

        val moment = rows.events.single { it.eventId == "moment-alpha" }
        assertNull(moment.counterQuantity)
        assertNull(moment.counterUnit)
        assertNull(moment.durationStartAt)
        assertNull(moment.durationEndAt)
        assertNull(moment.durationStatus)
        assertNull(moment.durationIncompleteReason)
        assertNull(moment.stateGroupId)
        assertNull(moment.stateGeneration)

        val counter = rows.events.single { it.eventId == "counter-alpha" }
        assertEquals("1.25", counter.counterQuantity)
        assertEquals("cups", counter.counterUnit)
        assertNull(counter.durationStartAt)
        assertNull(counter.durationEndAt)
        assertNull(counter.durationStatus)
        assertNull(counter.durationIncompleteReason)
        assertNull(counter.stateGroupId)
        assertNull(counter.stateGeneration)
        assertEquals("Counter at log", counter.snapshotRecordName)
        assertEquals("Alpha at log", counter.snapshotTargetName)
        assertEquals("Counter current", actual.records.getValue(RecordId("counter")).name)

        val duration = rows.events.single { it.eventId == "duration-no-target-open" }
        assertNull(duration.targetId)
        assertEquals("no-target", duration.targetScopeKey)
        assertEquals(90L, duration.durationStartAt)
        assertNull(duration.durationEndAt)
        assertEquals("OPEN", duration.durationStatus)
        assertNull(duration.durationIncompleteReason)
        assertNull(duration.counterQuantity)
        assertNull(duration.counterUnit)
        assertNull(duration.stateGroupId)
        assertNull(duration.stateGeneration)

        val completedDuration = rows.events.single { it.eventId == "duration-alpha-completed" }
        assertEquals(200L, completedDuration.durationStartAt)
        assertEquals(899L, completedDuration.durationEndAt)
        assertEquals("COMPLETED", completedDuration.durationStatus)
        assertNull(completedDuration.durationIncompleteReason)
        assertNull(completedDuration.counterQuantity)
        assertNull(completedDuration.counterUnit)
        assertNull(completedDuration.stateGroupId)
        assertNull(completedDuration.stateGeneration)

        val state = rows.events.single { it.eventId == "state-alpha-active" }
        assertEquals("group", state.stateGroupId)
        assertEquals(2L, state.stateGeneration)
        assertNull(state.counterQuantity)
        assertNull(state.counterUnit)
        assertNull(state.durationStartAt)
        assertNull(state.durationEndAt)
        assertNull(state.durationStatus)
        assertNull(state.durationIncompleteReason)
    }

    @Test
    fun readsAreDetachedFromSourceCollectionsAndRetainHistoricalSnapshotValues() {
        val source = aggregate()
        val persistence = InMemoryLocalPersistence(source)

        (source.records as MutableMap).clear()
        (source.events as MutableList).clear()

        val actual = persistence.read()
        assertEquals(7, actual.records.size)
        assertEquals(9, actual.events.size)
        assertEquals("Counter at log", actual.events.single { it.id == EventId("counter-alpha") }.snapshot.recordName)
        assertEquals("Counter current", actual.records.getValue(RecordId("counter")).name)
    }

    @Test
    fun eventQueriesUseExactRecordAndTargetScopesAndAscendingTimeThenSequence() {
        val queries = InMemoryPersistenceQueries(committedPersistence())
        val chronological = queries.readEvents()

        assertEquals(
            listOf(
                "duration-no-target-open", "counter-alpha", "duration-alpha-open", "moment-alpha", "moment-beta",
                "state-alpha-active", "state-no-target-active", "state-alpha-prior", "duration-alpha-completed",
            ),
            chronological.map { it.eventId },
        )
        assertEquals(listOf(3L, 5L, 9L), chronological.filter { it.occurredAt == 300L }.map { it.sequence })
        assertEquals(
            listOf("duration-no-target-open", "duration-alpha-open", "duration-alpha-completed"),
            queries.readRecordEvents(RecordId("duration")).map { it.eventId },
        )

        assertEquals(
            listOf("duration-alpha-open", "duration-alpha-completed"),
            queries.readScopeEvents(RecordId("duration"), TargetId("alpha")).map { it.eventId },
        )
        assertEquals(
            listOf("duration-no-target-open"),
            queries.readScopeEvents(RecordId("duration"), null).map { it.eventId },
        )
        assertEquals(emptyList<String>(), queries.readScopeEvents(RecordId("duration"), TargetId("beta")).map { it.eventId })
        assertEquals(listOf("moment-beta"), queries.readScopeEvents(RecordId("other"), TargetId("beta")).map { it.eventId })
    }

    @Test
    fun durationAndStateSelectionRespectOpenStatusAndActiveScopeGeneration() {
        val queries = InMemoryPersistenceQueries(committedPersistence())
        val alphaScope = StateScope(StateGroupId("group"), TargetId("alpha"))

        assertEquals("duration-alpha-open", queries.readOpenDuration(RecordId("duration"), TargetId("alpha"))?.eventId)
        assertEquals("duration-no-target-open", queries.readOpenDuration(RecordId("duration"), null)?.eventId)
        assertNull(queries.readOpenDuration(RecordId("duration"), TargetId("beta")))
        assertEquals("state-alpha-active", queries.readCurrentState(alphaScope)?.eventId)
        assertEquals("state-no-target-active", queries.readCurrentState(StateScope(StateGroupId("group"), null))?.eventId)
    }

    private fun committedPersistence(): InMemoryLocalPersistence {
        val persistence = InMemoryLocalPersistence()
        val expected = aggregate()
        assertTrue(persistence.commit(CommitOperation(persistence.read(), expected)))
        return persistence
    }

    private object TestClock : io.github.thrhead.taplog.core.engine.AcceptanceClock {
        override fun now() = EpochMillis(100)
    }

    private fun aggregate(): DomainState {
        val alpha = TargetId("alpha")
        val beta = TargetId("beta")
        val group = StateGroupId("group")
        val alphaScope = StateScope(group, alpha)
        val noTargetScope = StateScope(group, null)
        val records = listOf(
            Record(RecordId("moment"), "Moment current", null, Behavior.MOMENT, revision = Revision(2), hasEvents = true),
            Record(RecordId("counter"), "Counter current", "cup", Behavior.COUNTER, Lifecycle.ARCHIVED,
                UnitName("cups"), Quantity.exact("2.00"), revision = Revision(3), hasEvents = true),
            Record(RecordId("duration"), "Duration current", null, Behavior.DURATION, revision = Revision(4), hasEvents = true),
            Record(RecordId("state-prior"), "State prior", null, Behavior.STATE, stateGroupId = group, hasEvents = true),
            Record(RecordId("state-active"), "State active", null, Behavior.STATE, stateGroupId = group, hasEvents = true),
            Record(RecordId("state-no-target"), "State no target", null, Behavior.STATE, stateGroupId = group, hasEvents = true),
            Record(RecordId("other"), "Other current", null, Behavior.MOMENT, hasEvents = true),
        ).associateBy { it.id }
        val events = mutableListOf(
            event("duration-no-target-open", "duration", null, Behavior.DURATION, 100, 1,
                EventPayload.Duration(EpochMillis(90))),
            event("counter-alpha", "counter", alpha, Behavior.COUNTER, 200, 2,
                EventPayload.Counter(Quantity.exact("1.2500")!!, UnitName("cups")), "Counter at log", "Alpha at log"),
            event("duration-alpha-open", "duration", alpha, Behavior.DURATION, 300, 3,
                EventPayload.Duration(EpochMillis(250))),
            event("duration-alpha-completed", "duration", alpha, Behavior.DURATION, 900, 4,
                EventPayload.Duration(EpochMillis(200), EpochMillis(899), DurationStatus.COMPLETED)),
            event("moment-alpha", "moment", alpha, Behavior.MOMENT, 300, 5, EventPayload.Moment),
            event("state-alpha-prior", "state-prior", alpha, Behavior.STATE, 400, 6,
                EventPayload.State(group, DatasetGeneration(1))),
            event("state-alpha-active", "state-active", alpha, Behavior.STATE, 350, 7,
                EventPayload.State(group, DatasetGeneration(2))),
            event("state-no-target-active", "state-no-target", null, Behavior.STATE, 350, 8,
                EventPayload.State(group, DatasetGeneration(5))),
            event("moment-beta", "other", beta, Behavior.MOMENT, 300, 9, EventPayload.Moment),
        )
        val binding = BindingLifecycle(
            BindingId("binding-alpha"), RecordId("counter"), alpha, BindingStatus.ORPHANED, Revision(8),
            DisplaySnapshot("Counter at binding", "cup", "Alpha at binding", null),
            listOf(UndoInvalidation(ReceiptId("invalidated"), ResultReason.STALE_REVISION)),
        )
        val noTargetBinding = BindingLifecycle(
            BindingId("binding-no-target"), RecordId("duration"), null,
            lastKnownDisplay = DisplaySnapshot("Duration at binding", null, null, null),
        )
        val receipt = UndoReceipt(
            ReceiptId("receipt"), EventId("state-alpha-active"), Revision(5), DatasetGeneration(7),
            ScopeContext(alphaScope, DatasetGeneration(2)),
        )
        return DomainState(
            records = records,
            targets = mapOf(
                alpha to Target(alpha, "Alpha current", "alpha", Lifecycle.ARCHIVED, Revision(5)),
                beta to Target(beta, "Beta current", null, revision = Revision(6)),
            ),
            relationships = mapOf(
                (RecordId("counter") to alpha) to RecordTarget(RecordId("counter"), alpha, false, Revision(7)),
                (RecordId("duration") to alpha) to RecordTarget(RecordId("duration"), alpha, true, Revision(8)),
            ),
            events = events,
            stateGroups = mapOf(group to StateGroup(group, "States", Revision(9))),
            generation = DatasetGeneration(7),
            nextSequence = Sequence(20),
            currentStates = mapOf(alphaScope to RecordId("state-active"), noTargetScope to RecordId("state-no-target")),
            stateGenerations = mapOf(alphaScope to DatasetGeneration(2), noTargetScope to DatasetGeneration(5)),
            bindings = mapOf(binding.bindingId to binding, noTargetBinding.bindingId to noTargetBinding),
            undoReceipts = mapOf(receipt.receiptId to receipt),
        )
    }

    private fun event(
        id: String,
        record: String,
        targetId: TargetId?,
        behavior: Behavior,
        occurredAt: Long,
        sequence: Long,
        payload: EventPayload,
        snapshotRecordName: String = "$record at log",
        snapshotTargetName: String? = targetId?.let { "${it.value.replaceFirstChar(Char::uppercase)} at log" },
    ) = Event(
        EventId(id), RecordId(record), targetId, behavior, EpochMillis(occurredAt), EpochMillis(50), EpochMillis(60),
        Sequence(sequence), Source.NFC, Revision(sequence),
        EventSnapshot(snapshotRecordName, null, snapshotTargetName, null, behavior,
            if (behavior == Behavior.COUNTER) UnitName("cups") else null),
        payload,
    )
}
