---

description: "Task list for TapLog Core Domain + Event Engine"
---

# Tasks: TapLog Core Domain + Event Engine

**Input**: Design documents from `specs/003-core-event-engine/`

**Prerequisites**: `plan.md`, `spec.md`, `research.md`, `data-model.md`, `contracts/event-engine.md`, and `quickstart.md`

**Scope**: Extend the Android-independent `:core` module with the shared domain model and Event Engine. Room/SQLite, Android channels, UI, parser/AI, notifications, backup, and lifecycle/background work remain out of scope.

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Establish the core source/test layout and deterministic test dependencies.

- [X] T001 Establish the domain package layout through `core/src/main/kotlin/io/github/thrhead/taplog/core/domain/Primitives.kt` and the engine package layout through `core/src/main/kotlin/io/github/thrhead/taplog/core/engine/Commands.kt`
- [X] T002 Establish the pure-JVM test package layout through `core/src/test/kotlin/io/github/thrhead/taplog/core/domain/PrimitiveSemanticsTest.kt` and `core/src/test/kotlin/io/github/thrhead/taplog/core/engine/EventEngineLoggingTest.kt`
- [X] T003 Configure Kotlin/JVM test dependencies and deterministic test execution in `core/build.gradle.kts` without adding Android, database, network, or AI dependencies

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Implement shared value semantics and transaction-facing abstractions required by every story.

**⚠️ CRITICAL**: No user story work can begin until this phase is complete.

- [X] T004 [P] Define immutable identity, UTC epoch-millisecond time, monotonic sequence, revision, and dataset-generation value types in `core/src/main/kotlin/io/github/thrhead/taplog/core/domain/Primitives.kt`
- [X] T005 [P] Define exact positive decimal quantity/unit value objects and validation rules in `core/src/main/kotlin/io/github/thrhead/taplog/core/domain/Quantity.kt`
- [X] T006 [P] Define closed behavior, lifecycle, source, duration-status, and stable result-reason enums in `core/src/main/kotlin/io/github/thrhead/taplog/core/domain/DomainEnums.kt`
- [X] T007 Define transaction-facing state snapshot, commit operation, acceptance clock, and atomic commit boundary interfaces in `core/src/main/kotlin/io/github/thrhead/taplog/core/engine/TransactionBoundary.kt`
- [X] T008 [P] Add foundational value-semantics and validation tests for exact quantity, distinct no-Target scope, timestamps, sequences, revisions, and all supported source values in `core/src/test/kotlin/io/github/thrhead/taplog/core/domain/PrimitiveSemanticsTest.kt`

**Checkpoint**: Shared domain values and the commit boundary are available without Android or persistence technology.

---

## Phase 3: User Story 1 - Log a shared event (Priority: P1) 🎯 MVP

**Goal**: Accept equivalent validated Moment and Counter commands from every source through one channel-independent contract, preserving event meaning, timestamps, snapshots, and intentional repeats.

**Independent Test**: Submit equivalent Moment and Counter requests for `APP`, `WIDGET`, `QUICK_SETTINGS`, `NFC`, and `NATURAL_LANGUAGE`; assert identical domain meaning with source retained as metadata, and assert two intentional Counter requests create two Events.

### Tests for User Story 1

- [ ] T009 [P] [US1] Write cross-channel Moment/Counter, explicit/omitted timestamp, snapshot, and duplicate-counter acceptance tests in `core/src/test/kotlin/io/github/thrhead/taplog/core/engine/EventEngineLoggingTest.kt`
- [ ] T010 [P] [US1] Write invalid inactive-scope, unlinked-relationship, behavior-field, quantity, and unit tests in `core/src/test/kotlin/io/github/thrhead/taplog/core/engine/EventEngineLoggingInvalidTest.kt`

### Implementation for User Story 1

- [ ] T011 [P] [US1] Define `Record`, `Target`, `RecordTarget`, lifecycle metadata, optional State Group reference, default Counter quantity, and post-first-event behavior/unit lock fields in `core/src/main/kotlin/io/github/thrhead/taplog/core/domain/Definitions.kt`
- [ ] T012 [P] [US1] Define immutable Event identity, Record/Target references, behavior-specific payloads, occurred/created/updated timestamps, source, sequence, revision, and creation-time snapshots in `core/src/main/kotlin/io/github/thrhead/taplog/core/domain/Event.kt`
- [ ] T013 [US1] Define validated `LogMoment` and `AddCounter` commands plus common expected-context/source fields in `core/src/main/kotlin/io/github/thrhead/taplog/core/engine/Commands.kt`
- [ ] T014 [US1] Implement active-scope, relationship, behavior, timestamp, exact-quantity, fixed-unit, snapshot, and no-global-debounce validation in `core/src/main/kotlin/io/github/thrhead/taplog/core/engine/EventEngine.kt`
- [ ] T015 [US1] Implement atomic Moment/Counter event construction and commit-result mapping so `Applied` is returned only after commit in `core/src/main/kotlin/io/github/thrhead/taplog/core/engine/EventEngine.kt`
- [ ] T016 [US1] Add committed-result identity/revision assertions and pure-JVM quickstart coverage for User Story 1 in `core/src/test/kotlin/io/github/thrhead/taplog/core/engine/EventEngineLoggingTest.kt`

