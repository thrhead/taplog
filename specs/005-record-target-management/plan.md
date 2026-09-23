# Implementation Plan: Record + Target Management

Branch: 005-record-target-management | Date: 2026-09-23 | Spec: spec.md

## Summary

Deliver the first product-facing management slice in :app: Record and Target lists, create/edit forms, archive/unarchive, Record–Target assignment, and the confirmation-gated permanent Record/scope delete flow. The app uses a thin orchestration layer over the existing :core EventEngine/DefinitionManagement semantics and the :data AtomicCommitBoundary; it never accesses Room/DAOs directly or implements domain invariants.

The existing core management seam is extended only where 005 requires a missing operation (creation and explicit relink), while retaining existing result categories, ResultReason, lifecycle effects, revision checks, snapshots, deletion scopes, and the persistence boundary. Target definitions remain non-deletable as standalone objects in this slice.

## Technical Context

Language/Version: Kotlin 2.3.21, Java 17, Gradle Wrapper 9.6.0, Android API 26–36.

Primary Dependencies: Existing Kotlin/JVM, AndroidX Activity Compose, Compose Material 3/BOM, JUnit 4, AndroidX test runner, and existing :core/:data modules. Add only the smallest approved AndroidX lifecycle/ViewModel and Navigation Compose dependencies if the current version catalog supports them; no DI, Room access, coroutines, network, parser, billing, or feature module is introduced.

Storage: Existing Room/SQLite database and AtomicCommitBoundary in :data; no new schema or persistence strategy. Query/read access needed by :app must be exposed through a data-owned application-facing port backed by the existing aggregate read, not by DAOs.

Testing: Pure JVM tests for core management and app orchestration/result mapping; ViewModel/state-holder tests; Compose UI tests for critical management flows; existing :data repository/Room/restart tests reused and extended only where the new core state shape requires coverage.

Target Platform: Android application, minSdk 26, local-first/offline, Compose UI.

Project Type: Three-module Android mobile application (:core Kotlin/JVM, :data persistence, :app presentation/orchestration).

Performance Goals: Management list and mutation feedback remain local and responsive for the existing aggregate scale; no network or background sync is required. Exact benchmarking is not a 005 scope.

Constraints: :core remains Android/framework independent; :app cannot mutate persistence or access DAOs; historical Events/snapshots are immutable under ordinary management; only permanent deletion is confirmation-gated; no standalone Target deletion; no Home dashboard, Timeline, Duration/State execution UX, NFC, Widget, Quick Settings, parser, backup, statistics, monetization, AI, or sync.

Scale/Scope: Management flows only: Record and Target active/archived lists, create/edit, archive/unarchive, assignment, exact delete-impact preview/confirmation, deterministic loading/empty/error/success states, and restart-safe committed results.

## Constitution Check

Gate passed before Phase 0 research and re-checked after Phase 1 design.

| Principle | Design response | Status |
| --- | --- | --- |
| I. Event-first, channel-independent core | Management changes definitions and relationship state through the existing core engine; it does not create a second event or channel engine. | Pass |
| II. Local-first core | All management is backed by the existing local boundary and works without account, network, server, or AI. | Pass |
| III. Preserve history and confirm ambiguity | Ordinary edits/archive/unlink preserve Event IDs and snapshots; only exact core delete scopes can remove history, after explicit confirmation. | Pass |
| IV. Channel-owned interaction safety | No event duplicate suppression or execution channel is introduced. | Pass |
| V. Testable boundaries and scope discipline | Core/application behavior is testable outside Compose; UI owns interaction only; excluded roadmap slices remain excluded. | Pass |

No constitutional violation or complexity exception is required.

## Architectural Decisions

