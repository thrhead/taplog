package io.github.thrhead.taplog.core.engine

import io.github.thrhead.taplog.core.domain.*
data class UndoReceipt(val id: ReceiptId, val eventId: EventId, val expectedRevision: Revision,
    val generation: DatasetGeneration, val consumed: Boolean = false)
data class ConfirmationToken(val value: String)