**Checkpoint**: User Story 1 is independently functional; every valid source uses the same Event semantics and repeated valid Counter requests remain distinct.

---

## Phase 4: User Story 2 - Record durations and mutually exclusive states (Priority: P1)

**Goal**: Enforce scoped Duration lifecycle integrity and mutually exclusive State currentness, including archive/unlink terminal effects and lifecycle generations.

**Independent Test**: Exercise start, finish, toggle, equal timestamps, same-scope duplicate opens, concurrent Target scopes, archive/unlink incomplete transitions, state replacement/repetition, and generation resets using pure domain scenarios.

### Tests for User Story 2

- [ ] T017 [P] [US2] Write Duration start/finish/toggle tests covering OPEN, COMPLETED, negative intervals, equal timestamps, terminality, and one-open-per-Record-plus-scope in `core/src/test/kotlin/io/github/thrhead/taplog/core/engine/DurationEngineTest.kt`
- [ ] T018 [P] [US2] Write Target/no-Target concurrency and archive/unlink OPEN-to-INCOMPLETE lifecycle-effect tests in `core/src/test/kotlin/io/github/thrhead/taplog/core/engine/DurationLifecycleTest.kt`
- [ ] T019 [P] [US2] Write State Group currentness, same-State historical repetition, deterministic equal-time sequence ordering, scope clearing, and generation non-resurrection tests in `core/src/test/kotlin/io/github/thrhead/taplog/core/engine/StateEngineTest.kt`

### Implementation for User Story 2

- [ ] T020 [P] [US2] Add Duration payload/status invariants for `OPEN`, `COMPLETED`, and `INCOMPLETE`, including required timestamps/reasons and terminal transitions, in `core/src/main/kotlin/io/github/thrhead/taplog/core/domain/Event.kt`
- [ ] T021 [P] [US2] Add State Group, State payload, scoped lifecycle-generation, reset-sequence, and currentness models in `core/src/main/kotlin/io/github/thrhead/taplog/core/domain/State.kt`
- [ ] T022 [US2] Add `StartDuration`, `FinishDuration`, `ToggleDuration`, and `SetState` commands in `core/src/main/kotlin/io/github/thrhead/taplog/core/engine/Commands.kt`
- [ ] T023 [US2] Implement scoped OPEN Duration lookup, timestamp validation, terminal completion, and duplicate-open conflict/invalid handling in `core/src/main/kotlin/io/github/thrhead/taplog/core/engine/EventEngine.kt`
- [ ] T024 [US2] Implement State Group/scope validation, current-state replacement, same-State Event creation, sequence tie-breaking, and generation-aware currentness in `core/src/main/kotlin/io/github/thrhead/taplog/core/engine/EventEngine.kt`
- [ ] T025 [US2] Implement atomic archive/unlink lifecycle-effect construction for affected OPEN Durations, State generation resets, history preservation, and binding/Undo invalidation descriptors in `core/src/main/kotlin/io/github/thrhead/taplog/core/engine/LifecycleEffects.kt`
- [ ] T026 [US2] Integrate Duration and State commands/effects with the transaction boundary and assert no partial mutation on invalid or conflict results in `core/src/main/kotlin/io/github/thrhead/taplog/core/engine/EventEngine.kt`

**Checkpoint**: User Story 2 is independently functional; no invalid Duration or State transition can create a second open scope, invent time, or resurrect cleared history.

---

## Phase 5: User Story 3 - Preserve historical meaning while managing definitions (Priority: P1)

**Goal**: Keep historical Event snapshots and identities stable through ordinary definition management while exposing explicit, scoped lifecycle and permanent-delete effects.

**Independent Test**: Create Events, rename and re-icon definitions, archive/unarchive, unlink relationships, and inspect confirmed hard-delete impact; verify snapshots/history remain unchanged except for explicitly confirmed scope removal.

### Tests for User Story 3

