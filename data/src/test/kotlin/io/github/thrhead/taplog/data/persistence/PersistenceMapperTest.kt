package io.github.thrhead.taplog.data.persistence

import io.github.thrhead.taplog.core.domain.*
import io.github.thrhead.taplog.core.engine.DomainState
import io.github.thrhead.taplog.core.engine.StateScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/** Canonical, requirement-oriented JVM surface for the persistence mapper. */
class PersistenceMapperTest {
    @Test
    fun emptyDomainStateRoundTripsThroughCanonicalRows() {
        val rows = PersistenceMapper.toRows(DomainState())

        assertEquals(DomainState(), PersistenceMapper.fromRows(rows))
        assertEquals(listOf(DatasetMetadataEntity(1, 0, 1, 1)), rows.metadata)
    }

    @Test
    fun canonicalDecimalTextIsLocaleIndependentAndNumericallyEquivalent() {
        val quantity = Quantity.exact("1.2300")!!

        assertEquals("1.23", PersistenceMapper.encodeQuantity(quantity))
        assertEquals(0, PersistenceMapper.decodeQuantity("1.23").value.compareTo(quantity.value))
    }

    @Test
    fun epochMillisecondsAreStoredAsUtcIndependentSignedLongs() {
        listOf(Long.MIN_VALUE, -1L, 0L, 1L, Long.MAX_VALUE).forEach { value ->
            assertEquals(value, PersistenceMapper.encodeEpochMillis(EpochMillis(value)))
            assertEquals(EpochMillis(value), PersistenceMapper.decodeEpochMillis(value))
        }
    }

    @Test
    fun noTargetScopeKeyCannotCollideWithTargetIds() {
        assertEquals("no-target", PersistenceMapper.encodeTargetScope(TargetScope.NoTarget))
        assertEquals(TargetScope.NoTarget, PersistenceMapper.decodeTargetScope("no-target"))

        val target = TargetScope.ForTarget(TargetId("no-target"))
        assertEquals("target:no-target", PersistenceMapper.encodeTargetScope(target))
        assertEquals(target, PersistenceMapper.decodeTargetScope("target:no-target"))
    }

    @Test
    fun invalidRowsAreRejectedDeterministicallyWithMappingFailure() {
        val rows = PersistenceMapper.toRows(DomainState())
        val invalid = rows.copy(metadata = listOf(DatasetMetadataEntity(2, 0, 1, 1)))

        assertThrows(MappingFailure::class.java) { PersistenceMapper.fromRows(invalid) }
    }

    @Test
    fun snapshotsNullableTargetsAndDiscriminatorsRoundTripWithoutRepair() {
        val record = Record(RecordId("record"), "Current name", "current-icon", Behavior.MOMENT, hasEvents = true)
        val event = Event(
            EventId("event"), record.id, null, Behavior.MOMENT,
            EpochMillis(10), EpochMillis(11), EpochMillis(12), Sequence(1), Source.APP, Revision(1),
            EventSnapshot("Historical name", "historical-icon", null, null, Behavior.MOMENT, null),
            EventPayload.Moment,
        )
        val state = DomainState(records = mapOf(record.id to record), events = listOf(event), nextSequence = Sequence(2))
        val rows = PersistenceMapper.toRows(state)

        assertEquals("Historical name", rows.events.single().snapshotRecordName)
        assertEquals(null, rows.events.single().targetId)
        assertEquals("no-target", rows.events.single().targetScopeKey)
        assertEquals(state, PersistenceMapper.fromRows(rows))

        val wrongDiscriminator = rows.copy(events = listOf(rows.events.single().copy(behavior = "COUNTER")))
        assertThrows(MappingFailure::class.java) { PersistenceMapper.fromRows(wrongDiscriminator) }
    }
    @Test
    fun allEventBehaviorsRoundTripWithPositiveCounterQuantity() {
        val group = StateGroupId("group")
        val recordIds = Behavior.entries.associateWith { RecordId(it.name.lowercase()) }
        val records = recordIds.entries.associate { (behavior, id) -> id to Record(id, behavior.name, null, behavior,
                stateGroupId = if (behavior == Behavior.STATE) group else null,
                defaultQuantity = if (behavior == Behavior.COUNTER) Quantity.exact("2") else null,
                hasEvents = true) }
        val snapshot = { behavior: Behavior -> EventSnapshot(behavior.name, null, null, null, behavior, null) }
        val payloads = mapOf(
            Behavior.MOMENT to EventPayload.Moment,
            Behavior.COUNTER to EventPayload.Counter(Quantity.exact("0.125")!!, null),
            Behavior.DURATION to EventPayload.Duration(EpochMillis(10), EpochMillis(20), DurationStatus.COMPLETED),
            Behavior.STATE to EventPayload.State(group, DatasetGeneration(3)),
        )
        val events = Behavior.entries.mapIndexed { index, behavior ->
            Event(EventId("event-$index"), recordIds.getValue(behavior), null, behavior,
                EpochMillis(index.toLong()), EpochMillis(index.toLong()), EpochMillis(index.toLong()),
                Sequence(index + 1L), Source.APP, Revision(0), snapshot(behavior), payloads.getValue(behavior))
        }
        val state = DomainState(records = records, events = events, stateGroups = mapOf(group to StateGroup(group, "Group")),
            stateGenerations = mapOf(StateScope(group, null) to DatasetGeneration(3)), nextSequence = Sequence(5))

        val rows = PersistenceMapper.toRows(state)
        assertEquals(state, PersistenceMapper.fromRows(rows))
        listOf("0", "-1").forEach { quantity ->
            assertThrows(MappingFailure::class.java) {
                PersistenceMapper.fromRows(rows.copy(events = rows.events.map {
                    if (it.behavior == Behavior.COUNTER.name) it.copy(counterQuantity = quantity) else it
                }))
            }
        }
    }

