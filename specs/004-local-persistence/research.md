# Research: Local Persistence

## Decision: Use one Room/SQLite database owned by `:data`

**Rationale:** The approved architecture calls for a single local database so lifecycle effects, Event snapshots, bindings, and Undo metadata can commit together. Room supplies typed entities, foreign-key/index declarations, migrations, and transaction support without adding persistence knowledge to `:core`.

**Alternatives considered:** Separate databases increase cross-database atomicity risk. DataStore/files are unsuitable for relational scope queries, foreign keys, and partial unique constraints. No persistence technology is added to `:core`.

## Decision: Store Event payloads in the canonical Event row

The physical representation is one `EventEntity` row with a behavior/payload discriminator and typed nullable columns for Counter, Duration, and State fields. This matches the approved architecture and core `EventPayload` semantics: one Event identity, one snapshot, and one behavior-specific payload. A separate payload table, free-form JSON event store, or reconstruction from live definitions is not used.

**Alternatives considered:** A one-to-one payload table adds relational indirection not present in the approved model; free-form JSON weakens discriminator/invariant validation; live-definition reconstruction corrupts historical meaning.

## Decision: Implement `AtomicCommitBoundary` as a serialized compare-and-swap repository

**Rationale:** `EventEngine` already computes `CommitOperation(expected, state, lifecycleEffect)`. The adapter reads the authoritative state, compares expected entity revisions/generations inside the same transaction, writes the complete state, and returns `false` for stale compares. The engine maps the approved Boolean rejection to `StorageFailure`; command-level stale checks remain the only `Conflict` path, while storage exceptions also map to `StorageFailure`.

**Alternatives considered:** Last-write-wins would violate stale-write protection. Caller-only checks have a race between read and write. A new global mutation revision is rejected by the clarification; `DatasetGeneration` plus affected entity revisions remain authoritative. A typed adapter conflict result is deferred because it would change the approved Boolean port.

The 001 architecture data-model field for a global mutation counter is treated as a superseded earlier placeholder for this slice. The authoritative local-persistence decision is no global mutation revision; no replacement global counter is added.

## Contract gap: Boolean commit result cannot distinguish a race conflict

The existing `AtomicCommitBoundary.commit(operation): Boolean` is an approved `:core` contract, and `EventEngine` maps `false` to `StorageFailure`. Existing command-level expected-context checks already return `Conflict` before commit. The persistence implementation must therefore never overwrite on a compare failure, but must not claim that the current Boolean port can return `Conflict` for a race. This is an explicit follow-up decision for a future backward-compatible core contract extension; this slice preserves the existing port.

## Decision: Persist immutable historical snapshots in the Event row

**Rationale:** Each Event stores Record/Target identity, event-time display name/icon, behavior, and Counter unit. History therefore remains meaningful after rename, archive, unlink, or unarchive.

**Alternatives considered:** Reconstructing display data from live definitions changes history. Copying every future mutable definition field creates unnecessary schema coupling.

## Decision: Non-destructive, transactional schema migrations owned by `:data`

**Rationale:** The adapter owns schema versioning and migration execution. The initial schema is v1; the registry grows only through explicit ordered migrations. A migration runs in a Room transaction, validates invariants, and on failure rolls back without replacing/resetting the prior database, which remains usable on a later open; no destructive reset or silent repair is allowed. `:core` remains schema-agnostic.

For this feature the production upgrade matrix is empty: fresh creation is v1, and no v1→v2 edge is registered. The exported Room schema is `data/schemas/io.github.thrhead.taplog.data.persistence.TapLogDatabase/1.json`, owned by `:data`. Future migrations require a later approved feature and an explicit edge/fixture. A test-only failing transaction validates rollback without pretending an unsupported production edge exists.

## Decision: Bind deletion to the existing core impact

Core `DeleteScope`/`DeleteImpact` owns confirmation and produces the committed `DomainState`; persistence stores that state and never derives a broader deletion graph. Null target means the core Record-wide scope, including its no-Target Events. Non-null target means only the selected Record–Target pair. Target definitions are retained because the current core contract has no standalone Target-delete operation. Rows for bindings, relationships, and Undo receipts are removed only when absent from the core-produced state; lifecycle state is retained or changed only as represented by the core committed state/effect.

**Alternatives considered:** A data-only standalone Target delete or adapter-side cascade would redefine core behavior and is rejected.

## Decision: Typed failure boundary

Read/open/migration and malformed-row failures use typed `PersistenceFailure` categories inside `:data`; commit-time persistence failures return `false` through the approved Boolean port and become Engine `StorageFailure`. Stale compare also returns `false`, with no typed commit-conflict result. Corrupt historical rows are fail-closed and never silently repaired. Pure JVM tests own classification and offline behavior; Android/Room tests own open, migration, corruption, rollback, and restart behavior.

## Decision: Test-only in-memory adapter ownership

The repository contract owns a fake/in-memory adapter under `data/src/test`, not production code or `:core`. It implements the same `LocalPersistence` contract and supports deterministic failure injection, query checks, and restart simulation without Android or Room; Room-specific constraints and corruption recovery remain Android integration concerns.

## Decision: Preserve lifecycle history; delete only confirmed scope

Archive/unlink retain IDs, definitions, Events, and relationships (marked inactive/unlinked), while applying Duration/State/binding/Undo lifecycle effects atomically. Permanent delete requires a confirmed impact scope and removes only related rows and metadata; unrelated and no-Target history remains.

## Resolved unknowns

- Snapshot field set, migration ownership, global revision policy, and concurrent commit semantics were clarified in `spec.md` Session 2026-09-17.
- Room dependency/KSP wiring is a Phase 1 implementation decision bounded to `:data`; exact schema/API names are defined in `data-model.md` and `contracts/persistence.md`.
