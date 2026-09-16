package io.github.thrhead.taplog.core.domain

data class Record(
    val id: RecordId, val name: String, val icon: String?, val behavior: Behavior,
    val lifecycle: Lifecycle = Lifecycle.ACTIVE, val unit: UnitName? = null,
    val defaultQuantity: Quantity? = Quantity.exact("1"),
    val stateGroupId: StateGroupId? = null, val revision: Revision = Revision(0),
    val hasEvents: Boolean = false
)
data class Target(val id: TargetId, val name: String, val icon: String?,
    val lifecycle: Lifecycle = Lifecycle.ACTIVE, val revision: Revision = Revision(0))
data class RecordTarget(val recordId: RecordId, val targetId: TargetId,
    val linked: Boolean = true, val revision: Revision = Revision(0))
data class StateGroup(val id: StateGroupId, val name: String, val revision: Revision = Revision(0))
