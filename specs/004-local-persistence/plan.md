# TapLog Local Persistence Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add durable, local, atomic persistence for the existing `:core` `DomainState` and Event Engine without changing core domain semantics.

**Architecture:** `:data` owns one Room/SQLite database, entities, DAOs, migrations, mapping, and an implementation of `AtomicCommitBoundary`. The adapter performs serialized compare-and-check commits: expected entity revisions and generations are validated inside the same transaction, stale writes return `false`, and the existing Event Engine maps that Boolean rejection to `StorageFailure`; command-level expected-context checks continue to produce `Conflict`. `:core` remains Android/framework independent.

**Tech Stack:** Kotlin, Android library API 26+, Room with KSP, SQLite foreign keys/indexes/transactions, JUnit, AndroidX instrumentation.

**Spec:** [spec.md](spec.md)

## Global Constraints

- `:core` must not depend on Android, Room, SQLite, or persistence technology.
- `:data` depends on `:core`; `:app` is not changed by this feature. Any application wiring issue must be handled by a separate approved change and is not part of this persistence slice.
- One local Room/SQLite database is authoritative for the dataset.
- No global mutation revision; use DatasetGeneration plus affected entity revisions.
- Archive/unlink preserve identity and history; only confirmed permanent delete removes the bounded graph.
- Event snapshots are immutable historical values and are never rebuilt from live definitions.
- Event payloads use one `EventEntity` row with one discriminator and typed nullable behavior-specific columns; no separate payload table or free-form JSON representation is introduced.
- Migration is non-destructive and atomic; failure preserves the prior valid database and throws typed `PersistenceFailure` during open. Commit-time persistence failures remain `false`/`StorageFailure` through the approved Boolean port.
- UI, channels, parser/AI, backup/import/export, statistics, monetization, and cloud/sync remain excluded.

---

## Summary

This slice turns the existing in-memory `AtomicCommitBoundary` port into a durable Room-backed repository. It persists every field of `DomainState`, including lifecycle effects, Duration/State metadata, snapshots, bindings, Undo receipts, DatasetGeneration, and next sequence. It adds deterministic mapping validation, schema migrations, restart recovery, and pure plus Android integration tests.

## Technical Context

**Language/Version:** Kotlin/JVM and Android Kotlin, Java 17 toolchain.

**Primary Dependencies:** Existing Android Gradle catalog; add Room runtime/compiler (KSP) only to `:data`; JUnit and AndroidX test infrastructure already used by the repository.

**Storage:** One Room database backed by SQLite in the application-private area.

**Testing:** Pure JVM mapper/repository-contract/failure-injection tests plus Android/SQLite instrumentation and migration tests.

**Target Platform:** Android API 26+, compile SDK 36.

**Project Type:** Android multi-module application/library; this plan changes only the `:data` library and its tests.

**Performance Goals:** Local read and short commit operations for the specified 1,000-Event dataset; no network dependency. Exact latency is implementation-measured, not a product contract.

**Constraints:** Atomic all-or-nothing commits; serialized compare-and-check writes; deterministic failure categories; no destructive migration fallback.

**Scale/Scope:** One device, one local dataset, one application process in V1; four Event behaviors and the complete `DomainState` aggregate.

## Constitution Check

| Principle | Gate | Result |
|---|---|---|
| I. Event-first, shared semantics | Persistence stores core Events and snapshots without redefining behavior. | PASS |
| II. Local-first/privacy | Database is local-only; no account/network/cloud dependency. | PASS |
| III. History is sacred | Archive/unlink preserve history; hard delete is explicit and bounded. | PASS |
| IV. Channel-independent engine | Adapter implements only `AtomicCommitBoundary`; no UI/channel/parser behavior. | PASS |
| V. Testable boundaries | Mappers and repository contract are pure-testable; Room behavior has integration tests. | PASS |
| VI. Scope discipline | No UI, NFC, Widget, Quick Settings, parser, backup, statistics, monetization, AI, or sync work. | PASS |

