package io.github.thrhead.taplog.core.engine

import io.github.thrhead.taplog.core.domain.Behavior
import io.github.thrhead.taplog.core.domain.EpochMillis
import io.github.thrhead.taplog.core.domain.Lifecycle
import io.github.thrhead.taplog.core.domain.Quantity
import io.github.thrhead.taplog.core.domain.Record
import io.github.thrhead.taplog.core.domain.RecordId
import io.github.thrhead.taplog.core.domain.RecordTarget
import io.github.thrhead.taplog.core.domain.ResultReason
import io.github.thrhead.taplog.core.domain.Source
import io.github.thrhead.taplog.core.domain.Target
import io.github.thrhead.taplog.core.domain.TargetId
import io.github.thrhead.taplog.core.domain.UnitName
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EventEngineLoggingInvalidTest {
    @Test
    fun inactiveRecordAndTargetScopesAreRejectedWithoutCommit() {
        val archivedRecord = Record(RecordId("record"), "Coffee", null, Behavior.MOMENT, lifecycle = Lifecycle.ARCHIVED)
        val archivedTarget = Target(TargetId("target"), "Mug", null, lifecycle = Lifecycle.ARCHIVED)
        val archivedRecordBoundary = InMemoryBoundary(
            DomainState(records = mapOf(archivedRecord.id to archivedRecord)),
        )
        val activeRecord = archivedRecord.copy(lifecycle = Lifecycle.ACTIVE)
        val archivedTargetBoundary = InMemoryBoundary(
            DomainState(
                records = mapOf(activeRecord.id to activeRecord),
                targets = mapOf(archivedTarget.id to archivedTarget),
                relationships = mapOf((activeRecord.id to archivedTarget.id) to RecordTarget(activeRecord.id, archivedTarget.id)),
            ),
        )

        assertEquals(EngineResult.Invalid(ResultReason.INACTIVE_SCOPE), engine(archivedRecordBoundary).apply(LogMoment(archivedRecord.id, source = Source.APP)))
        assertEquals(EngineResult.Invalid(ResultReason.INACTIVE_SCOPE), engine(archivedTargetBoundary).apply(LogMoment(activeRecord.id, archivedTarget.id, source = Source.APP)))
        assertTrue(!archivedRecordBoundary.committed)
        assertTrue(!archivedTargetBoundary.committed)
    }

    @Test
    fun unlinkedTargetScopeIsRejectedWithoutCommit() {
        val record = Record(RecordId("record"), "Coffee", null, Behavior.MOMENT)
        val target = Target(TargetId("target"), "Mug", null)
        val boundary = InMemoryBoundary(
            DomainState(
                records = mapOf(record.id to record), targets = mapOf(target.id to target),
                relationships = mapOf((record.id to target.id) to RecordTarget(record.id, target.id, linked = false)),
            ),
        )

        assertEquals(EngineResult.Invalid(ResultReason.UNLINKED_RELATIONSHIP), engine(boundary).apply(LogMoment(record.id, target.id, source = Source.APP)))
        assertTrue(!boundary.committed)
    }

    @Test
    fun behaviorMismatchAndInvalidCounterQuantityAreRejectedWithoutCommit() {
        val moment = Record(RecordId("moment"), "Moment", null, Behavior.MOMENT)
        val counter = Record(RecordId("counter"), "Counter", null, Behavior.COUNTER)
        val boundary = InMemoryBoundary(DomainState(records = mapOf(moment.id to moment, counter.id to counter)))

        assertEquals(EngineResult.Invalid(ResultReason.BEHAVIOR_MISMATCH), engine(boundary).apply(AddCounter(moment.id, source = Source.APP)))
        assertEquals(EngineResult.Invalid(ResultReason.INVALID_QUANTITY), engine(boundary).apply(AddCounter(counter.id, quantity = Quantity(BigDecimal.ZERO), source = Source.APP)))
        assertTrue(!boundary.committed)
    }

    @Test
    fun blankCounterUnitIsRejectedWithoutCommit() {
        val counter = Record(RecordId("counter"), "Counter", null, Behavior.COUNTER, unit = UnitName(" "))
        val boundary = InMemoryBoundary(DomainState(records = mapOf(counter.id to counter)))

        assertEquals(EngineResult.Invalid(ResultReason.UNIT_MISMATCH), engine(boundary).apply(AddCounter(counter.id, source = Source.APP)))
        assertTrue(!boundary.committed)
    }

    @Test
    fun failedAtomicCommitReturnsStorageFailureAndLeavesBoundaryStateUnchanged() {
        val record = Record(RecordId("moment"), "Moment", null, Behavior.MOMENT)
        val boundary = InMemoryBoundary(DomainState(records = mapOf(record.id to record)), acceptCommits = false)

        assertEquals(EngineResult.StorageFailure, engine(boundary).apply(LogMoment(record.id, source = Source.APP)))
        assertTrue(boundary.state.events.isEmpty())
    }

    private fun engine(boundary: InMemoryBoundary) = EventEngine(boundary, object : AcceptanceClock {
        override fun now() = EpochMillis(1_000)
    }) { io.github.thrhead.taplog.core.domain.EventId("event") }

    private class InMemoryBoundary(initial: DomainState, private val acceptCommits: Boolean = true) : AtomicCommitBoundary {
        var state = initial
        var committed = false
        override fun read() = state
        override fun commit(operation: CommitOperation): Boolean {
            if (!acceptCommits) return false
            state = operation.state
            committed = true
            return true
        }
    }
}
