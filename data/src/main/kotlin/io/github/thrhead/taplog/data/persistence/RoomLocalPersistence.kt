package io.github.thrhead.taplog.data.persistence

import io.github.thrhead.taplog.core.engine.AtomicCommitBoundary
import io.github.thrhead.taplog.core.engine.CommitOperation
import io.github.thrhead.taplog.core.engine.DomainState
import java.util.concurrent.Callable

internal interface LocalPersistence : AtomicCommitBoundary {
    override fun read(): DomainState
    override fun commit(operation: CommitOperation): Boolean
}

internal class RoomLocalPersistence(private val database: TapLogDatabase) : LocalPersistence {
    override fun read(): DomainState {
        // Room opens lazily. Keep open failures distinct from transaction/query failures.
        try {
            database.openHelper.writableDatabase
        } catch (failure: PersistenceFailure) {
            throw failure
        } catch (failure: Exception) {
            throw DatabaseOpenFailure("Unable to open the local database", failure)
        }

        val rows = try {
            database.runInTransaction(Callable {
                val dao = database.persistenceDao()
                PersistenceRows(
                    records = dao.readRecords(),
                    targets = dao.readTargets(),
                    recordTargets = dao.readRecordTargets(),
                    stateGroups = dao.readStateGroups(),
                    events = dao.readEvents(),
                    stateScopes = dao.readStateScopes(),
                    bindings = dao.readBindings(),
                    bindingUndoInvalidations = dao.readBindingUndoInvalidations(),
                    undoReceipts = dao.readUndoReceipts(),
                    metadata = dao.readMetadataRows(),
                )
            })
        } catch (failure: PersistenceFailure) {
            throw failure
        } catch (failure: Exception) {
            throw DatabaseReadFailure("Unable to read the complete local aggregate", failure)
        }

        // Immutable scalar rows retain the transaction snapshot after it ends.
        // Mapping failures remain non-retryable and never trigger row repair.
        return PersistenceMapper.fromRows(rows)
    }

    /**
     * Internal upsert-only phase, not a complete [LocalPersistence.commit]. T027 must call
     * this inside its compare/write transaction, after required State Group parents exist.
     * Validate the complete aggregate before any write; leave history and all other row
     * families to their own phases, including deletion and lifecycle effects.
     */
    internal fun writeRecordsAndTargets(state: DomainState, dao: PersistenceWriteDao) {
        val rows = PersistenceMapper.toRows(state)
        if (rows.records.isNotEmpty()) dao.upsertRecords(rows.records)
        if (rows.targets.isNotEmpty()) dao.upsertTargets(rows.targets)
    }

    /**
     * Event-only upsert phase for T027's compare/write transaction, after Record, Target,
     * and State Group parents exist. Validate the complete aggregate before writes and
     * retain the supplied historical snapshots and typed payloads through the canonical
     * mapper. Omitted Events, lifecycle transitions, and metadata belong to other phases.
     */
    internal fun writeEvents(state: DomainState, dao: PersistenceWriteDao) {
        val rows = PersistenceMapper.toRows(state)
        if (rows.events.isNotEmpty()) dao.upsertEvents(rows.events)
    }

    // The complete atomic write boundary belongs to T027.
    override fun commit(operation: CommitOperation): Boolean =
        throw UnsupportedOperationException("Atomic commits are not implemented")
}
