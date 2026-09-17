package io.github.thrhead.taplog.data.persistence

import io.github.thrhead.taplog.core.domain.Behavior
import io.github.thrhead.taplog.core.domain.EpochMillis
import io.github.thrhead.taplog.core.domain.EventPayload
import io.github.thrhead.taplog.core.domain.Quantity
import io.github.thrhead.taplog.core.domain.TargetId
import io.github.thrhead.taplog.core.domain.TargetScope
import java.math.BigDecimal

internal sealed class PersistenceFailure(message: String, cause: Throwable? = null) : RuntimeException(message, cause) {
    val retryable: Boolean get() = this is DatabaseOpenFailure || this is DatabaseReadFailure
}
internal class DatabaseOpenFailure(message: String, cause: Throwable? = null) : PersistenceFailure(message, cause)
internal class DatabaseReadFailure(message: String, cause: Throwable? = null) : PersistenceFailure(message, cause)
internal class MigrationFailure(message: String, cause: Throwable? = null) : PersistenceFailure(message, cause)
internal class CorruptRowFailure(message: String, cause: Throwable? = null) : PersistenceFailure(message, cause)
internal class MappingFailure(message: String, cause: Throwable? = null) : PersistenceFailure(message, cause)

internal object PersistenceMapper {
    private val canonicalQuantityText = Regex("(?:[1-9][0-9]*(?:\\.[0-9]*[1-9])?|0\\.[0-9]*[1-9])")

    /** Numeric value is exact; redundant decimal scale is not part of the stored representation. */
    fun encodeQuantity(quantity: Quantity): String {
        requireInvariant(quantity.isValid, "Quantity must be positive")
        return try {
            quantity.value.stripTrailingZeros().toPlainString()
        } catch (cause: ArithmeticException) {
            throw MappingFailure("Quantity cannot be encoded as plain decimal text", cause)
        }
    }

    /** Persisted text must already be canonical; invalid rows are never normalized on read. */
    fun decodeQuantity(text: String): Quantity {
        // Reject exponent/locale/scale variants before any potential decimal expansion.
        requireInvariant(canonicalQuantityText.matches(text), "Quantity text must be positive canonical decimal")
        val decimal = try {
            BigDecimal(text)
        } catch (cause: NumberFormatException) {
            throw MappingFailure("Invalid quantity text", cause)
        }
        val quantity = Quantity.exact(decimal) ?: throw MappingFailure("Quantity must be positive")
        return quantity
    }

    // Core EpochMillis is already a UTC epoch offset, not a local wall-clock time.
    fun encodeEpochMillis(epoch: EpochMillis): Long = epoch.value
    fun decodeEpochMillis(value: Long): EpochMillis = EpochMillis(value)

    fun encodeEnum(value: Enum<*>): String = value.name

    fun <E : Enum<E>> decodeEnum(text: String, type: Class<E>): E = try {
        java.lang.Enum.valueOf(type, text)
    } catch (cause: IllegalArgumentException) {
        throw MappingFailure("Unknown ${type.simpleName} value: $text", cause)
    }

    fun encodeTargetScope(scope: TargetScope): String = when (scope) {
        TargetScope.NoTarget -> NO_TARGET_SCOPE_KEY
        is TargetScope.ForTarget -> TARGET_SCOPE_KEY_PREFIX + scope.targetId.value
    }

    fun decodeTargetScope(key: String): TargetScope = when {
        key == NO_TARGET_SCOPE_KEY -> TargetScope.NoTarget
        key.startsWith(TARGET_SCOPE_KEY_PREFIX) ->
            TargetScope.ForTarget(TargetId(key.removePrefix(TARGET_SCOPE_KEY_PREFIX)))
        else -> throw MappingFailure("Invalid target scope key: $key")
    }

    fun payloadBehavior(payload: EventPayload): Behavior = when (payload) {
        EventPayload.Moment -> Behavior.MOMENT
        is EventPayload.Counter -> Behavior.COUNTER
        is EventPayload.Duration -> Behavior.DURATION
        is EventPayload.State -> Behavior.STATE
    }

    fun requirePayloadAgreement(behavior: Behavior, payload: EventPayload) {
        requireInvariant(behavior == payloadBehavior(payload), "Behavior and payload discriminator disagree")
    }

    fun requireInvariant(valid: Boolean, message: String) {
        if (!valid) throw MappingFailure(message)
    }
}
