---

description: "Implementation tasks for Record + Target Management"
---

# Tasks: TapLog Record + Target Management

**Input**: Design documents from `/specs/005-record-target-management/`

**Prerequisites**: `spec.md`, `plan.md`, `research.md`, `data-model.md`, `contracts/management-application.md`, `contracts/management-ui.md`, and `quickstart.md`.

**Scope guard**: This task list covers management only. It does not add Home, Timeline, Duration/State execution UX, NFC, Widget, Quick Settings, parser, backup, statistics, monetization, AI, sync, cloud, or standalone Target permanent deletion.

## Phase 1: Setup

**Purpose**: Prepare the existing three-module Android project for the approved management seams without changing product behavior.

- [ ] T001 Add only the approved AndroidX lifecycle/ViewModel and Navigation Compose dependencies, if supported by `gradle/libs.versions.toml`, and wire them in `app/build.gradle.kts` without adding DI, coroutines, network, or feature modules.
- [ ] T002 Create the package/file skeleton specified by `specs/005-record-target-management/plan.md` under `core/src/test/kotlin/io/github/thrhead/taplog/core/engine/`, `data/src/main/kotlin/io/github/thrhead/taplog/data/persistence/`, `app/src/main/java/io/github/thrhead/taplog/management/`, and `app/src/androidTest/kotlin/io/github/thrhead/taplog/management/`; keep new files empty or test-scaffold-only until their story tasks execute.

---

## Phase 2: Foundational Core and Data Boundaries

**Purpose**: Complete the approved core seam and expose the smallest data-owned aggregate boundary. No UI or story-specific orchestration starts until this phase is complete.

- [ ] T003 [P] Add JVM contract tests for Record/Target creation, required names, supported Moment/Counter/Duration/State behaviors, Counter default quantity of 1 when omitted, positive quantity validation, optional unit, State Group requirements, immutable IDs, and initial monotonic revisions in `core/src/test/kotlin/io/github/thrhead/taplog/core/engine/ManagementCreationTest.kt`.
- [ ] T004 [P] Add JVM contract tests for permitted Record/Target edits, name/icon/default-quantity revision changes, post-event behavior and Counter-unit locks, lifecycle revision changes, and invalid/stale expected contexts in `core/src/test/kotlin/io/github/thrhead/taplog/core/engine/ManagementDefinitionTest.kt`.
- [ ] T005 [P] Add JVM contract tests for active-pair link, unlink, and explicit relink: inactive/nonexistent targets are rejected; relationship revision increments; relink enables future Events; historical Events, snapshots, orphaned bindings, state generations, and terminal effects are untouched in `core/src/test/kotlin/io/github/thrhead/taplog/core/engine/ManagementRelationshipTest.kt`.
- [ ] T006 [P] Add JVM contract tests for exact null-target Record-wide and non-null Record–Target deletion scopes, `NeedsConfirmation`, cancel/missing confirmation no-op behavior, unrelated Target/no-Target history preservation, and unavailable standalone Target deletion in `core/src/test/kotlin/io/github/thrhead/taplog/core/engine/ManagementDeletionTest.kt`.
- [ ] T007 Implement validated creation and explicit relink through the existing `EventEngine`/`DefinitionManagement` seam in `core/src/main/kotlin/io/github/thrhead/taplog/core/engine/DefinitionManagement.kt`, `core/src/main/kotlin/io/github/thrhead/taplog/core/engine/EventEngine.kt`, `core/src/main/kotlin/io/github/thrhead/taplog/core/engine/Commands.kt`, and `core/src/main/kotlin/io/github/thrhead/taplog/core/engine/Results.kt`; preserve authoritative `EngineResult`/`ResultReason`, expected-context conflict checks, atomic commit routing, and do not create a second management engine.
- [ ] T008 Implement the minimum application-facing read/commit adapter over the existing aggregate and `AtomicCommitBoundary` in `data/src/main/kotlin/io/github/thrhead/taplog/data/persistence/ManagementPersistencePort.kt` and `data/src/main/kotlin/io/github/thrhead/taplog/data/persistence/ManagementPersistenceAdapter.kt`; keep Room entities, DAOs, and `RoomLocalPersistence` internals inaccessible to `:app`, add no schema or competing persistence contract, and preserve `StorageFailure` semantics.
- [ ] T009 Add focused JVM boundary tests for the data adapter’s aggregate round-trip, core-produced lifecycle/deletion diffs, compare/write failure, and no partial commit in `data/src/test/kotlin/io/github/thrhead/taplog/data/persistence/ManagementPersistenceAdapterTest.kt`; extend existing 004 fixtures only where the new relationship/creation state requires it and do not duplicate the full 004 repository suite.
- [ ] T010 Run the Phase 2 core/data JVM tests and fix only contract-proven incompatibilities in `core/src/test/kotlin/io/github/thrhead/taplog/core/engine/` and `data/src/test/kotlin/io/github/thrhead/taplog/data/persistence/`; record any real 003/004 contract break as an upstream decision rather than silently changing persistence semantics.

