package io.github.thrhead.taplog.core.engine

import io.github.thrhead.taplog.core.domain.*

data class ExpectedContext(val revision: Revision? = null, val datasetGeneration: DatasetGeneration? = null,
    val scopeGeneration: DatasetGeneration? = null)
sealed interface Command { val source: Source; val expected: ExpectedContext? }
data class LogMoment(val recordId: RecordId, val targetId: TargetId? = null, val occurredAt: EpochMillis? = null,
    override val source: Source, override val expected: ExpectedContext? = null) : Command
data class AddCounter(val recordId: RecordId, val targetId: TargetId? = null, val quantity: Quantity? = null,
    val occurredAt: EpochMillis? = null, override val source: Source, override val expected: ExpectedContext? = null) : Command
