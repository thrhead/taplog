package io.github.thrhead.taplog.core.engine

import io.github.thrhead.taplog.core.domain.*
import org.junit.Assert.*
import org.junit.Test

class PermanentDeleteScopeTest {
    @Test fun confirmedDeleteRemovesUndoMetadataForDeletedEvents() {
        val r = Record(RecordId("r"), "Thing", null, Behavior.MOMENT)
        val boundary = Boundary(DomainState(records = mapOf(r.id to r)))
        val engine = EventEngine(boundary, Clock)
        engine.apply(LogMoment(r.id, source = Source.APP))
        val event = boundary.state.events.single()
        val receipt = UndoReceipt(ReceiptId("receipt"), event.id, event.revision, boundary.state.generation)
        boundary.state = boundary.state.copy(undoReceipts = mapOf(receipt.receiptId to receipt))

        engine.deleteScope(r.id, confirmed = true)

        assertTrue(boundary.state.undoReceipts.isEmpty())
    }

    @Test fun impactPreviewIsScopedAndConfirmedDeleteRetainsUnrelatedTargetHistory() {
        val r = Record(RecordId("r"), "Thing", null, Behavior.MOMENT)
        val one = Target(TargetId("one"), "One", null)
        val two = Target(TargetId("two"), "Two", null)
        val state = DomainState(records = mapOf(r.id to r), targets = mapOf(one.id to one, two.id to two),
            relationships = mapOf((r.id to one.id) to RecordTarget(r.id, one.id), (r.id to two.id) to RecordTarget(r.id, two.id)))
        val boundary = Boundary(state); val engine = EventEngine(boundary, Clock)
        engine.apply(LogMoment(r.id, one.id, source = Source.APP))
        engine.apply(LogMoment(r.id, two.id, source = Source.APP))
        val impact = DeletionScope.preview(boundary.state, r.id, one.id)
        assertEquals(1, impact.eventIds.size)
        assertTrue(engine.deleteScope(r.id, one.id) is EngineResult.NeedsConfirmation)
        assertTrue(engine.deleteScope(r.id, one.id, confirmed = true) is EngineResult.Applied)
        assertEquals(listOf(two.id), boundary.state.events.map { it.targetId })
    }
    private object Clock : AcceptanceClock { override fun now() = EpochMillis(100) }
    private class Boundary(var state: DomainState) : AtomicCommitBoundary {
        override fun read() = state
        override fun commit(operation: CommitOperation): Boolean { if (operation.expected != state) return false; state = operation.state; return true }
    }
}
