package io.github.thrhead.taplog.core.domain

import java.math.BigDecimal

@JvmInline value class UnitName(val value: String) {
    val isValid: Boolean get() = value.isNotBlank()
}
class Quantity private constructor(val value: BigDecimal) {
    val isValid: Boolean get() = value.signum() > 0

    override fun equals(other: Any?): Boolean = other is Quantity && value == other.value
    override fun hashCode(): Int = value.hashCode()
    override fun toString(): String = "Quantity(value=$value)"

    companion object {
        fun exact(raw: String): Quantity? = parse(raw).takeIf { it.isValid }
        fun exact(raw: BigDecimal): Quantity? = raw.takeIf { it.signum() > 0 }?.let(::Quantity)
        fun parse(raw: String): Quantity = exactOrZero(raw)

        private fun exactOrZero(raw: String): Quantity =
            runCatching { exact(BigDecimal(raw)) }.getOrNull() ?: Quantity(BigDecimal.ZERO)
    }
}
