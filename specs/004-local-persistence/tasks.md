# Implementation Tasks: Local Persistence

## Overview

This task list implements the approved Room/SQLite persistence slice in `:data`.
It preserves the existing `:core` contracts, including the Boolean
`AtomicCommitBoundary.commit()` result. Command-level stale checks remain
`Conflict`; a stale compare inside the adapter returns `false` and is surfaced
by the existing engine as `StorageFailure`. No typed commit-conflict result or
new global mutation revision is introduced.

## Dependencies and execution order

```text
Phase 1 Setup
    -> Phase 2 Foundational persistence primitives
        -> US1 durable aggregate read/write
            -> US2 event and lifecycle recovery
                -> US3 relationships, bindings, undo, archive and delete
                    -> US4 atomic boundary and failure semantics
                        -> US5 migrations and final verification
```

US1 is the suggested MVP because it establishes a restartable local
`DomainState` repository. US2–US4 depend on the entity/mapping foundation and
are independently reviewable after their preceding story is complete. US5
depends on the complete schema and adapter behavior.

## Phase 1: Setup

- [X] T001 Bootstrap/verify the `:data` Android library module in `settings.gradle.kts` and `data/build.gradle.kts`: include `:data`, set its namespace/minSdk, depend only on `:core`, and establish the source-set/test configuration before adding persistence code.
- [X] T002 Add Room runtime/compiler through KSP and the existing JVM/Android test dependencies in `data/build.gradle.kts`, keeping Room dependencies confined to `:data`.
- [ ] T003 Create the persistence package directories under `data/src/main/kotlin/io/github/thrhead/taplog/data/persistence`, `data/src/test/kotlin/io/github/thrhead/taplog/data/persistence`, and `data/src/androidTest/kotlin/io/github/thrhead/taplog/data/persistence`.
- [ ] T004 Add the data-module test fixtures/build configuration, including the test-owned in-memory `LocalPersistence` fake and Android Room fixtures, in `data/src/test/kotlin/io/github/thrhead/taplog/data/persistence/TestFixtures.kt` and `data/src/androidTest/kotlin/io/github/thrhead/taplog/data/persistence/RoomTestFixtures.kt`.

## Phase 2: Foundational persistence primitives

- [ ] T005 Define the canonical single-row Room entities and all column/discriminator representations in `data/src/main/kotlin/io/github/thrhead/taplog/data/persistence/PersistenceEntities.kt`, covering Record, Target, Record–Target, State Group, Event with typed nullable behavior payload columns, State scope, Binding, Binding undo invalidation, Undo receipt, and singleton dataset metadata. Do not create an EventPayloadEntity or free-form JSON Event store.
- [ ] T006 Define immutable String primary keys, signed 64-bit timestamp/revision/generation/sequence columns, nullable target scope sentinel, foreign keys, restrict behavior, chronology/scope/lifecycle indexes, and the partial unique OPEN Duration index in `data/src/main/kotlin/io/github/thrhead/taplog/data/persistence/PersistenceEntities.kt`.
- [ ] T007 Define aggregate read/write DAO interfaces and explicit query methods in `data/src/main/kotlin/io/github/thrhead/taplog/data/persistence/PersistenceDaos.kt`, including metadata, definitions, relationships, events/payloads, state scopes, bindings, undo metadata, bounded deletion, and transaction-supporting operations.
- [ ] T008a [P] Add deterministic decimal quantity text encoding/decoding, epoch-millisecond conversion, enum/discriminator conversion, no-Target scope-key conversion, and invariant/error types in `data/src/main/kotlin/io/github/thrhead/taplog/data/persistence/PersistenceMapper.kt`; this is a prerequisite for T008.
- [ ] T008 Implement `DomainState` aggregate mapping in both directions in `data/src/main/kotlin/io/github/thrhead/taplog/data/persistence/PersistenceMapper.kt`, preserving snapshots, nullable targets, payload agreement, lifecycle metadata, revisions, generations, sequence values, and the “no global mutation revision” rule.
- [ ] T009 Add pure JVM mapper tests in `data/src/test/kotlin/io/github/thrhead/taplog/data/persistence/PersistenceMapperTest.kt` for round trips, nullability/discriminator validation, decimal canonical text, UTC epoch milliseconds, no-Target keys, snapshots, and deterministic rejection of invalid rows.
- [ ] T010 Add the Room database declaration and singleton metadata initialization in `data/src/main/kotlin/io/github/thrhead/taplog/data/persistence/TapLogDatabase.kt` with `version = 1`, an explicitly empty production upgrade matrix for this feature, exported schema configuration for `data/schemas/io.github.thrhead.taplog.data.persistence.TapLogDatabase/1.json`, foreign-key enforcement, and no destructive fallback.

