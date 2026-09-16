package io.github.thrhead.taplog.core.engine

import io.github.thrhead.taplog.core.domain.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class LifecycleEffectResultTest {
    @Test fun archiveTargetEmitsCommittedLifecycleEffectForTerminalDurationsStateResetsBindingsAndUndo() {
        val fixture = Fixture()

        val result = fixture.engine.archiveTarget(fixture.target.id) as EngineResult.Applied

        val invalidations = listOf(
            UndoInvalidation(fixture.durationReceipt.receiptId, ResultReason.STALE_REVISION),
            UndoInvalidation(fixture.stateReceipt.receiptId, ResultReason.STALE_REVISION),
        )
        val expected = LifecycleEffect(
            durationEvents = listOf(fixture.durationEvent.copy(
                updatedAt = Clock.now(),
                revision = Revision(1),
                payload = EventPayload.Duration(EpochMillis(10), status = DurationStatus.INCOMPLETE,
                    incompleteReason = "scope became inactive"),
            )),
            resetScopes = listOf(fixture.stateScope),
            orphanedBindings = listOf(
                fixture.durationBinding.copy(status = BindingStatus.ORPHANED, revision = Revision(1),
                    undoInvalidations = listOf(invalidations[0])),
                fixture.stateBinding.copy(status = BindingStatus.ORPHANED, revision = Revision(1),
                    undoInvalidations = listOf(invalidations[1])),
            ),
            invalidatedUndo = invalidations,
        )

        assertEquals(expected, result.lifecycleEffect)
        assertEquals(expected, fixture.boundary.lastOperation?.lifecycleEffect)
    }

    @Test fun unlinkEmitsOnlyTheAffectedScopeLifecycleEffect() {
        val fixture = Fixture()

        val result = fixture.engine.unlink(fixture.durationRecord.id, fixture.target.id) as EngineResult.Applied

        val invalidation = UndoInvalidation(fixture.durationReceipt.receiptId, ResultReason.STALE_REVISION)
        val expected = LifecycleEffect(
            durationEvents = listOf(fixture.durationEvent.copy(
                updatedAt = Clock.now(),
                revision = Revision(1),
                payload = EventPayload.Duration(EpochMillis(10), status = DurationStatus.INCOMPLETE,
                    incompleteReason = "scope became inactive"),
            )),
            orphanedBindings = listOf(fixture.durationBinding.copy(
                status = BindingStatus.ORPHANED,
                revision = Revision(1),
                undoInvalidations = listOf(invalidation),
            )),
            invalidatedUndo = listOf(invalidation),
        )

        assertEquals(expected, result.lifecycleEffect)
        assertEquals(expected, fixture.boundary.lastOperation?.lifecycleEffect)
    }

    @Test fun rejectedUnlinkLifecycleCommitReturnsStorageFailureWithoutAppliedEffectOrMutation() {
        val fixture = Fixture(acceptCommits = false)
        val before = fixture.boundary.state

        val result = fixture.engine.unlink(fixture.durationRecord.id, fixture.target.id)

        assertEquals(EngineResult.StorageFailure, result)
        assertFalse(result is EngineResult.Applied)
        assertEquals(before, fixture.boundary.state)
        assertEquals(null, fixture.boundary.lastOperation)
    }

    private class Fixture(acceptCommits: Boolean = true) {
        val durationRecord = Record(RecordId("duration"), "Run", "run", Behavior.DURATION)
        val stateRecord = Record(RecordId("state"), "Mood", "mood", Behavior.STATE, stateGroupId = StateGroupId("mood"))
        val target = Target(TargetId("target"), "Home", "home")
        val stateScope = StateScope(StateGroupId("mood"), target.id)
        val durationEvent = event(EventId("duration-event"), durationRecord, EventPayload.Duration(EpochMillis(10)))
        val stateEvent = event(EventId("state-event"), stateRecord, EventPayload.State(StateGroupId("mood"), DatasetGeneration(0)))
        val durationBinding = binding(BindingId("duration-binding"), durationRecord)
        val stateBinding = binding(BindingId("state-binding"), stateRecord)
        val durationReceipt = UndoReceipt(ReceiptId("duration-receipt"), durationEvent.id, durationEvent.revision, DatasetGeneration(0))
        val stateReceipt = UndoReceipt(ReceiptId("state-receipt"), stateEvent.id, stateEvent.revision, DatasetGeneration(0),
            ScopeContext(stateScope, DatasetGeneration(0)))
        val boundary = Boundary(DomainState(
            records = mapOf(durationRecord.id to durationRecord, stateRecord.id to stateRecord),
            targets = mapOf(target.id to target),
            relationships = mapOf(
                (durationRecord.id to target.id) to RecordTarget(durationRecord.id, target.id),
                (stateRecord.id to target.id) to RecordTarget(stateRecord.id, target.id),
            ),
            events = listOf(durationEvent, stateEvent),
            stateGroups = mapOf(StateGroupId("mood") to StateGroup(StateGroupId("mood"), "Mood")),
            currentStates = mapOf(stateScope to stateRecord.id),
            stateGenerations = mapOf(stateScope to DatasetGeneration(0)),
            bindings = mapOf(durationBinding.bindingId to durationBinding, stateBinding.bindingId to stateBinding),
            undoReceipts = mapOf(durationReceipt.receiptId to durationReceipt, stateReceipt.receiptId to stateReceipt),
        ), acceptCommits)
        val engine = EventEngine(boundary, Clock)

        private fun event(id: EventId, record: Record, payload: EventPayload) = Event(
            id, record.id, target.id, record.behavior, EpochMillis(10), EpochMillis(10), EpochMillis(10),
            Sequence(1), Source.APP, Revision(0),
            EventSnapshot(record.name, record.icon, target.name, target.icon, record.behavior, record.unit), payload,
        )

        private fun binding(id: BindingId, record: Record) = BindingLifecycle(
            id, record.id, target.id, lastKnownDisplay = DisplaySnapshot(record.name, record.icon, target.name, target.icon),
        )
    }

    private object Clock : AcceptanceClock { override fun now() = EpochMillis(100) }

    private class Boundary(var state: DomainState, private val acceptCommits: Boolean = true) : AtomicCommitBoundary {
        var lastOperation: CommitOperation? = null
        override fun read() = state
        override fun commit(operation: CommitOperation): Boolean {
            if (!acceptCommits || operation.expected != state) return false
            lastOperation = operation
            state = operation.state
            return true
        }
    }
}
