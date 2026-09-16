package io.github.thrhead.taplog.core.engine

import io.github.thrhead.taplog.core.domain.*
import org.junit.Assert.*
import org.junit.Test

class ConflictHandlingTest {
    @Test fun staleRevisionAndGenerationNeverOverwriteCurrentState() {
        val r = Record(RecordId("r"), "R", null, Behavior.MOMENT)
        val b = Boundary(DomainState(records = mapOf(r.id to r), generation = DatasetGeneration(2)))
        val e = EventEngine(b, Clock)
        assertEquals(EngineResult.Conflict(ResultReason.STALE_REVISION), e.apply(LogMoment(r.id, source = Source.APP, expected = ExpectedContext(Revision(9)))))
        assertEquals(EngineResult.Conflict(ResultReason.STALE_DATASET_GENERATION), e.apply(LogMoment(r.id, source = Source.APP, expected = ExpectedContext(datasetGeneration = DatasetGeneration(1)))))
        assertTrue(b.state.events.isEmpty())
    }
    private object Clock : AcceptanceClock { override fun now() = EpochMillis(100) }
    private class Boundary(var state: DomainState) : AtomicCommitBoundary {
        override fun read() = state
        override fun commit(operation: CommitOperation): Boolean { if (operation.expected != state) return false; state = operation.state; return true }
    }
}
