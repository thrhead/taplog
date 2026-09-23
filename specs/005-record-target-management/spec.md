# Feature Specification: TapLog Record + Target Management

**Feature Branch**: `005-record-target-management`

**Created**: 2026-09-23

**Status**: Draft

**Input**: User description: "Create the specification for TapLog Record + Target Management, building on the approved Core Event Engine and local persistence contracts."

## Scope and Traceability

This feature specifies the user-visible and application-facing management of
Records, Targets, and their relationships. It builds on, and does not replace,
the approved contracts in:

- Product requirements document: `docs/product/TapLog_V1_PRD.md`, especially
  §§5–9, 26–32, 42–45.
- Approved architecture: `specs/001-v1-architecture/`, especially the
  implementation roadmap and module-boundary decisions.
- Core Event Engine: `specs/003-core-event-engine/`, especially
  `contracts/event-engine.md`, `data-model.md`, and definition/lifecycle
  management requirements.
- Local persistence: the approved 004-local-persistence contracts. The
  approved artifacts are present and authoritative for this feature. Planning
  and implementation MUST use that contract and MUST NOT invent a replacement
  persistence API.

The fundamental product primitive remains **EVENT + TIMESTAMP**. A Record is a
definition, not an Event. Ordinary edit, archive, unarchive, and unlink
operations preserve historical Events and their creation-time snapshots.

This feature explicitly excludes the Home dashboard, Timeline/history UI,
Duration or State execution UX, NFC, Widget, Quick Settings, natural-language
parsing, backup/import/export, statistics, Free/Pro enforcement, AI, and
sync/cloud/server behavior.

## Clarifications

### Session 2026-09-23

- Q: Should 005 exclude standalone permanent deletion of a Target, allowing only Target archive/unarchive and Record or Record–Target-scope deletion exactly as defined by core `DeleteScope`/`DeleteImpact`? → A: A — Exclude standalone Target permanent deletion; support Target archive/unarchive only, while honoring core deletion scopes.
- Q: Which management actions should require an explicit confirmation before they are applied? → A: A — Confirm permanent deletion only; apply archive, unarchive, link, and unlink directly.
- Q: Should management use cases live in `:app` as thin orchestration that delegates to existing `:core` management functions and the approved persistence boundary? → A: A — `:app` orchestrates user intents; `:core` owns semantics; `:data` owns persistence.
- Q: Should the management UI map each core result category and stable reason to a deterministic user-facing state, while keeping the exact core reason authoritative and avoiding raw exception text? → A: A — Preserve category/reason keys and map them to stable localized UI states.
- Q: When a previously unlinked Record–Target pair is explicitly linked again, should the pair become eligible for new Events while existing orphaned historical bindings remain orphaned? → A: A — Re-enable the pair for new Events with a higher relationship revision, but preserve existing orphaned bindings and historical Events unchanged.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Manage Records (Priority: P1)

As a user, I want to create and maintain the definitions of the things I
track, so that later Event actions have a reliable Record context.

**Why this priority**: Records are the reusable definitions required by every
Event-creation channel and are the primary management object in this feature.

**Independent Test**: Create a Record for each supported behavior, edit fields
before and after Events exist, archive and unarchive it, and verify active
eligibility, revisions, deterministic results, and preserved history.

**Acceptance Scenarios**:

1. **Given** no matching Record, **When** the user submits a valid definition,
   **Then** an active Record with a unique identity and revision is created.
2. **Given** a new Record, **When** the user selects Moment, Counter, Duration,
   or State, **Then** only fields valid for that behavior are shown and the
   definition is validated by the core contract.
3. **Given** a Counter Record, **When** the user changes its default quantity,
   **Then** future Counter requests use the new default while existing Events
   retain their quantities.
4. **Given** a Record with Events, **When** the user changes its name or icon,
   **Then** the Record revision advances and existing Event snapshots do not
   change.
