package io.github.thrhead.taplog.data.persistence

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert

/** Row reads shared by the aggregate DAO; no domain objects or reconstructed payloads. */
internal interface PersistenceReadDao {
    @Transaction
    @Query("SELECT * FROM dataset_metadata ORDER BY singletonKey")
    fun readMetadataRows(): List<DatasetMetadataEntity>

    @Query("SELECT * FROM dataset_metadata WHERE singletonKey = :singletonKey")
    fun readMetadata(singletonKey: Int): DatasetMetadataEntity?

    @Transaction
    @Query("SELECT * FROM records ORDER BY recordId")
    fun readRecords(): List<RecordEntity>

    @Query("SELECT * FROM records WHERE recordId = :recordId")
    fun readRecord(recordId: String): RecordEntity?

    @Transaction
    @Query("SELECT * FROM targets ORDER BY targetId")
    fun readTargets(): List<TargetEntity>

    @Query("SELECT * FROM targets WHERE targetId = :targetId")
    fun readTarget(targetId: String): TargetEntity?

    @Transaction
    @Query("SELECT * FROM state_groups ORDER BY stateGroupId")
    fun readStateGroups(): List<StateGroupEntity>

    @Query("SELECT * FROM state_groups WHERE stateGroupId = :stateGroupId")
    fun readStateGroup(stateGroupId: String): StateGroupEntity?

    @Transaction
    @Query("SELECT * FROM record_targets ORDER BY recordId, targetId")
    fun readRecordTargets(): List<RecordTargetEntity>

    @Query("SELECT * FROM record_targets WHERE recordId = :recordId AND targetId = :targetId")
    fun readRecordTarget(recordId: String, targetId: String): RecordTargetEntity?

    @Transaction
    @Query("SELECT * FROM events ORDER BY occurredAt ASC, sequence ASC")
    fun readEvents(): List<EventEntity>

    @Query("SELECT * FROM events WHERE eventId = :eventId")
    fun readEvent(eventId: String): EventEntity?

    /** Record-wide history intentionally includes every Target scope and no-Target. */
    @Query("SELECT * FROM events WHERE recordId = :recordId ORDER BY occurredAt ASC, sequence ASC")
    fun readRecordEvents(recordId: String): List<EventEntity>

    /** Pass NO_TARGET_SCOPE_KEY or TARGET_SCOPE_KEY_PREFIX + targetId, never a raw Target ID. */
    @Query(
        "SELECT * FROM events WHERE recordId = :recordId AND targetScopeKey = :targetScopeKey " +
            "ORDER BY occurredAt ASC, sequence ASC",
    )
    fun readScopeEvents(recordId: String, targetScopeKey: String): List<EventEntity>

    @Query(
        "SELECT * FROM events WHERE recordId = :recordId AND targetScopeKey = :targetScopeKey " +
            "AND behavior = 'DURATION' AND durationStatus = 'OPEN' " +
            "ORDER BY occurredAt DESC, sequence DESC LIMIT 1",
    )
    fun readOpenDuration(recordId: String, targetScopeKey: String): EventEntity?

    /** Only the persisted scope's active generation can supply its latest State Event. */
    @Query(
        "SELECT events.* FROM events INNER JOIN state_scopes " +
            "ON events.stateGroupId = state_scopes.stateGroupId " +
            "AND events.targetScopeKey = state_scopes.targetScopeKey " +
            "AND events.stateGeneration = state_scopes.generation " +
            "WHERE events.stateGroupId = :stateGroupId AND events.targetScopeKey = :targetScopeKey " +
            "AND events.behavior = 'STATE' " +
            "ORDER BY events.occurredAt DESC, events.sequence DESC LIMIT 1",
    )
    fun readCurrentState(stateGroupId: String, targetScopeKey: String): EventEntity?

    @Transaction
    @Query("SELECT * FROM state_scopes ORDER BY stateGroupId, targetScopeKey")
    fun readStateScopes(): List<StateScopeEntity>

    @Query(
        "SELECT * FROM state_scopes " +
            "WHERE stateGroupId = :stateGroupId AND targetScopeKey = :targetScopeKey",
    )
    fun readStateScope(stateGroupId: String, targetScopeKey: String): StateScopeEntity?

