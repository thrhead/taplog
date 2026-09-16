package io.github.thrhead.taplog.core.engine

import io.github.thrhead.taplog.core.domain.*

data class ScopeContext(val scope: StateScope, val generation: DatasetGeneration)
data class UndoReceipt(val receiptId: ReceiptId, val eventId: EventId, val eventRevision: Revision,
    val datasetGeneration: DatasetGeneration, val scope: ScopeContext? = null)
