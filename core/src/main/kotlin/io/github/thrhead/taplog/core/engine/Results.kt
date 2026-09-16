package io.github.thrhead.taplog.core.engine

import io.github.thrhead.taplog.core.domain.*
sealed interface EngineResult {
    data class Applied(val eventIds: List<EventId> = emptyList(), val revisions: Map<String, Revision> = emptyMap()) : EngineResult
    data class NeedsConfirmation(val reason: ResultReason) : EngineResult
    data class Conflict(val reason: ResultReason) : EngineResult
    data class Invalid(val reason: ResultReason) : EngineResult
    data object StorageFailure : EngineResult
}
