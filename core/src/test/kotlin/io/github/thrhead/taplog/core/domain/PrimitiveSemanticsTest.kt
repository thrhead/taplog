package io.github.thrhead.taplog.core.domain

import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrimitiveSemanticsTest {
    @Test
    fun primitiveSemanticsTestLaneIsOperational() {
        assertTrue("Primitive semantics test lane should run", true)
    }

    @Test
    fun foundationalValuesPreserveIdentityOrderingAndDistinctNoTargetScope() {
        val target = TargetId("target-1")

        assertEquals(RecordId("record-1"), RecordId("record-1"))
        assertEquals(EpochMillis(1_700_000_000_000L), EpochMillis(1_700_000_000_000L))
        assertTrue(EventSequence(2) > EventSequence(1))
        assertTrue(Revision(2) > Revision(1))
        assertTrue(DatasetGeneration(2) > DatasetGeneration(1))
        assertFalse(TargetScope.NoTarget == TargetScope.ForTarget(target))
    }

    @Test
    fun quantityAcceptsOnlyPositiveExactDecimalsAndNonBlankUnits() {
        assertEquals(BigDecimal("1.25"), Quantity.parse("1.25").value)
        assertFalse(Quantity.parse("0").isValid)
        assertFalse(Quantity.parse("-1").isValid)
        assertFalse(Quantity.parse("NaN").isValid)
        assertEquals(UnitName("ml"), UnitName("ml"))
    }

    @Test
    fun sourceEnumContainsEveryApprovedOriginWithoutChangingBehavior() {
        assertEquals(
            setOf(
                EventSource.APP,
                EventSource.WIDGET,
                EventSource.QUICK_SETTINGS,
                EventSource.NFC,
                EventSource.NATURAL_LANGUAGE,
            ),
            EventSource.entries.toSet(),
        )
    }
}
