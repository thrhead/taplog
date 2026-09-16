package io.github.thrhead.taplog.core.engine

import io.github.thrhead.taplog.core.domain.*

data class RecordEdit(val name: String? = null, val icon: String? = null, val defaultQuantity: Quantity? = null,
    val behavior: Behavior? = null, val unit: UnitName? = null)
data class TargetEdit(val name: String? = null, val icon: String? = null)

object DefinitionManagement {
    fun edit(record: Record, change: RecordEdit): Record {
        require(change.defaultQuantity == null || change.defaultQuantity.isValid)
        require(!record.hasEvents || (change.behavior == null || change.behavior == record.behavior))
        require(!record.hasEvents || (change.unit == null || change.unit == record.unit))
        return record.copy(
        name = change.name ?: record.name, icon = change.icon ?: record.icon,
        behavior = change.behavior ?: record.behavior, unit = change.unit ?: record.unit,
        defaultQuantity = change.defaultQuantity ?: record.defaultQuantity,
        revision = Revision(record.revision.value + 1),
        )
    }
    fun edit(target: io.github.thrhead.taplog.core.domain.Target, change: TargetEdit): io.github.thrhead.taplog.core.domain.Target = target.copy(name = change.name ?: target.name, icon = change.icon ?: target.icon, revision = Revision(target.revision.value + 1))
    fun archive(record: Record): Record = record.copy(lifecycle = Lifecycle.ARCHIVED, revision = Revision(record.revision.value + 1))
    fun unarchive(record: Record): Record = record.copy(lifecycle = Lifecycle.ACTIVE, revision = Revision(record.revision.value + 1))
    fun archive(target: io.github.thrhead.taplog.core.domain.Target): io.github.thrhead.taplog.core.domain.Target = target.copy(lifecycle = Lifecycle.ARCHIVED, revision = Revision(target.revision.value + 1))
    fun unarchive(target: io.github.thrhead.taplog.core.domain.Target): io.github.thrhead.taplog.core.domain.Target = target.copy(lifecycle = Lifecycle.ACTIVE, revision = Revision(target.revision.value + 1))
    fun unlink(binding: RecordTarget): RecordTarget = binding.copy(linked = false, revision = Revision(binding.revision.value + 1))
}