    @Transaction
    @Query("SELECT * FROM bindings ORDER BY bindingId")
    fun readBindings(): List<BindingEntity>

    @Query("SELECT * FROM bindings WHERE bindingId = :bindingId")
    fun readBinding(bindingId: String): BindingEntity?

    /** SQL IS matches null only to null, preserving the no-Target binding scope. */
    @Query("SELECT * FROM bindings WHERE recordId = :recordId AND targetId IS :targetId ORDER BY bindingId")
    fun readScopeBindings(recordId: String, targetId: String?): List<BindingEntity>

    @Transaction
    @Query("SELECT * FROM binding_undo_invalidations ORDER BY bindingId, receiptId")
    fun readBindingUndoInvalidations(): List<BindingUndoInvalidationEntity>

    @Query("SELECT * FROM binding_undo_invalidations WHERE bindingId = :bindingId ORDER BY receiptId")
    fun readBindingUndoInvalidations(bindingId: String): List<BindingUndoInvalidationEntity>

    @Transaction
    @Query("SELECT * FROM undo_receipts ORDER BY receiptId")
    fun readUndoReceipts(): List<UndoReceiptEntity>

    @Query("SELECT * FROM undo_receipts WHERE receiptId = :receiptId")
    fun readUndoReceipt(receiptId: String): UndoReceiptEntity?
}

/**
 * Batches update existing rows without REPLACE's implicit delete. Deletions are bounded by
 * supplied immutable keys; the repository must derive them from core's committed aggregate.
 */
internal interface PersistenceWriteDao {
    @Transaction
    @Upsert
    fun upsertMetadata(rows: List<DatasetMetadataEntity>)

    @Transaction
    @Upsert
    fun upsertRecords(rows: List<RecordEntity>)

    @Transaction
    @Upsert
    fun upsertTargets(rows: List<TargetEntity>)

    @Transaction
    @Upsert
    fun upsertStateGroups(rows: List<StateGroupEntity>)

    @Transaction
    @Upsert
    fun upsertRecordTargets(rows: List<RecordTargetEntity>)

    @Transaction
    @Upsert
    fun upsertEvents(rows: List<EventEntity>)

    @Transaction
    @Upsert
    fun upsertStateScopes(rows: List<StateScopeEntity>)

    @Transaction
    @Upsert
    fun upsertBindings(rows: List<BindingEntity>)

    @Transaction
    @Upsert
    fun upsertBindingUndoInvalidations(rows: List<BindingUndoInvalidationEntity>)

    @Transaction
    @Upsert
    fun upsertUndoReceipts(rows: List<UndoReceiptEntity>)

    @Transaction
    @Query("DELETE FROM events WHERE eventId IN (:eventIds)")
    fun deleteEvents(eventIds: List<String>): Int

    @Transaction
    @Query("DELETE FROM undo_receipts WHERE receiptId IN (:receiptIds)")
    fun deleteUndoReceipts(receiptIds: List<String>): Int

    @Transaction
    @Delete
    fun deleteBindingUndoInvalidations(rows: List<BindingUndoInvalidationEntity>): Int

    @Transaction
    @Delete
    fun deleteBindings(rows: List<BindingEntity>): Int

    @Transaction
    @Delete
    fun deleteRecordTargets(rows: List<RecordTargetEntity>): Int

    @Transaction
    @Delete
    fun deleteStateScopes(rows: List<StateScopeEntity>): Int

    @Transaction
    @Delete
    fun deleteRecords(rows: List<RecordEntity>): Int

    @Transaction
    @Delete
    fun deleteTargets(rows: List<TargetEntity>): Int

    @Transaction
    @Delete
    fun deleteStateGroups(rows: List<StateGroupEntity>): Int
}

/**
 * Aggregate row access for the later Room database. Method transactions cover one batch/read;
 * the repository must wrap all aggregate reads and all commit phases in one database transaction.
 * These declarations neither recompute delete scope nor orchestrate foreign-key write ordering.
 */
@Dao
internal interface PersistenceDao : PersistenceReadDao, PersistenceWriteDao