5. **Given** a Record with Events, **When** the user attempts to change its
   behavior or Counter unit, **Then** the operation is rejected deterministically
   and no data is changed.
6. **Given** an active Record, **When** the user archives it, **Then** it leaves
   active management and creation choices, relevant lifecycle effects are
   applied by core, and its identity/history remain available in archive views.
7. **Given** an archived Record, **When** the user unarchives it, **Then**
   it becomes active with the same identity, preserved history, and a new
   revision; terminal Duration/State effects are not reversed.
8. **Given** an archived or otherwise deletable Record, **When** the user opens
   permanent deletion, **Then** the impact is previewed and no mutation occurs
   until the explicit irreversible confirmation is accepted.

---

### User Story 2 - Manage Targets (Priority: P1)

As a user, I want to create and maintain reusable Targets independently from
Records, so that the same person, object, or place can provide context for
multiple Records.

**Why this priority**: Targets are independent V1 entities and are needed to
express the PRD's reusable Record–Target model.

**Independent Test**: Create, edit, archive, and unarchive a Target; verify
identity, revisions, historical display snapshots, and orphaning effects; and
verify that standalone permanent Target deletion is unavailable and cannot
mutate persisted data.

**Acceptance Scenarios**:

1. **Given** no matching Target, **When** the user submits a valid name and
   optional icon, **Then** an active Target is created with a unique identity.
2. **Given** a Target used by one or more Events, **When** the user edits its
   name or icon, **Then** the Target revision advances and historical snapshots
   remain unchanged.
3. **Given** an active Target, **When** the user archives it, **Then** it is
   excluded from active Target choices and its relationships/history are kept.
4. **Given** an archived Target, **When** the user unarchives it, **Then**
   it returns to active eligibility without automatically rebinding orphaned
   channel bindings or reversing terminal lifecycle effects.
5. **Given** a Record–Target scope with related history, **When** permanent
   deletion is requested, **Then** only the scope permitted by the existing
   core deletion contract can proceed after explicit confirmation; standalone
   Target deletion is unavailable and cannot mutate data.

---

### User Story 3 - Assign Targets to Records (Priority: P1)

As a user, I want to link and unlink Targets from a Record, so that I can use
the same Record with valid contexts without creating duplicate Records.

**Why this priority**: Record–Target relationships control which contextual
Events may be created and must preserve the historical meaning of prior Events.

**Independent Test**: Link multiple active Targets, list valid Targets for a
Record, unlink one relationship, and verify that only the selected creation
context is disabled while both definitions and historical Events remain.

**Acceptance Scenarios**:

1. **Given** an active Record and active Target, **When** the user links them,
   **Then** one active relationship with its own identity/revision becomes
   eligible for new contextual Event requests.
2. **Given** a Record with linked Targets, **When** the user opens assignment,
   **Then** only valid, active, linked-eligible Targets are offered and a
   no-Target option remains distinct.
3. **Given** a linked Record–Target pair, **When** the user unlinks it,
   **Then** the relationship becomes unavailable for new Events, its revision
   advances, affected core lifecycle effects are applied atomically, and both
   definitions plus historical context remain.
4. **Given** a prior Event for a linked pair, **When** the pair is unlinked or
   either definition is archived, **Then** the Event's Record/Target identity
   and creation-time snapshot remain readable and are not silently rewritten.

---

### User Story 4 - Recover and Understand Management Outcomes (Priority: P2)


As a user, I want management actions to be restart-safe and understandable,
so that I can trust what happened even after errors or reopening the app.

**Independent Test**: Exercise loading, empty, invalid, conflict, storage
failure, confirmation, and restart scenarios against deterministic application
results and persisted state.

**Acceptance Scenarios**:

1. **Given** a management action is in progress, **When** the app is restarted,
   **Then** committed state is restored locally and no half-applied action is
     shown.
2. **Given** stale revision or dataset context, **When** the user submits an
   outdated edit/archive/link/unlink/delete action, **Then** the user receives a
   conflict result and newer state is not overwritten.