- [ ] T027 [P] [US3] Write snapshot immutability tests for Record/Target rename and icon changes, archive/unarchive, relationship unlink, Counter default-quantity updates for future Events, and preservation of historical Event quantities in `core/src/test/kotlin/io/github/thrhead/taplog/core/engine/HistoryPreservationTest.kt`
- [ ] T028 [P] [US3] Write scoped permanent-delete impact and confirmed-removal tests proving unrelated Target/no-Target history is retained in `core/src/test/kotlin/io/github/thrhead/taplog/core/engine/PermanentDeleteScopeTest.kt`

### Implementation for User Story 3

- [ ] T029 [US3] Implement definition edit operations that preserve immutable Event snapshots, allow a Counter default quantity update for future requests only, preserve every prior Event quantity unchanged, and enforce behavior/Counter-unit immutability after the first Event in `core/src/main/kotlin/io/github/thrhead/taplog/core/engine/DefinitionManagement.kt`
- [ ] T030 [US3] Implement archive, unarchive, and relationship unlink domain operations with identity/history retention and creation-eligibility rules in `core/src/main/kotlin/io/github/thrhead/taplog/core/engine/DefinitionManagement.kt`
- [ ] T031 [US3] Define the canonical `BindingLifecycle` interface and data model in `core/src/main/kotlin/io/github/thrhead/taplog/core/domain/LifecycleMetadata.kt`: immutable `bindingId`, immutable Record/Target scope, active/orphaned state, revision, last-known Record/Target name-and-icon display snapshot, and related Undo invalidation data; archive/unlink emits `ORPHANED`, unarchive does not reactivate it, and the interface is aligned with `specs/003-core-event-engine/contracts/event-engine.md`
- [ ] T032 [US3] Define permanent-delete impact preview, explicit confirmation token, and confirmed scoped deletion operation in `core/src/main/kotlin/io/github/thrhead/taplog/core/engine/DeletionScope.kt`
- [ ] T033 [US3] Integrate definition management and confirmed deletion with atomic transaction-facing effects and deterministic result categories in `core/src/main/kotlin/io/github/thrhead/taplog/core/engine/EventEngine.kt`

**Checkpoint**: User Story 3 is independently functional; ordinary edits/lifecycle changes preserve meaning and permanent deletion cannot exceed its confirmed scope.

---

## Phase 6: User Story 4 - Correct safely through deterministic outcomes (Priority: P2)

**Goal**: Return deterministic Applied, NeedsConfirmation, Conflict, Invalid, and StorageFailure outcomes while preventing stale corrections from overwriting newer state.

**Independent Test**: Run valid, ambiguous, unconfirmed-delete, stale-revision, stale-generation, stale-scope, invariant-failure, commit-failure, and post-commit-feedback-failure scenarios and assert result plus mutation outcome.

### Tests for User Story 4

- [ ] T034 [P] [US4] Write result-category and stable-reason tests for Applied, NeedsConfirmation, Conflict, Invalid, and StorageFailure in `core/src/test/kotlin/io/github/thrhead/taplog/core/engine/EngineResultTest.kt`
- [ ] T035 [P] [US4] Write stale revision/dataset-generation/scope and no-overwrite tests in `core/src/test/kotlin/io/github/thrhead/taplog/core/engine/ConflictHandlingTest.kt`
- [ ] T036 [P] [US4] Write `CreateRecordAndLog`, EditEvent, DeleteEvent, Undo, and commit-failure tests in `core/src/test/kotlin/io/github/thrhead/taplog/core/engine/CorrectionAndAtomicityTest.kt`, plus a transaction/caller-boundary test proving feedback failure after commit leaves the Engine's `Applied` result and committed data unchanged in `core/src/test/kotlin/io/github/thrhead/taplog/core/engine/PostCommitFeedbackBoundaryTest.kt`

### Implementation for User Story 4

- [ ] T037 [P] [US4] Define sealed Engine result types with affected identities/revisions and stable non-Applied reason codes in `core/src/main/kotlin/io/github/thrhead/taplog/core/engine/Results.kt`
- [ ] T038 [P] [US4] Define expected revision, dataset generation, scope context, Undo receipt, and confirmation context models in `core/src/main/kotlin/io/github/thrhead/taplog/core/engine/ContextGuards.kt`
- [ ] T039 [US4] Add `CreateRecordAndLog`, `EditEvent`, `DeleteEvent`, and `Undo` command models with validated permitted changes and confirmation requirements in `core/src/main/kotlin/io/github/thrhead/taplog/core/engine/Commands.kt`
- [ ] T040 [US4] Implement optimistic context checks before mutation, returning Conflict for stale revision/generation/scope without overwriting newer state in `core/src/main/kotlin/io/github/thrhead/taplog/core/engine/EventEngine.kt`
- [ ] T041 [US4] Implement all-or-nothing CreateRecordAndLog, edit/delete/Undo correction semantics, and NeedsConfirmation handling in `core/src/main/kotlin/io/github/thrhead/taplog/core/engine/EventEngine.kt`
- [ ] T042 [US4] Implement commit failure mapping, unchanged-state guarantees, and preservation of the already committed `Applied` result in `core/src/main/kotlin/io/github/thrhead/taplog/core/engine/EventEngine.kt`; do not invoke, retry, or own post-commit feedback, which remains with the transaction boundary/caller

