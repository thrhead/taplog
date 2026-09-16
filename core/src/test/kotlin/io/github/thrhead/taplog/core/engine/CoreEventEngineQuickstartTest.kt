package io.github.thrhead.taplog.core.engine

import io.github.thrhead.taplog.core.domain.*
import org.junit.Assert.*
import org.junit.Test

class CoreEventEngineQuickstartTest {
    @Test fun quickstartMatrixCoversAllSourcesAndRepeatedCounter() {
        Source.entries.forEach { source ->
            val r = Record(RecordId("r"), "R", null, Behavior.COUNTER)
            var state = DomainState(records = mapOf(r.id to r))
            val b = object : AtomicCommitBoundary {
                override fun read() = state
                override fun commit(operation: CommitOperation): Boolean { state = operation.state; return true }
            }
            val e = EventEngine(b, object : AcceptanceClock { override fun now() = EpochMillis(1) })
            e.apply(AddCounter(r.id, source = source)); e.apply(AddCounter(r.id, source = source))
            assertEquals(2, state.events.size)
        }
    }
}
