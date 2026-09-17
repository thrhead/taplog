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

- [ ] T001 Update `data/build.gradle.kts` with Room runtime/compiler through KSP and the existing JVM/Android test dependencies, keeping Room dependencies confined to `:data`.
- [ ] T002 Create the persistence package directories under `data/src/main/kotlin/io/github/thrhead/taplog/data/persistence`, `data/src/test/kotlin/io/github/thrhead/taplog/data/persistence`, and `data/src/androidTest/kotlin/io/github/thrhead/taplog/data/persistence`.
- [ ] T003 [P] Add the data-module test fixtures/build configuration needed for in-memory pure JVM adapters and Android Room tests in `data/src/test/kotlin/io/github/thrhead/taplog/data/persistence/TestFixtures.kt` and `data/src/androidTest/kotlin/io/github/thrhead/taplog/data/persistence/RoomTestFixtures.kt`.

## Phase 2: Foundational persistence primitives

- [ ] T004 Define Room entities and all column/discriminator representations in `data/src/main/kotlin/io/github/thrhead/taplog/data/persistence/PersistenceEntities.kt`, covering Record, Target, Record–Target, State Group, Event, Event payload, State scope, Binding, Binding undo invalidation, Undo receipt, and singleton dataset metadata.
- [ ] T005 Define immutable String primary keys, signed 64-bit timestamp/revision/generation/sequence columns, nullable target scope sentinel, foreign keys, restrict behavior, chronology/scope/lifecycle indexes, and the partial unique OPEN Duration index in `data/src/main/kotlin/io/github/thrhead/taplog/data/persistence/PersistenceEntities.kt`.
- [ ] T006 [P] Define aggregate read/write DAO interfaces and explicit query methods in `data/src/main/kotlin/io/github/thrhead/taplog/data/persistence/PersistenceDaos.kt`, including metadata, definitions, relationships, events/payloads, state scopes, bindings, undo metadata, bounded deletion, and transaction-supporting operations.
- [ ] T007 [P] Add deterministic decimal quantity text encoding/decoding, epoch-millisecond conversion, enum/discriminator conversion, no-Target scope-key conversion, and invariant/error types in `data/src/main/kotlin/io/github/thrhead/taplog/data/persistence/PersistenceMapper.kt`.
- [ ] T008 Implement `DomainState` aggregate mapping in both directions in `data/src/main/kotlin/io/github/thrhead/taplog/data/persistence/PersistenceMapper.kt`, preserving snapshots, nullable targets, payload agreement, lifecycle metadata, revisions, generations, sequence values, and the “no global mutation revision” rule.
- [ ] T009 Add pure JVM mapper tests in `data/src/test/kotlin/io/github/thrhead/taplog/data/persistence/PersistenceMapperTest.kt` for round trips, nullability/discriminator validation, decimal canonical text, UTC epoch milliseconds, no-Target keys, snapshots, and deterministic rejection of invalid rows.
- [ ] T010 Add the Room database declaration and singleton metadata initialization in `data/src/main/kotlin/io/github/thrhead/taplog/data/persistence/TapLogDatabase.kt`, with an explicit integer schema version, migration registry, foreign-key enforcement, and no destructive fallback.

## Phase 3: User Story 1 — Persist and reopen the complete local dataset (P1)

Goal: the application can read and durably replace the complete `DomainState`
without exposing Room or Android types to `:core`.

Independent test criteria: persist a state containing all definitions,
relationships, State Group data, bindings, Undo metadata, all four Event
payloads, snapshots, generations, revisions, and metadata; reopen the database
and assert value-for-value equality.

- [ ] T011 [US1] Implement `LocalPersistence` read behavior and complete aggregate reconstruction in `data/src/main/kotlin/io/github/thrhead/taplog/data/persistence/RoomLocalPersistence.kt`, returning the latest fully committed `DomainState` only.
- [ ] T012 [P] [US1] Add repository contract tests with an in-memory persistence adapter in `data/src/test/kotlin/io/github/thrhead/taplog/data/persistence/RepositoryContractTest.kt`, covering read-after-write, all entity families, immutable snapshots, nullable target scope, and persistence of revisions/generations/next sequence.
- [ ] T013 [US1] Add Android/Room integration coverage for foreign keys, indexes, singleton metadata, complete aggregate round-trip, and entity-to-domain isolation in `data/src/androidTest/kotlin/io/github/thrhead/taplog/data/persistence/RoomAtomicCommitBoundaryTest.kt`.

## Phase 4: User Story 2 — Preserve event history and recover lifecycle state (P1)

Goal: Moment, Counter, Duration, and State events, including restart-sensitive
metadata, remain correct after persistence and reopening.

