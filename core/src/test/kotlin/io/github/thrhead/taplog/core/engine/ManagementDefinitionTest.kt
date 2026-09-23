package io.github.thrhead.taplog.core.engine

import io.github.thrhead.taplog.core.domain.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ManagementDefinitionTest {
    @Test
    fun counterDefinitionEditAfterEventsChangesOnlyPermittedFieldsAndPreservesHistory() {
        val record = Record(RecordId("coffee"), "Coffee", "cup", Behavior.COUNTER, unit = UnitName("cups"))
        val target = Target(TargetId("desk"), "Desk", "desk")
        val boundary = Boundary(DomainState(
            records = mapOf(record.id to record),
            targets = mapOf(target.id to target),
            relationships = mapOf((record.id to target.id) to RecordTarget(record.id, target.id)),
            generation = DatasetGeneration(7),
        ))
        val engine = engine(boundary)

        assertTrue(engine.apply(AddCounter(record.id, target.id, source = Source.APP)) is EngineResult.Applied)
        val event = boundary.state.events.single()
        val recordAfterEvent = boundary.state.records.getValue(record.id)
        assertEquals(
            EngineResult.Applied(revisions = mapOf(record.id.value to Revision(2))),
            engine.editRecord(record.id, RecordEdit(name = "Tea", icon = "teapot", defaultQuantity = Quantity.exact("2"))),
        )

        val edited = boundary.state.records.getValue(record.id)
        assertEquals("Tea", edited.name)
        assertEquals("teapot", edited.icon)
        assertEquals(Quantity.exact("2"), edited.defaultQuantity)
        assertEquals(record.behavior, edited.behavior)
        assertEquals(record.unit, edited.unit)
        assertEquals(Revision(2), edited.revision)
        assertEquals(recordAfterEvent.id, edited.id)
        assertEquals(DatasetGeneration(7), boundary.state.generation)
        assertEquals(target, boundary.state.targets.getValue(target.id))
        assertEquals(event, boundary.state.events.single())
        assertEquals("Coffee", event.snapshot.recordName)
        assertEquals("cup", event.snapshot.recordIcon)
        assertEquals(Quantity.exact("1"), (event.payload as EventPayload.Counter).quantity)
    }

    @Test
    fun targetDefinitionEditAfterEventsAdvancesOnlyTargetRevisionAndPreservesSnapshot() {
        val record = Record(RecordId("water"), "Water", "drop", Behavior.MOMENT)
        val target = Target(TargetId("bottle"), "Bottle", "bottle")
        val boundary = Boundary(DomainState(
            records = mapOf(record.id to record),
            targets = mapOf(target.id to target),
            relationships = mapOf((record.id to target.id) to RecordTarget(record.id, target.id)),
        ))
        val engine = engine(boundary)

        assertTrue(engine.apply(LogMoment(record.id, target.id, source = Source.APP)) is EngineResult.Applied)
        val event = boundary.state.events.single()
        val recordAfterEvent = boundary.state.records.getValue(record.id)
        assertEquals(EngineResult.Applied(), engine.editTarget(target.id, TargetEdit("Glass", "glass")))

        val edited = boundary.state.targets.getValue(target.id)
        assertEquals(target.id, edited.id)
        assertEquals("Glass", edited.name)
        assertEquals("glass", edited.icon)
        assertEquals(Revision(1), edited.revision)
        assertEquals(recordAfterEvent, boundary.state.records.getValue(record.id))
        assertEquals(event, boundary.state.events.single())
        assertEquals("Bottle", event.snapshot.targetName)
        assertEquals("bottle", event.snapshot.targetIcon)
    }

    @Test
    fun postEventBehaviorAndCounterUnitChangesAreRejectedWithoutMutation() {
        val record = Record(RecordId("coffee"), "Coffee", null, Behavior.COUNTER, unit = UnitName("cups"))
        val boundary = Boundary(DomainState(records = mapOf(record.id to record)))
        val engine = engine(boundary)
        assertTrue(engine.apply(AddCounter(record.id, source = Source.APP)) is EngineResult.Applied)
        val afterEvent = boundary.state

        assertThrows(IllegalArgumentException::class.java) {
            engine.editRecord(record.id, RecordEdit(behavior = Behavior.MOMENT))
        }
        assertThrows(IllegalArgumentException::class.java) {
            engine.editRecord(record.id, RecordEdit(unit = UnitName("ml")))
        }

        assertEquals(afterEvent, boundary.state)
    }

    @Test
    fun recordAndTargetArchiveUnarchiveAdvanceOnlyTheirOwnRevisions() {
        val record = Record(RecordId("run"), "Run", null, Behavior.MOMENT)
        val target = Target(TargetId("park"), "Park", null)
        val boundary = Boundary(DomainState(records = mapOf(record.id to record), targets = mapOf(target.id to target)))
        val engine = engine(boundary)

        assertEquals(EngineResult.Applied(revisions = mapOf(record.id.value to Revision(1))), engine.archiveRecord(record.id))
        assertEquals(Revision(1), boundary.state.records.getValue(record.id).revision)
        assertEquals(Lifecycle.ARCHIVED, boundary.state.records.getValue(record.id).lifecycle)
        assertEquals(target, boundary.state.targets.getValue(target.id))
        assertEquals(EngineResult.Applied(revisions = mapOf(record.id.value to Revision(2))), engine.unarchiveRecord(record.id))
        assertEquals(Revision(2), boundary.state.records.getValue(record.id).revision)
        assertEquals(Lifecycle.ACTIVE, boundary.state.records.getValue(record.id).lifecycle)

        assertEquals(EngineResult.Applied(), engine.archiveTarget(target.id))
        assertEquals(Revision(1), boundary.state.targets.getValue(target.id).revision)
        assertEquals(Lifecycle.ARCHIVED, boundary.state.targets.getValue(target.id).lifecycle)
        assertEquals(Revision(2), boundary.state.records.getValue(record.id).revision)
        assertEquals(EngineResult.Applied(), engine.unarchiveTarget(target.id))
        assertEquals(Revision(2), boundary.state.targets.getValue(target.id).revision)
        assertEquals(Lifecycle.ACTIVE, boundary.state.targets.getValue(target.id).lifecycle)
    }

    @Test
    fun staleExpectedRecordRevisionAndDatasetContextsReturnConflictWithoutCommit() {
        val record = Record(RecordId("coffee"), "Coffee", null, Behavior.COUNTER)
        val state = DomainState(records = mapOf(record.id to record), generation = DatasetGeneration(4))
        val boundary = Boundary(state)
        val engine = engine(boundary)

        assertEquals(
            EngineResult.Conflict(ResultReason.STALE_REVISION),
            engine.apply(AddCounter(record.id, source = Source.APP, expected = ExpectedContext(revision = Revision(1)))),
        )
        assertEquals(
            EngineResult.Conflict(ResultReason.STALE_DATASET_GENERATION),
            engine.apply(AddCounter(record.id, source = Source.APP, expected = ExpectedContext(datasetGeneration = DatasetGeneration(5)))),
        )
        assertEquals(state, boundary.state)
        assertFalse(boundary.committed)
    }

    @Test
    fun absentDefinitionContextsAreInvalidWithoutCommit() {
        val boundary = Boundary(DomainState())
        val engine = engine(boundary)

        assertEquals(
            EngineResult.Invalid(ResultReason.INVALID_REQUEST),
            engine.editRecord(RecordId("missing-record"), RecordEdit(name = "Replacement")),
        )
        assertEquals(
            EngineResult.Invalid(ResultReason.INVALID_REQUEST),
            engine.editTarget(TargetId("missing-target"), TargetEdit(name = "Replacement")),
        )
        assertFalse(boundary.committed)
    }

    @Test
    fun commitTimeRejectionOfDefinitionEditIsStorageFailureAndDoesNotMutateState() {
        val record = Record(RecordId("water"), "Water", null, Behavior.MOMENT)
        val boundary = Boundary(DomainState(records = mapOf(record.id to record)), rejectCommits = true)

        assertEquals(EngineResult.StorageFailure, engine(boundary).editRecord(record.id, RecordEdit(name = "Tea")))
        assertEquals(record, boundary.state.records.getValue(record.id))
        assertTrue(boundary.committed)
    }

    private fun engine(boundary: Boundary) = EventEngine(boundary, object : AcceptanceClock {
        override fun now() = EpochMillis(1_000)
    })

    private class Boundary(initial: DomainState, private val rejectCommits: Boolean = false) : AtomicCommitBoundary {
        var state = initial
        var committed = false

        override fun read() = state

        override fun commit(operation: CommitOperation): Boolean {
            committed = true
            if (rejectCommits || operation.expected != state) return false
            state = operation.state
            return true
        }
    }
}
