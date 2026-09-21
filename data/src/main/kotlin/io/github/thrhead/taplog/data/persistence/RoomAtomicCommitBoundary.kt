package io.github.thrhead.taplog.data.persistence

import io.github.thrhead.taplog.core.engine.AtomicCommitBoundary
import io.github.thrhead.taplog.core.engine.CommitOperation
import io.github.thrhead.taplog.core.engine.DomainState
import io.github.thrhead.taplog.core.engine.LifecycleEffect
import java.util.concurrent.Callable

/** Serialized Room compare-and-write boundary for the existing core Boolean contract. */
internal class RoomAtomicCommitBoundary(private val database: TapLogDatabase) : AtomicCommitBoundary {
    private val persistence = RoomLocalPersistence(database)

    override fun read(): DomainState = persistence.read()

    override fun commit(operation: CommitOperation): Boolean = database.runInTransaction(Callable {
        val dao = database.persistenceDao()
        val current = persistence.readInTransaction(dao)
        if (current != operation.expected) return@Callable false

        when {
            hasDeletionDiff(current, operation.state) -> persistence.writePermanentDeletion(operation.state, dao)
            operation.lifecycleEffect != LifecycleEffect() ->
                persistence.writeLifecycleEffects(operation.state, dao)
            else -> persistence.writeAggregate(operation.state, dao)
        }
        true
    })

    private fun hasDeletionDiff(current: DomainState, next: DomainState): Boolean {
        val nextEventIds = next.events.map { it.id }.toSet()
        val nextInvalidations = next.bindings.values.flatMap { it.undoInvalidations }.toSet()
        return current.records.keys.any { it !in next.records } ||
            current.relationships.keys.any { it !in next.relationships } ||
            current.events.any { it.id !in nextEventIds } ||
            current.bindings.keys.any { it !in next.bindings } ||
            current.undoReceipts.keys.any { it !in next.undoReceipts } ||
            current.bindings.values.flatMap { it.undoInvalidations }.any { invalidation ->
                invalidation !in nextInvalidations
            }
    }
}
