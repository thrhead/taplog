package io.github.thrhead.taplog.core.engine

import io.github.thrhead.taplog.core.domain.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DurationLifecycleTest {
    @Test fun targetScopesAndNoTargetScopeCanEachHaveOneOpenDuration() {
        val r = Record(RecordId("r"), "Run", null, Behavior.DURATION)
        val t = Target(TargetId("t"), "Target", null)
        val b = Boundary(DomainState(records = mapOf(r.id to r), targets = mapOf(t.id to t), relationships = mapOf((r.id to t.id) to RecordTarget(r.id, t.id))))
        val e = EventEngine(b, Clock)
        assertTrue(e.apply(StartDuration(r.id, source = Source.APP)) is EngineResult.Applied)
        assertTrue(e.apply(StartDuration(r.id, t.id, source = Source.APP)) is EngineResult.Applied)
        assertEquals(2, b.state.events.size)
        assertTrue(e.apply(StartDuration(r.id, source = Source.APP)) is EngineResult.Invalid)
    }
    @Test fun unlinkMakesOnlyAffectedOpenDurationIncomplete() {
        val r = Record(RecordId("r"), "Run", null, Behavior.DURATION)
        val t = Target(TargetId("t"), "Target", null)
        val b = Boundary(DomainState(records = mapOf(r.id to r), targets = mapOf(t.id to t), relationships = mapOf((r.id to t.id) to RecordTarget(r.id, t.id))))
        val e = EventEngine(b, Clock); e.apply(StartDuration(r.id, t.id, source = Source.APP)); e.unlink(r.id, t.id)
        assertEquals(DurationStatus.INCOMPLETE, (b.state.events.single().payload as EventPayload.Duration).status)
        assertEquals(null, (b.state.events.single().payload as EventPayload.Duration).endAt)
    }
    private object Clock : AcceptanceClock { override fun now() = EpochMillis(100) }
    private class Boundary(var state: DomainState) : AtomicCommitBoundary {
        override fun read() = state
        override fun commit(operation: CommitOperation): Boolean { if (operation.expected != state) return false; state = operation.state; return true }
    }
}