3. **Given** a persistence failure, **When** a management action is submitted,
   **Then** the application reports failure, treats the state as unchanged, and
   does not display a false success.

### Edge Cases

- A Record name or Target name that is empty or otherwise invalid is rejected
  before mutation with a stable invalid result.
- A Record cannot be linked to an inactive or nonexistent Target, and an
  archived/unlinked pair cannot be used to create a new Event.
- A Record may have no Target, one Target, or multiple Targets. The no-Target
  scope must not collide with a real Target identity.
- A State Record requires its already-defined State Group contract; management
  UI must not invent State execution semantics.
- A Counter default quantity must be positive and valid. Zero, negative, or
  invalid values are rejected; an explicit Event quantity remains per-request.
- Archiving or unlinking an active Duration scope can make its OPEN Event
  INCOMPLETE without an invented end timestamp; this is a core lifecycle effect,
  not UI behavior.
- Unarchive must not reopen terminal Duration Events, restore cleared State,
  or automatically reactivate an orphaned binding.
- Permanent deletion of a Record or Record–Target scope must be limited to the
  exact impact preview and confirmation scope supplied by core. Standalone
  permanent Target deletion is not available in this feature.
- Empty active and archived lists, loading, invalid input, stale conflict,
  storage failure, and successful completion each have distinct user-visible
  states.

## Requirements *(mandatory)*


### Functional Requirements

- **FR-001**: The application MUST provide management entry points for active
  and archived Records and Targets, including empty, loading, and error states.
- **FR-002**: Users MUST be able to create a Record with a name, optional icon,
  one of Moment, Counter, Duration, or State behavior, and only the fields valid
  for that behavior.
- **FR-003**: The application MUST support Counter quantity configuration with
  one optional unit and a default quantity that starts at 1 when omitted.
- **FR-004**: The application MUST allow a Counter default quantity to change
  for future Event requests while preserving every existing Event quantity.
- **FR-005**: The application MUST allow Record definition edits permitted by
  the core contract and MUST surface deterministic rejection for invalid edits.
- **FR-006**: After the first Event, behavior and Counter unit MUST be locked as
  required by core; the application MUST not bypass or duplicate this rule.
- **FR-007**: Record and Target mutations MUST advance only the affected
  entity's monotonic revision according to core; no global mutationRevision may
  be introduced.
- **FR-008**: Users MUST be able to archive and unarchive Records and Targets.
  Archive is reversible lifecycle state, not permanent deletion.
- **FR-009**: Archived Records MUST be absent from active Event-creation choices;
  archived Targets MUST be absent from active assignment choices.
- **FR-010**: Unarchive MUST preserve identity and history but MUST NOT reverse
  terminal Duration/State effects or automatically reactivate orphaned bindings.
- **FR-011**: The application MUST provide an explicit permanent-delete flow
  only where the existing core contract permits it. Standalone permanent
  deletion of a Target is excluded because the current core contract provides
  no standalone Target-delete command.
- **FR-012**: Permanent delete MUST first show the exact core-provided impact,
  clearly state irreversibility, require explicit confirmation, and perform no
  mutation if the user cancels or confirmation is absent. Archive, unarchive,
  link, and unlink do not require a confirmation step.
- **FR-013**: Permanent delete MUST remove only the confirmed core scope,
  including associated history/snapshots and related metadata where core says so;
  unrelated Target and no-Target history MUST remain. A Target may be archived
  or unarchived, but cannot be permanently deleted as a standalone definition
  in this feature.
- **FR-014**: The application MUST preserve historical Event identities and
  creation-time Record/Target display snapshots through ordinary edits,
  archive, unarchive, and unlink.
- **FR-015**: Users MUST be able to link an active Target to an active Record,
  unlink the relationship, explicitly relink a previously unlinked pair, and
  view valid Targets for a Record. Explicit relinking re-enables the pair for
  new Events and advances its relationship revision.
