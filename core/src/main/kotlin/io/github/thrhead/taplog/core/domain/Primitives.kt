package io.github.thrhead.taplog.core.domain

@JvmInline value class RecordId(val value: String)
@JvmInline value class TargetId(val value: String)
@JvmInline value class EventId(val value: String)
@JvmInline value class StateGroupId(val value: String)
@JvmInline value class BindingId(val value: String)
@JvmInline value class ReceiptId(val value: String)

@JvmInline value class EpochMillis(val value: Long) : Comparable<EpochMillis> {
    override fun compareTo(other: EpochMillis) = value.compareTo(other.value)
}
@JvmInline value class EventSequence(val value: Long) : Comparable<EventSequence> {
    override fun compareTo(other: EventSequence) = value.compareTo(other.value)
}
typealias Sequence = EventSequence
@JvmInline value class Revision(val value: Long) : Comparable<Revision> {
    override fun compareTo(other: Revision) = value.compareTo(other.value)
}
@JvmInline value class DatasetGeneration(val value: Long) : Comparable<DatasetGeneration> {
    override fun compareTo(other: DatasetGeneration) = value.compareTo(other.value)
}

sealed interface TargetScope {
    data object NoTarget : TargetScope
    data class ForTarget(val targetId: TargetId) : TargetScope
}
