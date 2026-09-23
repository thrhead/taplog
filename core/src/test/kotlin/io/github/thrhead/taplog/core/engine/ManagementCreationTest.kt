package io.github.thrhead.taplog.core.engine

import io.github.thrhead.taplog.core.domain.Behavior
import io.github.thrhead.taplog.core.domain.DatasetGeneration
import io.github.thrhead.taplog.core.domain.EpochMillis
import io.github.thrhead.taplog.core.domain.Lifecycle
import io.github.thrhead.taplog.core.domain.Quantity
import io.github.thrhead.taplog.core.domain.Record
import io.github.thrhead.taplog.core.domain.RecordId
import io.github.thrhead.taplog.core.domain.ResultReason
import io.github.thrhead.taplog.core.domain.Revision
import io.github.thrhead.taplog.core.domain.Source
import io.github.thrhead.taplog.core.domain.StateGroup
import io.github.thrhead.taplog.core.domain.StateGroupId
import io.github.thrhead.taplog.core.domain.Target
import io.github.thrhead.taplog.core.domain.TargetId
import io.github.thrhead.taplog.core.domain.UnitName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ManagementCreationTest {
    @Test
    fun recordCreationSupportsEveryBehaviorWithoutCreatingAnEvent() {
        val stateGroup = StateGroup(StateGroupId("moods"), "Moods")
        val records = listOf(
            Record(RecordId("moment"), "Water", null, Behavior.MOMENT),
            Record(RecordId("counter"), "Coffee", null, Behavior.COUNTER),
            Record(RecordId("duration"), "Focus", null, Behavior.DURATION),
            Record(RecordId("state"), "Mood", null, Behavior.STATE, stateGroupId = stateGroup.id),
        )
        val boundary = Boundary(DomainState(stateGroups = mapOf(stateGroup.id to stateGroup)))
        val engine = engine(boundary)

        records.forEach { record ->
            assertEquals(
                EngineResult.Applied(revisions = mapOf(record.id.value to Revision(0))),
                engine.apply(CreateRecord(record, source = Source.APP)),
            )
            assertEquals(record, boundary.state.records[record.id])
            assertEquals(Lifecycle.ACTIVE, boundary.state.records.getValue(record.id).lifecycle)
        }

        assertTrue(boundary.state.events.isEmpty())
    }

    @Test
    fun counterCreationDefaultsAnOmittedQuantityToOne() {
        val record = Record(RecordId("coffee"), "Coffee", null, Behavior.COUNTER)
        val boundary = Boundary(DomainState())

        assertEquals(
            EngineResult.Applied(revisions = mapOf("coffee" to Revision(0))),
            engine(boundary).apply(CreateRecord(record, source = Source.APP)),
        )
        assertEquals(Quantity.exact("1"), boundary.state.records.getValue(record.id).defaultQuantity)
    }

    @Test
    fun counterCreationPreservesOneOptionalValidUnit() {
        val record = Record(
            RecordId("water"), "Water", null, Behavior.COUNTER,
            unit = UnitName("cups"), defaultQuantity = Quantity.exact("2"),
        )
        val boundary = Boundary(DomainState())

        assertEquals(
            EngineResult.Applied(revisions = mapOf("water" to Revision(0))),
            engine(boundary).apply(CreateRecord(record, source = Source.APP)),
        )
        assertEquals(UnitName("cups"), boundary.state.records.getValue(record.id).unit)
        assertEquals(Quantity.parse("2"), boundary.state.records.getValue(record.id).defaultQuantity)
    }

    @Test
    fun counterCreationRejectsABlankOptionalUnit() {
        val record = Record(
            RecordId("water"), "Water", null, Behavior.COUNTER, unit = UnitName(" "),
        )
        val boundary = Boundary(DomainState())

        assertEquals(
            EngineResult.Invalid(ResultReason.UNIT_MISMATCH),
            engine(boundary).apply(CreateRecord(record, source = Source.APP)),
        )
        assertFalse(boundary.committed)
    }

    @Test
    fun counterCreationRejectsZeroNegativeAndMalformedDefaultQuantities() {
        val invalidQuantities = listOf("0", "-1", "not-a-number")

        invalidQuantities.forEach { quantity ->
            val boundary = Boundary(DomainState())
            val record = Record(
                RecordId("counter-$quantity"), "Counter", null, Behavior.COUNTER,
                defaultQuantity = Quantity.parse(quantity),
            )

            assertEquals(
                EngineResult.Invalid(ResultReason.INVALID_QUANTITY),
                engine(boundary).apply(CreateRecord(record, source = Source.APP)),
            )
            assertFalse(boundary.committed)
            assertTrue(boundary.state.records.isEmpty())
        }
    }

    @Test
    fun recordCreationRejectsBlankNamesWithoutCommitting() {
        listOf("", "  ").forEach { name ->
            val boundary = Boundary(DomainState())
            val record = Record(RecordId("record-${name.length}"), name, null, Behavior.MOMENT)

            assertEquals(
                EngineResult.Invalid(ResultReason.INVALID_REQUEST),
                engine(boundary).apply(CreateRecord(record, source = Source.APP)),
            )
            assertFalse(boundary.committed)
        }
    }

    @Test
    fun recordCreationRejectsFieldsOutsideTheSelectedBehavior() {
        val stateGroup = StateGroup(StateGroupId("moods"), "Moods")
        val invalidRecords = listOf(
            Record(RecordId("moment"), "Water", null, Behavior.MOMENT, unit = UnitName("cups")),
            Record(RecordId("duration"), "Focus", null, Behavior.DURATION, unit = UnitName("minutes")),
            Record(RecordId("counter"), "Coffee", null, Behavior.COUNTER, stateGroupId = stateGroup.id),
            Record(
                RecordId("state"), "Mood", null, Behavior.STATE,
                unit = UnitName("levels"), stateGroupId = stateGroup.id,
            ),
        )
        val boundary = Boundary(DomainState(stateGroups = mapOf(stateGroup.id to stateGroup)))
        val engine = engine(boundary)

        invalidRecords.forEach { record ->
            assertEquals(
                EngineResult.Invalid(ResultReason.INVALID_REQUEST),
                engine.apply(CreateRecord(record, source = Source.APP)),
            )
        }
        assertFalse(boundary.committed)
        assertTrue(boundary.state.records.isEmpty())
    }

    @Test
    fun recordCreationRejectsExplicitCounterQuantityForNonCounterBehaviors() {
        val stateGroup = StateGroup(StateGroupId("moods"), "Moods")
        val invalidRecords = listOf(
            Record(
                RecordId("moment-quantity"), "Water", null, Behavior.MOMENT,
                defaultQuantity = Quantity.exact("2"),
            ),
            Record(
                RecordId("duration-quantity"), "Focus", null, Behavior.DURATION,
                defaultQuantity = Quantity.exact("2"),
            ),
            Record(
                RecordId("state-quantity"), "Mood", null, Behavior.STATE,
                defaultQuantity = Quantity.exact("2"), stateGroupId = stateGroup.id,
            ),
        )
        val boundary = Boundary(DomainState(stateGroups = mapOf(stateGroup.id to stateGroup)))
        val engine = engine(boundary)

        invalidRecords.forEach { record ->
            assertEquals(
                EngineResult.Invalid(ResultReason.INVALID_REQUEST),
                engine.apply(CreateRecord(record, source = Source.APP)),
            )
        }
        assertFalse(boundary.committed)
        assertTrue(boundary.state.records.isEmpty())
    }

    @Test
    fun stateRecordCreationRequiresAnExistingStateGroup() {
        val missingGroup = Record(RecordId("state-missing"), "Mood", null, Behavior.STATE)
        val unknownGroup = Record(
            RecordId("state-unknown"), "Mood", null, Behavior.STATE,
            stateGroupId = StateGroupId("unknown"),
        )

        listOf(missingGroup, unknownGroup).forEach { record ->
            val boundary = Boundary(DomainState())

            assertEquals(
                EngineResult.Invalid(ResultReason.MISSING_STATE_GROUP),
                engine(boundary).apply(CreateRecord(record, source = Source.APP)),
            )
            assertFalse(boundary.committed)
        }
    }

    @Test
    fun targetCreationRequiresANonBlankName() {
        listOf("", "  ").forEach { name ->
            val boundary = Boundary(DomainState())
            val target = Target(TargetId("target-${name.length}"), name, null)

            assertEquals(
                EngineResult.Invalid(ResultReason.INVALID_REQUEST),
                engine(boundary).apply(CreateTarget(target, source = Source.APP)),
            )
            assertFalse(boundary.committed)
        }
    }

    @Test
    fun creationPreservesCallerAssignedIdsAtInitialRevisionWithoutGlobalRevisionMutation() {
        val record = Record(RecordId("water"), "Water", null, Behavior.MOMENT)
        val target = Target(TargetId("bottle"), "Bottle", null)
        val boundary = Boundary(DomainState(generation = DatasetGeneration(7)))
        val engine = engine(boundary)

        assertEquals(
            EngineResult.Applied(revisions = mapOf("water" to Revision(0))),
            engine.apply(CreateRecord(record, source = Source.APP)),
        )
        assertEquals(
            EngineResult.Applied(revisions = mapOf("bottle" to Revision(0))),
            engine.apply(CreateTarget(target, source = Source.APP)),
        )

        assertEquals(record.id, boundary.state.records.getValue(record.id).id)
        assertEquals(Revision(0), boundary.state.records.getValue(record.id).revision)
        assertEquals(Lifecycle.ACTIVE, boundary.state.records.getValue(record.id).lifecycle)
        assertEquals(target.id, boundary.state.targets.getValue(target.id).id)
        assertEquals("Bottle", boundary.state.targets.getValue(target.id).name)
        assertEquals(Revision(0), boundary.state.targets.getValue(target.id).revision)
        assertEquals(Lifecycle.ACTIVE, boundary.state.targets.getValue(target.id).lifecycle)
        assertEquals(DatasetGeneration(7), boundary.state.generation)
    }

    @Test
    fun creationDoesNotOverwriteAnExistingImmutableIdentity() {
        val existingRecord = Record(RecordId("water"), "Water", null, Behavior.MOMENT)
        val existingTarget = Target(TargetId("bottle"), "Bottle", null)
        val boundary = Boundary(
            DomainState(
                records = mapOf(existingRecord.id to existingRecord),
                targets = mapOf(existingTarget.id to existingTarget),
            ),
        )
        val engine = engine(boundary)

        assertEquals(
            EngineResult.Conflict(ResultReason.STALE_REVISION),
            engine.apply(CreateRecord(existingRecord.copy(name = "Replacement"), source = Source.APP)),
        )
        assertEquals(
            EngineResult.Conflict(ResultReason.STALE_REVISION),
            engine.apply(CreateTarget(existingTarget.copy(name = "Replacement"), source = Source.APP)),
        )
        assertEquals(existingRecord, boundary.state.records.getValue(existingRecord.id))
        assertEquals(existingTarget, boundary.state.targets.getValue(existingTarget.id))
        assertFalse(boundary.committed)
    }

    @Test
    fun staleCreationDatasetContextConflictsWithoutCommit() {
        val before = DomainState(generation = DatasetGeneration(7))
        val boundary = Boundary(before)
        val engine = engine(boundary)
        val stale = ExpectedContext(datasetGeneration = DatasetGeneration(6))

        assertEquals(
            EngineResult.Conflict(ResultReason.STALE_DATASET_GENERATION),
            engine.apply(CreateRecord(
                Record(RecordId("water"), "Water", null, Behavior.MOMENT),
                source = Source.APP,
                expected = stale,
            )),
        )
        assertEquals(
            EngineResult.Conflict(ResultReason.STALE_DATASET_GENERATION),
            engine.apply(CreateTarget(
                Target(TargetId("bottle"), "Bottle", null),
                source = Source.APP,
                expected = stale,
            )),
        )
        assertEquals(before, boundary.state)
        assertEquals(0, boundary.commitAttempts)
    }

    @Test
    fun rejectedCreationCommitsAreStorageFailuresAndPreserveState() {
        val before = DomainState(generation = DatasetGeneration(7))
        val recordBoundary = Boundary(before, rejectCommits = true)
        val targetBoundary = Boundary(before, rejectCommits = true)

        assertEquals(
            EngineResult.StorageFailure,
            engine(recordBoundary).apply(CreateRecord(
                Record(RecordId("water"), "Water", null, Behavior.MOMENT),
                source = Source.APP,
                expected = ExpectedContext(datasetGeneration = before.generation),
            )),
        )
        assertEquals(
            EngineResult.StorageFailure,
            engine(targetBoundary).apply(CreateTarget(
                Target(TargetId("bottle"), "Bottle", null),
                source = Source.APP,
                expected = ExpectedContext(datasetGeneration = before.generation),
            )),
        )
        assertEquals(before, recordBoundary.state)
        assertEquals(before, targetBoundary.state)
        assertEquals(1, recordBoundary.commitAttempts)
        assertEquals(1, targetBoundary.commitAttempts)
    }

    private fun engine(boundary: Boundary) = EventEngine(boundary, object : AcceptanceClock {
        override fun now() = EpochMillis(1_000)
    })

    private class Boundary(initial: DomainState, private val rejectCommits: Boolean = false) : AtomicCommitBoundary {
        var state = initial
        var committed = false
        var commitAttempts = 0

        override fun read() = state

        override fun commit(operation: CommitOperation): Boolean {
            commitAttempts += 1
            if (rejectCommits || operation.expected != state) return false
            state = operation.state
            committed = true
            return true
        }
    }
}