- **FR-016**: A Record–Target relationship MUST have its own lifecycle/revision
  semantics from core. Unlink MUST block new Events for that pair without
  deleting either definition or historical Events. Explicit relinking MUST NOT
  reactivate existing orphaned bindings or rewrite historical Events.
- **FR-017**: Record–Target assignment MUST retain a distinct no-Target option;
  it MUST NOT be represented as a real Target or silently converted to one.
- **FR-018**: Archive/unlink operations MUST expose and persist the core's
  lifecycle effects, including Duration terminalization, State reset, orphaned
  binding snapshots, and Undo invalidation, without reimplementing those rules
  in UI/application code.
- **FR-019**: The :app layer MUST own user-facing navigation, form state,
  use-case orchestration, confirmation presentation, loading/error rendering,
  and mapping of user intent to approved core operations; it MUST not define
  domain invariants or directly mutate persisted state.
- **FR-020**: The :core layer MUST remain Android/framework independent and MUST
  remain the sole authority for Record, Target, relationship, Event, lifecycle,
  revision, snapshot, deletion-scope, and result semantics.
- **FR-021**: The :data layer MUST own local persistence and MUST use the approved
  004-local-persistence read/write and AtomicCommitBoundary contracts. This
  feature MUST NOT add a competing persistence strategy or schema contract.
- **FR-022**: Management operations MUST use the existing core
  management functions, commands/results, and persistence transaction boundary;
  this feature MUST NOT add a competing management engine or persistence API.
- **FR-023**: Every mutating operation MUST resolve to deterministic Applied,
  NeedsConfirmation, Conflict, Invalid, or StorageFailure handling as defined by
  core. The application MUST preserve the core result category and stable
  `ResultReason` as the decision key and map it to user-facing localized copy;
  it MUST NOT display raw exception text as the management result. Applied is
  shown only after commit; feedback failure cannot undo commit.
- **FR-024**: Pre-commit command-level detection of a stale entity revision,
  relationship revision, lifecycle context, or dataset generation MUST result
  in Conflict without overwriting newer state. `AtomicCommitBoundary.commit()`
  remains Boolean: a commit-time stale comparison or rejection returns `false`,
  and EventEngine maps that `false` result to StorageFailure. This feature MUST
  NOT introduce typed commit-conflict semantics.
- **FR-025**: Management MUST be local-first: creating, editing, assigning,
  archiving, unarchiving, and permitted deletion require no account, network,
  server, or AI availability.
- **FR-026**: Committed management state MUST survive process death and restart;
  interrupted operations MUST not surface as partially committed state.
- **FR-027**: Management UI MUST include only the necessary flows: Record list,
  create/edit Record, Target list, create/edit Target, assignment, archive and
  unarchive actions, permanent-delete confirmation, and relevant
  empty/loading/error states.
- **FR-028**: This feature MUST NOT add Home dashboard behavior beyond
  management navigation, Timeline UI, Duration/State execution UX, channel
  integrations, parser, backup/import/export, statistics, monetization, AI, or
  cloud/sync behavior.

### Key Entities

- **Record**: A reusable definition with immutable identity, name, icon, behavior,
  lifecycle, optional Counter unit/default quantity, optional State Group, and
  monotonic revision.
- **Target**: An independent reusable person/object/place context with name,
  icon, lifecycle, identity, and revision; it may relate to many Records.
- **Record–Target relationship**: The link and its own revision/lifecycle state;
  unlink removes creation eligibility for the pair but preserves both sides and
  history.
- **Event**: The fundamental occurrence, with Record/Target references,
  behavior-specific payload, timestamps, and immutable creation-time snapshot.
- **Lifecycle effect**: Core-provided consequences of archive/unlink, including
  Duration, State, binding, and Undo effects.
- **Deletion impact/confirmation**: Core-provided exact scope and explicit user
  confirmation required for irreversible deletion.
- **Management result**: The deterministic outcome consumed by the application
  and rendered as success, confirmation, conflict, invalid, or storage error.