## Phase 3: User Story 1 — Persist and reopen the complete local dataset (P1)

Goal: the application can read and durably replace the complete `DomainState`
without exposing Room or Android types to `:core`.

Independent test criteria: persist a state containing all definitions,
relationships, State Group data, bindings, Undo metadata, all four Event
payloads, snapshots, generations, revisions, and metadata; reopen the database
and assert value-for-value equality.

- [ ] T011 [US1] Implement `LocalPersistence` read behavior and complete aggregate reconstruction in `data/src/main/kotlin/io/github/thrhead/taplog/data/persistence/RoomLocalPersistence.kt`, returning the latest fully committed `DomainState` only.
- [ ] T012 [US1] Add repository contract tests with the test-owned in-memory persistence adapter in `data/src/test/kotlin/io/github/thrhead/taplog/data/persistence/RepositoryContractTest.kt`, covering read-after-write, the single-row Event payload representation, all entity families, immutable snapshots, nullable target scope, persistence of revisions/generations/next sequence, ascending `(occurredAt, sequence)` ordering, equal-time sequence ordering, exact Record/Target/no-Target filtering, OPEN-only Duration selection, and active-generation State selection. Depends on T004 and T008–T011.
- [ ] T013 [US1] Add Android/Room integration coverage for foreign keys, indexes, singleton metadata, complete aggregate round-trip, entity-to-domain isolation, canonical single-row Event payload columns, and the same query semantics in `data/src/androidTest/kotlin/io/github/thrhead/taplog/data/persistence/RoomAtomicCommitBoundaryTest.kt`. Depends on T005–T011; this test follows the complete aggregate mapper/repository path and does not depend on the later US2 behavior-specific implementation tasks.

## Phase 4: User Story 2 — Preserve event history and recover lifecycle state (P1)

Goal: Moment, Counter, Duration, and State events, including restart-sensitive
metadata, remain correct after persistence and reopening.

Independent test criteria: commit each behavior and `CreateRecordAndLog`, kill
and reopen the database, and assert event identity, timestamps, payload,
sequence, revision, snapshots, OPEN/COMPLETED/INCOMPLETE status, and State
generation are unchanged or advanced exactly as specified.

- [ ] T014 [US2] Implement Record and Target persistence/update mapping paths in `data/src/main/kotlin/io/github/thrhead/taplog/data/persistence/RoomLocalPersistence.kt`, retaining identity, lifecycle, behavior/unit/default-quantity invariants, `revision`, and `hasEvents`.
- [ ] T015 [US2] Implement Event persistence in the single `EventEntity` row in `data/src/main/kotlin/io/github/thrhead/taplog/data/persistence/RoomLocalPersistence.kt`, storing occurred/created/updated timestamps, unique sequence, source, revision, immutable display/behavior/unit snapshots, one discriminator, and typed nullable Counter/Duration/State payload fields.
- [ ] T016 [US2] Implement Duration persistence and restart recovery in `data/src/main/kotlin/io/github/thrhead/taplog/data/persistence/RoomLocalPersistence.kt`, including the reserved no-Target scope key, OPEN uniqueness, terminal status/reason, and preservation of completed/incomplete history.
- [ ] T017 [US2] Implement State Group and State scope persistence in `data/src/main/kotlin/io/github/thrhead/taplog/data/persistence/RoomLocalPersistence.kt`, enforcing one active state per `(stateGroupId,targetScopeKey)`, preserving generation/reset metadata, and recovering exclusivity after restart.
- [ ] T018 [P] [US2] Add pure JVM event/lifecycle mapping and monotonicity tests in `data/src/test/kotlin/io/github/thrhead/taplog/data/persistence/PersistenceMapperTest.kt` for all four behaviors, positive Counter quantities, Duration terminal rules, State scope/generation, and non-decreasing sequence/revisions.
- [ ] T019 [US2] Add Android restart recovery tests in `data/src/androidTest/kotlin/io/github/thrhead/taplog/data/persistence/RestartRecoveryTest.kt` for Moment, Counter, Duration, State, and `CreateRecordAndLog`, including database close/reopen, process-kill/crash boundary simulation at each supported interruption point, old-or-new complete-state assertions, and OPEN Duration/active State assertions. For SC-001, perform at least 100 sequential successful commits for each of the four behavior types (400 behavior commits total) before restart and assert 100% preservation of Event identity, timestamps, payload, snapshot, source, and sequence.

