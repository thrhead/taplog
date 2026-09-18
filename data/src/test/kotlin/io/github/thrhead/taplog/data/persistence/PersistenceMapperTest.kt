package io.github.thrhead.taplog.data.persistence

import io.github.thrhead.taplog.core.domain.*
import io.github.thrhead.taplog.core.engine.DomainState
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
}
