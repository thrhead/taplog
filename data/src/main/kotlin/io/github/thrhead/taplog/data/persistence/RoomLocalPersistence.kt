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

    /**
     * State Group/scope upsert phase for T027's compare/write transaction. Run before
     * Record/Event phases so their State Group parents exist. Scope rows retain the
     * supplied current pointer and generation; omitted scopes and history are untouched.
     * Validate both the supplied aggregate and stored unsupported reset context before
     * any write. DomainState cannot represent reset fields, so overwriting them would
     * silently discard data. The caller must keep the reads and writes in one transaction.
     */
    internal fun writeStateGroupsAndScopes(state: DomainState, dao: PersistenceDao) {
        val rows = PersistenceMapper.toRows(state)
        dao.readStateScopes().forEach {
            PersistenceMapper.requireInvariant(it.resetSequence == null && it.resetAt == null,
                "State reset metadata is not represented by DomainState")
        }
        if (rows.stateGroups.isNotEmpty()) dao.upsertStateGroups(rows.stateGroups)
        if (rows.stateScopes.isNotEmpty()) dao.upsertStateScopes(rows.stateScopes)
    }

    /**
     * Relationship-only upsert phase for T027's caller-owned compare/write transaction,
     * after Record/Target parents exist. Unlink is supplied as linked=false with a higher
     * revision; omitted pairs, definitions, history, and other lifecycle families remain
     * untouched. Preflight the complete batch before writing. This revision guard is a
     * monotonicity invariant, not expected-context CAS or a complete atomic commit.
     */
    internal fun writeRelationships(state: DomainState, dao: PersistenceDao) {
        val rows = PersistenceMapper.toRows(state)
        // Validate stored Dataset context without replacing it or comparing caller context.
        PersistenceMapper.fromRows(PersistenceRows(metadata = dao.readMetadataRows()))
        rows.recordTargets.forEach { row ->
            PersistenceMapper.requireInvariant(dao.readRecord(row.recordId) != null, "Missing stored referenced Record")
            PersistenceMapper.requireInvariant(dao.readTarget(row.targetId) != null, "Missing stored referenced Target")
            dao.readRecordTarget(row.recordId, row.targetId)?.let { stored ->
                PersistenceMapper.requireInvariant(stored.revision >= 0, "Stored Relationship revision must be nonnegative")
                PersistenceMapper.requireInvariant(row.revision >= stored.revision, "Relationship revision must not decrease")
                PersistenceMapper.requireInvariant(row.linked == stored.linked || row.revision > stored.revision,
                    "Relationship lifecycle change requires a higher revision")
            }
        }
        if (rows.recordTargets.isNotEmpty()) dao.upsertRecordTargets(rows.recordTargets)
    }

    /**
     * Binding/child-metadata upsert phase for the caller-owned T027 transaction, after
     * definition parents exist. Persist core-supplied orphaning and historical display
     * values; never derive them from live definitions. Validate supplied and stored
     * aggregates before writes, including unsupported reset/Undo context. Binding scope
     * is immutable, and changes require a higher revision. Omitted rows remain retained;
     * receipt invalidation/removal and other lifecycle phases have separate ownership.
     */
    internal fun writeBindings(state: DomainState, dao: PersistenceDao) {
        val rows = PersistenceMapper.toRows(state)
        val storedRows = PersistenceRows(
            records = dao.readRecords(), targets = dao.readTargets(), recordTargets = dao.readRecordTargets(),
            stateGroups = dao.readStateGroups(), events = dao.readEvents(), stateScopes = dao.readStateScopes(),
            bindings = dao.readBindings(), bindingUndoInvalidations = dao.readBindingUndoInvalidations(),
            undoReceipts = dao.readUndoReceipts(), metadata = dao.readMetadataRows(),
        )
        PersistenceMapper.fromRows(storedRows)
        val recordIds = storedRows.records.map { it.recordId }.toSet()
        val targetIds = storedRows.targets.map { it.targetId }.toSet()
        val storedBindings = storedRows.bindings.associateBy { it.bindingId }
        val storedInvalidations = storedRows.bindingUndoInvalidations.groupBy { it.bindingId }
        val suppliedInvalidations = rows.bindingUndoInvalidations.groupBy { it.bindingId }
        rows.bindings.forEach { row ->
            PersistenceMapper.requireInvariant(row.recordId in recordIds, "Missing stored referenced Record")
            PersistenceMapper.requireInvariant(row.targetId == null || row.targetId in targetIds, "Missing stored referenced Target")
            storedBindings[row.bindingId]?.let { stored ->
                PersistenceMapper.requireInvariant(row.recordId == stored.recordId && row.targetId == stored.targetId,
                    "Binding scope must not change")
                PersistenceMapper.requireInvariant(row.revision >= stored.revision, "Binding revision must not decrease")
                val sameInvalidations = suppliedInvalidations[row.bindingId].orEmpty().associate { it.receiptId to it.reason } ==
                    storedInvalidations[row.bindingId].orEmpty().associate { it.receiptId to it.reason }
                PersistenceMapper.requireInvariant(row.revision > stored.revision ||
                    (row == stored && sameInvalidations), "Binding change requires a higher revision")
            }
        }
        if (rows.bindings.isNotEmpty()) dao.upsertBindings(rows.bindings)
        if (rows.bindingUndoInvalidations.isNotEmpty()) dao.upsertBindingUndoInvalidations(rows.bindingUndoInvalidations)
    }

    /**
     * Receipt/child-invalidation upsert phase for the caller-owned T027 transaction.
     * Keep core-provided expected context exactly, including receipts for deleted Events.
     * DomainState cannot represent adapter-only consumed/before-image metadata; reject
     * that stored context rather than overwriting it. Omitted rows remain retained.
     * Revision guards validate monotonic metadata, not expected-context CAS.
     */
    internal fun writeUndoMetadata(state: DomainState, dao: PersistenceDao) {
        val rows = PersistenceMapper.toRows(state)
        val storedRows = PersistenceRows(
            records = dao.readRecords(), targets = dao.readTargets(), recordTargets = dao.readRecordTargets(),
            stateGroups = dao.readStateGroups(), events = dao.readEvents(), stateScopes = dao.readStateScopes(),
            bindings = dao.readBindings(), bindingUndoInvalidations = dao.readBindingUndoInvalidations(),
            undoReceipts = dao.readUndoReceipts(), metadata = dao.readMetadataRows(),
        )
        PersistenceMapper.fromRows(storedRows)
        val storedReceipts = storedRows.undoReceipts.associateBy { it.receiptId }
        rows.undoReceipts.forEach { row ->
            storedReceipts[row.receiptId]?.let { stored ->
                PersistenceMapper.requireInvariant(row.eventId == stored.eventId &&
                    row.stateGroupId == stored.stateGroupId && row.targetScopeKey == stored.targetScopeKey,
                    "Undo receipt identity and scope must not change")
                PersistenceMapper.requireInvariant(row.expectedEventRevision >= stored.expectedEventRevision &&
                    row.expectedDatasetGeneration >= stored.expectedDatasetGeneration &&
                    (row.expectedScopeGeneration == null || row.expectedScopeGeneration >= stored.expectedScopeGeneration!!),
                    "Undo expected revisions and generations must not decrease")
                PersistenceMapper.requireInvariant(row == stored || row.expectedEventRevision > stored.expectedEventRevision,
                    "Undo receipt change requires a higher Event revision")
            }
        }
        val storedBindings = storedRows.bindings.associateBy { it.bindingId }
        val suppliedBindings = rows.bindings.associateBy { it.bindingId }
        val storedInvalidations = storedRows.bindingUndoInvalidations.associateBy { it.bindingId to it.receiptId }
        rows.bindingUndoInvalidations.forEach { row ->
            val binding = suppliedBindings.getValue(row.bindingId)
            val stored = storedBindings[row.bindingId]
                ?: throw MappingFailure("Undo invalidation references a missing stored Binding")
            PersistenceMapper.requireInvariant(binding.recordId == stored.recordId && binding.targetId == stored.targetId,
                "Binding scope must not change")
            PersistenceMapper.requireInvariant(binding.revision >= stored.revision, "Binding revision must not decrease")
            PersistenceMapper.requireInvariant(storedInvalidations[row.bindingId to row.receiptId] == row || binding.revision > stored.revision,
                "Undo invalidation change requires a higher Binding revision")
        }
        if (rows.undoReceipts.isNotEmpty()) dao.upsertUndoReceipts(rows.undoReceipts)
        if (rows.bindingUndoInvalidations.isNotEmpty()) dao.upsertBindingUndoInvalidations(rows.bindingUndoInvalidations)
    }

    // The complete atomic write boundary belongs to T027.
    override fun commit(operation: CommitOperation): Boolean =
        throw UnsupportedOperationException("Atomic commits are not implemented")
}