## Phase 5: User Story 3 — Preserve, orphan, correct, archive, and delete bounded history (P1)

Goal: lifecycle operations preserve history and identity unless the caller
explicitly confirms a bounded permanent deletion graph.

Independent test criteria: archive/unlink a scope and verify history remains,
OPEN Duration becomes INCOMPLETE, State generation advances, bindings become
ORPHANED with last-known snapshots, and related Undo metadata is invalidated;
then confirm permanent deletion and verify unrelated and no-Target history
remains.

- [ ] T020 [US3] Implement Record–Target relationship persistence and unlink semantics in `data/src/main/kotlin/io/github/thrhead/taplog/data/persistence/RoomLocalPersistence.kt`, using `linked=false` plus monotonic relationship revisions without deleting Events or either definition.
- [ ] T021 [US3] Implement Binding persistence and orphaning in `data/src/main/kotlin/io/github/thrhead/taplog/data/persistence/RoomLocalPersistence.kt`, retaining binding identity/status and immutable last-known display snapshots when a Record/Target scope is archived or unlinked.
- [ ] T022 [US3] Implement Undo receipt and `BindingUndoInvalidationEntity` persistence in `data/src/main/kotlin/io/github/thrhead/taplog/data/persistence/RoomLocalPersistence.kt`, retaining consumed/invalid receipts, expected revisions/generations, before-images, and deterministic invalidation reasons.
- [ ] T023 [US3] Implement atomic archive/unlink lifecycle effects in `data/src/main/kotlin/io/github/thrhead/taplog/data/persistence/RoomLocalPersistence.kt`, including Event history preservation, Duration termination, State generation advance, binding orphaning, and Undo invalidation.
- [ ] T024 [US3] Implement confirmed permanent deletion by persisting the state produced by core `DeleteScope`/`DeleteImpact` in `data/src/main/kotlin/io/github/thrhead/taplog/data/persistence/RoomLocalPersistence.kt`: null-target core state removes the Record, its relationships/bindings, listed Record Events including no-Target Events, and their Undo receipts; non-null-target core state removes only the selected relationship/pair bindings, listed pair Events, and their Undo receipts. Retain Target definitions, the Record in pair scope, shared/unrelated relationships, other-target/no-Target history outside the core-produced state diff, and lifecycle metadata not removed by that state. Do not implement standalone Target deletion or adapter-side cascade.
- [ ] T025 [US3] Add pure JVM contract tests for archive, unlink, orphaned binding, Undo invalidation, and exact core deletion state-diff persistence in `data/src/test/kotlin/io/github/thrhead/taplog/data/persistence/RepositoryContractTest.kt`, invoking core `DeleteScope.preview`/confirmed delete for Record-wide and Record–Target pair scopes and asserting no-Target Events, shared relationships, unrelated history, and lifecycle metadata match the core-produced state. Depends on T020–T024.
- [ ] T026 [US3] Add Android integration tests for foreign-key restrict behavior, archive/unlink effects, orphan snapshots, exact `DeleteImpact` graph boundaries, row-level Event/payload/snapshot/relationship/binding/Undo assertions, and preservation of unrelated/no-Target history in `data/src/androidTest/kotlin/io/github/thrhead/taplog/data/persistence/RoomAtomicCommitBoundaryTest.kt`.

## Phase 6: User Story 4 — Commit atomically through the existing core boundary (P1)

Goal: `EventEngine` can use one durable adapter with serialized compare-and-swap
semantics and no partial writes.

Independent test criteria: two commits with the same expected context result in
exactly one successful write; a stale compare returns `false` without
overwriting newer state; injected failures leave the previous aggregate
readable; command-level stale checks still report `Conflict` through existing
core tests.