## Success Criteria *(mandatory)*


### Measurable Outcomes

- **SC-001**: In 100% of acceptance scenarios, a user can create, edit, archive,
  unarchive, and (where permitted) permanently delete a Record or Target with a
  visible deterministic outcome and no hidden mutation.
- **SC-002**: In 100% of tested ordinary edit/archive/unarchive/unlink cases,
  all prior Event identities and creation-time snapshots remain unchanged.
- **SC-003**: In 100% of tested post-first-Event Record edits, behavior and
  Counter unit changes are rejected, while permitted name/icon/default-quantity
  changes follow the core revision rules.
- **SC-004**: In 100% of tested relationship cases, a Record can expose exactly
  its valid active Targets, unlink disables only the selected pair, and the
  distinct no-Target scope remains available when valid.
- **SC-005**: In 100% of permanent-delete tests, cancellation, missing
  confirmation, stale context, and persistence failure leave persisted state
  unchanged; confirmed deletion removes no data outside the core impact scope.
- **SC-006**: At least 95% of representative users can complete Record creation
  and Record–Target assignment without leaving the management flow or receiving
  an unexplained error.
- **SC-007**: After process restart, 100% of committed management operations are
  recoverable locally and no operation appears partially applied.
- **SC-008**: The management flows remain usable with network and AI unavailable
  in 100% of offline acceptance tests.

## Assumptions


- The user is the sole local actor in V1; no account, roles, or remote
  collaboration model is introduced.
- Names and icons are the editable display definition fields unless the approved
  core contract states otherwise; no additional metadata is invented here.
- Record creation and editing do not create an Event unless an already-approved
  core command explicitly requests the atomic Record-plus-first-Event path.
- The approved 004-local-persistence artifacts are present and are a
  prerequisite contract for planning and implementation.
- Core remains the authority for all behavior locks, lifecycle effects,
  deletion scope, revision checks, and result categories.
- Management screens may provide navigation to future Home/Timeline areas but
  do not implement those areas.

## Traceability Matrix

| Requirement group | Canonical source | Constraint carried into this feature |
|---|---|---|
| Record, Target, relationship identities and reuse | PRD §§5–6, 43; 001 architecture; 003 `data-model.md` | Definitions are independent; Target may serve many Records; no-Target is distinct. |
| Four behaviors and Counter configuration | PRD §§7–8, 44.1(8); 003 core contract | Moment/Counter/Duration/State are selected at definition time; Counter quantity/unit rules remain core-owned. |
| Snapshot/history preservation | Constitution III; PRD §§12, 28–32, 42; 003 §§US3/data model | Ordinary management never rewrites historical Event snapshots. |
| Lifecycle and orphaning | PRD §§18, 28–32, 43, 44.1(4–6); 003 `event-engine.md` | Archive/unlink effects, orphan status, terminal Duration, State reset, and no automatic rebind remain unchanged. |
| Hard delete | PRD §§31, 43, 44.1(1); 003 deletion scope | Explicit impact preview and irreversible confirmation; exact confirmed scope only. |
| Commands/results/atomicity | 003 `contracts/event-engine.md` | Application maps intent to existing core operations and renders deterministic results. |
| Persistence boundary | 001 roadmap; approved 004-local-persistence contract | `:data` owns local persistence and atomic commit; no competing contract is created. |
| Local-first and exclusions | PRD §§34, 40–42; constitution I–V | No network/account/AI dependency; excluded product areas stay out of scope. |

## Out of Scope

- Home dashboard beyond management navigation.
- Timeline or Event-history UI, including Event correction UI.
- Duration execution UX and State execution UX.
- NFC, Widget, Quick Settings, Natural Language parser, and AI.
- Backup/import/export, statistics, Free/Pro enforcement, monetization.
- Sync, cloud, server, account, sharing, or remote collaboration.
- Any change to the approved Core Event Engine or local persistence contracts.
