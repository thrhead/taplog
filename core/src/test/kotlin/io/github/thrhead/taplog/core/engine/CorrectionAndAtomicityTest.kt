package io.github.thrhead.taplog.core.engine

import io.github.thrhead.taplog.core.domain.*
import org.junit.Assert.*
import org.junit.Test

class CorrectionAndAtomicityTest {
    @Test fun createRecordAndLogRejectsInvalidDefinitionWithoutMutation() {
        val group = StateGroup(StateGroupId("missing"), "Missing")
        val invalidCounter = Record(RecordId("counter"), "Counter", null, Behavior.COUNTER,
            defaultQuantity = Quantity.parse("0"))
        val invalidState = Record(RecordId("state"), "State", null, Behavior.STATE,
            stateGroupId = group.id)
        val boundary = Boundary(DomainState(stateGroups = emptyMap()))
        val engine = EventEngine(boundary, Clock)

        assertEquals(EngineResult.Invalid(ResultReason.INVALID_QUANTITY),
            engine.apply(CreateRecordAndLog(invalidCounter, source = Source.APP, confirmed = true)))
        assertEquals(EngineResult.Invalid(ResultReason.MISSING_STATE_GROUP),
            engine.apply(CreateRecordAndLog(invalidState, source = Source.APP, confirmed = true)))
        assertTrue(boundary.state.records.isEmpty())
        assertTrue(boundary.state.events.isEmpty())
    }

    @Test fun undoRejectsAReceiptWithStaleScopeGeneration() {
        val group = StateGroup(StateGroupId("group"), "Group")
        val record = Record(RecordId("state"), "State", null, Behavior.STATE, stateGroupId = group.id)
        val scope = StateScope(group.id, null)
        val boundary = Boundary(DomainState(records = mapOf(record.id to record), stateGroups = mapOf(group.id to group),
            stateGenerations = mapOf(scope to DatasetGeneration(2))))
        val engine = EventEngine(boundary, Clock)
        engine.apply(SetState(record.id, source = Source.APP))
        val event = boundary.state.events.single()

        assertEquals(EngineResult.Conflict(ResultReason.STALE_SCOPE_CONTEXT),
            engine.apply(Undo(UndoReceipt(ReceiptId("receipt"), event.id, event.revision,
                boundary.state.generation, ScopeContext(scope, DatasetGeneration(1))), Source.APP)))
        assertEquals(1, boundary.state.events.size)
    }

    @Test fun ambiguousCreateAndUnconfirmedDeleteDoNotMutate() {
        val r = Record(RecordId("r"), "R", null, Behavior.MOMENT)
        val b = Boundary(DomainState(records = mapOf(r.id to r)))
        val e = EventEngine(b, Clock)
        assertTrue(e.apply(CreateRecordAndLog(Record(RecordId("new"), "New", null, Behavior.MOMENT), source = Source.NATURAL_LANGUAGE)) is EngineResult.NeedsConfirmation)
        e.apply(LogMoment(r.id, source = Source.APP))
        assertTrue(e.apply(DeleteEvent(b.state.events.single().id, Source.APP)) is EngineResult.NeedsConfirmation)
        assertEquals(1, b.state.events.size)
    }
    @Test fun createRecordAndLogCommitsDefinitionAndFirstEventTogether() {
        val id = RecordId("new"); val b = Boundary(DomainState()); val e = EventEngine(b, Clock)
        assertTrue(e.apply(CreateRecordAndLog(Record(id, "New", null, Behavior.MOMENT), source = Source.APP, confirmed = true)) is EngineResult.Applied)
        assertTrue(b.state.records.containsKey(id)); assertEquals(1, b.state.events.size)
    }
    private object Clock : AcceptanceClock { override fun now() = EpochMillis(100) }
    private class Boundary(var state: DomainState) : AtomicCommitBoundary {
        override fun read() = state
        override fun commit(operation: CommitOperation): Boolean { if (operation.expected != state) return false; state = operation.state; return true }
    }
}