- [ ] T027 [US4] Implement the sole `AtomicCommitBoundary` adapter in `data/src/main/kotlin/io/github/thrhead/taplog/data/persistence/RoomAtomicCommitBoundary.kt`, serializing commits, comparing expected revisions/generations/sequence metadata inside the Room transaction, validating the mapped state, and returning `true` only after transaction success. Core `DeleteScope`/`DeleteImpact` semantics are consumed through the resulting `DomainState`; no adapter-level deletion contract is added.
- [ ] T028 [US4] Ensure `RoomAtomicCommitBoundary` maps stale expected context and failed compare to Boolean `false`/storage failure behavior without modifying `core/src/main/**`, adding a typed commit-conflict result, or adding a global mutation revision in `data/src/main/kotlin/io/github/thrhead/taplog/data/persistence/RoomAtomicCommitBoundary.kt`.
- [ ] T029 [US4] Add pure JVM failure-injection and concurrency contract tests in `data/src/test/kotlin/io/github/thrhead/taplog/data/persistence/FailureInjectionTest.kt`, covering each logical write phase, no partial writes, monotonic revisions, stale compare non-overwrite, Boolean failure semantics, and classification of commit-time persistence failures as `false`/Engine `StorageFailure`. For SC-002, execute at least 20 deterministic failure-injection attempts for each atomic operation family (Event, Record+Event, archive/unlink, permanent delete) and assert no partial state in 100% of attempts. Depends on T027–T028; persistence atomicity ownership remains in `:data`.
- [ ] T029a [US4] Add an explicit stale `UndoReceipt` persistence test in `data/src/test/kotlin/io/github/thrhead/taplog/data/persistence/FailureInjectionTest.kt`, proving that a stale Event revision, dataset generation, or State-scope generation is rejected without mutation and returns Boolean `false`/Engine `StorageFailure` at the adapter boundary; preserve the existing core command-level stale checks and `Conflict` result without adding a public contract. Depends on T027–T028.
- [ ] T030 [US4] Add Android transaction rollback, concurrent stale-commit, and reopen-after-interruption tests in `data/src/androidTest/kotlin/io/github/thrhead/taplog/data/persistence/RoomAtomicCommitBoundaryTest.kt`, interrupting before begin, after compare, after each write phase, before SQLite commit, and after commit before result observation; assert exactly one winner, old-or-new complete state, no partial rows, and no duplicate retry. Depends on T027–T028.
- [ ] T031 [US4] Run and preserve the existing command-level Conflict/StorageFailure assertions in `core/src/test/kotlin/io/github/thrhead/taplog/core/engine/ConflictHandlingTest.kt` and `core/src/test/kotlin/io/github/thrhead/taplog/core/engine/CorrectionAndAtomicityTest.kt`; change no core contract or semantics.

## Phase 7: User Story 5 — Version and migrate the local schema safely (P2)

Goal: fresh Room schema v1 creation is deterministic, the explicit production
upgrade matrix is empty for this feature, and test-only migration failures
rollback atomically while preserving the prior valid database.

Independent test criteria: create and reopen a real SQLite/Room v1 database,
validate rows through the mapper/invariants, verify the empty production
migration registry and exported schema, and inject a test-only migration
failure to prove no reset, silent repair, or partial migrated state occurs.

- [ ] T032 [US5] Define `version = 1`, the explicit production upgrade matrix `[]`, and the Room schema export configuration in `data/src/main/kotlin/io/github/thrhead/taplog/data/persistence/TapLogDatabase.kt`; fresh creation is supported, no v1 upgrade edge is registered, unknown versions fail closed, and no destructive fallback is allowed.
- [ ] T033 [US5] Own and check in the Room schema export at `data/schemas/io.github.thrhead.taplog.data.persistence.TapLogDatabase/1.json`; document fresh v1 creation, empty production upgrade matrix, and future v2+ migration ownership in `specs/004-local-persistence/quickstart.md`. Depends on T032.
- [ ] T034 [US5] Add real SQLite/Room schema/open tests in `data/src/androidTest/kotlin/io/github/thrhead/taplog/data/persistence/PersistenceMigrationTest.kt` for fresh v1 creation, exported-schema validation, empty production migration registry, mapper validation, foreign-key/index reconstruction, and successful restart.
- [ ] T035 [US5] Add test-only failing-migration rollback, database-open/read failure, corrupt-row, mapping-failure, and retryability tests in `data/src/androidTest/kotlin/io/github/thrhead/taplog/data/persistence/PersistenceMigrationTest.kt`, asserting typed `MigrationFailure`/`DatabaseOpenFailure`/`DatabaseReadFailure`/`CorruptRowFailure`/`MappingFailure`, that only `DatabaseOpenFailure` and `DatabaseReadFailure` are retryable without rewriting data, old-v1 usability after rollback, and no destructive reset, silent repair, or row dropping.

