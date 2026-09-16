package io.github.thrhead.taplog.core.engine

import io.github.thrhead.taplog.core.domain.*
import org.junit.Assert.assertEquals
import org.junit.Test

class StateEngineTest {
    @Test
    fun sameStateIsHistoricalAndLatestStateWinsWithinGroupScope() {
        val group = StateGroup(StateGroupId("mood"), "Mood")
        val first = Record(RecordId("calm"), "Calm", null, Behavior.STATE, stateGroupId = group.id)
        val second = Record(RecordId("busy"), "Busy", null, Behavior.STATE, stateGroupId = group.id)
        val boundary = Boundary(DomainState(records = mapOf(first.id to first, second.id to second), stateGroups = mapOf(group.id to group)))
        val engine = EventEngine(boundary, Clock)

        engine.apply(SetState(first.id, occurredAt = EpochMillis(10), source = Source.APP))
        engine.apply(SetState(first.id, occurredAt = EpochMillis(10), source = Source.APP))
        engine.apply(SetState(second.id, occurredAt = EpochMillis(11), source = Source.APP))

        assertEquals(3, boundary.state.events.size)
        assertEquals(second.id, boundary.state.currentStates.getValue(StateScope(group.id, null)))
    }

    private object Clock : AcceptanceClock { override fun now() = EpochMillis(100) }
    private class Boundary(initial: DomainState) : AtomicCommitBoundary {
        var state = initial
        override fun read() = state
        override fun commit(operation: CommitOperation): Boolean { if (operation.expected != state) return false; state = operation.state; return true }
    }
}
