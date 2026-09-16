package io.github.thrhead.taplog.core.engine

import io.github.thrhead.taplog.core.domain.*
data class DeleteImpact(val recordIds: Set<RecordId>, val targetIds: Set<TargetId>, val eventIds: Set<EventId>)
data class DeleteRequest(val recordId: RecordId? = null, val targetId: TargetId? = null, val confirmation: ConfirmationToken? = null)
