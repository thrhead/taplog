package io.github.thrhead.taplog.core.engine

import io.github.thrhead.taplog.core.domain.*

data class DeleteImpact(val recordId: RecordId, val targetId: TargetId?, val eventIds: List<EventId>)
data class DeleteConfirmation(val token: String, val impact: DeleteImpact)
object DeletionScope {
    fun preview(state: DomainState, recordId: RecordId, targetId: TargetId? = null): DeleteImpact =
        DeleteImpact(recordId, targetId, state.events.filter { it.recordId == recordId && (targetId == null || it.targetId == targetId) }.map { it.id })

    fun confirm(impact: DeleteImpact, token: String): DeleteConfirmation = DeleteConfirmation(token, impact)
}