**Checkpoint**: Core owns invariants/results/lifecycle/deletion semantics, data owns aggregate persistence, and no app code can reach Room/DAO internals.

---

## Phase 3: User Story 1 — Manage Records (Priority: P1) 🎯 MVP

**Goal**: Create, edit, archive, unarchive, list, and confirmation-delete Records while preserving behavior rules, Counter defaults, revisions, and Event history.

**Independent Test**: From a clean local aggregate, create all four behaviors, edit before/after Events, verify Counter defaults and locks, archive/unarchive, preview/cancel/confirm permitted deletion, and assert deterministic results plus unchanged historical snapshots.

### Tests for User Story 1

- [ ] T011 [P] [US1] Add pure JVM tests for Record management intents, expected-context construction, read filters, Applied-after-commit handling, and no direct persistence mutation in `app/src/test/kotlin/io/github/thrhead/taplog/management/records/RecordManagementUseCaseTest.kt`.
- [ ] T012 [P] [US1] Add pure JVM tests for Record delete preview/confirmation/cancellation, exact impact propagation, confirmation-only destructive behavior, and stable category/reason mapping in `app/src/test/kotlin/io/github/thrhead/taplog/management/records/RecordDeletionUseCaseTest.kt`.
- [ ] T013 [P] [US1] Add ViewModel/state-holder tests for Record loading, active/archived empty/content states, create/edit validation, refresh after commit, conflict retention, storage error retry, and no optimistic success in `app/src/test/kotlin/io/github/thrhead/taplog/management/records/RecordManagementViewModelTest.kt`.

### Implementation for User Story 1

- [ ] T014 [US1] Implement Record read/create/edit/archive/unarchive and permitted delete-preview/confirm use cases in `app/src/main/java/io/github/thrhead/taplog/management/records/RecordManagementUseCases.kt` using the data port and existing core operations; submit only behavior-valid fields, default omitted Counter quantity to 1, pass expected entity/dataset context, and never derive lifecycle effects or deletion counts in `:app`.
- [ ] T015 [US1] Implement Record application state and state-holder/ViewModel in `app/src/main/java/io/github/thrhead/taplog/management/records/RecordManagementState.kt` and `app/src/main/java/io/github/thrhead/taplog/management/records/RecordManagementViewModel.kt`; represent loading/empty/content/confirmation/success/invalid/conflict/storage-error states and refresh persisted state after Applied.
- [ ] T016 [US1] Implement Record list, active/archived filter, editor, behavior-specific fields, Counter unit/default quantity controls, archive/unarchive actions, and permanent-delete impact confirmation in `app/src/main/java/io/github/thrhead/taplog/management/records/RecordListScreen.kt`, `app/src/main/java/io/github/thrhead/taplog/management/records/RecordEditorScreen.kt`, and `app/src/main/java/io/github/thrhead/taplog/management/records/RecordDeleteConfirmation.kt`; offer confirmation only for permanent deletion and render stable UI state/resource keys.
- [ ] T017 [US1] Add localized Record management strings, including behavior/unit-lock, invalid quantity, archive/unarchive, exact irreversible impact, conflict, storage-error, loading, empty, and success copy, in `app/src/main/res/values/strings.xml`; do not render raw exception text or invent execution UX.
- [ ] T018 [US1] Add Compose critical-flow tests for all behavior creation, Counter default handling, post-event behavior/unit lock messaging, archive/unarchive, delete preview/cancel/confirm, and loading/empty/error/success rendering in `app/src/androidTest/kotlin/io/github/thrhead/taplog/management/records/RecordManagementScreenTest.kt`.

**Checkpoint**: Record management is independently demonstrable and testable without Home, Timeline, or Event execution UI.

---

## Phase 4: User Story 2 — Manage Targets (Priority: P1)

**Goal**: Create, edit, archive, unarchive, and list reusable Targets, while making standalone permanent deletion unavailable.

**Independent Test**: Create/edit/archive/unarchive a Target, verify revisions, active/archived eligibility, preserved history and snapshots, deterministic outcomes, and that no standalone delete affordance or mutation exists.

### Tests for User Story 2

