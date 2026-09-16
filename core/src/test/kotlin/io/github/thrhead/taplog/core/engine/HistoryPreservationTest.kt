package io.github.thrhead.taplog.core.engine

import io.github.thrhead.taplog.core.domain.*
import org.junit.Assert.*
import org.junit.Test

class HistoryPreservationTest {
    @Test fun deletingTheLastEventDoesNotUnlockRecordBehaviorOrUnit() {
        val record = Record(RecordId("counter"), "Counter", null, Behavior.COUNTER, unit = UnitName("cups"))
        val boundary = Boundary(DomainState(records = mapOf(record.id to record)))
        val engine = EventEngine(boundary, Clock)
        engine.apply(AddCounter(record.id, source = Source.APP))

        engine.apply(DeleteEvent(boundary.state.events.single().id, Source.APP, confirmed = true))

        assertThrows(IllegalArgumentException::class.java) {
            engine.editRecord(record.id, RecordEdit(behavior = Behavior.MOMENT))
        }
        assertThrows(IllegalArgumentException::class.java) {
            engine.editRecord(record.id, RecordEdit(unit = UnitName("kg")))
        }
    }

    @Test fun definitionAndLifecycleMutationsAdvanceRevisions() {
        val record = Record(RecordId("r"), "R", null, Behavior.MOMENT)
        val target = Target(TargetId("t"), "T", null)
        val boundary = Boundary(DomainState(
            records = mapOf(record.id to record), targets = mapOf(target.id to target),
            relationships = mapOf((record.id to target.id) to RecordTarget(record.id, target.id)),
        ))
        val engine = EventEngine(boundary, Clock)

        engine.editRecord(record.id, RecordEdit(name = "R2"))
        assertEquals(Revision(1), boundary.state.records.getValue(record.id).revision)
        engine.archiveRecord(record.id)
        assertEquals(Revision(2), boundary.state.records.getValue(record.id).revision)
        engine.editTarget(target.id, TargetEdit(name = "T2"))
        assertEquals(Revision(1), boundary.state.targets.getValue(target.id).revision)
        engine.unlink(record.id, target.id)
        assertEquals(Revision(1), boundary.state.relationships.getValue(record.id to target.id).revision)
    }

    @Test fun definitionEditsDoNotRewriteSnapshotsAndDefaultOnlyAffectsFutureEvents() {
        val record = Record(RecordId("coffee"), "Coffee", "cup", Behavior.COUNTER, unit = UnitName("cups"))
        val boundary = Boundary(DomainState(records = mapOf(record.id to record)))
        val engine = EventEngine(boundary, Clock)
        engine.apply(AddCounter(record.id, source = Source.APP))
        engine.editRecord(record.id, RecordEdit(name = "Tea", icon = "tea", defaultQuantity = Quantity.exact("2")))
        engine.apply(AddCounter(record.id, source = Source.APP))
        assertEquals("Coffee", boundary.state.events[0].snapshot.recordName)
        assertEquals(Quantity.exact("1"), (boundary.state.events[0].payload as EventPayload.Counter).quantity)
        assertEquals(Quantity.exact("2"), (boundary.state.events[1].payload as EventPayload.Counter).quantity)
    }
    @Test fun archiveAndUnarchiveRetainIdentityAndHistoryWithoutReopening() {
        val record = Record(RecordId("run"), "Run", null, Behavior.DURATION)
        val boundary = Boundary(DomainState(records = mapOf(record.id to record)))
        val engine = EventEngine(boundary, Clock)
        engine.apply(StartDuration(record.id, occurredAt = EpochMillis(10), source = Source.APP))
        engine.archiveRecord(record.id)
        assertEquals(DurationStatus.INCOMPLETE, (boundary.state.events.single().payload as EventPayload.Duration).status)
        engine.unarchiveRecord(record.id)
        assertEquals(1, boundary.state.events.size)
        assertEquals(Lifecycle.ACTIVE, boundary.state.records.getValue(record.id).lifecycle)
        assertEquals(DurationStatus.INCOMPLETE, (boundary.state.events.single().payload as EventPayload.Duration).status)
    }
    private object Clock : AcceptanceClock { override fun now() = EpochMillis(100) }
    private class Boundary(var state: DomainState) : AtomicCommitBoundary {
        override fun read() = state
        override fun commit(operation: CommitOperation): Boolean {
            if (operation.expected != state) return false
            state = operation.state; return true
        }
    }
}
