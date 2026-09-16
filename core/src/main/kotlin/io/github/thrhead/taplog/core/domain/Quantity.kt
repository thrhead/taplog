package io.github.thrhead.taplog.core.domain

import java.math.BigDecimal

@JvmInline value class UnitName(val value: String) {
    val isValid: Boolean get() = value.isNotBlank()
}
data class Quantity(val value: BigDecimal, val isValid: Boolean = true) {
    companion object {
        fun exact(raw: String): Quantity? = parse(raw).takeIf { it.isValid }
        fun exact(raw: BigDecimal): Quantity? = raw.takeIf { it.signum() > 0 }?.let(::Quantity)
        fun parse(raw: String): Quantity = runCatching { Quantity(BigDecimal(raw), BigDecimal(raw).signum() > 0) }
            .getOrElse { Quantity(BigDecimal.ZERO, false) }
    }
}
