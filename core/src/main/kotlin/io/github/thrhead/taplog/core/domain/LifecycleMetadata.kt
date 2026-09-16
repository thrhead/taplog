package io.github.thrhead.taplog.core.domain

data class DisplaySnapshot(val recordName: String, val recordIcon: String?, val targetName: String?, val targetIcon: String?)
data class UndoInvalidation(val receiptId: ReceiptId, val reason: ResultReason)
data class BindingLifecycle(
    val bindingId: BindingId,
    val recordId: RecordId,
    val targetId: TargetId?,
    val status: BindingStatus = BindingStatus.ACTIVE,
    val revision: Revision = Revision(0),
    val lastKnownDisplay: DisplaySnapshot,
    val undoInvalidations: List<UndoInvalidation> = emptyList(),
)
