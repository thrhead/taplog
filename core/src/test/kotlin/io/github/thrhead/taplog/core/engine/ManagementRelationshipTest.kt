package io.github.thrhead.taplog.core.engine

import io.github.thrhead.taplog.core.domain.*
import io.github.thrhead.taplog.core.domain.Target
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ManagementRelationshipTest {
    @Test
    fun linkRequiresActiveExistingDefinitionsAndMakesThePairEligibleForFutureEvents() {
        val record = Record(RecordId("water"), "Water", "drop", Behavior.MOMENT)
        val archivedRecord = Record(RecordId("archived-water"), "Archived Water", "drop", Behavior.MOMENT,
            lifecycle = Lifecycle.ARCHIVED)
        val active = Target(TargetId("bottle"), "Bottle", "bottle")
        val archived = Target(TargetId("shelf"), "Shelf", "shelf", Lifecycle.ARCHIVED)
        val boundary = Boundary(DomainState(
            records = mapOf(record.id to record, archivedRecord.id to archivedRecord),
            targets = mapOf(active.id to active, archived.id to archived),
            generation = DatasetGeneration(7),
        ))
        val engine = engine(boundary)

        assertEquals(EngineResult.Applied(), engine.link(record.id, active.id))
        assertEquals(RecordTarget(record.id, active.id), boundary.state.relationships.getValue(record.id to active.id))
        assertTrue(engine.apply(LogMoment(record.id, active.id, source = Source.APP)) is EngineResult.Applied)

        val afterActiveLink = boundary.state
        assertEquals(EngineResult.Invalid(ResultReason.INACTIVE_SCOPE), engine.link(archivedRecord.id, active.id))
        assertEquals(EngineResult.Invalid(ResultReason.INACTIVE_SCOPE), engine.link(RecordId("missing-record"), active.id))
        assertEquals(EngineResult.Invalid(ResultReason.INACTIVE_SCOPE), engine.link(record.id, archived.id))
        assertEquals(EngineResult.Invalid(ResultReason.INACTIVE_SCOPE), engine.link(record.id, TargetId("missing")))
        assertEquals(afterActiveLink, boundary.state)
    }

    @Test
    fun unlinkAdvancesOnlyItsPairAndAppliesItsTerminalOrphanAndStateEffects() {
        val fixture = LifecycleFixture()

        assertTrue(fixture.engine.unlink(fixture.durationRecord.id, fixture.target.id) is EngineResult.Applied)
        assertEquals(RecordTarget(fixture.durationRecord.id, fixture.target.id, linked = false, revision = Revision(5)),
            fixture.boundary.state.relationships.getValue(fixture.durationRecord.id to fixture.target.id))
        assertEquals(fixture.stateRelationship, fixture.boundary.state.relationships.getValue(fixture.stateRecord.id to fixture.target.id))
        assertEquals(fixture.durationEvent.copy(updatedAt = EpochMillis(1_000), revision = Revision(1),
            payload = EventPayload.Duration(EpochMillis(10), status = DurationStatus.INCOMPLETE,
                incompleteReason = "scope became inactive")), fixture.boundary.state.events.first())
        assertEquals(fixture.stateEvent, fixture.boundary.state.events.last())
        assertEquals(fixture.durationBinding.copy(status = BindingStatus.ORPHANED, revision = Revision(3),
            undoInvalidations = listOf(UndoInvalidation(fixture.durationReceipt.receiptId, ResultReason.STALE_REVISION))),
            fixture.boundary.state.bindings.getValue(fixture.durationBinding.bindingId))
        assertEquals(fixture.stateBinding, fixture.boundary.state.bindings.getValue(fixture.stateBinding.bindingId))
        assertFalse(fixture.durationReceipt.receiptId in fixture.boundary.state.undoReceipts)
        val afterDurationUnlink = fixture.boundary.state
        assertEquals(EngineResult.Invalid(ResultReason.UNLINKED_RELATIONSHIP),
            fixture.engine.apply(StartDuration(fixture.durationRecord.id, fixture.target.id, source = Source.APP)))
        assertEquals(afterDurationUnlink, fixture.boundary.state)

        assertTrue(fixture.engine.unlink(fixture.stateRecord.id, fixture.target.id) is EngineResult.Applied)
        assertEquals(RecordTarget(fixture.stateRecord.id, fixture.target.id, linked = false, revision = Revision(9)),
            fixture.boundary.state.relationships.getValue(fixture.stateRecord.id to fixture.target.id))
        assertEquals(DatasetGeneration(4), fixture.boundary.state.stateGenerations.getValue(fixture.stateScope))
        assertFalse(fixture.stateScope in fixture.boundary.state.currentStates)
        assertEquals(fixture.stateBinding.copy(status = BindingStatus.ORPHANED, revision = Revision(5),
            undoInvalidations = listOf(UndoInvalidation(fixture.stateReceipt.receiptId, ResultReason.STALE_REVISION))),
            fixture.boundary.state.bindings.getValue(fixture.stateBinding.bindingId))
        assertFalse(fixture.stateReceipt.receiptId in fixture.boundary.state.undoReceipts)
    }

    @Test
    fun explicitRelinkAdvancesThePairEnablesFutureEventsAndPreservesHistoricalLifecycleState() {
        val record = Record(RecordId("water"), "Water", "drop", Behavior.MOMENT)
        val target = Target(TargetId("bottle"), "Bottle", "bottle")
        val historicalEvent = event(EventId("old"), record, target, EventPayload.Moment)
        val terminalDuration = Event(EventId("terminal"), RecordId("run"), target.id, Behavior.DURATION,
            EpochMillis(10), EpochMillis(10), EpochMillis(10), Sequence(2), Source.APP, Revision(3),
            EventSnapshot("Run", "run", target.name, target.icon, Behavior.DURATION, null),
            EventPayload.Duration(EpochMillis(5), status = DurationStatus.INCOMPLETE, incompleteReason = "scope became inactive"))
        val scope = StateScope(StateGroupId("mood"), target.id)
        val orphan = BindingLifecycle(BindingId("old-binding"), record.id, target.id, BindingStatus.ORPHANED, Revision(6),
            DisplaySnapshot("Old Water", "old-drop", "Old Bottle", "old-bottle"))
        val boundary = Boundary(DomainState(
            records = mapOf(record.id to record),
            targets = mapOf(target.id to target),
            relationships = mapOf((record.id to target.id) to RecordTarget(record.id, target.id, linked = false, revision = Revision(5))),
            events = listOf(historicalEvent, terminalDuration),
            stateGenerations = mapOf(scope to DatasetGeneration(4)),
            bindings = mapOf(orphan.bindingId to orphan),
            generation = DatasetGeneration(9),
            nextSequence = Sequence(3),
        ))
        val engine = engine(boundary)

        assertEquals(EngineResult.Applied(), engine.relink(record.id, target.id))
        assertEquals(RecordTarget(record.id, target.id, linked = true, revision = Revision(6)),
            boundary.state.relationships.getValue(record.id to target.id))
        assertEquals(listOf(historicalEvent, terminalDuration), boundary.state.events)
        assertEquals(DatasetGeneration(4), boundary.state.stateGenerations.getValue(scope))
        assertEquals(orphan, boundary.state.bindings.getValue(orphan.bindingId))
        assertTrue(engine.apply(LogMoment(record.id, target.id, source = Source.APP)) is EngineResult.Applied)
        assertEquals(historicalEvent, boundary.state.events.first())
        assertEquals("Water", historicalEvent.snapshot.recordName)
        assertEquals("Bottle", historicalEvent.snapshot.targetName)
    }

    private fun engine(boundary: Boundary) = EventEngine(boundary, object : AcceptanceClock {
        override fun now() = EpochMillis(1_000)
    }, idGenerator = { EventId("new-event") })

    private fun event(id: EventId, record: Record, target: Target, payload: EventPayload) = Event(
        id, record.id, target.id, record.behavior, EpochMillis(10), EpochMillis(10), EpochMillis(10),
        Sequence(1), Source.APP, Revision(0),
        EventSnapshot(record.name, record.icon, target.name, target.icon, record.behavior, record.unit), payload,
    )

    private class LifecycleFixture {
        val target = Target(TargetId("home"), "Home", "home")
        val durationRecord = Record(RecordId("run"), "Run", "run", Behavior.DURATION)
        val stateRecord = Record(RecordId("mood"), "Mood", "mood", Behavior.STATE, stateGroupId = StateGroupId("mood"))
        val stateScope = StateScope(StateGroupId("mood"), target.id)
        val durationEvent = event(EventId("open"), durationRecord, target, EventPayload.Duration(EpochMillis(10)))
        val stateEvent = event(EventId("state"), stateRecord, target, EventPayload.State(StateGroupId("mood"), DatasetGeneration(3)))
        val durationBinding = binding(BindingId("run-binding"), durationRecord, Revision(2))
        val stateBinding = binding(BindingId("mood-binding"), stateRecord, Revision(4))
        val durationReceipt = UndoReceipt(ReceiptId("run-receipt"), durationEvent.id, durationEvent.revision, DatasetGeneration(2))
        val stateReceipt = UndoReceipt(ReceiptId("mood-receipt"), stateEvent.id, stateEvent.revision, DatasetGeneration(2),
            ScopeContext(stateScope, DatasetGeneration(3)))
        val stateRelationship = RecordTarget(stateRecord.id, target.id, revision = Revision(8))
        val boundary = Boundary(DomainState(
            records = mapOf(durationRecord.id to durationRecord, stateRecord.id to stateRecord),
            targets = mapOf(target.id to target),
            relationships = mapOf(
                (durationRecord.id to target.id) to RecordTarget(durationRecord.id, target.id, revision = Revision(4)),
                (stateRecord.id to target.id) to stateRelationship,
            ),
            events = listOf(durationEvent, stateEvent),
            stateGroups = mapOf(stateScope.groupId to StateGroup(stateScope.groupId, "Mood")),
            currentStates = mapOf(stateScope to stateRecord.id),
            stateGenerations = mapOf(stateScope to DatasetGeneration(3)),
            bindings = mapOf(durationBinding.bindingId to durationBinding, stateBinding.bindingId to stateBinding),
            undoReceipts = mapOf(durationReceipt.receiptId to durationReceipt, stateReceipt.receiptId to stateReceipt),
        ))
        val engine = EventEngine(boundary, object : AcceptanceClock { override fun now() = EpochMillis(1_000) })

        private fun event(id: EventId, record: Record, target: Target, payload: EventPayload) = Event(
            id, record.id, target.id, record.behavior, EpochMillis(10), EpochMillis(10), EpochMillis(10),
            Sequence(1), Source.APP, Revision(0),
            EventSnapshot(record.name, record.icon, target.name, target.icon, record.behavior, record.unit), payload,
        )

        private fun binding(id: BindingId, record: Record, revision: Revision) = BindingLifecycle(
            id, record.id, target.id, revision = revision,
            lastKnownDisplay = DisplaySnapshot(record.name, record.icon, target.name, target.icon),
        )
    }

    private class Boundary(initial: DomainState) : AtomicCommitBoundary {
        var state = initial
        override fun read() = state
        override fun commit(operation: CommitOperation): Boolean {
            if (operation.expected != state) return false
            state = operation.state
            return true
        }
    }
}
