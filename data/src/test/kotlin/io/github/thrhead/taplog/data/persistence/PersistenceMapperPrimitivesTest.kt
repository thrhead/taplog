package io.github.thrhead.taplog.data.persistence

import io.github.thrhead.taplog.core.domain.*
import java.math.BigDecimal
import java.math.BigInteger
import java.util.Locale
import org.junit.Assert.*
import org.junit.Test

class PersistenceMapperPrimitivesTest {
    @Test
    fun quantitiesEncodeAsCanonicalPlainDecimalRegardlessOfLocale() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMANY)
            listOf("1.2300" to "1.23", "1E+3" to "1000", "0.0000100" to "0.00001",
                "12345678901234567890.123456789" to "12345678901234567890.123456789")
                .forEach { (input, expected) ->
                    assertEquals(expected, PersistenceMapper.encodeQuantity(Quantity.exact(input)!!))
                    assertEquals(0, PersistenceMapper.decodeQuantity(expected).value.compareTo(Quantity.exact(input)!!.value))
                }
        } finally {
            Locale.setDefault(previous)
        }
    }

    @Test
    fun invalidAndNonCanonicalQuantityTextIsRejectedWithoutCoercingToZero() {
        listOf("", "0", "-1", "NaN", "1,5", " 1", "1 ", "+1", "01", "1.0", "1e3")
            .forEach { text -> mappingFailure { PersistenceMapper.decodeQuantity(text) } }
        mappingFailure { PersistenceMapper.encodeQuantity(Quantity.parse("not a quantity")) }
    }

    @Test
    fun decimalConversionOverflowIsReportedAsMappingFailure() {
        val quantity = Quantity.exact(BigDecimal(BigInteger.ONE, Int.MIN_VALUE))!!
        val error = mappingFailure { PersistenceMapper.encodeQuantity(quantity) }
        assertTrue(error.cause is ArithmeticException)
    }

    @Test
    fun epochConversionPreservesEverySignedLongBoundaryWithoutTimezoneAdjustment() {
        listOf(Long.MIN_VALUE, -1L, 0L, 1L, Long.MAX_VALUE).forEach { value ->
            assertEquals(value, PersistenceMapper.encodeEpochMillis(EpochMillis(value)))
            assertEquals(EpochMillis(value), PersistenceMapper.decodeEpochMillis(value))
        }
    }

    @Test
    fun enumNamesRoundTripAcrossAllPersistedDomainEnums() {
        assertEnums(Behavior.entries, Behavior::class.java)
        assertEnums(Lifecycle.entries, Lifecycle::class.java)
        assertEnums(Source.entries, Source::class.java)
        assertEnums(DurationStatus.entries, DurationStatus::class.java)
        assertEnums(BindingStatus.entries, BindingStatus::class.java)
        assertEnums(ResultReason.entries, ResultReason::class.java)
        assertEquals("QUICK_SETTINGS", PersistenceMapper.encodeEnum(Source.QUICK_SETTINGS))
        assertEquals(Behavior.COUNTER, PersistenceMapper.decodeEnum("COUNTER", Behavior::class.java))
    }

    @Test
    fun unknownOrWrongCaseEnumsFailWithContextAndNoDefault() {
        listOf("counter", "COUNTER ", "FUTURE", "").forEach { raw ->
            val error = mappingFailure { PersistenceMapper.decodeEnum(raw, Behavior::class.java) }
            assertTrue(error.message!!.contains("Behavior"))
            assertFalse(error.retryable)
        }
    }

    @Test
    fun scopeKeysKeepNoTargetDisjointFromAllRealTargetIds() {
        assertEquals("no-target", PersistenceMapper.encodeTargetScope(TargetScope.NoTarget))
        assertEquals(TargetScope.NoTarget, PersistenceMapper.decodeTargetScope("no-target"))
        listOf("" to "target:", "no-target" to "target:no-target",
            "target:x" to "target:target:x", "normal" to "target:normal").forEach { (id, key) ->
            val scope = TargetScope.ForTarget(TargetId(id))
            assertEquals(key, PersistenceMapper.encodeTargetScope(scope))
            assertEquals(scope, PersistenceMapper.decodeTargetScope(key))
        }
    }

    @Test
    fun malformedScopeKeysFailWithoutSubstitutingNoTarget() {
        listOf("", "normal", "no-target ", "Target:x").forEach { key ->
            mappingFailure { PersistenceMapper.decodeTargetScope(key) }
        }
    }

    @Test
    fun payloadDiscriminatorMustAgreeWithItsTypedPayload() {
        val payloads = listOf(
            Behavior.MOMENT to EventPayload.Moment,
            Behavior.COUNTER to EventPayload.Counter(Quantity.exact("2")!!, null),
            Behavior.DURATION to EventPayload.Duration(EpochMillis(-1)),
            Behavior.STATE to EventPayload.State(StateGroupId("group"), DatasetGeneration(0)),
        )
        payloads.forEach { (expected, payload) ->
            assertEquals(expected, PersistenceMapper.payloadBehavior(payload))
            PersistenceMapper.requirePayloadAgreement(expected, payload)
            Behavior.entries.filter { it != expected }.forEach { other ->
                mappingFailure { PersistenceMapper.requirePayloadAgreement(other, payload) }
            }
        }
    }

    @Test
    fun invariantGuardsRejectInvalidValuesWithTypedContext() {
        PersistenceMapper.requireInvariant(true, "valid")
        val error = mappingFailure { PersistenceMapper.requireInvariant(false, "event revision is negative") }
        assertEquals("event revision is negative", error.message)
    }

    @Test
    fun failureCategoriesExposeApprovedRetryabilityAndPreserveCauses() {
        val cause = IllegalStateException("cause")
        val failures = listOf(
            DatabaseOpenFailure("open", cause) to true,
            DatabaseReadFailure("read", cause) to true,
            MigrationFailure("migration", cause) to false,
            CorruptRowFailure("corrupt", cause) to false,
            MappingFailure("mapping", cause) to false,
        )
        failures.forEach { (error, retryable) ->
            assertEquals(retryable, error.retryable)
            assertSame(cause, error.cause)
        }
    }

    private fun <E : Enum<E>> assertEnums(values: List<E>, type: Class<E>) {
        values.forEach { value ->
            assertEquals(value.name, PersistenceMapper.encodeEnum(value))
            assertEquals(value, PersistenceMapper.decodeEnum(value.name, type))
        }
    }

    private fun mappingFailure(block: () -> Unit): MappingFailure =
        assertThrows(MappingFailure::class.java, block)
}