Independent test criteria: commit each behavior and `CreateRecordAndLog`, kill
and reopen the database, and assert event identity, timestamps, payload,
sequence, revision, snapshots, OPEN/COMPLETED/INCOMPLETE status, and State
generation are unchanged or advanced exactly as specified.

- [ ] T014 [US2] Implement Record and Target persistence/update mapping paths in `data/src/main/kotlin/io/github/thrhead/taplog/data/persistence/RoomLocalPersistence.kt`, retaining identity, lifecycle, behavior/unit/default-quantity invariants, `revision`, and `hasEvents`.
- [ ] T015 [US2] Implement Event and EventPayload persistence in `data/src/main/kotlin/io/github/thrhead/taplog/data/persistence/RoomLocalPersistence.kt`, storing occurred/created/updated timestamps, unique sequence, source, revision, immutable display/behavior/unit snapshots, and typed payload fields.
- [ ] T016 [US2] Implement Duration persistence and restart recovery in `data/src/main/kotlin/io/github/thrhead/taplog/data/persistence/RoomLocalPersistence.kt`, including the reserved no-Target scope key, OPEN uniqueness, terminal status/reason, and preservation of completed/incomplete history.
- [ ] T017 [US2] Implement State Group and State scope persistence in `data/src/main/kotlin/io/github/thrhead/taplog/data/persistence/RoomLocalPersistence.kt`, enforcing one active state per `(stateGroupId,targetScopeKey)`, preserving generation/reset metadata, and recovering exclusivity after restart.
- [ ] T018 [P] [US2] Add pure JVM event/lifecycle mapping and monotonicity tests in `data/src/test/kotlin/io/github/thrhead/taplog/data/persistence/PersistenceMapperTest.kt` for all four behaviors, positive Counter quantities, Duration terminal rules, State scope/generation, and non-decreasing sequence/revisions.
- [ ] T019 [US2] Add Android restart recovery tests in `data/src/androidTest/kotlin/io/github/thrhead/taplog/data/persistence/RestartRecoveryTest.kt` for Moment, Counter, Duration, State, and `CreateRecordAndLog`, including database close/reopen and OPEN Duration/active State assertions.

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
- [ ] T024 [US3] Implement explicit confirmed bounded permanent deletion in `data/src/main/kotlin/io/github/thrhead/taplog/data/persistence/RoomLocalPersistence.kt`, deleting only selected definitions, eligible relationship rows, matching Event/payload/snapshot rows, bindings, Undo metadata, and affected State scopes while retaining unrelated definitions/history and no-Target Events.
- [ ] T025 [P] [US3] Add pure JVM contract tests for archive, unlink, orphaned binding, Undo invalidation, and bounded delete in `data/src/test/kotlin/io/github/thrhead/taplog/data/persistence/RepositoryContractTest.kt`.
- [ ] T026 [US3] Add Android integration tests for foreign-key restrict behavior, archive/unlink effects, orphan snapshots, explicit deletion graph boundaries, and preservation of unrelated/no-Target history in `data/src/androidTest/kotlin/io/github/thrhead/taplog/data/persistence/RoomAtomicCommitBoundaryTest.kt`.

## Phase 6: User Story 4 — Commit atomically through the existing core boundary (P1)

Goal: `EventEngine` can use one durable adapter with serialized compare-and-swap
semantics and no partial writes.

Independent test criteria: two commits with the same expected context result in
exactly one successful write; a stale compare returns `false` without
overwriting newer state; injected failures leave the previous aggregate
readable; command-level stale checks still report `Conflict` through existing
core tests.

- [ ] T027 [US4] Implement the sole `AtomicCommitBoundary` adapter in `data/src/main/kotlin/io/github/thrhead/taplog/data/persistence/RoomAtomicCommitBoundary.kt`, serializing commits, comparing expected revisions/generations/sequence metadata inside the Room transaction, validating the mapped state, and returning `true` only after transaction success.
- [ ] T028 [US4] Ensure `RoomAtomicCommitBoundary` maps stale expected context and failed compare to Boolean `false`/storage failure behavior without modifying `core/src/main/**`, adding a typed commit-conflict result, or adding a global mutation revision in `data/src/main/kotlin/io/github/thrhead/taplog/data/persistence/RoomAtomicCommitBoundary.kt`.
- [ ] T029 [P] [US4] Add pure JVM failure-injection and concurrency contract tests in `data/src/test/kotlin/io/github/thrhead/taplog/data/persistence/FailureInjectionTest.kt`, covering each logical write phase, no partial writes, monotonic revisions, stale compare non-overwrite, and Boolean failure semantics.
- [ ] T030 [US4] Add Android transaction rollback and concurrent stale-commit tests in `data/src/androidTest/kotlin/io/github/thrhead/taplog/data/persistence/RoomAtomicCommitBoundaryTest.kt`, asserting exactly one winner and complete old/new state visibility after failures or restart.
- [ ] T031 [US4] Run and preserve the existing command-level Conflict/StorageFailure assertions in `core/src/test/kotlin/io/github/thrhead/taplog/core/engine/ConflictHandlingTest.kt` and `core/src/test/kotlin/io/github/thrhead/taplog/core/engine/CorrectionAndAtomicityTest.kt`; change no core contract or semantics.