## Phase 8: Polish and cross-cutting verification

- [ ] T036 Add dependency-boundary tests or Gradle validation in `data/src/test/kotlin/io/github/thrhead/taplog/data/persistence/DependencyBoundaryTest.kt` and `data/build.gradle.kts`, proving `:data` is bootstrapped correctly, `:core` has no Android/Room dependency, persistence types do not leak into core contracts, and all excluded product areas remain outside the feature. Depends on T001–T002.
- [ ] T037 Add schema/entity invariant coverage in `data/src/androidTest/kotlin/io/github/thrhead/taplog/data/persistence/RoomAtomicCommitBoundaryTest.kt` for primary-key uniqueness, foreign-key enforcement, chronology/scope indexes, partial OPEN Duration uniqueness, and non-decreasing values. Depends on T005–T010.
- [ ] T038 Run the feature quickstart commands `./gradlew :data:testDebugUnitTest`, `./gradlew :data:connectedDebugAndroidTest`, and `./gradlew :data:lintDebug :detekt`; record any environment limitation and results in `specs/004-local-persistence/quickstart.md` without changing product scope.
- [ ] T039 Run the existing `:core` test suite and final repository checks, including `./gradlew :core:test`, `git diff --check`, and inspection that only approved `:data`/test/build files changed; verify no UI, NFC, Widget, Quick Settings, parser, backup/import/export, statistics, monetization, AI, sync, or cloud tasks were introduced.
- [ ] T040 [US1] Add executable offline boundary coverage in `data/src/test/kotlin/io/github/thrhead/taplog/data/persistence/OfflineLocalPersistenceTest.kt` and `data/src/androidTest/kotlin/io/github/thrhead/taplog/data/persistence/OfflineLocalPersistenceTest.kt`, proving core commands and local persistence work without account, network, server, AI, or channel dependencies, including the 1,000-Event offline reopen criterion.

## Parallel execution opportunities

- Phase 1: T003 follows T001–T002; T004 follows T003 and owns the test-only fake/fixtures.
- Phase 2: T007 and T008a can run in parallel after T005–T006; T008 depends on T008a; T009 follows T008; T010 can proceed after T005–T006.
- US1: T011 follows the foundation; T012 depends on T004 and T008–T011; T013 follows T005–T011 and therefore tests the complete aggregate mapper/repository path before US2 behavior-specific implementation.
- US2: T018 can run alongside T014–T017 once mapper primitives exist; T019 follows the behavior persistence paths.
- US3: T025 and T026 follow T020–T024; they are separate pure-JVM and Room verification tracks.
- US4: T029 and T029a follow T027–T028; T030 and T031 are then separate Room and core verification tracks.
- US5: T033 follows T032; T034 and T035 are sequential because failure tests depend on the migration registry and corruption fixtures.
- Final phase: T036 and T037 follow their respective setup/foundation tasks; T040 follows the test adapter and Room setup; T038 and T039 follow T040 and all implementation phases. These are not marked parallel because boundary/offline/final verification share build and fixture state. T029a remains in the US4 verification track and does not change the core Conflict path.

## Implementation strategy

1. Establish Room/KSP wiring, entities, DAOs, mapping, and database metadata.
2. Deliver the MVP complete aggregate read/write path and pure/Android tests.
3. Add event behavior and restart-sensitive lifecycle recovery.
4. Add history-preserving lifecycle effects and explicit bounded deletion.
5. Adapt the existing Boolean atomic boundary with serialized compare-and-swap and failure rollback tests.
6. Add migration fixtures/tests and perform full quickstart, core, lint, detekt, and dependency-boundary verification.