    @Test
    fun durationTerminalRowsRemainTerminalAndMalformedTerminalsFailClosed() {
        val record = Record(RecordId("duration"), "Duration", null, Behavior.DURATION, hasEvents = true)
        fun event(id: String, status: DurationStatus, endAt: EpochMillis?, reason: String?) = Event(
            EventId(id), record.id, null, Behavior.DURATION, EpochMillis(1), EpochMillis(1), EpochMillis(1),
            Sequence(if (status == DurationStatus.COMPLETED) 1 else 2), Source.APP, Revision(0),
            EventSnapshot("Duration", null, null, null, Behavior.DURATION, null),
            EventPayload.Duration(EpochMillis(0), endAt, status, reason),
        )
        val state = DomainState(records = mapOf(record.id to record), events = listOf(
            event("completed", DurationStatus.COMPLETED, EpochMillis(5), null),
            event("incomplete", DurationStatus.INCOMPLETE, null, "interrupted"),
        ), nextSequence = Sequence(3))
        val rows = PersistenceMapper.toRows(state)

        assertEquals(state, PersistenceMapper.fromRows(rows))
        assertThrows(MappingFailure::class.java) {
            PersistenceMapper.fromRows(rows.copy(events = rows.events.map {
                if (it.eventId == "incomplete") it.copy(durationEndAt = 9) else it
            }))
        }
    }

    @Test
    fun statePayloadPreservesScopeGenerationAndRejectsScopeMismatch() {
        val group = StateGroupId("group")
        val target = TargetId("target")
        val scope = StateScope(group, target)
        val record = Record(RecordId("state"), "State", null, Behavior.STATE, stateGroupId = group, hasEvents = true)
        val state = DomainState(
            records = mapOf(record.id to record),
            targets = mapOf(target to Target(target, "Target", null)),
            stateGroups = mapOf(group to StateGroup(group, "Group")),
            events = listOf(Event(EventId("state-event"), record.id, target, Behavior.STATE,
                EpochMillis(1), EpochMillis(1), EpochMillis(1), Sequence(1), Source.APP, Revision(2),
                EventSnapshot("State", null, "Target", null, Behavior.STATE, null),
                EventPayload.State(group, DatasetGeneration(4)))),
            stateGenerations = mapOf(scope to DatasetGeneration(4)),
            nextSequence = Sequence(2),
        )
        val rows = PersistenceMapper.toRows(state)

        assertEquals(state, PersistenceMapper.fromRows(rows))
        assertThrows(MappingFailure::class.java) {
            PersistenceMapper.fromRows(rows.copy(events = rows.events.map { it.copy(targetScopeKey = "no-target") }))
        }
        assertThrows(MappingFailure::class.java) {
            PersistenceMapper.fromRows(rows.copy(events = rows.events.map {
                it.copy(stateGroupId = "other-group")
            }))
        }
        assertThrows(MappingFailure::class.java) {
            PersistenceMapper.fromRows(rows.copy(events = rows.events.map {
                it.copy(stateGeneration = 5)
            }))
        }
    }

    @Test
    fun mapperAcceptsNonnegativeRevisionsAndAllocatedSequencesOnly() {
        val record = Record(RecordId("moment"), "Moment", null, Behavior.MOMENT, revision = Revision(0), hasEvents = true)
        val event = Event(EventId("event"), record.id, null, Behavior.MOMENT,
            EpochMillis(1), EpochMillis(1), EpochMillis(1), Sequence(1), Source.APP, Revision(0),
            EventSnapshot("Moment", null, null, null, Behavior.MOMENT, null), EventPayload.Moment)
        val state = DomainState(records = mapOf(record.id to record), events = listOf(event), nextSequence = Sequence(2))
        val rows = PersistenceMapper.toRows(state)

        val restored = PersistenceMapper.fromRows(rows)
        assertEquals(state, restored)
        assertEquals(0L, restored.records.getValue(record.id).revision.value)
        assertEquals(0L, restored.events.single().revision.value)
        assertEquals(1L, restored.events.single().sequence.value)
        assertEquals(2L, restored.nextSequence.value)
        assertThrows(MappingFailure::class.java) {
            PersistenceMapper.fromRows(rows.copy(events = rows.events.map { it.copy(sequence = 2) }))
        }
        assertThrows(MappingFailure::class.java) {
            PersistenceMapper.fromRows(rows.copy(events = rows.events + rows.events.single().copy(eventId = "duplicate")))
        }
        assertThrows(MappingFailure::class.java) {
            PersistenceMapper.fromRows(rows.copy(records = rows.records.map { it.copy(revision = -1) }))
        }
    }
}