- [ ] T019 [P] [US2] Add pure JVM tests for Target list/create/edit/archive/unarchive use cases, required-name validation, monotonic revision propagation, active/archived filters, and explicit absence of standalone Target deletion in `app/src/test/kotlin/io/github/thrhead/taplog/management/targets/TargetManagementUseCaseTest.kt`.
- [ ] T020 [P] [US2] Add ViewModel/state-holder tests for Target loading, empty/content/error states, refresh after mutation, conflict handling, restart-safe re-read, and no delete action in `app/src/test/kotlin/io/github/thrhead/taplog/management/targets/TargetManagementViewModelTest.kt`.

### Implementation for User Story 2

- [ ] T021 [US2] Implement Target read/create/edit/archive/unarchive use cases in `app/src/main/java/io/github/thrhead/taplog/management/targets/TargetManagementUseCases.kt`; delegate validation, revisions, lifecycle effects, and results to core through the data boundary and do not add Target permanent deletion.
- [ ] T022 [US2] Implement Target application state and state-holder/ViewModel in `app/src/main/java/io/github/thrhead/taplog/management/targets/TargetManagementState.kt` and `app/src/main/java/io/github/thrhead/taplog/management/targets/TargetManagementViewModel.kt`, including deterministic loading/empty/content/invalid/conflict/storage-error/success states and post-commit refresh.
- [ ] T023 [US2] Implement Target list and editor Compose screens with active/archived filtering and direct archive/unarchive actions in `app/src/main/java/io/github/thrhead/taplog/management/targets/TargetListScreen.kt` and `app/src/main/java/io/github/thrhead/taplog/management/targets/TargetEditorScreen.kt`; omit standalone permanent-delete controls.
- [ ] T024 [US2] Add localized Target management strings and stable result-state copy in `app/src/main/res/values/strings.xml`, preserving the distinction between archive/unarchive and permanent deletion.
- [ ] T025 [US2] Add Compose critical-flow tests for Target create/edit/archive/unarchive, active/archived empty/content states, invalid/storage/conflict states, and absence of standalone Target deletion in `app/src/androidTest/kotlin/io/github/thrhead/taplog/management/targets/TargetManagementScreenTest.kt`.

**Checkpoint**: Target management is independently demonstrable and cannot broaden core deletion scope.

---

## Phase 5: User Story 3 — Assign Targets to Records (Priority: P1)

**Goal**: List valid active Targets for a Record, retain distinct no-Target scope, and support direct link/unlink/relink with relationship revisions and historical orphan preservation.

**Independent Test**: Link multiple active Targets, show only eligible active choices plus no-Target, unlink one pair, relink it explicitly, and verify only that pair’s future eligibility changes while definitions, snapshots, Events, and orphaned historical bindings remain unchanged.

### Tests for User Story 3

- [ ] T026 [P] [US3] Add pure JVM tests for assignment reads and link/unlink/relink intents, active Target filtering, distinct `TargetScope.NoTarget`, relationship expected-context revisions, direct-action semantics, and deterministic core result propagation in `app/src/test/kotlin/io/github/thrhead/taplog/management/assignment/RecordTargetAssignmentUseCaseTest.kt`.
- [ ] T027 [P] [US3] Add state-holder tests for assignment loading/empty/content/error states, stale relationship conflict refresh, direct unlink/relink feedback, and no optimistic state before commit in `app/src/test/kotlin/io/github/thrhead/taplog/management/assignment/RecordTargetAssignmentViewModelTest.kt`.

### Implementation for User Story 3

- [ ] T028 [US3] Implement assignment list/link/unlink/relink use cases in `app/src/main/java/io/github/thrhead/taplog/management/assignment/RecordTargetAssignmentUseCases.kt`; offer only active valid Targets, preserve no-Target as `targetId=null`/`TargetScope.NoTarget`, pass relationship/dataset context, and invoke direct actions without confirmation.
- [ ] T029 [US3] Implement assignment state and state-holder/ViewModel in `app/src/main/java/io/github/thrhead/taplog/management/assignment/RecordTargetAssignmentState.kt` and `app/src/main/java/io/github/thrhead/taplog/management/assignment/RecordTargetAssignmentViewModel.kt`; expose relationship revisions and core-provided lifecycle effects without reconstructing them.
- [ ] T030 [US3] Implement Record assignment Compose UI in `app/src/main/java/io/github/thrhead/taplog/management/assignment/RecordTargetAssignmentScreen.kt`, including active eligible Target selection, no-Target option, link/unlink/relink affordances, loading/empty/error/conflict/success states, and no permanent-delete confirmation.
- [ ] T031 [US3] Add Compose tests for valid Target filtering, no-Target distinction, direct unlink/relink, relationship revision feedback, and preservation-facing UI states in `app/src/androidTest/kotlin/io/github/thrhead/taplog/management/assignment/RecordTargetAssignmentScreenTest.kt`.
- [ ] T032 [US3] Extend the narrow integration boundary coverage in `data/src/androidTest/kotlin/io/github/thrhead/taplog/data/persistence/ManagementRestartIntegrationTest.kt` to prove archive/unlink lifecycle effects, relink future eligibility, orphaned BindingLifecycle/history preservation, Event snapshot preservation, and committed relationship revisions survive restart without duplicating all 004 persistence tests.

