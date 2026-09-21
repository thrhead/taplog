# Persistence Data Model

The following are Room entities in `:data`; they are not replacements for core domain classes. All IDs are immutable String primary keys containing the value of the corresponding core inline ID. All revisions, generations, timestamps, and sequence values are stored as signed 64-bit integers.

## Entities

| Entity | Primary key | Important columns | Relationships and constraints |
|---|---|---|---|
| `RecordEntity` | `recordId` | name, icon nullable, behavior discriminator, lifecycle, unit nullable, default quantity decimal text nullable, stateGroupId nullable, revision, hasEvents | FK stateGroup nullable; behavior/unit invariant validated in mapping; archive is retained. |
| `TargetEntity` | `targetId` | name, icon nullable, lifecycle, revision | Independent reusable definition; archive is retained. |
| `RecordTargetEntity` | `(recordId,targetId)` | linked, revision | FKs to Record/Target with restrict semantics for ordinary lifecycle; unlink marks `linked=false`. |
| `StateGroupEntity` | `stateGroupId` | name, revision | Referenced by Records and State payloads; retained until confirmed deletion scope permits removal. |
| `EventEntity` | `eventId` | recordId, targetId nullable, behavior/payload discriminator, occurredAt, createdAt, updatedAt, sequence, source, revision; snapshotRecordName/icon, snapshotTargetName/icon, snapshotBehavior, snapshotUnit; nullable Counter quantity/unit, Duration start/end/status/reason, State group/generation | One canonical Event row contains the discriminator and typed nullable behavior-specific payload columns. FKs to Record/Target use history-preserving references; nullable target is a distinct no-Target scope. Unique sequence; indexes for chronology and scope. |
| `StateScopeEntity` | `(stateGroupId,targetScopeKey)` | generation, currentRecordId nullable, resetSequence, resetAt | `targetScopeKey` uses reserved no-target sentinel; currentRecordId references Record without deleting history. |
| `BindingEntity` | `bindingId` | recordId, targetId nullable, status, revision, display snapshot fields | Immutable scope reference; archive/unlink sets ORPHANED and retains last-known display snapshot. |
| `BindingUndoInvalidationEntity` | `(bindingId,receiptId)` | reason | Child metadata for deterministic invalidation. |
| `UndoReceiptEntity` | `receiptId` | operation/event reference, expected event revision, expected dataset/scope generations, before-image JSON/typed fields, consumed, invalidation reason | Not an audit log; consumed or invalid receipts remain classifiable until scope deletion. |
| `DatasetMetadataEntity` | singleton key | datasetGeneration, nextSequence, schemaVersion | Exactly one row; initial database schema is v1, supported versions are explicit in the migration registry; no global mutation revision. |

## Mapping rules

`PersistenceMapper` converts the canonical entity rows to/from `DomainState`, preserving nullable target scope, enum/discriminator, decimal quantity text, UTC epoch milliseconds, sequence, revisions, generations, snapshots, and payload fields. Mapping validates foreign-key references, discriminator/payload agreement, Duration terminal rules, State scope/generation, positive quantities, and monotonic values. Invalid rows produce a deterministic typed `PersistenceFailure` rather than a repaired domain object. There is no separate payload table, free-form JSON event representation, or reconstruction from live definitions.

## Referential behavior

Foreign keys prevent accidental orphan rows. Ordinary archive/unlink never cascades into Event deletion. Persistence executes the core `DeleteImpact(recordId, targetId?, eventIds)` exactly and does not recompute or widen it. For `targetId = null`, the core Record scope removes the Record row, all RecordTarget rows for that Record, its bindings, the listed Event rows (including its no-Target Events), and Undo receipts whose eventId is listed; Target rows and unrelated metadata remain. For `targetId != null`, the core pair scope removes only that RecordTarget row, pair binding rows, listed pair Event rows, and their Undo receipts; the Record, Target, other relationships, other-target Events, and no-Target Events remain. A standalone Target definition deletion is not represented by the current core contract and is out of scope. Duration/State/lifecycle metadata is changed or retained only as represented by the core committed `DomainState`/`LifecycleEffect`; the adapter adds no cascade or reset.

## Indexes and invariants

- Chronology: `(occurredAt, sequence)` and `(recordId, targetId, occurredAt, sequence)`.
- Current/open queries: `(recordId, targetScopeKey, status)` for Duration and `(stateGroupId, targetScopeKey, generation, occurredAt, sequence)` for State.
- Lifecycle lookup: `(lifecycle)`, `(linked)`, `(status)` as appropriate.
- Partial unique index ensures at most one OPEN Duration per Record + target scope, with a reserved no-Target key.
- `sequence`, entity `revision`, DatasetGeneration, and State generation never decrease or reuse prior values.
- A failed or interrupted migration leaves the pre-migration database usable; unsupported schema versions fail closed and are never destructively reset.

## Schema version matrix

| Operation | Supported in this feature | Result |
|---|---|---|
| Fresh database creation | Yes | Create Room schema version 1 and singleton metadata. |
| v1 → v1 migration | No | Not a migration edge; fresh/open validation only. |
| v1 → v2 or later | No | Future approved feature only; requires a new explicit migration and exported schema. |
| Unknown older/newer version | No | Fail closed with typed `PersistenceFailure`; never destructive-reset. |

The exported Room schema is owned by `:data` at `data/schemas/io.github.thrhead.taplog.data.persistence.TapLogDatabase/1.json`.

## Persistence failure categories

`PersistenceFailure` is the data-facing typed error surface. `DatabaseOpenFailure` and `DatabaseReadFailure` are retryable without rewriting data; `MigrationFailure`, `CorruptRowFailure`, and `MappingFailure` are non-retryable until an approved migration or external data replacement/repair is available. Commit-time `MappingFailure`, constraint, and database-write failures return `false` through the approved Boolean boundary and become Engine `StorageFailure`; they do not introduce a typed commit-conflict result. No category silently drops, repairs, or rewrites historical rows. Pure JVM tests own mapping/error classification; Android/Room tests own open, migration, corrupt-row, and rollback behavior.
