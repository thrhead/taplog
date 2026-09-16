package io.github.thrhead.taplog.core.engine

import io.github.thrhead.taplog.core.domain.*
import org.junit.Assert.*
import org.junit.Test

class PostCommitFeedbackBoundaryTest {
    @Test fun appliedDoesNotDependOnFeedbackAndCommittedDataRemains() {
        val r = Record(RecordId("r"), "R", null, Behavior.MOMENT); val b = Boundary(DomainState(records = mapOf(r.id to r)))
        val result = EventEngine(b, Clock).apply(LogMoment(r.id, source = Source.APP))
        assertTrue(result is EngineResult.Applied); assertEquals(1, b.state.events.size)
    }
    private object Clock : AcceptanceClock { override fun now() = EpochMillis(100) }
    private class Boundary(var state: DomainState) : AtomicCommitBoundary {
        override fun read() = state
        override fun commit(operation: CommitOperation): Boolean { if (operation.expected != state) return false; state = operation.state; return true }
    }
}
