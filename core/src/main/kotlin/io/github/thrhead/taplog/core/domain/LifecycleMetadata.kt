package io.github.thrhead.taplog.core.domain

data class DisplaySnapshot(val recordName: String, val recordIcon: String?, val targetName: String?, val targetIcon: String?)
data class BindingLifecycle(val bindingId: BindingId, val recordId: RecordId, val targetId: TargetId?,
    val status: BindingStatus, val revision: Revision, val lastKnown: DisplaySnapshot)
