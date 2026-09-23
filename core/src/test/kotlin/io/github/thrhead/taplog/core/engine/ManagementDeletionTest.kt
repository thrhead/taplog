package io.github.thrhead.taplog.core.engine

import io.github.thrhead.taplog.core.domain.*
import io.github.thrhead.taplog.core.domain.Target as DomainTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ManagementDeletionTest {
    @Test
    fun publicDeletionSurfaceProvidesRecordScopesAndNoStandaloneTargetDeletion() {
        val deletionMethods = EventEngine::class.java.methods.filter {
            it.declaringClass == EventEngine::class.java && it.name.contains("delete", ignoreCase = true)
        }

        assertEquals(listOf("deleteScope"), deletionMethods.map { it.name })
        assertEquals(RecordId::class.java, deletionMethods.single().parameterTypes.first())
    }

    @Test
    fun previewAndMissingOrCancelledConfirmationLeaveTheExactScopeUnchanged() {
        val fixture = Fixture()
        val before = fixture.boundary.state

        assertEquals(
            DeleteImpact(fixture.record.id, fixture.firstTarget.id, listOf(EventId("first-target-event"))),
            DeletionScope.preview(before, fixture.record.id, fixture.firstTarget.id),
        )
        assertEquals(
            DeleteImpact(fixture.record.id, null, listOf(
                EventId("first-target-event"), EventId("second-target-event"), EventId("no-target-event"),
            )),
            DeletionScope.preview(before, fixture.record.id),
        )
        assertEquals(
            EngineResult.NeedsConfirmation(ResultReason.PERMANENT_DELETE_IMPACT),
            fixture.engine.deleteScope(fixture.record.id, fixture.firstTarget.id),
        )
        assertEquals(before, fixture.boundary.state)
        assertEquals(
            EngineResult.NeedsConfirmation(ResultReason.PERMANENT_DELETE_IMPACT),
            fixture.engine.deleteScope(fixture.record.id, confirmed = false),
        )
        assertEquals(before, fixture.boundary.state)
    }

    @Test
    fun confirmedPairDeletionRemovesOnlyThatPairHistoryAndKeepsOtherAndNoTargetHistory() {
        val fixture = Fixture()

        assertTrue(fixture.engine.deleteScope(fixture.record.id, fixture.firstTarget.id, confirmed = true) is EngineResult.Applied)

        val actual = fixture.boundary.state
        assertEquals(fixture.record, actual.records.getValue(fixture.record.id))
        assertEquals(fixture.firstTarget, actual.targets.getValue(fixture.firstTarget.id))
        assertEquals(fixture.secondTarget, actual.targets.getValue(fixture.secondTarget.id))
        assertFalse(actual.relationships.containsKey(fixture.record.id to fixture.firstTarget.id))
        assertEquals(fixture.secondRelationship, actual.relationships.getValue(fixture.record.id to fixture.secondTarget.id))
        assertEquals(listOf(
            fixture.secondTargetEvent, fixture.noTargetEvent, fixture.unrelatedEvent, fixture.unrelatedNoTargetEvent,
        ), actual.events)
        assertFalse(actual.bindings.containsKey(fixture.firstBinding.bindingId))
        assertEquals(fixture.secondBinding, actual.bindings.getValue(fixture.secondBinding.bindingId))
        assertEquals(fixture.noTargetBinding, actual.bindings.getValue(fixture.noTargetBinding.bindingId))
        assertEquals(fixture.unrelatedBinding, actual.bindings.getValue(fixture.unrelatedBinding.bindingId))
        assertEquals(fixture.unrelatedNoTargetBinding, actual.bindings.getValue(fixture.unrelatedNoTargetBinding.bindingId))
        assertFalse(actual.undoReceipts.containsKey(fixture.firstReceipt.receiptId))
        assertEquals(fixture.secondReceipt, actual.undoReceipts.getValue(fixture.secondReceipt.receiptId))
        assertEquals(fixture.noTargetReceipt, actual.undoReceipts.getValue(fixture.noTargetReceipt.receiptId))
        assertEquals(fixture.unrelatedReceipt, actual.undoReceipts.getValue(fixture.unrelatedReceipt.receiptId))
        assertEquals(fixture.unrelatedNoTargetReceipt, actual.undoReceipts.getValue(fixture.unrelatedNoTargetReceipt.receiptId))
    }

    @Test
    fun confirmedRecordDeletionRemovesAllRecordScopesButRetainsStandaloneTargetsAndUnrelatedHistory() {
        val fixture = Fixture()

        assertTrue(fixture.engine.deleteScope(fixture.record.id, confirmed = true) is EngineResult.Applied)

        val actual = fixture.boundary.state
        assertFalse(actual.records.containsKey(fixture.record.id))
        assertEquals(fixture.firstTarget, actual.targets.getValue(fixture.firstTarget.id))
        assertEquals(fixture.secondTarget, actual.targets.getValue(fixture.secondTarget.id))
        assertFalse(actual.relationships.keys.any { it.first == fixture.record.id })
        assertEquals(listOf(fixture.unrelatedEvent, fixture.unrelatedNoTargetEvent), actual.events)
        assertFalse(actual.bindings.values.any { it.recordId == fixture.record.id })
        assertEquals(fixture.unrelatedBinding, actual.bindings.getValue(fixture.unrelatedBinding.bindingId))
        assertEquals(fixture.unrelatedNoTargetBinding, actual.bindings.getValue(fixture.unrelatedNoTargetBinding.bindingId))
        assertEquals(mapOf(
            fixture.unrelatedReceipt.receiptId to fixture.unrelatedReceipt,
            fixture.unrelatedNoTargetReceipt.receiptId to fixture.unrelatedNoTargetReceipt,
        ), actual.undoReceipts)
    }

    private class Fixture {
        val record = Record(RecordId("record"), "Record", "record", Behavior.MOMENT, hasEvents = true)
        private val unrelatedRecord = Record(RecordId("other"), "Other", "other", Behavior.MOMENT, hasEvents = true)
        val firstTarget = DomainTarget(TargetId("first"), "First", "first")
        val secondTarget = DomainTarget(TargetId("second"), "Second", "second")
        private val firstRelationship = RecordTarget(record.id, firstTarget.id, revision = Revision(3))
        val secondRelationship = RecordTarget(record.id, secondTarget.id, revision = Revision(4))
        private val firstTargetEvent = event(EventId("first-target-event"), record, firstTarget)
        val secondTargetEvent = event(EventId("second-target-event"), record, secondTarget)
        val noTargetEvent = event(EventId("no-target-event"), record, null)
        val unrelatedEvent = event(EventId("unrelated-event"), unrelatedRecord, firstTarget)
        val unrelatedNoTargetEvent = event(EventId("unrelated-no-target-event"), unrelatedRecord, null)
        val firstBinding = binding(BindingId("first-binding"), record, firstTarget)
        val secondBinding = binding(BindingId("second-binding"), record, secondTarget)
        val noTargetBinding = binding(BindingId("no-target-binding"), record, null)
        val unrelatedBinding = binding(BindingId("unrelated-binding"), unrelatedRecord, firstTarget)
        val unrelatedNoTargetBinding = binding(BindingId("unrelated-no-target-binding"), unrelatedRecord, null)
        val firstReceipt = UndoReceipt(ReceiptId("first-receipt"), firstTargetEvent.id, Revision(0), DatasetGeneration(2))
        val secondReceipt = UndoReceipt(ReceiptId("second-receipt"), secondTargetEvent.id, Revision(0), DatasetGeneration(2))
        val noTargetReceipt = UndoReceipt(ReceiptId("no-target-receipt"), noTargetEvent.id, Revision(0), DatasetGeneration(2))
        val unrelatedReceipt = UndoReceipt(ReceiptId("unrelated-receipt"), unrelatedEvent.id, Revision(0), DatasetGeneration(2))
        val unrelatedNoTargetReceipt = UndoReceipt(ReceiptId("unrelated-no-target-receipt"), unrelatedNoTargetEvent.id, Revision(0), DatasetGeneration(2))
        val boundary = Boundary(DomainState(
            records = mapOf(record.id to record, unrelatedRecord.id to unrelatedRecord),
            targets = mapOf(firstTarget.id to firstTarget, secondTarget.id to secondTarget),
            relationships = mapOf(
                (record.id to firstTarget.id) to firstRelationship,
                (record.id to secondTarget.id) to secondRelationship,
            ),
            events = listOf(firstTargetEvent, secondTargetEvent, noTargetEvent, unrelatedEvent, unrelatedNoTargetEvent),
            bindings = mapOf(
                firstBinding.bindingId to firstBinding,
                secondBinding.bindingId to secondBinding,
                noTargetBinding.bindingId to noTargetBinding,
                unrelatedBinding.bindingId to unrelatedBinding,
                unrelatedNoTargetBinding.bindingId to unrelatedNoTargetBinding,
            ),
            undoReceipts = mapOf(
                firstReceipt.receiptId to firstReceipt,
                secondReceipt.receiptId to secondReceipt,
                noTargetReceipt.receiptId to noTargetReceipt,
                unrelatedReceipt.receiptId to unrelatedReceipt,
                unrelatedNoTargetReceipt.receiptId to unrelatedNoTargetReceipt,
            ),
        ))
        val engine = EventEngine(boundary, object : AcceptanceClock { override fun now() = EpochMillis(1_000) })

        private fun event(id: EventId, record: Record, target: DomainTarget?) = Event(
            id, record.id, target?.id, record.behavior, EpochMillis(10), EpochMillis(10), EpochMillis(10),
            Sequence(1), Source.APP, Revision(0),
            EventSnapshot(record.name, record.icon, target?.name, target?.icon, record.behavior, record.unit), EventPayload.Moment,
        )

        private fun binding(id: BindingId, record: Record, target: DomainTarget?) = BindingLifecycle(
            id, record.id, target?.id, lastKnownDisplay = DisplaySnapshot(record.name, record.icon, target?.name, target?.icon),
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
