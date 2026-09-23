package io.github.thrhead.taplog.core.engine

import io.github.thrhead.taplog.core.domain.*
import io.github.thrhead.taplog.core.domain.Target

data class ExpectedContext(val revision: Revision? = null, val datasetGeneration: DatasetGeneration? = null,
    val scopeGeneration: DatasetGeneration? = null)
sealed interface Command { val source: Source; val expected: ExpectedContext? }
data class LogMoment(val recordId: RecordId, val targetId: TargetId? = null, val occurredAt: EpochMillis? = null,
    override val source: Source, override val expected: ExpectedContext? = null) : Command
data class AddCounter(val recordId: RecordId, val targetId: TargetId? = null, val quantity: Quantity? = null,
    val occurredAt: EpochMillis? = null, override val source: Source, override val expected: ExpectedContext? = null) : Command
data class StartDuration(val recordId: RecordId, val targetId: TargetId? = null, val occurredAt: EpochMillis? = null,
    override val source: Source, override val expected: ExpectedContext? = null) : Command
data class FinishDuration(val openEventId: EventId, val endedAt: EpochMillis,
    override val source: Source, override val expected: ExpectedContext? = null) : Command
data class ToggleDuration(val recordId: RecordId, val targetId: TargetId? = null, val occurredAt: EpochMillis? = null,
    override val source: Source, override val expected: ExpectedContext? = null) : Command
data class SetState(val recordId: RecordId, val targetId: TargetId? = null, val occurredAt: EpochMillis? = null,
    override val source: Source, override val expected: ExpectedContext? = null) : Command
data class CreateRecordAndLog(val record: Record, val targetId: TargetId? = null, val occurredAt: EpochMillis? = null,
    override val source: Source, override val expected: ExpectedContext? = null, val confirmed: Boolean = false) : Command
data class CreateRecord(val record: Record, override val source: Source,
    override val expected: ExpectedContext? = null) : Command
data class CreateTarget(val target: Target, override val source: Source,
    override val expected: ExpectedContext? = null) : Command
data class EventChanges(val occurredAt: EpochMillis? = null, val quantity: Quantity? = null)
data class EditEvent(val eventId: EventId, val changes: EventChanges, override val source: Source,
    override val expected: ExpectedContext? = null) : Command
data class DeleteEvent(val eventId: EventId, override val source: Source, override val expected: ExpectedContext? = null,
    val confirmed: Boolean = false) : Command
data class Undo(val receipt: UndoReceipt, override val source: Source, override val expected: ExpectedContext? = null) : Command
