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
import io.github.thrhead.taplog.core.engine.AcceptanceClock
import io.github.thrhead.taplog.core.engine.CommitOperation
import io.github.thrhead.taplog.core.engine.DomainState
import io.github.thrhead.taplog.core.engine.EngineResult
import io.github.thrhead.taplog.core.engine.EventEngine
import io.github.thrhead.taplog.core.engine.ScopeContext
import io.github.thrhead.taplog.core.engine.StateScope
import io.github.thrhead.taplog.core.engine.UndoReceipt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ManagementPersistenceAdapterTest {
    @Test
    fun readAndCommitRoundTripTheManagementAggregateFamilies() {
        val backing = InMemoryLocalPersistence()
        val adapter = ManagementPersistenceAdapter(backing)
        val expected = completeAggregate()

        assertTrue(adapter.commit(CommitOperation(adapter.read(), expected)))

        assertEquals(expected, adapter.read())
        assertEquals(1, adapter.read().records.size)
        assertEquals(1, adapter.read().targets.size)
        assertEquals(1, adapter.read().relationships.size)
        assertEquals(1, adapter.read().events.size)
        assertEquals(1, adapter.read().stateGroups.size)
        assertEquals(1, adapter.read().currentStates.size)
        assertEquals(1, adapter.read().stateGenerations.size)
        assertEquals(1, adapter.read().bindings.size)
        assertEquals(1, adapter.read().undoReceipts.size)
    }

    @Test
    fun coreLifecycleDiffPersistsTerminalHistoryAndOrphanedBindingThroughAdapter() {
        val fixture = lifecycleFixture()
        val adapter = ManagementPersistenceAdapter(InMemoryLocalPersistence(fixture.state))
        val engine = EventEngine(adapter, TestClock)

        assertTrue(engine.archiveTarget(fixture.target.id) is EngineResult.Applied)

        val actual = adapter.read()
        assertEquals(Lifecycle.ARCHIVED, actual.targets.getValue(fixture.target.id).lifecycle)
        assertEquals(fixture.relationship, actual.relationships.getValue(fixture.record.id to fixture.target.id))
        assertEquals(DurationStatus.INCOMPLETE, (actual.events.single().payload as EventPayload.Duration).status)
        assertEquals(BindingStatus.ORPHANED, actual.bindings.getValue(fixture.binding.bindingId).status)
        assertEquals(
            listOf(UndoInvalidation(fixture.receipt.receiptId, ResultReason.STALE_REVISION)),
            actual.bindings.getValue(fixture.binding.bindingId).undoInvalidations,
        )
        assertFalse(actual.undoReceipts.containsKey(fixture.receipt.receiptId))
    }

    @Test
    fun coreConfirmedPairDeletionPersistsOnlySelectedRelationshipAndHistoryThroughAdapter() {
        val fixture = deletionFixture()
        val adapter = ManagementPersistenceAdapter(InMemoryLocalPersistence(fixture.state))
        val engine = EventEngine(adapter, TestClock)

        assertTrue(engine.deleteScope(fixture.record.id, fixture.deletedTarget.id, confirmed = true) is EngineResult.Applied)

        val actual = adapter.read()
        assertEquals(fixture.record, actual.records.getValue(fixture.record.id))
        assertEquals(setOf(fixture.deletedTarget.id, fixture.retainedTarget.id), actual.targets.keys)
        assertFalse(actual.relationships.containsKey(fixture.record.id to fixture.deletedTarget.id))
        assertEquals(fixture.retainedRelationship, actual.relationships.getValue(fixture.record.id to fixture.retainedTarget.id))
        assertEquals(listOf(fixture.retainedEvent, fixture.noTargetEvent), actual.events)
        assertFalse(actual.bindings.containsKey(fixture.deletedBinding.bindingId))
        assertEquals(fixture.retainedBinding, actual.bindings.getValue(fixture.retainedBinding.bindingId))
        assertEquals(fixture.noTargetBinding, actual.bindings.getValue(fixture.noTargetBinding.bindingId))
        assertFalse(actual.undoReceipts.containsKey(fixture.deletedReceipt.receiptId))
        assertEquals(fixture.retainedReceipt, actual.undoReceipts.getValue(fixture.retainedReceipt.receiptId))
        assertEquals(fixture.noTargetReceipt, actual.undoReceipts.getValue(fixture.noTargetReceipt.receiptId))
    }

    @Test
    fun staleCompareWriteReturnsFalseAndDoesNotPartiallyPublishTheOperation() {
        val before = completeAggregate()
        val backing = InMemoryLocalPersistence(before)
        val adapter = ManagementPersistenceAdapter(backing)
        val current = before.copy(generation = DatasetGeneration(2))
        assertTrue(backing.commit(CommitOperation(before, current)))
        val rejected = before.copy(
            records = emptyMap(),
            targets = emptyMap(),
            relationships = emptyMap(),
            events = emptyList(),
            bindings = emptyMap(),
            undoReceipts = emptyMap(),
        )

        assertFalse(adapter.commit(CommitOperation(before, rejected)))

        assertEquals(current, adapter.read())
    }

    @Test
    fun adapterWriteRejectionMapsToStorageFailureWithoutPublishingTheCoreProposal() {
        val record = Record(RecordId("moment"), "Moment", null, Behavior.MOMENT)
        val before = DomainState(records = mapOf(record.id to record))
        val backing = InMemoryLocalPersistence(before)
        val adapter = ManagementPersistenceAdapter(backing)
        backing.failNextCommit()

        assertEquals(EngineResult.StorageFailure, EventEngine(adapter, TestClock) { EventId("event") }
            .apply(io.github.thrhead.taplog.core.engine.LogMoment(record.id, source = Source.APP)))

        assertEquals(before, adapter.read())
    }

    private fun completeAggregate(): DomainState {
        val target = Target(TargetId("target"), "Target", "target", revision = Revision(2))
        val group = StateGroup(StateGroupId("group"), "Group", Revision(3))
        val record = Record(RecordId("state"), "State", "state", Behavior.STATE, stateGroupId = group.id,
            revision = Revision(4), hasEvents = true)
        val scope = StateScope(group.id, target.id)
        val event = event(EventId("state-event"), record, target, EventPayload.State(group.id, DatasetGeneration(5)))
        val binding = BindingLifecycle(BindingId("binding"), record.id, target.id,
            lastKnownDisplay = DisplaySnapshot(record.name, record.icon, target.name, target.icon))
        val receipt = UndoReceipt(ReceiptId("receipt"), event.id, event.revision, DatasetGeneration(5),
            ScopeContext(scope, DatasetGeneration(5)))
        return DomainState(
            records = mapOf(record.id to record),
            targets = mapOf(target.id to target),
            relationships = mapOf((record.id to target.id) to RecordTarget(record.id, target.id, revision = Revision(6))),
            events = listOf(event),
            stateGroups = mapOf(group.id to group),
            generation = DatasetGeneration(5),
            nextSequence = Sequence(7),
            currentStates = mapOf(scope to record.id),
            stateGenerations = mapOf(scope to DatasetGeneration(5)),
            bindings = mapOf(binding.bindingId to binding),
            undoReceipts = mapOf(receipt.receiptId to receipt),
        )
    }

    private fun lifecycleFixture(): LifecycleFixture {
        val record = Record(RecordId("duration"), "Run", "run", Behavior.DURATION, hasEvents = true)
        val target = Target(TargetId("target"), "Target", "target")
        val relationship = RecordTarget(record.id, target.id)
        val event = event(EventId("duration-event"), record, target, EventPayload.Duration(EpochMillis(10)))
        val binding = BindingLifecycle(BindingId("duration-binding"), record.id, target.id,
            lastKnownDisplay = DisplaySnapshot(record.name, record.icon, target.name, target.icon))
        val receipt = UndoReceipt(ReceiptId("duration-receipt"), event.id, event.revision, DatasetGeneration(0))
        return LifecycleFixture(record, target, relationship, binding, receipt, DomainState(
            records = mapOf(record.id to record),
            targets = mapOf(target.id to target),
            relationships = mapOf((record.id to target.id) to relationship),
            events = listOf(event),
            bindings = mapOf(binding.bindingId to binding),
            undoReceipts = mapOf(receipt.receiptId to receipt),
        ))
    }

    private fun deletionFixture(): DeletionFixture {
        val record = Record(RecordId("record"), "Record", "record", Behavior.MOMENT, hasEvents = true)
        val deletedTarget = Target(TargetId("deleted"), "Deleted", "deleted")
        val retainedTarget = Target(TargetId("retained"), "Retained", "retained")
        val deletedRelationship = RecordTarget(record.id, deletedTarget.id)
        val retainedRelationship = RecordTarget(record.id, retainedTarget.id)
        val deletedEvent = event(EventId("deleted-event"), record, deletedTarget, EventPayload.Moment)
        val retainedEvent = event(EventId("retained-event"), record, retainedTarget, EventPayload.Moment)
        val noTargetEvent = event(EventId("no-target-event"), record, null, EventPayload.Moment)
        val deletedBinding = binding(BindingId("deleted-binding"), record, deletedTarget)
        val retainedBinding = binding(BindingId("retained-binding"), record, retainedTarget)
        val noTargetBinding = binding(BindingId("no-target-binding"), record, null)
        val deletedReceipt = UndoReceipt(ReceiptId("deleted-receipt"), deletedEvent.id, deletedEvent.revision, DatasetGeneration(0))
        val retainedReceipt = UndoReceipt(ReceiptId("retained-receipt"), retainedEvent.id, retainedEvent.revision, DatasetGeneration(0))
        val noTargetReceipt = UndoReceipt(ReceiptId("no-target-receipt"), noTargetEvent.id, noTargetEvent.revision, DatasetGeneration(0))
        return DeletionFixture(record, deletedTarget, retainedTarget, retainedRelationship, retainedEvent, noTargetEvent,
            deletedBinding, retainedBinding, noTargetBinding, deletedReceipt, retainedReceipt, noTargetReceipt, DomainState(
                records = mapOf(record.id to record),
                targets = mapOf(deletedTarget.id to deletedTarget, retainedTarget.id to retainedTarget),
                relationships = mapOf(
                    (record.id to deletedTarget.id) to deletedRelationship,
                    (record.id to retainedTarget.id) to retainedRelationship,
                ),
                events = listOf(deletedEvent, retainedEvent, noTargetEvent),
                bindings = mapOf(
                    deletedBinding.bindingId to deletedBinding,
                    retainedBinding.bindingId to retainedBinding,
                    noTargetBinding.bindingId to noTargetBinding,
                ),
                undoReceipts = mapOf(
                    deletedReceipt.receiptId to deletedReceipt,
                    retainedReceipt.receiptId to retainedReceipt,
                    noTargetReceipt.receiptId to noTargetReceipt,
                ),
            ))
    }

    private fun event(id: EventId, record: Record, target: Target?, payload: EventPayload) = Event(
        id, record.id, target?.id, record.behavior, EpochMillis(10), EpochMillis(10), EpochMillis(10),
        Sequence(1), Source.APP, Revision(0),
        EventSnapshot(record.name, record.icon, target?.name, target?.icon, record.behavior, record.unit), payload,
    )

    private fun binding(id: BindingId, record: Record, target: Target?) = BindingLifecycle(
        id, record.id, target?.id, lastKnownDisplay = DisplaySnapshot(record.name, record.icon, target?.name, target?.icon),
    )

    private data class LifecycleFixture(
        val record: Record,
        val target: Target,
        val relationship: RecordTarget,
        val binding: BindingLifecycle,
        val receipt: UndoReceipt,
        val state: DomainState,
    )

    private data class DeletionFixture(
        val record: Record,
        val deletedTarget: Target,
        val retainedTarget: Target,
        val retainedRelationship: RecordTarget,
        val retainedEvent: Event,
        val noTargetEvent: Event,
        val deletedBinding: BindingLifecycle,
        val retainedBinding: BindingLifecycle,
        val noTargetBinding: BindingLifecycle,
        val deletedReceipt: UndoReceipt,
        val retainedReceipt: UndoReceipt,
        val noTargetReceipt: UndoReceipt,
        val state: DomainState,
    )

    private object TestClock : AcceptanceClock {
        override fun now() = EpochMillis(100)
    }
}
