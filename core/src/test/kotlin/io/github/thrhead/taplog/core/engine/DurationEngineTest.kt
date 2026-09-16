package io.github.thrhead.taplog.core.engine

import io.github.thrhead.taplog.core.domain.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DurationEngineTest {
    @Test fun finishRejectsStaleDatasetGenerationWithoutMutation() {
        val record = Record(RecordId("run"), "Run", null, Behavior.DURATION)
        val boundary = Boundary(DomainState(records = mapOf(record.id to record), generation = DatasetGeneration(3)))
        val engine = EventEngine(boundary, Clock)
        engine.apply(StartDuration(record.id, occurredAt = EpochMillis(10), source = Source.APP))
        val event = boundary.state.events.single()

        assertEquals(
            EngineResult.Conflict(ResultReason.STALE_DATASET_GENERATION),
            engine.apply(FinishDuration(event.id, EpochMillis(20), Source.APP,
                ExpectedContext(datasetGeneration = DatasetGeneration(2)))),
        )
        assertEquals(DurationStatus.OPEN, (boundary.state.events.single().payload as EventPayload.Duration).status)
    }

    @Test
    fun startsAndFinishesOneDurationPerScope() {
        val record = Record(RecordId("run"), "Run", null, Behavior.DURATION)
        val boundary = Boundary(DomainState(records = mapOf(record.id to record)))
        val engine = EventEngine(boundary, Clock)

        assertTrue(engine.apply(StartDuration(record.id, occurredAt = EpochMillis(10), source = Source.APP)) is EngineResult.Applied)
        assertEquals(EngineResult.Invalid(ResultReason.OPEN_DURATION_EXISTS), engine.apply(StartDuration(record.id, occurredAt = EpochMillis(11), source = Source.APP)))
        assertTrue(engine.apply(FinishDuration(boundary.state.events.single().id, EpochMillis(20), Source.APP)) is EngineResult.Applied)
        val payload = boundary.state.events.single().payload as EventPayload.Duration
        assertEquals(DurationStatus.COMPLETED, payload.status)
        assertEquals(EpochMillis(20), payload.endAt)
    }

    @Test
    fun differentTargetScopesMayBeOpenConcurrentlyAndEarlierFinishIsInvalid() {
        val record = Record(RecordId("run"), "Run", null, Behavior.DURATION)
        val one = Target(TargetId("one"), "One", null)
        val two = Target(TargetId("two"), "Two", null)
        val relationships = mapOf(
            (record.id to one.id) to RecordTarget(record.id, one.id),
            (record.id to two.id) to RecordTarget(record.id, two.id),
        )
        val boundary = Boundary(DomainState(records = mapOf(record.id to record), targets = mapOf(one.id to one, two.id to two), relationships = relationships))
        val engine = EventEngine(boundary, Clock)

        engine.apply(StartDuration(record.id, one.id, EpochMillis(20), Source.APP))
        engine.apply(StartDuration(record.id, two.id, EpochMillis(20), Source.APP))
        assertEquals(EngineResult.Invalid(ResultReason.NEGATIVE_DURATION), engine.apply(FinishDuration(boundary.state.events.first().id, EpochMillis(19), Source.APP)))
        assertEquals(2, boundary.state.events.size)
    }

    private object Clock : AcceptanceClock { override fun now() = EpochMillis(100) }
    private class Boundary(initial: DomainState) : AtomicCommitBoundary {
        var state = initial
        override fun read() = state
        override fun commit(operation: CommitOperation): Boolean { if (operation.expected != state) return false; state = operation.state; return true }
    }
}
