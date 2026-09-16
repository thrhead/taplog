package io.github.thrhead.taplog.core.domain

data class StateScope(val groupId: StateGroupId, val targetId: TargetId?, val generation: DatasetGeneration)
data class CurrentState(val eventId: EventId, val recordId: RecordId, val scope: StateScope)
data class StateReset(val scope: StateScope, val resetSequence: Sequence)
