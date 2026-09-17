package io.github.thrhead.taplog.data.persistence

import androidx.room.Entity

/**
 * Persistence-only scalar rows. IDs use their core String values, quantities use
 * decimal text, and timestamps use UTC epoch milliseconds. Revisions,
 * generations and sequences use signed 64-bit integers.
 *
 * Discriminators store the corresponding core enum names: Behavior, Lifecycle,
 * Source, DurationStatus, BindingStatus and ResultReason. Mapping owns decoding
 * and validation so an unknown stored value cannot silently become a default.
 * Keys, foreign keys and indices are defined by the following schema task.
 */
@Entity(tableName = "records")
internal data class RecordEntity(
    val recordId: String,
    val name: String,
    val icon: String?,
    val behavior: String,
    val lifecycle: String,
    val unit: String?,
    val defaultQuantity: String?,
    val stateGroupId: String?,
    val revision: Long,
    val hasEvents: Boolean,
)

@Entity(tableName = "targets")
internal data class TargetEntity(
    val targetId: String,
    val name: String,
    val icon: String?,
    val lifecycle: String,
    val revision: Long,
)

@Entity(tableName = "record_targets")
internal data class RecordTargetEntity(
    val recordId: String,
    val targetId: String,
    val linked: Boolean,
    val revision: Long,
)

@Entity(tableName = "state_groups")
internal data class StateGroupEntity(
    val stateGroupId: String,
    val name: String,
    val revision: Long,
)

/**
 * One row per Event. [behavior] is the sole payload discriminator; MOMENT has
 * no additional payload columns. All other payload families remain nullable
 * so mapping can reject missing, mixed or discriminator-inconsistent fields.
 *
 * Snapshot columns contain historical values, independent of live definitions.
 * [targetId] remains nullable; [targetScopeKey] is its indexable scope encoding.
 */
@Entity(tableName = "events")
internal data class EventEntity(
    val eventId: String,
    val recordId: String,
    val targetId: String?,
    val targetScopeKey: String,
    val behavior: String,
    val occurredAt: Long,
    val createdAt: Long,
    val updatedAt: Long,
    val sequence: Long,
    val source: String,
    val revision: Long,
    val snapshotRecordName: String,
    val snapshotRecordIcon: String?,
    val snapshotTargetName: String?,
    val snapshotTargetIcon: String?,
    val snapshotBehavior: String,
    val snapshotUnit: String?,
    val counterQuantity: String? = null,
    val counterUnit: String? = null,
    val durationStartAt: Long? = null,
    val durationEndAt: Long? = null,
    val durationStatus: String? = null,
    val durationIncompleteReason: String? = null,
    val stateGroupId: String? = null,
    val stateGeneration: Long? = null,
)

@Entity(tableName = "state_scopes")
internal data class StateScopeEntity(
    val stateGroupId: String,
    val targetScopeKey: String,
    val generation: Long,
    val currentRecordId: String?,
    val resetSequence: Long? = null,
    val resetAt: Long? = null,
)

/** Last-known display values are stored even when a binding is orphaned. */
@Entity(tableName = "bindings")
internal data class BindingEntity(
    val bindingId: String,
    val recordId: String,
    val targetId: String?,
    val status: String,
    val revision: Long,
    val snapshotRecordName: String,
    val snapshotRecordIcon: String?,
    val snapshotTargetName: String?,
    val snapshotTargetIcon: String?,
)

@Entity(tableName = "binding_undo_invalidations")
internal data class BindingUndoInvalidationEntity(
    val bindingId: String,
    val receiptId: String,
    val reason: String,
)

/**
 * Expected context is stored separately from retained receipt metadata. A null
 * scope group/key/generation represents a receipt without a State scope.
 * Before-image JSON is Undo metadata only, never an Event payload store.
 * Mapping and commit tasks own consumption and invalidation semantics.
 */
@Entity(tableName = "undo_receipts")
internal data class UndoReceiptEntity(
    val receiptId: String,
    val eventId: String,
    val expectedEventRevision: Long,
    val expectedDatasetGeneration: Long,
    val stateGroupId: String? = null,
    val targetScopeKey: String? = null,
    val expectedScopeGeneration: Long? = null,
    val operation: String? = null,
    val beforeImageJson: String? = null,
    val consumed: Boolean = false,
    val invalidationReason: String? = null,
)

/** Dataset metadata contains no global mutation revision. */
@Entity(tableName = "dataset_metadata")
internal data class DatasetMetadataEntity(
    val singletonKey: Int,
    val datasetGeneration: Long,
    val nextSequence: Long,
    val schemaVersion: Int,
)
