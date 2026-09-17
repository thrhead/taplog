package io.github.thrhead.taplog.data.persistence

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Scope keys have disjoint namespaces even when a core Target ID is itself
 * "no-target" or starts with "target:". T008a owns encoding/decoding: null maps
 * to [NO_TARGET_SCOPE_KEY], every real ID to [TARGET_SCOPE_KEY_PREFIX] + ID.
 * Raw nullable targetId columns remain the referential IDs, not these keys.
 */
internal const val NO_TARGET_SCOPE_KEY = "no-target"
internal const val TARGET_SCOPE_KEY_PREFIX = "target:"

internal const val OPEN_DURATION_INDEX_NAME = "index_events_open_duration_scope"

/**
 * Room 2.8.4's Index annotation has no predicate. T010 must execute this DDL on
 * database creation before writes and own its open/schema validation; T037
 * must verify the partial index through SQLite, including retained history.
 * Do not replace this with an unfiltered unique Room index.
 */
internal const val CREATE_OPEN_DURATION_INDEX_SQL =
    "CREATE UNIQUE INDEX IF NOT EXISTS `index_events_open_duration_scope` " +
        "ON `events` (`recordId`, `targetScopeKey`) " +
        "WHERE `behavior` = 'DURATION' AND `durationStatus` = 'OPEN'"

/**
 * Persistence-only scalar rows. IDs use their core String values, quantities use
 * decimal text, and timestamps use UTC epoch milliseconds. Revisions,
 * generations and sequences use signed 64-bit integers.
 *
 * Discriminators store the corresponding core enum names: Behavior, Lifecycle,
 * Source, DurationStatus, BindingStatus and ResultReason. Mapping owns decoding
 * and validation so an unknown stored value cannot silently become a default.
 * Foreign keys restrict deletes and updates; lifecycle changes never cascade
 * into Event history. Mapping owns monotonicity and payload/scope invariants.
 */
@Entity(
    tableName = "records",
    foreignKeys = [
        ForeignKey(
            entity = StateGroupEntity::class,
            parentColumns = ["stateGroupId"],
            childColumns = ["stateGroupId"],
            onDelete = ForeignKey.RESTRICT,
            onUpdate = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index(value = ["stateGroupId"]), Index(value = ["lifecycle"])],
)
internal data class RecordEntity(
    @PrimaryKey
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

@Entity(tableName = "targets", indices = [Index(value = ["lifecycle"])])
internal data class TargetEntity(
    @PrimaryKey
    val targetId: String,
    val name: String,
    val icon: String?,
    val lifecycle: String,
    val revision: Long,
)

@Entity(
    tableName = "record_targets",
    primaryKeys = ["recordId", "targetId"],
    foreignKeys = [
        ForeignKey(
            entity = RecordEntity::class,
            parentColumns = ["recordId"],
            childColumns = ["recordId"],
            onDelete = ForeignKey.RESTRICT,
            onUpdate = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = TargetEntity::class,
            parentColumns = ["targetId"],
            childColumns = ["targetId"],
            onDelete = ForeignKey.RESTRICT,
            onUpdate = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index(value = ["targetId"]), Index(value = ["linked"])],
)
internal data class RecordTargetEntity(
    val recordId: String,
    val targetId: String,
    val linked: Boolean,
    val revision: Long,
)

@Entity(tableName = "state_groups")
internal data class StateGroupEntity(
    @PrimaryKey
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
@Entity(
    tableName = "events",
    foreignKeys = [
        ForeignKey(
            entity = RecordEntity::class,
            parentColumns = ["recordId"],
            childColumns = ["recordId"],
            onDelete = ForeignKey.RESTRICT,
            onUpdate = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = TargetEntity::class,
            parentColumns = ["targetId"],
            childColumns = ["targetId"],
            onDelete = ForeignKey.RESTRICT,
            onUpdate = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = StateGroupEntity::class,
            parentColumns = ["stateGroupId"],
            childColumns = ["stateGroupId"],
            onDelete = ForeignKey.RESTRICT,
            onUpdate = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["sequence"], unique = true),
        Index(value = ["occurredAt", "sequence"]),
        Index(value = ["recordId", "targetId", "occurredAt", "sequence"]),
        Index(value = ["recordId", "targetScopeKey", "durationStatus"]),
        Index(value = ["stateGroupId", "targetScopeKey", "stateGeneration", "occurredAt", "sequence"]),
        Index(value = ["targetId"]),
        Index(value = ["durationStatus"]),
    ],
)
internal data class EventEntity(
    @PrimaryKey
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

/**
 * [currentRecordId] is contextual metadata, not a lifetime-owned FK: the core
 * confirmed Record deletion can retain State scope metadata unchanged. Scope
 * keys also cannot reference raw Target IDs because they are encoded.
 */
@Entity(
    tableName = "state_scopes",
    primaryKeys = ["stateGroupId", "targetScopeKey"],
    foreignKeys = [
        ForeignKey(
            entity = StateGroupEntity::class,
            parentColumns = ["stateGroupId"],
            childColumns = ["stateGroupId"],
            onDelete = ForeignKey.RESTRICT,
            onUpdate = ForeignKey.RESTRICT,
        ),
    ],
)
internal data class StateScopeEntity(
    val stateGroupId: String,
    val targetScopeKey: String,
    val generation: Long,
    val currentRecordId: String?,
    val resetSequence: Long? = null,
    val resetAt: Long? = null,
)

/** Last-known display values are stored even when a binding is orphaned. */
@Entity(
    tableName = "bindings",
    foreignKeys = [
        ForeignKey(
            entity = RecordEntity::class,
            parentColumns = ["recordId"],
            childColumns = ["recordId"],
            onDelete = ForeignKey.RESTRICT,
            onUpdate = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = TargetEntity::class,
            parentColumns = ["targetId"],
            childColumns = ["targetId"],
            onDelete = ForeignKey.RESTRICT,
            onUpdate = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["recordId", "targetId"]),
        Index(value = ["targetId"]),
        Index(value = ["status"]),
    ],
)
internal data class BindingEntity(
    @PrimaryKey
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

/** [receiptId] remains classifiable after core lifecycle removes the receipt. */
@Entity(
    tableName = "binding_undo_invalidations",
    primaryKeys = ["bindingId", "receiptId"],
    foreignKeys = [
        ForeignKey(
            entity = BindingEntity::class,
            parentColumns = ["bindingId"],
            childColumns = ["bindingId"],
            onDelete = ForeignKey.RESTRICT,
            onUpdate = ForeignKey.RESTRICT,
        ),
    ],
)
internal data class BindingUndoInvalidationEntity(
    val bindingId: String,
    val receiptId: String,
    val reason: String,
)

/**
 * Expected context is stored separately from retained receipt metadata. A null
 * scope group/key/generation represents a receipt without a State scope.
 * Before-image JSON is Undo metadata only, never an Event payload store.
 * Mapping and commit tasks own consumption and invalidation semantics. Expected
 * State context is scalar metadata and does not own a scope/group lifetime.
 */
@Entity(
    tableName = "undo_receipts",
    foreignKeys = [
        ForeignKey(
            entity = EventEntity::class,
            parentColumns = ["eventId"],
            childColumns = ["eventId"],
            onDelete = ForeignKey.RESTRICT,
            onUpdate = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index(value = ["eventId"])],
)
internal data class UndoReceiptEntity(
    @PrimaryKey
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
    @PrimaryKey
    val singletonKey: Int,
    val datasetGeneration: Long,
    val nextSequence: Long,
    val schemaVersion: Int,
)