## Phase 7: User Story 5 — Version and migrate the local schema safely (P2)

Goal: supported prior Room schemas migrate non-destructively and atomically,
while failures preserve the prior valid database and surface deterministic
storage failure behavior.

Independent test criteria: migrate every supported prior schema using real
SQLite/Room databases, validate rows through the mapper/invariants, reopen the
result, and inject migration failure to prove no reset, silent repair, or
partial migrated state occurs.

- [ ] T032 [US5] Define ordered non-destructive Room migrations and schema-version metadata in `data/src/main/kotlin/io/github/thrhead/taplog/data/persistence/TapLogDatabase.kt`, preserving prior rows, snapshots, identities, revisions, generations, sequence values, and singleton metadata.
- [ ] T033 [P] [US5] Add exported Room schema fixtures for each supported prior version under `data/schemas/io.github.thrhead.taplog.data.persistence.TapLogDatabase/` and document migration assumptions in `specs/004-local-persistence/quickstart.md` only if the existing quickstart lacks the required version matrix.
- [ ] T034 [US5] Add real SQLite/Room migration tests in `data/src/androidTest/kotlin/io/github/thrhead/taplog/data/persistence/PersistenceMigrationTest.kt` for every supported prior schema, index/foreign-key reconstruction, mapper validation, and successful restart after migration.
- [ ] T035 [US5] Add failed-migration preservation tests in `data/src/androidTest/kotlin/io/github/thrhead/taplog/data/persistence/PersistenceMigrationTest.kt`, asserting the old valid database remains usable and no destructive reset or silent row dropping occurs.

## Phase 8: Polish and cross-cutting verification

- [ ] T036 [P] Add dependency-boundary tests or Gradle validation in `data/src/test/kotlin/io/github/thrhead/taplog/data/persistence/DependencyBoundaryTest.kt` and `data/build.gradle.kts`, proving `:core` has no Android/Room dependency and persistence types do not leak into core contracts.
- [ ] T037 [P] Add schema/entity invariant coverage in `data/src/androidTest/kotlin/io/github/thrhead/taplog/data/persistence/RoomAtomicCommitBoundaryTest.kt` for primary-key uniqueness, foreign-key enforcement, chronology/scope indexes, partial OPEN Duration uniqueness, and non-decreasing values.
- [ ] T038 Run the feature quickstart commands `./gradlew :data:testDebugUnitTest`, `./gradlew :data:connectedDebugAndroidTest`, and `./gradlew :data:lintDebug :detekt`; record any environment limitation and results in `specs/004-local-persistence/quickstart.md` without changing product scope.
- [ ] T039 Run the existing `:core` test suite and final repository checks, including `./gradlew :core:test`, `git diff --check`, and inspection that only approved `:data`/test/build files changed; verify no UI, NFC, Widget, Quick Settings, parser, backup/import/export, statistics, monetization, AI, sync, or cloud tasks were introduced.

## Parallel execution opportunities

- Phase 1: T003 can run in parallel with T001–T002 after the module paths are confirmed.
- Phase 2: T006 and T007 can run in parallel; T009 can begin after T007/T008, while T010 can proceed independently after T004–T005.
- US1: T012 can run alongside T011 after the contract fixtures exist; T013 follows the database declaration and entities.
- US2: T018 can run alongside T014–T017 once mapper primitives exist; T019 follows the behavior persistence paths.
- US3: T025 can run alongside T020–T024 after the repository contract is stable; T026 follows the Room implementation.
- US4: T029 can run alongside T027–T028 only when the adapter seam is testable; T030 and T031 are then verification tracks.
- US5: T033 can run alongside T032; T034 and T035 are sequential because failure tests depend on the migration registry.
- Final phase: T036 and T037 can run in parallel with each other after implementation stabilizes.

## Implementation strategy

1. Establish Room/KSP wiring, entities, DAOs, mapping, and database metadata.
2. Deliver the MVP complete aggregate read/write path and pure/Android tests.
3. Add event behavior and restart-sensitive lifecycle recovery.
4. Add history-preserving lifecycle effects and explicit bounded deletion.
5. Adapt the existing Boolean atomic boundary with serialized compare-and-swap and failure rollback tests.
6. Add migration fixtures/tests and perform full quickstart, core, lint, detekt, and dependency-boundary verification.

