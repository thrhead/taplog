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
data class Event(val id: EventId, val recordId: RecordId, val targetId: TargetId?, val behavior: Behavior,
    val occurredAt: EpochMillis, val createdAt: EpochMillis, val updatedAt: EpochMillis,
    val sequence: Sequence, val source: Source, val revision: Revision,
    val snapshot: EventSnapshot, val payload: EventPayload)