## Project Structure

```text
data/
├── build.gradle.kts                         # Room + KSP wiring, test dependencies
└── src/
    ├── main/kotlin/io/github/thrhead/taplog/data/persistence/
    │   ├── TapLogDatabase.kt                 # Room database, version, migrations
    │   ├── PersistenceEntities.kt             # Room entities and indices
    │   ├── PersistenceDaos.kt                # aggregate read/write queries
    │   ├── PersistenceMapper.kt              # entity <-> core mapping/validation
    │   ├── RoomLocalPersistence.kt            # aggregate read/reconstruction adapter
    │   └── RoomAtomicCommitBoundary.kt       # serialized CAS transaction adapter
    ├── test/kotlin/io/github/thrhead/taplog/data/persistence/
    │   ├── PersistenceMapperTest.kt
    │   ├── RepositoryContractTest.kt
    │   ├── FailureInjectionTest.kt
    │   ├── TestFixtures.kt                    # test-only in-memory adapter
    │   ├── DependencyBoundaryTest.kt
    │   └── OfflineLocalPersistenceTest.kt
    └── androidTest/kotlin/io/github/thrhead/taplog/data/persistence/
        ├── RoomAtomicCommitBoundaryTest.kt
        ├── PersistenceMigrationTest.kt
        ├── RestartRecoveryTest.kt
        ├── RoomTestFixtures.kt
        └── OfflineLocalPersistenceTest.kt
```

The checked-in Room schema export is owned by `:data`:

```text
data/schemas/io.github.thrhead.taplog.data.persistence.TapLogDatabase/1.json
```

**Structure Decision:** Bootstrap and retain the existing three modules with `:data` included by settings, an Android library namespace, and a `:data → :core` dependency before persistence implementation begins. Add implementation only under `:data`; do not edit core contracts or app presentation. The exact Room entity/DAO split may remain file-local if it preserves the responsibilities above.

## Important Architectural Decisions

1. `RoomAtomicCommitBoundary` is the sole durable implementation of the existing `AtomicCommitBoundary` port. It maps `DomainState` rather than exposing entities to core.
2. Event rows retain immutable snapshots and identity references; ordinary lifecycle operations never cascade-delete history.
3. A reserved no-Target scope key is used for partial unique Duration and State indexes because SQL NULL uniqueness is not sufficient.
4. Entity revisions, DatasetGeneration, State generation, and next sequence are persisted monotonically. There is no extra global mutation counter.
5. Foreign keys prevent accidental orphans, while permanent delete persists only the state produced by core `DeleteScope`/`DeleteImpact`; the adapter does not create a second deletion graph or generic cascade behavior.

## Transaction Boundaries

- Every Event creation/edit/delete, `CreateRecordAndLog`, archive/unlink lifecycle effect, and confirmed permanent delete is one Room transaction.
- Compare expected revisions/generations and current metadata inside that same transaction before writes; a race rejection is write-free and currently surfaces through the approved Boolean port as `StorageFailure`.
- Serialize valid commits so two readers cannot both overwrite the same expected state.
- Commit is observable as `Applied` only after Room transaction success; feedback failures are outside this boundary.

## Data-Model Tradeoffs

- Typed nullable payload columns plus a discriminator preserve the core sealed payload while keeping relational queries/indexes available.
- Decimal quantities are stored as canonical text to avoid SQLite REAL precision loss.
- Historical snapshots duplicate small display fields intentionally; this trades storage for immutable history guarantees.
- Lifecycle rows are retained for archive/unlink; explicit deletion avoids accidental loss of unrelated history.

## Migration Strategy

