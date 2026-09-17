# Persistence Data Model

The following are Room entities in `:data`; they are not replacements for core domain classes. All IDs are immutable String primary keys containing the value of the corresponding core inline ID. All revisions, generations, timestamps, and sequence values are stored as signed 64-bit integers.

## Entities

| Entity | Primary key | Important columns | Relationships and constraints |
|---|---|---|---|
| `RecordEntity` | `recordId` | name, icon nullable, behavior discriminator, lifecycle, unit nullable, default quantity decimal text nullable, stateGroupId nullable, revision, hasEvents | FK stateGroup nullable; behavior/unit invariant validated in mapping; archive is retained. |
| `TargetEntity` | `targetId` | name, icon nullable, lifecycle, revision | Independent reusable definition; archive is retained. |
| `RecordTargetEntity` | `(recordId,targetId)` | linked, revision | FKs to Record/Target with restrict semantics for ordinary lifecycle; unlink marks `linked=false`. |
| `StateGroupEntity` | `stateGroupId` | name, revision | Referenced by Records and State payloads; retained until confirmed deletion scope permits removal. |
| `EventEntity` | `eventId` | recordId, targetId nullable, behavior, occurredAt, createdAt, updatedAt, sequence, source, revision; snapshotRecordName/icon, snapshotTargetName/icon, snapshotBehavior, snapshotUnit | FKs to Record/Target use history-preserving references; nullable target is a distinct no-Target scope. Unique sequence; indexes for chronology and scope. |
| `EventPayloadEntity` | `eventId` | payload discriminator; counter quantity decimal text/unit, duration start/end/status/reason, stateGroupId/stateGeneration | One-to-one Event payload; discriminator must match Event behavior. |
| `StateScopeEntity` | `(stateGroupId,targetScopeKey)` | generation, currentRecordId nullable, resetSequence, resetAt | `targetScopeKey` uses reserved no-target sentinel; currentRecordId references Record without deleting history. |
| `BindingEntity` | `bindingId` | recordId, targetId nullable, status, revision, display snapshot fields | Immutable scope reference; archive/unlink sets ORPHANED and retains last-known display snapshot. |
| `BindingUndoInvalidationEntity` | `(bindingId,receiptId)` | reason | Child metadata for deterministic invalidation. |
| `UndoReceiptEntity` | `receiptId` | operation/event reference, expected event revision, expected dataset/scope generations, before-image JSON/typed fields, consumed, invalidation reason | Not an audit log; consumed or invalid receipts remain classifiable until scope deletion. |
| `DatasetMetadataEntity` | singleton key | datasetGeneration, nextSequence, schemaVersion | Exactly one row; no global mutation revision. |

## Mapping rules

`PersistenceMapper` converts each entity aggregate to/from `DomainState`, preserving nullable target scope, enum/discriminator, decimal quantity text, UTC epoch milliseconds, sequence, revisions, generations, snapshots, and payload fields. Mapping validates foreign-key references, discriminator/payload agreement, Duration terminal rules, State scope/generation, positive quantities, and monotonic values. Invalid rows produce a deterministic storage/schema failure rather than a repaired domain object.

## Referential behavior

Foreign keys prevent accidental orphan rows. Ordinary archive/unlink never cascades into Event deletion. Confirmed permanent delete executes an explicit, bounded deletion graph: selected Record/Target, eligible relationship rows, matching Event/payload/snapshot rows, bindings, Undo metadata, and affected State scope metadata. Other Targets, Records, no-Target Events, and unrelated snapshots remain.

## Indexes and invariants

- Chronology: `(occurredAt, sequence)` and `(recordId, targetId, occurredAt, sequence)`.
- Current/open queries: `(recordId, targetScopeKey, status)` for Duration and `(stateGroupId, targetScopeKey, generation, occurredAt, sequence)` for State.
- Lifecycle lookup: `(lifecycle)`, `(linked)`, `(status)` as appropriate.
- Partial unique index ensures at most one OPEN Duration per Record + target scope, with a reserved no-Target key.
- `sequence`, entity `revision`, DatasetGeneration, and State generation never decrease or reuse prior values.
