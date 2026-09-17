package io.github.thrhead.taplog.data.persistence

import io.github.thrhead.taplog.core.engine.AtomicCommitBoundary
import io.github.thrhead.taplog.core.engine.CommitOperation
import io.github.thrhead.taplog.core.engine.DomainState

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
