# Research: Local Persistence

## Decision: Use one Room/SQLite database owned by `:data`

**Rationale:** The approved architecture calls for a single local database so lifecycle effects, Event snapshots, bindings, and Undo metadata can commit together. Room supplies typed entities, foreign-key/index declarations, migrations, and transaction support without adding persistence knowledge to `:core`.

**Alternatives considered:** Separate databases increase cross-database atomicity risk. DataStore/files are unsuitable for relational scope queries, foreign keys, and partial unique constraints. No persistence technology is added to `:core`.

## Decision: Implement `AtomicCommitBoundary` as a serialized compare-and-swap repository

**Rationale:** `EventEngine` already computes `CommitOperation(expected, state, lifecycleEffect)`. The adapter reads the authoritative state, compares expected entity revisions/generations inside the same transaction, writes the complete state, and returns `false` for stale or failed commits. The engine maps a rejected compare to `Conflict`; storage exceptions map to `StorageFailure`.

**Alternatives considered:** Last-write-wins would violate stale-write protection. Caller-only checks have a race between read and write. A new global mutation revision is rejected by the clarification; `DatasetGeneration` plus affected entity revisions remain authoritative.

## Contract gap: Boolean commit result cannot distinguish a race conflict

The existing `AtomicCommitBoundary.commit(operation): Boolean` is an approved `:core` contract, and `EventEngine` maps `false` to `StorageFailure`. Existing command-level expected-context checks already return `Conflict` before commit. The persistence implementation must therefore never overwrite on a compare failure, but must not claim that the current Boolean port can return `Conflict` for a race. This is an explicit follow-up decision for a future backward-compatible core contract extension; this slice preserves the existing port.

## Decision: Persist immutable historical snapshots in the Event row

**Rationale:** Each Event stores Record/Target identity, event-time display name/icon, behavior, and Counter unit. History therefore remains meaningful after rename, archive, unlink, or unarchive.

**Alternatives considered:** Reconstructing display data from live definitions changes history. Copying every future mutable definition field creates unnecessary schema coupling.

## Decision: Non-destructive, transactional schema migrations owned by `:data`

**Rationale:** The adapter owns schema versioning and migration execution. A migration runs in a Room transaction, validates invariants, and leaves the prior database usable if it fails; no destructive reset or silent repair is allowed. `:core` remains schema-agnostic.

## Decision: Preserve lifecycle history; delete only confirmed scope

Archive/unlink retain IDs, definitions, Events, and relationships (marked inactive/unlinked), while applying Duration/State/binding/Undo lifecycle effects atomically. Permanent delete requires a confirmed impact scope and removes only related rows and metadata; unrelated and no-Target history remains.

## Resolved unknowns

- Snapshot field set, migration ownership, global revision policy, and concurrent commit semantics were clarified in `spec.md` Session 2026-09-17.
- Room dependency/KSP wiring is a Phase 1 implementation decision bounded to `:data`; exact schema/API names are defined in `data-model.md` and `contracts/persistence.md`.