**Checkpoint**: All five result categories are deterministic, stable, and mutation-safe; stale correction never silently overwrites newer history.

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: Validate the complete core slice, preserve traceability, and keep boundaries explicit.

- [ ] T043 [P] Add a pure-JVM execution test proving core Event creation requires no network, AI, account, server, or Android runtime service in `core/src/test/kotlin/io/github/thrhead/taplog/core/engine/CoreRuntimeIndependenceTest.kt`
- [ ] T044 [P] Add repository-wide scenario coverage for the quickstart matrix in `core/src/test/kotlin/io/github/thrhead/taplog/core/engine/CoreEventEngineQuickstartTest.kt`
- [ ] T045 [P] Document public core command/result/domain contracts and excluded Android/persistence/channel responsibilities in `core/src/main/kotlin/io/github/thrhead/taplog/core/README.md`
- [ ] T046 Run `./gradlew :core:test` and fix only core-slice failures in `core/src/test/kotlin/io/github/thrhead/taplog/core/`
- [ ] T047 Run `./gradlew test` and verify no Android, Room/SQLite, parser/AI, network, or channel dependency entered `core/build.gradle.kts`

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: T001–T003 have no feature dependencies and may run in parallel where files do not overlap.
- **Foundational (Phase 2)**: T004–T008 depend on the core layout; this phase blocks all user stories.
- **User Stories (Phases 3–6)**: Each depends on Phase 2. US2 depends on US1’s Event/definition models; US3 depends on US1 snapshots and US2 lifecycle-effect models; US4 depends on the command, result, and transaction structures from US1–US3.
- **Polish (Phase 7)**: T043–T047 depend on the desired story phases being complete.

### User Story Dependencies

- **US1 (P1)**: Starts after Phase 2; MVP foundation for shared Event creation.
- **US2 (P1)**: Starts after US1’s Event and command infrastructure; adds Duration/State behavior.
- **US3 (P1)**: Starts after US1 snapshots and US2 lifecycle-effect descriptors; adds definition management/history scope.
- **US4 (P2)**: Starts after the prior stories’ commands and transaction boundary; adds correction and deterministic outcomes.

### Parallel Opportunities

- T001, T002, and T003 can proceed in parallel after confirming the existing `:core` module.
- T004–T006 and T008 can proceed in parallel; T007 follows the shared value contracts.
- Within US1, T009–T012 can proceed in parallel; T013–T015 then proceed in dependency order.
- Within US2, T017–T021 can proceed in parallel; T022–T026 follow command/model dependencies.
- Within US3, T027–T032 can proceed in parallel when their test/model files are isolated; T033 integrates them.
- Within US4, T034–T039 can proceed in parallel by file; T040–T042 then integrate guards, commands, and result semantics.
- After foundational work, separate workers can own US1, US2, and US3 test/model files, but shared `EventEngine.kt` integration must remain sequential. T043 and T044 can run in parallel after story completion.

## Implementation Strategy

### MVP First (User Story 1)

1. Complete Phase 1 and Phase 2.
2. Complete US1 tests first, observe intended failures, then implement shared Moment/Counter logging.
3. Run `./gradlew :core:test` and validate the cross-channel and repeated-Counter criteria.
4. Stop for an MVP demo before adding Duration, State, management, or correction behavior.

### Incremental Delivery

1. Add US2 and validate Duration/State invariants independently.
2. Add US3 and validate snapshots, lifecycle effects, and deletion scope independently.
3. Add US4 and validate all result categories, stale guards, and atomicity.
4. Run the runtime-independence test, full quickstart matrix, and repository test lane in Phase 7.

## Traceability Notes

- The four story phases map directly to the priorities and acceptance scenarios in `spec.md`.
- Domain constraints such as exact positive quantities, terminal Duration statuses, no-Target scope separation, immutable snapshots, generation resets, and stable result reasons are copied into the relevant task descriptions or their named test files.
- The implementation deliberately leaves Room/SQLite realization, Android lifecycle, input-channel duplicate handling, parser/AI, feedback, backup/import, statistics, and monetization to later slices.