**Checkpoint**: Assignment controls only future eligibility; they never reactivate orphaned historical bindings or rewrite Event history.

---

## Phase 6: User Story 4 — Recover and Understand Management Outcomes (Priority: P2)

**Goal**: Make every management result deterministic, localized, restart-safe, and reachable through management-only navigation.

**Independent Test**: Exercise loading, empty, invalid, conflict, storage failure, Applied, confirmation/cancel, process recreation, and restart; verify stable category/reason state, no raw exception text, no false success, and committed-only recovery.

### Tests for User Story 4

- [ ] T033 [P] [US4] Add pure JVM tests covering every `EngineResult` category and stable `ResultReason` to management UI state/resource-key mapping, including raw exception exclusion and post-commit feedback failure behavior, in `app/src/test/kotlin/io/github/thrhead/taplog/management/ManagementResultMapperTest.kt`.
- [ ] T034 [P] [US4] Add state-holder tests for process recreation, restart-safe reload, interrupted/cancelled confirmation, stale entity/relationship/dataset conflicts, persistence failure, and committed-only success in `app/src/test/kotlin/io/github/thrhead/taplog/management/ManagementViewModelRestartTest.kt`.
- [ ] T035 [P] [US4] Add an integration/dependency test proving `:app` management sources contain no Room/DAO/internal persistence references and all writes route through the public data port in `app/src/test/kotlin/io/github/thrhead/taplog/management/ManagementBoundaryTest.kt`; retain core Android-independence coverage in `core/src/test/kotlin/io/github/thrhead/taplog/core/engine/CoreRuntimeIndependenceTest.kt`.

### Implementation for User Story 4

- [ ] T036 [US4] Implement shared management application outcome types, read states, and deterministic `EngineResult`/`ResultReason` mapping in `app/src/main/java/io/github/thrhead/taplog/management/ManagementApplication.kt` and `app/src/main/java/io/github/thrhead/taplog/management/ManagementResultMapper.kt`; keep core category/reason authoritative and expose stable localized keys only.
- [ ] T037 [US4] Implement shared state-holder/ViewModel lifecycle and refresh/recreation behavior in `app/src/main/java/io/github/thrhead/taplog/management/ManagementState.kt` and `app/src/main/java/io/github/thrhead/taplog/management/ManagementViewModel.kt`; ensure Applied is emitted only after commit and StorageFailure leaves state retry-safe.
- [ ] T038 [US4] Implement management-only navigation from the existing activity shell in `app/src/main/java/io/github/thrhead/taplog/management/ManagementNavigation.kt` and `app/src/main/java/io/github/thrhead/taplog/MainActivity.kt`, covering management entry, Record list/editor/assignment, Target list/editor, archive filters, and delete confirmation without adding excluded destinations.
- [ ] T039 [US4] Add shared localized state strings and accessibility/content descriptions for loading, empty, invalid, conflict, storage error, confirmation, and success in `app/src/main/res/values/strings.xml`; ensure every screen uses resource keys rather than exception text.
- [ ] T040 [US4] Add Compose/navigation critical-flow tests for management entry, back-stack transitions, active/archived filters, create/edit/assignment destinations, delete confirmation cancellation/confirmation, and deterministic error/success states in `app/src/androidTest/kotlin/io/github/thrhead/taplog/management/ManagementNavigationTest.kt`.

**Checkpoint**: All four stories have independently testable app flows, stable result mapping, and management-only navigation.

---

## Phase 7: Polish and Final Verification

**Purpose**: Verify scope, dependencies, persistence/restart behavior, and the complete quickstart without adding new product behavior.

