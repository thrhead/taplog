package io.github.thrhead.taplog.data.persistence

import io.github.thrhead.taplog.core.engine.AtomicCommitBoundary
import io.github.thrhead.taplog.core.engine.CommitOperation
import io.github.thrhead.taplog.core.engine.DomainState
import io.github.thrhead.taplog.core.engine.StateScope
import io.github.thrhead.taplog.core.domain.RecordId
import io.github.thrhead.taplog.core.domain.TargetId
import io.github.thrhead.taplog.core.domain.TargetScope

/** Test-owned aggregate store, never a production persistence fallback. */
internal class InMemoryLocalPersistence(initial: DomainState = DomainState()) : AtomicCommitBoundary {
    private var state = initial.detached()
    private var rejectNextCommit = false

    @Synchronized
    override fun read(): DomainState = state.detached()

    /** Reject the next commit attempt without publishing any of its state. */
    @Synchronized
    fun failNextCommit() {
        rejectNextCommit = true
    }

    @Synchronized
    override fun commit(operation: CommitOperation): Boolean {
        if (rejectNextCommit) {
            rejectNextCommit = false
            return false
        }
        if (operation.expected != state) return false
        state = operation.state.detached()
        return true
    }
}

/** Test-only DAO-shaped query surface over the fake's persisted scalar rows. */
internal class InMemoryPersistenceQueries(private val persistence: InMemoryLocalPersistence) {
    fun readEvents(): List<EventEntity> = rows().events.chronological()

    fun readRecordEvents(recordId: RecordId): List<EventEntity> =
        rows().events.filter { it.recordId == recordId.value }.chronological()

    fun readScopeEvents(recordId: RecordId, targetId: TargetId?): List<EventEntity> =
        rows().events.filter { it.recordId == recordId.value && it.targetScopeKey == scopeKey(targetId) }.chronological()

    fun readOpenDuration(recordId: RecordId, targetId: TargetId?): EventEntity? =
        readScopeEvents(recordId, targetId)
            .filter { it.behavior == "DURATION" && it.durationStatus == "OPEN" }
            .maxWithOrNull(compareBy<EventEntity> { it.occurredAt }.thenBy { it.sequence })

    fun readCurrentState(scope: StateScope): EventEntity? {
        val persisted = rows()
        val scopeKey = scopeKey(scope.targetId)
        val generation = persisted.stateScopes.single {
            it.stateGroupId == scope.groupId.value && it.targetScopeKey == scopeKey
        }.generation
        return persisted.events.filter {
            it.behavior == "STATE" && it.stateGroupId == scope.groupId.value && it.targetScopeKey == scopeKey &&
                it.stateGeneration == generation
        }.maxWithOrNull(compareBy<EventEntity> { it.occurredAt }.thenBy { it.sequence })
    }

    private fun rows(): PersistenceRows = PersistenceMapper.toRows(persistence.read())

    private fun scopeKey(targetId: TargetId?): String = PersistenceMapper.encodeTargetScope(
        targetId?.let(TargetScope::ForTarget) ?: TargetScope.NoTarget,
    )

    private fun List<EventEntity>.chronological(): List<EventEntity> =
        sortedWith(compareBy<EventEntity> { it.occurredAt }.thenBy { it.sequence })
}

private fun DomainState.detached(): DomainState = copy(
    records = LinkedHashMap(records),
    targets = LinkedHashMap(targets),
    relationships = LinkedHashMap(relationships),
    events = ArrayList(events),
    stateGroups = LinkedHashMap(stateGroups),
    currentStates = LinkedHashMap(currentStates),
    stateGenerations = LinkedHashMap(stateGenerations),
    bindings = bindings.mapValues { (_, binding) ->
        binding.copy(undoInvalidations = ArrayList(binding.undoInvalidations))
    },
    undoReceipts = LinkedHashMap(undoReceipts),
)
