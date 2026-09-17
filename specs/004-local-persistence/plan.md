# TapLog Local Persistence Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add durable, local, atomic persistence for the existing `:core` `DomainState` and Event Engine without changing core domain semantics.

**Architecture:** `:data` owns one Room/SQLite database, entities, DAOs, migrations, mapping, and an implementation of `AtomicCommitBoundary`. The adapter performs serialized compare-and-check commits: expected entity revisions and generations are validated inside the same transaction, stale writes return `false`, and the existing Event Engine maps that Boolean rejection to `StorageFailure`; command-level expected-context checks continue to produce `Conflict`. `:core` remains Android/framework independent.

**Tech Stack:** Kotlin, Android library API 26+, Room with KSP, SQLite foreign keys/indexes/transactions, JUnit, AndroidX instrumentation.

**Spec:** [spec.md](spec.md)

## Global Constraints

- `:core` must not depend on Android, Room, SQLite, or persistence technology.
- `:data` depends on `:core`; `:app` is not changed by this feature.
- One local Room/SQLite database is authoritative for the dataset.
- No global mutation revision; use DatasetGeneration plus affected entity revisions.
- Archive/unlink preserve identity and history; only confirmed permanent delete removes the bounded graph.
- Event snapshots are immutable historical values and are never rebuilt from live definitions.
- Migration is non-destructive and atomic; failure preserves the prior valid database and returns StorageFailure.
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
    │   └── RoomAtomicCommitBoundary.kt       # serialized CAS transaction adapter
    ├── test/kotlin/io/github/thrhead/taplog/data/persistence/
    │   ├── PersistenceMapperTest.kt
    │   ├── RepositoryContractTest.kt
    │   └── FailureInjectionTest.kt
    └── androidTest/kotlin/io/github/thrhead/taplog/data/persistence/
        ├── RoomAtomicCommitBoundaryTest.kt
        ├── PersistenceMigrationTest.kt
        └── RestartRecoveryTest.kt
```

**Structure Decision:** Keep the existing three modules. Add implementation only under `:data`; do not edit core contracts or app presentation. The exact Room entity/DAO split may remain file-local if it preserves the responsibilities above.

## Important Architectural Decisions

1. `RoomAtomicCommitBoundary` is the sole durable implementation of the existing `AtomicCommitBoundary` port. It maps `DomainState` rather than exposing entities to core.
2. Event rows retain immutable snapshots and identity references; ordinary lifecycle operations never cascade-delete history.
3. A reserved no-Target scope key is used for partial unique Duration and State indexes because SQL NULL uniqueness is not sufficient.
4. Entity revisions, DatasetGeneration, State generation, and next sequence are persisted monotonically. There is no extra global mutation counter.
5. Foreign keys prevent accidental orphans, while permanent delete uses an explicit confirmed deletion graph instead of generic cascade behavior.

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

`TapLogDatabase` declares an integer schema version and an ordered migration list. Each migration is non-destructive, validates the resulting rows through the mapper/invariant checks, and runs transactionally. A failed migration does not reset or overwrite the old database and is surfaced as deterministic `StorageFailure`. Migration tests use real SQLite/Room schemas for every supported prior version.

## Testing Strategy

- Pure JVM: round-trip mapping, discriminator/nullability validation, decimal encoding, no-Target keys, monotonic revisions, invariant failures, repository contract with an in-memory adapter, injected commit failures, and explicit verification that race rejection never overwrites newer state. Existing core tests cover command-level `Conflict`; a typed adapter conflict result is deferred to a future core contract change.
- Android/Room: foreign keys, indexes, partial unique OPEN Duration constraints, transaction rollback, concurrent stale commit, archive/unlink effects, bounded hard delete, migration, and process-restart reopening.
- Run the feature quickstart and existing `:core` tests; no core API or test semantics are redesigned.

## Exact Module/File Areas Expected to Change

- Modify `data/build.gradle.kts` for Room/KSP and Android test dependencies.
- Create the persistence production files and test files listed in Project Structure.
- Do not modify `core/src/main/**`, `core/build.gradle.kts`, or `app/src/**` unless a compile-only wiring adjustment is proven necessary; any such adjustment must preserve dependency direction and core contracts.

## Phase 1 Re-evaluation

All constitution gates remain PASS after design. No unresolved technical clarification remains for planning; physical table names and DAO query details are implementation-level choices constrained by `data-model.md` and `contracts/persistence.md`.

The current Boolean transaction port is a known contract limitation, not an implementation omission: this plan preserves it and records the future typed-conflict extension as follow-up work rather than silently changing `:core`.

## Complexity Tracking

No constitution violations. The repository/mapping layers are required to isolate Android persistence and to make atomic, migration, and failure behavior independently testable.