- [ ] T041 [P] Add or update focused Room restart assertions for committed Record/Target definitions, relationship revisions, lifecycle effects, Event snapshots, orphaned bindings, and exact deletion scope in `data/src/androidTest/kotlin/io/github/thrhead/taplog/data/persistence/RestartRecoveryTest.kt`; reuse 004 coverage and do not add schema/migration work.
- [ ] T042 [P] Add a management-specific local-first/offline test lane in `app/src/test/kotlin/io/github/thrhead/taplog/management/ManagementOfflineTest.kt` proving create/edit/archive/assignment/permitted delete do not require network, account, or AI.
- [ ] T043 Review `app/src/main/java/io/github/thrhead/taplog/management/`, `core/src/main/kotlin/io/github/thrhead/taplog/core/engine/`, and `data/src/main/kotlin/io/github/thrhead/taplog/data/persistence/` for boundary violations, duplicate management engines, UI-owned invariants, global mutation revisions, raw exception rendering, standalone Target deletion, or excluded-scope code; resolve only findings traced to the approved artifacts.
- [ ] T044 Run `./gradlew :core:test :data:test :app:test` and `./gradlew :app:connectedDebugAndroidTest`, then fix failures within the scoped files and record any environment-only connected-test limitation rather than weakening assertions.
- [ ] T045 Run `./gradlew :core:dependencies :data:dependencies :app:dependencies`, inspect for Android/Room leakage into `:core` or direct DAO access from `:app`, and verify `specs/005-record-target-management/quickstart.md` device-independent, connected, restart, and boundary checks are all represented.
- [ ] T046 Run `graft build` after implementation changes and perform final checklist review against `specs/005-record-target-management/spec.md`, `plan.md`, contracts, PRD, constitution, and the explicit out-of-scope list; do not modify those canonical artifacts as part of task execution.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1** has no dependencies. T001 and T002 touch separate setup concerns and can run in parallel.
- **Phase 2** depends on Phase 1. T003–T006 are independent test files and can run in parallel; T007 depends on T003–T006; T008 depends on the public core/data boundary decisions in T007; T009 depends on T008; T010 gates all stories.
- **User Stories 1–3** depend on T010. They are independently reviewable after the foundation, but their UI tasks should not be merged into the same files concurrently; US3 also depends on the core relink and data adapter behavior validated by Phase 2.
- **User Story 4** depends on the application outcome shape from US1–US3 and should follow their use-case/state-holder interfaces; its pure mapper test T033 may start after T007 if the result contract is stable.
- **Phase 7** depends on all desired stories, with T041 also depending on T032 and T044–T046 depending on all implementation and test tasks.

### Story Dependencies

- **US1 (P1 MVP)**: Phase 2 only; no dependency on Target UI.
- **US2 (P1)**: Phase 2 only; no standalone Target deletion.
- **US3 (P1)**: Phase 2 plus the Record/Target read models; uses US1/US2 domain identities but remains independently testable with fixtures.
- **US4 (P2)**: Uses shared application result/state interfaces established by US1–US3; it does not add domain or persistence semantics.

### Parallel Execution Examples

```text
After Phase 1:
  T003, T004, T005, T006

After Phase 2:
  T011, T012, T013      (US1 tests)
  T019, T020            (US2 tests)
  T026, T027            (US3 tests)

After the corresponding tests:
  T014 → T015 → T016 → T018  (Record flow)
  T021 → T022 → T023 → T025  (Target flow)
  T028 → T029 → T030 → T031  (Assignment flow)

After story interfaces stabilize:
  T033, T034, T035         (US4 tests)
  T041, T042               (final verification support)
```

## Implementation Strategy

### MVP First

1. Complete Phase 1 and Phase 2.
2. Complete US1 through T018.
3. Run the US1 independent test and the JVM lanes.
4. Stop for review/demo before adding Targets, assignment, or broader navigation.

### Incremental Delivery

1. Add US2 Target management and validate it without standalone deletion.
2. Add US3 assignment and verify relink/orphan/history behavior.
3. Add US4 shared outcome recovery/navigation.
4. Complete Phase 7 and the quickstart checks.

### SDD Guidance

- Tests in each story are written first and must fail for the intended reason before implementation tasks proceed.
- Keep core, data, use-case, state-holder, and Compose changes in separately reviewable task groups.
- A task marked `[P]` uses a distinct file and has no dependency on another incomplete task in the same parallel group.
- Do not broaden a task to repair an upstream 003/004 contract; stop and record the incompatibility for architectural review.

## Traceability Notes

- Core creation/relink and management result semantics trace to `specs/005-record-target-management/plan.md` and `research.md`.
- Record behavior/default/unit rules trace to FR-002–FR-007 and `data-model.md`.
- Lifecycle/history/orphan/relink rules trace to FR-008–FR-018 and the approved 003/004 contracts.
- App/data boundaries and deterministic UI mapping trace to FR-019–FR-026 and both management contracts.
- Navigation, Compose states, exclusions, and verification trace to FR-027–FR-028 and `quickstart.md`.
