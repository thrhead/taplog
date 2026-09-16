package io.github.thrhead.taplog.core.domain

data class EventSnapshot(val recordName: String, val recordIcon: String?, val targetName: String?,
    val targetIcon: String?, val behavior: Behavior, val unit: UnitName?)
sealed interface EventPayload {
    data object Moment : EventPayload
    data class Counter(val quantity: Quantity, val unit: UnitName?) : EventPayload
    data class Duration(val startAt: EpochMillis, val endAt: EpochMillis? = null,
        val status: DurationStatus = DurationStatus.OPEN, val incompleteReason: String? = null) : EventPayload
    data class State(val stateGroupId: StateGroupId, val generation: DatasetGeneration) : EventPayload
}

fun EventPayload.isValidFor(behavior: Behavior): Boolean = when (behavior) {
    Behavior.MOMENT -> this === EventPayload.Moment
    Behavior.COUNTER -> this is EventPayload.Counter && quantity.isValid && (unit == null || unit.isValid)
    Behavior.DURATION -> this is EventPayload.Duration && when (status) {
        DurationStatus.OPEN -> endAt == null && incompleteReason == null
        DurationStatus.COMPLETED -> endAt != null && endAt >= startAt && incompleteReason == null
        DurationStatus.INCOMPLETE -> endAt == null && !incompleteReason.isNullOrBlank()
    }
    Behavior.STATE -> this is EventPayload.State
}

data class Event(val id: EventId, val recordId: RecordId, val targetId: TargetId?, val behavior: Behavior,
    val occurredAt: EpochMillis, val createdAt: EpochMillis, val updatedAt: EpochMillis,
    val sequence: Sequence, val source: Source, val revision: Revision,
    val snapshot: EventSnapshot, val payload: EventPayload) {
    init {
        require(payload.isValidFor(behavior)) { "Invalid payload for event behavior" }
        require(snapshot.behavior == behavior) { "Snapshot behavior must match event behavior" }
        if (payload is EventPayload.Counter) require(snapshot.unit == payload.unit) { "Snapshot unit must match counter unit" }
    }
}
