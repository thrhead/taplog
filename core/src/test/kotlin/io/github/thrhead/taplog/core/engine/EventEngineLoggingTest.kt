package io.github.thrhead.taplog.core.engine

import io.github.thrhead.taplog.core.domain.Behavior
import io.github.thrhead.taplog.core.domain.EpochMillis
import io.github.thrhead.taplog.core.domain.EventId
import io.github.thrhead.taplog.core.domain.EventPayload
import io.github.thrhead.taplog.core.domain.Lifecycle
import io.github.thrhead.taplog.core.domain.Quantity
import io.github.thrhead.taplog.core.domain.Record
import io.github.thrhead.taplog.core.domain.RecordId
import io.github.thrhead.taplog.core.domain.RecordTarget
import io.github.thrhead.taplog.core.domain.Revision
import io.github.thrhead.taplog.core.domain.Source
import io.github.thrhead.taplog.core.domain.Target
import io.github.thrhead.taplog.core.domain.TargetId
import io.github.thrhead.taplog.core.domain.UnitName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EventEngineLoggingTest {
    @Test
    fun eventEngineLoggingTestLaneIsOperational() {
        assertTrue("Event engine logging test lane should run", true)
    }

    @Test
    fun equivalentMomentRequestsRetainSourceAsMetadataOnlyAcrossEverySource() {
        val events = Source.entries.map { source ->
            val boundary = boundaryWith(momentRecord())
            EventEngine(boundary, FixedClock).apply(LogMoment(RecordId("moment"), source = source))
            boundary.state.events.single()
        }

        assertEquals(Source.entries.toList(), events.map { it.source })
        assertTrue(events.zipWithNext().all { (first, second) ->
            first.copy(id = EventId("same"), source = Source.APP) == second.copy(id = EventId("same"), source = Source.APP)
        })
        assertTrue(events.all { it.payload == EventPayload.Moment })
    }

    @Test
    fun explicitOccurrenceTimeWinsAndCreationSnapshotDoesNotUseLiveDefinitions() {
        val record = momentRecord(name = "Coffee", icon = "cup")
        val target = Target(TargetId("plant"), "Fern", "leaf")
        val boundary = boundaryWith(record, target, linked = true)

        EventEngine(boundary, FixedClock).apply(
            LogMoment(record.id, target.id, EpochMillis(100), Source.NFC),
        )

        val event = boundary.state.events.single()
        assertEquals(EpochMillis(100), event.occurredAt)
        assertEquals(EpochMillis(1_000), event.createdAt)
        assertEquals(EpochMillis(1_000), event.updatedAt)
        assertEquals("Coffee", event.snapshot.recordName)
        assertEquals("cup", event.snapshot.recordIcon)
        assertEquals("Fern", event.snapshot.targetName)
        assertEquals("leaf", event.snapshot.targetIcon)
    }

    @Test
    fun omittedOccurrenceTimeUsesAcceptanceTime() {
        val boundary = boundaryWith(momentRecord())

        EventEngine(boundary, AdvancingClock).apply(LogMoment(RecordId("moment"), source = Source.APP))

        val event = boundary.state.events.single()
        assertEquals(EpochMillis(1_000), event.occurredAt)
        assertEquals(event.occurredAt, event.createdAt)
        assertEquals(event.createdAt, event.updatedAt)
    }

    @Test
    fun counterUsesDefaultOrExplicitQuantityAndPreservesIntentionalRepeats() {
        val record = counterRecord(defaultQuantity = Quantity.exact("2")!!)
        val boundary = boundaryWith(record)
        val engine = EventEngine(boundary, FixedClock)

        engine.apply(AddCounter(record.id, source = Source.WIDGET))
        engine.apply(AddCounter(record.id, quantity = Quantity.exact("1.5"), source = Source.WIDGET))

        val events = boundary.state.events
        assertEquals(2, events.size)
        assertNotEquals(events[0].id, events[1].id)
        assertEquals(1L, events[0].sequence.value)
        assertEquals(2L, events[1].sequence.value)
        assertEquals(Quantity.exact("2"), (events[0].payload as EventPayload.Counter).quantity)
        assertEquals(Quantity.exact("1.5"), (events[1].payload as EventPayload.Counter).quantity)
        assertEquals(UnitName("cups"), (events[0].payload as EventPayload.Counter).unit)
    }

    @Test
    fun appliedIsReturnedOnlyAfterCommitWithCommittedEventIdentityAndRecordRevision() {
        val record = momentRecord()
        val boundary = boundaryWith(record)

        val result = EventEngine(boundary, FixedClock) { EventId("event-1") }
            .apply(LogMoment(record.id, source = Source.QUICK_SETTINGS))

        assertEquals(
            EngineResult.Applied(listOf(EventId("event-1")), mapOf(record.id.value to Revision(1))),
            result,
        )
        assertEquals(Revision(1), boundary.state.records.getValue(record.id).revision)
        assertTrue(boundary.committed)
    }

    private fun momentRecord(name: String = "Moment", icon: String? = null) =
        Record(RecordId("moment"), name, icon, Behavior.MOMENT)

    private fun counterRecord(defaultQuantity: Quantity) =
        Record(RecordId("counter"), "Coffee", "cup", Behavior.COUNTER, unit = UnitName("cups"), defaultQuantity = defaultQuantity)

    private fun boundaryWith(record: Record, target: Target? = null, linked: Boolean = false): InMemoryBoundary {
        val targets = target?.let { mapOf(it.id to it) }.orEmpty()
        val relationships = target?.let { mapOf((record.id to it.id) to RecordTarget(record.id, it.id, linked)) }.orEmpty()
        return InMemoryBoundary(DomainState(records = mapOf(record.id to record), targets = targets, relationships = relationships))
    }

    private object FixedClock : AcceptanceClock {
        override fun now() = EpochMillis(1_000)
    }

    private object AdvancingClock : AcceptanceClock {
        private var calls = 0

        override fun now(): EpochMillis {
            calls += 1
            return EpochMillis(calls * 1_000L)
        }
    }

    private class InMemoryBoundary(initial: DomainState) : AtomicCommitBoundary {
        var state = initial
        var committed = false
        override fun read() = state
        override fun commit(operation: CommitOperation): Boolean {
            state = operation.state
            committed = true
            return true
        }
    }
}
