package io.github.thrhead.taplog.data.persistence

import io.github.thrhead.taplog.core.domain.DomainState
import io.github.thrhead.taplog.core.domain.EpochMillis
import io.github.thrhead.taplog.core.domain.TargetId
import io.github.thrhead.taplog.core.domain.TargetScope
import io.github.thrhead.taplog.core.domain.Quantity
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
    fun aggregateSnapshotsAndNullableDiscriminatorsRemainCoveredByCanonicalSurface() {
        PersistenceMapperAggregateTest().completeAggregateRoundTripsEveryFamilyAndHistoricalSnapshot()
        PersistenceMapperAggregateTest().rowsRejectMissingAndExtraneousTypedPayloadColumns()
        PersistenceMapperAggregateTest().rowsRejectDiscriminatorSnapshotUnitQuantityAndScopeDisagreement()
    }
}