`TapLogDatabase` declares `version = 1` for fresh database creation. The supported upgrade matrix for this feature is explicit and empty: `[]`; there is no v0, v1→v1, or v1→v2 production migration in this slice. The exported Room schema is owned by `:data` at `data/schemas/io.github.thrhead.taplog.data.persistence.TapLogDatabase/1.json`. Future v2+ migrations are out of scope until a later approved feature adds an explicit edge, exported schema, migration implementation, and test fixture. A test-only failing migration transaction verifies rollback mechanics without registering an unsupported production edge. If migration/open fails, Room rolls back the attempted transaction, the adapter does not replace/reset the original database, and a later open may retry the prior valid v1 database; the caller receives typed `PersistenceFailure`. Unknown versions fail closed.

## Testing Strategy

- Pure JVM: round-trip mapping, discriminator/nullability validation, decimal encoding, no-Target keys, monotonic revisions, invariant failures, repository contract with a test-owned in-memory adapter, injected commit failures, query semantics, explicit stale `UndoReceipt` rejection, and explicit verification that race rejection never overwrites newer state. Existing core tests cover command-level `Conflict`; a typed adapter conflict result is deferred to a future core contract change.
- Android/Room: foreign keys, indexes, partial unique OPEN Duration constraints, transaction rollback, concurrent stale commit, core-generated `DeleteImpact` state-diff persistence, corrupted-row rejection, migration/open rollback, and process-restart reopening.
- Atomicity ownership: `:data` tests own persistence transaction, rollback, crash/reopen, and adapter failure semantics; `:core` tests only preserve command-level stale `Conflict` and `StorageFailure` mapping without redefining the persistence contract.
- Failure ownership: `PersistenceFailure` is the data-facing typed exception for read/open, malformed-row, mapping, database-open, and migration failures. `DatabaseReadFailure` and `DatabaseOpenFailure` are retryable without rewriting data; `MigrationFailure`, `CorruptRowFailure`, and `MappingFailure` are non-retryable until an external approved migration or data replacement is available. Commit-time persistence failures return `false` through the approved Boolean port and therefore become Engine `StorageFailure`; stale compare also returns `false` and is not typed as a conflict. Uncertain post-commit retries use the expected context and must never blindly duplicate a committed operation.
- Recovery ownership: supported interruption points are before transaction begin, after compare and before writes, after each logical write phase, after metadata update before SQLite commit, and after commit before the caller observes the Boolean. Every restart exposes either the previous complete state or the new complete state, never partial rows; uncertain post-commit retries use the expected context and cannot duplicate a committed operation.
- Offline ownership: `OfflineLocalPersistenceTest` executes core commands against the test adapter with no account, network, server, AI, or channel objects; an Android/device test opens and commits the Room database with network unavailable.
- Scope enforcement: final task review must prove no UI, channel, parser, AI, backup/import/export, encryption, sync/cloud, statistics, or monetization behavior or module boundary was added.
- Run the feature quickstart and existing `:core` tests; no core API or test semantics are redesigned.

## Exact Module/File Areas Expected to Change

- Modify `data/build.gradle.kts` for Room/KSP and Android test dependencies.
- Verify `settings.gradle.kts` and `data/build.gradle.kts` preserve the approved `:data → :core` module boundary; no new module is introduced.
- Create the persistence production files and test files listed in Project Structure.
- Do not modify `core/src/main/**`, `core/build.gradle.kts`, or `app/src/**` for this feature. If a compile-only application wiring adjustment is proven necessary, stop and create a separate approved scope change rather than expanding this persistence task set.

## Phase 1 Re-evaluation

All constitution gates remain PASS after design. No unresolved technical clarification remains for planning; physical table names and DAO query details are implementation-level choices constrained by `data-model.md` and `contracts/persistence.md`.

The current Boolean transaction port is a known contract limitation, not an implementation omission: this plan preserves it and records the future typed-conflict extension as follow-up work rather than silently changing `:core`. Adapter stale races are `false`/`StorageFailure`; command-level stale checks are the only `Conflict` path.

## Complexity Tracking

No constitution violations. The repository/mapping layers are required to isolate Android persistence and to make atomic, migration, and failure behavior independently testable.
