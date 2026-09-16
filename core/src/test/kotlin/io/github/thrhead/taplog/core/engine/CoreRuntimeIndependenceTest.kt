package io.github.thrhead.taplog.core.engine

import io.github.thrhead.taplog.core.domain.*
import org.junit.Assert.assertTrue
import org.junit.Test

class CoreRuntimeIndependenceTest {
    @Test fun coreEventCreationUsesOnlyJVMDomainAndBoundary() {
        val r = Record(RecordId("r"), "R", null, Behavior.MOMENT)
        var state = DomainState(records = mapOf(r.id to r))
        val boundary = object : AtomicCommitBoundary {
            override fun read() = state
            override fun commit(operation: CommitOperation): Boolean { state = operation.state; return true }
        }
        assertTrue(EventEngine(boundary, object : AcceptanceClock { override fun now() = EpochMillis(1) })
            .apply(LogMoment(r.id, source = Source.APP)) is EngineResult.Applied)
    }
}