1. Keep EventEngine as the transaction-facing core authority and DefinitionManagement as its pure definition transformation helper. Add missing create/relink operations to that seam rather than creating RecordManagement, TargetManagement, or an app-owned domain engine.
2. App use cases read a current aggregate through a data-owned port, construct validated core inputs/expected context, invoke core, and translate EngineResult without changing its category or reason. App form validation is for presentation; core remains authoritative.
3. Expose a narrow application-facing read/write adapter from :data around the approved AtomicCommitBoundary/local persistence. Keep Room entities, DAOs, and RoomLocalPersistence internal. Do not add a competing repository or schema contract.
4. Relinking an existing unlinked pair sets linked=true and increments only RecordTarget.revision. It does not touch historical Events, orphaned bindings, binding snapshots, state generations, or terminal lifecycle effects.
5. Preview and confirmation use DeletionScope.preview/confirm and EventEngine.deleteScope(recordId, targetId?, confirmed). App does not count, cascade, or broaden impact. Null target is Record-wide; non-null target is pair-scoped; Target-only deletion has no app entry point.
6. Preserve Applied, NeedsConfirmation, Conflict, Invalid, and StorageFailure as typed app outcomes. Localized UI copy is keyed by stable ResultReason; raw exception messages never reach presentation.
7. Add a small management graph rooted from the existing activity shell: Record list/editor/assignment and Target list/editor, with archive filters and delete confirmation, but no Home dashboard or execution UX.

## Application / Use-case Boundaries

The app layer contains thin use cases or an equivalent application service:

- ObserveRecords / ObserveTargets: load active or archived definitions and relationship candidates through the data port; return loading, empty, loaded, and typed failure states.
- CreateRecord / EditRecord: construct a Record or RecordEdit, apply core validation, and return the exact core result. Counter defaults use the approved rule (omitted means 1); behavior-specific fields are not invented in UI.
- CreateTarget / EditTarget: create or edit independent Target definitions through core semantics.
- ArchiveRecord, UnarchiveRecord, ArchiveTarget, UnarchiveTarget: invoke existing lifecycle operations and expose core effects.
- ListRecordTargets, LinkTarget, UnlinkTarget, RelinkTarget: use active definitions plus relationship state. Link/relink reject inactive/nonexistent targets in core and preserve no-Target as a separate scope.
- PreviewDeleteScope, ConfirmDeleteScope: preview without mutation, show exact impact, require explicit confirmation, then invoke confirmed core deletion. Cancellation and missing confirmation perform no commit.
- MapManagementResult: deterministic pure mapping from core result category/reason to localized resource keys and stable UI state.

Every mutating use case reads the latest state immediately before invocation and supplies expected entity/relationship/dataset context where defined. Applied is rendered as success only after commit; a failed commit is never rendered as success.

## Upstream 003/004 Constraints and Compatibility Work

- Record, Target, RecordTarget identities/revisions, Record.hasEvents, LifecycleEffect, DeleteImpact, DeleteConfirmation, EngineResult, and ResultReason remain authoritative.
- Existing 003 methods cover edit/archive/unarchive/unlink and scope deletion. Creation and explicit relink are missing from the current implementation and must be added as compatible extensions to the existing engine/management seam, with pure JVM contract tests first.
- AtomicCommitBoundary.commit(CommitOperation): Boolean is preserved. A false compare/write result remains mapped by the existing engine to StorageFailure; app must not relabel it as a typed conflict. Expected-context checks provide deterministic Conflict before commit.
- 004 persistence retains Target definitions for permanent deletion and applies only the core-produced deletion diff. No standalone Target delete, Room DAO exposure, migration, or schema version change is planned.
- Existing lifecycle logic can terminalize open Duration events, reset State generations, orphan bindings, and invalidate Undo metadata. App displays returned effects but does not reproduce those rules.
- Historical Event snapshots are stored in Event rows and must not be regenerated from edited live definitions.

## Project Structure

Documentation:

    specs/005-record-target-management/
    ├── plan.md
    ├── research.md
    ├── data-model.md
    ├── quickstart.md
    ├── contracts/
    │   ├── management-application.md
    │   └── management-ui.md
    └── tasks.md                 # created later by $speckit-tasks

