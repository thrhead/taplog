# Research: Record + Target Management

## Decision: Extend the existing core management seam

Decision: Keep EventEngine as the only transaction-facing domain engine and DefinitionManagement as the pure transformation helper. Add only the missing creation and explicit relink operations required by 005.

Rationale: 003 already owns lifecycle effects, expected-context checks, result categories, relationship revisions, deletion scope, and commit routing. A new management engine would duplicate invariants and violate the feature boundary.

Alternatives considered: An app-owned repository/service with copied rules was rejected because it would make UI/application code authoritative. A second core ManagementEngine was rejected because it would compete with EventEngine and split transaction semantics.

## Decision: Use the existing aggregate/atomic boundary

Decision: :data exposes only a narrow app-facing read/commit adapter backed by the approved local aggregate read. Room entities and DAOs remain internal.

Rationale: 004 defines one local Room aggregate and a compare-and-commit boundary that preserves atomic lifecycle effects and deletion diffs. Direct DAO access from :app would bypass those guarantees.

Alternatives considered: Direct Room/DAO access, a second app repository with its own writes, and a new database were rejected as boundary violations and duplication of 004.

## Decision: Relink is an explicit relationship transition

Decision: Relink an existing pair by setting linked=true and incrementing only the relationship revision. Historical Events and orphaned BindingLifecycle rows remain unchanged.

Rationale: 005 requires future eligibility without reactivating historical bindings. The existing relationship entity already has linked and revision, so no new identity or historical mutation is needed.

Alternatives considered: Creating a replacement relationship row was rejected because the persistence key is the pair. Reactivating bindings was rejected because it rewrites historical lifecycle meaning.

## Decision: Preserve the 004 permanent-deletion contract

Decision: Use DeletionScope.preview/confirm and EventEngine.deleteScope(recordId, targetId?, confirmed). Null target means Record-wide; non-null target means pair scope. Target definitions are retained; standalone Target deletion is unavailable.

Rationale: 004 validates that targets, state groups/scopes, and unrelated rows are retained and only core-provided omissions are persisted.

Alternatives considered: UI-calculated impact, data-layer cascade, and standalone Target deletion were rejected because they broaden or redefine core semantics.

## Decision: Deterministic application result mapping

Decision: Keep core result category and ResultReason as typed application state and map them to stable localized resource keys. Render success only after Applied and never expose raw exception text.

Rationale: The spec requires deterministic outcomes across invalid input, stale context, confirmation, and storage failure. Stable keys make mapping pure-JVM testable and localization-safe.

Alternatives considered: Exception-driven UI messages, boolean success/failure, and optimistic success were rejected because they lose authoritative reason/category or can display false success.

## Decision: Management-only Compose navigation

Decision: Add only management destinations below the existing activity shell: Record list/editor/assignment and Target list/editor, with active/archived filters and permanent-delete confirmation.

Rationale: The feature explicitly excludes Home, Timeline, execution UX, and all external channels. A small graph keeps navigation scope aligned with the roadmap slice.

Alternatives considered: Building Home first, adding a generic dashboard, or creating a full feature-module/navigation framework were rejected as scope expansion.

## Upstream constraints resolved

- :core is Kotlin/JVM and cannot depend on Compose, Android lifecycle, Room, or localized resources.
- AtomicCommitBoundary.commit remains Boolean; compare/write failure cannot be relabeled as a typed race Conflict by app code.
- 004 schema is v1 with no migration edge planned for 005; no schema change is needed for management operations.
- Existing core operations cover edit/archive/unarchive/unlink and deletion, but current source lacks create/relink and app-facing query composition. These are implementation gaps to resolve inside the approved seams, not reasons to redesign upstream contracts.