Expected source areas:

    core/src/main/kotlin/io/github/thrhead/taplog/core/
    ├── domain/Definitions.kt
    └── engine/
        ├── DefinitionManagement.kt
        ├── EventEngine.kt
        ├── Commands.kt / Results.kt
        └── DeletionScope.kt
    core/src/test/kotlin/.../core/engine/Management*Test.kt
    data/src/main/kotlin/.../data/persistence/
    ├── application-facing port/adapter
    └── existing RoomLocalPersistence/RoomAtomicCommitBoundary
    app/src/main/java/io/github/thrhead/taplog/
    ├── MainActivity.kt
    ├── management/ManagementApplication.kt
    ├── management/ManagementState.kt
    ├── management/ManagementViewModel.kt
    ├── management/ManagementNavigation.kt
    ├── management/records/
    ├── management/targets/
    └── ui/
    app/src/main/res/values/strings.xml

Structure decision: retain exactly :core → :data → :app; do not add a feature module. :app may depend on public ports exposed by :data, but never on internal DAOs/entities. If current :data visibility prevents this, make the smallest boundary-preserving adapter public rather than widening Room access.

## Dependency-Ordered Implementation Slices

1. Core management contract completion: test-first creation, field validation, behavior/unit locks, revision rules, target creation, link/relink, expected-context conflicts, and exact delete semantics using the existing EventEngine seam.
2. Data application boundary: expose the minimum read/commit adapter needed by app orchestration; prove aggregate round-trip, atomic lifecycle effects, deletion scope, failure behavior, and restart safety by reusing 004 fixtures/tests.
3. Pure app orchestration and result mapping: implement intent-to-core adapters, typed application outcomes, localized reason keys, and tests for every result category and confirmation path without Compose.
4. Management state holders and navigation: add ViewModel/state-holder ownership, loading/empty/error/success state restoration, and management-only navigation rooted in MainActivity.
5. Record flows: active/archived list, create/edit behavior-specific forms, Counter quantity/unit/default handling, archive/unarchive, and permanent-delete preview/confirmation.
6. Target flows: list/create/edit/archive/unarchive; omit standalone permanent deletion.
7. Assignment flows: valid active Target listing, no-Target distinction, link/unlink/relink, direct execution without confirmation, and relationship revision feedback.
8. Critical UI and integration verification: Compose tests for create/edit, archive, assignment, cancellation/confirmation, and deterministic error states; Room/restart tests for orphan preservation; dependency-boundary checks.

## Testing Strategy

- Core JVM first: valid creation for all four behaviors; Counter default 1 and invalid quantities; names/units; post-event behavior/unit lock; allowed name/icon/default changes; monotonic revisions; active/inactive target/link validation; archive/unarchive effects; explicit relink enabling future events without changing historical orphaned bindings; no-Target scope; exact null/non-null deletion impact; confirmation-required and stale-context outcomes.
- App pure JVM: intent mapping without direct persistence mutation; deterministic mapping for every core category/reason; raw exception text excluded; delete cancel/missing confirmation has no commit; feedback failure cannot change Applied.
- ViewModel/state-holder: loading to loaded/empty/error transitions, refresh after mutation, stale conflict retaining newer state, recreation/restart restoring committed state, and no optimistic success before commit.
- Compose/UI: critical flows only—Record create for each behavior, edit lock messaging, archive/unarchive, Target create/edit/archive, assignment filtering, no-Target option, direct unlink/relink, permanent-delete impact and cancel/confirm. Assert stable states/resource keys.
- Data integration reuse: extend 004 repository and Room tests for new aggregate states, not duplicate all persistence coverage. Add restart assertions for definitions, relationships, Event snapshots, orphaned bindings, lifecycle effects, and exact deletion scope. Preserve schema v1 unless implementation evidence reveals a real incompatibility.
- Boundary tests: prove :core has no Android dependency, :app has no DAO/Room references, and core/application semantics are testable without Compose.

## Constitution Re-check After Design

All gates remain Pass. The design introduces no global mutation revision, no new persistence strategy, no standalone Target deletion, no UI-owned domain rule, and no excluded product area. Any discovered incompatibility with 003/004 must stop implementation and be recorded as an upstream contract decision rather than silently redesigned here.

## Complexity Tracking

No violations. The additional app-facing data adapter is a boundary-preserving port required to prevent direct :app persistence access; it does not create a competing persistence API or management engine.
