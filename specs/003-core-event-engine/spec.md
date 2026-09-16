# Feature Specification: TapLog Core Domain + Event Engine

**Feature Branch**: `003-core-event-engine`

**Created**: 2026-09-16

**Status**: Draft

**Input**: User description: "Create the specification for TapLog Core Domain + Event Engine, scoped to the core domain and channel-independent Event Engine."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Log a shared event (Priority: P1)

When a user performs an action through App, Widget, Quick Settings, NFC, or a
future channel adapter, the channel submits a validated event request. TapLog
interprets that request through one shared domain contract, so the resulting
history has the same meaning regardless of origin.

**Why this priority**: EVENT + TIMESTAMP is the product's fundamental primitive;
inconsistent channel behavior would make the history unreliable. (PRD §§1, 5,
20, 23, 43; architecture constitution Principle I.)

**Independent Test**: Submit equivalent Moment and Counter requests with each
source value and compare the committed Event meaning. Submit two intentional
Counter requests and verify that two Events are created.

**Acceptance Scenarios**:

1. **Given** an active Moment Record, **When** a validated request supplies the
   Record and an occurrence timestamp, **Then** exactly one Event is created
   with that Record, timestamp, behavior, source, and historical snapshot.
2. **Given** an active Counter Record with default quantity 1, **When** the
   user intentionally submits two requests, **Then** two distinct Events are
   created; the Engine does not merge or reject either request as a global
   duplicate.
3. **Given** a request from any supported input channel, **When** the request
   is valid, **Then** the Engine applies the same domain rules and returns the
   same result semantics; channel feedback is outside the Engine.
4. **Given** a request for an archived Record or an unlinked Record–Target
   relationship, **When** it is submitted, **Then** no Event is created and a
   deterministic invalid result identifies the inactive scope.

### User Story 2 - Record durations and mutually exclusive states (Priority: P1)

The user can start and finish a Duration, including an intentional incomplete
end, and can set a State within a State Group. Duration and State rules remain
consistent across every channel.

**Why this priority**: Duration integrity prevents invented history, while State
exclusivity makes the current state trustworthy. (PRD §§7.3–7.4, 32, 43;
§44.1 items 2–4; approved Event Engine contract.)

**Independent Test**: Exercise start, finish, archive/unlink, state replacement,
same-state repetition, equal timestamps, and stale transitions using pure
domain scenarios.

**Acceptance Scenarios**:

1. **Given** no OPEN Duration for a Record and optional Target scope, **When**
   Start Duration is applied, **Then** one OPEN Event is created with a start
   timestamp and no completion timestamp.
2. **Given** an OPEN Duration, **When** Finish or toggle is applied with an
   end timestamp not earlier than its start, **Then** that Event becomes
   COMPLETED and is terminal.
3. **Given** an OPEN Duration, **When** its Record is archived or its
   Record–Target relationship is unlinked, **Then** it becomes INCOMPLETE with
   no invented end timestamp and cannot be reopened by unarchive or ordinary
   edit.
4. **Given** two different Target scopes for one Duration Record, **When** both
   are started, **Then** each scope may have one OPEN Duration independently;
   the no-Target scope is separate from all Target scopes.
5. **Given** a State Group and Target scope, **When** a State is set, **Then**
   it is the sole current State in that scope. **When** the same State is set
   again, **Then** a new historical Event is recorded rather than a no-op.
6. **Given** the current State's Record is archived or its relationship is
   unlinked, **When** lifecycle effects are applied, **Then** only that scope's
   current State is cleared and an older State is not automatically revived.

### User Story 3 - Preserve historical meaning while managing definitions (Priority: P1)

The user can use Records and Targets as reusable definitions without changing
what past Events meant. Normal archive, unarchive, rename, icon changes, and
unlink operations preserve history; only an explicitly confirmed permanent
delete removes the approved history scope.

**Why this priority**: TapLog records real events and must remain trustworthy
after later definition changes. (PRD §§5–6, 12, 28–32, 42–43; constitution
Principle III.)

**Independent Test**: Create Events, rename definitions, archive/unarchive,
unlink a relationship, and perform confirmed hard-delete impact checks. Compare
historical snapshots and retained Event identities before and after each action.

**Acceptance Scenarios**:

1. **Given** an Event created under a named/iconed Record and Target, **When**
   either definition is renamed or its icon changes, **Then** the Event's
   historical name and icon remain unchanged.
2. **Given** an archived Record or Target, **When** it is unarchived, **Then**
   its identity and past Events remain, but archived lifecycle effects such as
   INCOMPLETE Duration, cleared State, and ORPHANED channel binding are not
   automatically reversed.
3. **Given** a Record–Target relationship, **When** it is unlinked, **Then** the
   relationship cannot create new Events, while both definitions and their
   historical Events remain available for history.
4. **Given** an explicitly confirmed permanent delete, **When** the requested
   scope is applied, **Then** only the confirmed definitions, Events, snapshots,
   and related metadata in that scope are permanently removed; unrelated Target
   history remains.

### User Story 4 - Correct safely through deterministic outcomes (Priority: P2)

Channels and later management features can distinguish a committed change from
an invalid request, a request needing confirmation, a stale request, or a failed
commit. A correction cannot silently overwrite newer history.

**Why this priority**: Fast correction is useful only when it does not create a
second, less visible history error. (PRD §§13, 27, 42–43; approved Event Engine
contract.)

**Independent Test**: Run valid, invalid, ambiguous/confirmation-required,
stale-revision, stale-generation, and storage-failure scenarios and assert the
result category and mutation outcome.

**Acceptance Scenarios**:

1. **Given** a valid command, **When** its change is committed, **Then** the
   result is `Applied` and includes affected identity/revision information.
2. **Given** a command with stale expected revision, dataset generation, or
   Duration/State context, **When** it is applied, **Then** the result is
   `Conflict` and newer data is not overwritten.
3. **Given** an ambiguous intent, approximate match, or unconfirmed permanent
   delete, **When** the request reaches the boundary, **Then** the result is
   `NeedsConfirmation` and no domain mutation occurs.
4. **Given** an invariant violation, **When** the command is evaluated, **Then**
   the result is `Invalid` with a stable reason and no partial mutation.
5. **Given** a commit failure, **When** the operation cannot be committed,
   **Then** the result is `StorageFailure` and the user data is treated as
   unchanged; feedback failure after a commit does not change `Applied`.

### Edge Cases

- A Counter quantity of zero, a negative value, a non-finite value, or a value
  that cannot be represented as an exact decimal is rejected.
- A Counter request with a unit different from the Record's established unit is
  rejected; no automatic unit conversion is performed.
- A Duration finish earlier than its start is rejected as invalid; the Engine
  never fabricates a completion time.
- A second OPEN Duration in the same Record + optional Target scope is rejected
  or returned as a conflict according to whether the caller's context is stale;
  it must never create a second OPEN Event.
- A COMPLETED or INCOMPLETE Duration is terminal. Edit, undo, archive reversal,
  or unarchive cannot make it OPEN.
- Equal Event timestamps are ordered deterministically by their assigned
  sequence, so State currentness and history do not depend on incidental order.
- A State operation for a missing State Group, inactive Record, inactive Target,
  or unlinked relationship is invalid and does not change the current State.
- Record behavior and Counter unit become immutable after the first Event. A
  default Counter quantity may be changed without rewriting prior Events.
- A no-Target scope is distinct from every Target scope for Duration and State;
  it must not collide with a real Target identity.
- A stale UndoReceipt conflicts when the affected Event revision, required
  lifecycle context, or dataset generation has changed; Undo is correction aid,
  not an audit log.
- A source/channel may suppress its own physical or interaction duplicate, but
  the Event Engine must accept two valid requests that arrive as intentional
  consecutive actions.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The domain MUST model `Record` as the reusable definition of what
  is tracked, distinct from the Events that record occurrences. (PRD §§5.1, 8.)
- **FR-002**: The domain MUST model an independent `Target` with its own name and
  icon; one Target MAY be related to multiple Records. (PRD §§5.3, 6, 43.)
- **FR-003**: A Record–Target relationship MUST be optional, many-to-many in
  effect, and unlinkable without archiving either side; an unlinked pair MUST
  not be eligible for new Events. (PRD §6; §44.1 item 5.)
- **FR-004**: The domain MUST support exactly four behavior types: Moment,
  Counter, Duration, and State. Each Event MUST carry its behavior meaning and
  only the behavior-specific data valid for that type. (PRD §7; §43 Behavior.)
- **FR-005**: Every Event MUST contain an immutable event identity, Record
  reference, optional Target reference, behavior, occurrence timestamp, source,
  and the historical definition snapshot needed to preserve its meaning. (PRD
  §§5.2, 12, 43; architecture data model.)
- **FR-006**: Event timestamp semantics MUST distinguish the time the real event
  occurred from the time the Event was accepted/created and later modified. If
  the user supplies no occurrence time, the input acceptance time MUST be used;
  the Engine MUST not replace an explicit occurrence time with processing time.
  (PRD §§5.2, 9, 43; approved data model.)
- **FR-007**: The Event source MUST identify the originating input category
  needed for history and channel feedback (App, Widget, Quick Settings, NFC, or
  Natural Language), while source MUST NOT alter the domain meaning or invoke
  channel-specific behavior in the Engine. (PRD §§20, 23; constitution Principle I.)
- **FR-008**: Moment commands MUST create one timestamped Event for one valid
  occurrence and MUST not require quantity or Duration/State fields. (PRD §7.1.)
- **FR-009**: Counter Events MUST accept a positive, finite, exact integer or
  decimal quantity. If quantity is omitted, the Record's default quantity MUST
  be used; the default is initially 1. A Record MAY have one unit, and that unit
  becomes fixed after the first Event; automatic conversion is prohibited. (PRD
  §§7.2, 44.1 item 8; architecture data model.)
- **FR-010**: The Record's default Counter quantity MUST be changeable for
  future requests without rewriting prior Event quantities. An explicitly
  supplied quantity MUST override the default for that Event only. (PRD §44.1
  item 8.)
- **FR-011**: Duration Events MUST represent `OPEN`, `COMPLETED`, and
  `INCOMPLETE`. OPEN requires a start timestamp and no end/reason; COMPLETED
  requires an end timestamp at or after start; INCOMPLETE requires no end
  timestamp and a reason, and is terminal. (PRD §32; architecture data model.)
- **FR-012**: The Engine MUST allow no more than one OPEN Duration per Record +
  optional Target scope, with the no-Target scope treated separately from each
  Target scope. The Engine MUST allow different Target scopes to be open
  concurrently. (PRD §44.1 item 2; event-engine contract.)
- **FR-013**: Duration start, finish, and toggle commands MUST use supplied event
  timestamps; a finish earlier than start MUST return `Invalid` and MUST NOT
  create an invented duration. A normal close/restart flow MUST NOT turn a
  Duration INCOMPLETE. (PRD §§32, 44.1 items 2–3.)
- **FR-014**: Archive or unlink effects MUST transition affected OPEN Durations
  to INCOMPLETE without an end timestamp, invalidate the affected State scope,
  and preserve the historical Events. Unarchive MUST NOT reopen terminal
  Durations or restore cleared States. (PRD §§28–32; §44.1 items 4–5.)
- **FR-015**: State Events MUST belong to a State Group and optional Target
  scope. Each group + scope MUST have one current State at most; currentness is
  determined by occurrence timestamp and deterministic tie-break sequence within
  the active lifecycle generation. (PRD §§7.4, 44.1 item 4; architecture data model.)
- **FR-016**: Setting the same State again MUST create a new historical Event.
  Archive/unlink reset MUST advance the affected scope's lifecycle generation;
  older generation Events remain historical but MUST NOT become current again.
  (PRD §44.1 item 4; architecture data model.)
- **FR-017**: After the first Event for a Record, its behavior and Counter unit
  MUST remain fixed. Event snapshots MUST preserve the Record/Target name, icon,
  behavior, and unit as they were at Event creation. (PRD §44.1 item 7–8;
  architecture data model.)
- **FR-018**: Normal archive, unarchive, rename, icon change, and relationship
  unlink MUST preserve Event history. Permanently deleting history MUST require
  an explicit confirmation that identifies its impact and MUST remove only the
  confirmed scope, including related snapshots and domain metadata. (PRD §§28–31,
  43; constitution Principle III.)
- **FR-019**: Archive/unlink effects that affect channel bindings MUST expose the
  binding as `ORPHANED` with an immutable binding identity, affected
  Record/Target scope, revision, and last-known Record/Target name-and-icon
  display snapshot. The Engine exposes this as a transaction-facing lifecycle
  effect; later channel layers own rebind decisions and post-commit feedback.
  This feature does not implement NFC, Widget, or Quick Settings behavior.
  (PRD §§18, 20, 23; §44.1 items 5–6.)
- **FR-020**: The Event Engine MUST accept only validated domain/application
  commands such as LogMoment, AddCounter, StartDuration, FinishDuration,
  ToggleDuration, SetState, CreateRecordAndLog, EditEvent, DeleteEvent, and Undo.
  It MUST not accept UI objects, Android intents, or natural-language text as
  domain commands. (Approved Event Engine contract; PRD §§9, 20, 23–24.)
- **FR-021**: `CreateRecordAndLog` for a resolved completed action MUST create the
  Record and its first Event as one atomic domain operation; a name-only or
  otherwise ambiguous request MUST stop for confirmation and MUST not guess.
  Natural-language parsing itself is outside this feature. (PRD §§9, 24, 26–27.)
- **FR-022**: Duplicate suppression MUST NOT be a global Event Engine rule. The
  Engine MUST preserve deliberate consecutive Counter requests, while each
  input channel MAY apply its own interaction/physical-read handling before
  submitting a command. (PRD §20; constitution Principle IV.)
- **FR-023**: Results MUST be deterministic and limited to `Applied`,
  `NeedsConfirmation`, `Conflict`, `Invalid`, and `StorageFailure`. `Applied`
  MUST be returned only after commit; other outcomes MUST not report a committed
  mutation. (Approved Event Engine contract.)
- **FR-024**: `Invalid` and `Conflict` results MUST identify a stable reason such
  as inactive scope, behavior mismatch, invalid quantity, negative duration,
  duplicate OPEN scope, terminal Duration, stale revision, stale dataset
  generation, or stale lifecycle context. `NeedsConfirmation` MUST identify
  the confirmation required, including ambiguous intent or permanent-delete
  impact. (Approved Event Engine contract; PRD §§27, 31.)
- **FR-025**: Commands MAY carry expected Event revision and dataset generation.
  When supplied, the Engine MUST reject stale values as `Conflict` rather than
  overwrite newer history. Undo MUST use the same revision/context checks and
  MUST not be treated as an audit record. (PRD §13; approved Event Engine
  contract; architecture data model.)
- **FR-026**: A committed Event MUST remain committed if post-commit channel
  feedback fails. The transaction boundary or caller owns post-commit feedback;
  the Event Engine MUST only preserve and return the already committed
  `Applied` result and MUST NOT invoke, retry, or otherwise own feedback.
  Notifications, platform bindings, and channel duplicate handling are outside
  the Event Engine boundary. (PRD §§13, 20, 23; approved architecture
  plan/contracts.)
- **FR-027**: The boundary MUST be usable offline and without AI, an account, or
  a server connection for core Event creation. AI and parser behavior are not
  part of this feature. (PRD §§25, 34, 37, 43; constitution Principle II.)

### Traceability

| Specification area | Canonical source |
|---|---|
| Event-first primitive and shared channels | PRD §§1, 5, 20, 23, 42–43; constitution Principle I |
| Record, Target, relationship | PRD §§5–6, 8–9, 28–31, 43; architecture `data-model.md` |
| Four behaviors and quantity/unit rules | PRD §§7, 32, 44.1 items 2–4, 8; architecture `data-model.md` |
| Timestamp, snapshots, revisions, generations | PRD §§5, 12–13, 32, 44.1 item 7; architecture `data-model.md` |
| Archive/unlink/delete effects | PRD §§18, 28–32, 43, 44.1 items 1, 4–6; architecture `event-engine.md` |
| Commands, results, atomic outcomes, Undo | Approved architecture `contracts/event-engine.md` |
| Scope and implementation sequencing | Approved architecture `implementation-roadmap.md`, Slice 2 and Slice 5 |

### Key Entities

- **Record**: Reusable definition of what the user tracks; has identity,
  display metadata, behavior, lifecycle, optional Counter unit/default quantity,
  and optional State Group membership.
- **Target**: Independent person, object, place, or other entity associated
  with Events; reusable across Records and separately archivable.
- **RecordTarget**: The optional relationship that gives a Record a Target
  context; unlinking ends eligibility for new Events without deleting history.
- **State Group**: Set of State Records whose current values are mutually
  exclusive within a Target scope.
- **Event**: Immutable-identity occurrence of a Record at an occurrence time,
  with behavior-specific data, source, revisions, and historical snapshot.
- **Duration state**: The lifecycle value and timestamps that distinguish an
  open, completed, or incomplete Duration Event.
- **State scope/generation**: The Target-scoped lifecycle boundary that prevents
  cleared historical State Events from becoming current again.
- **Event command**: A validated request to create or change domain history,
  independent of its input channel.
- **Engine result**: Deterministic applied, confirmation, conflict, invalid, or
  storage-failure outcome consumed by the caller.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: In 100% of equivalent cross-channel acceptance tests, a valid
  command produces the same Event behavior, timestamp semantics, quantity/unit
  meaning, and historical snapshot regardless of source.
- **SC-002**: In 100% of intentional consecutive Counter tests, each accepted
  request produces a separate Event; no valid request is removed by a global
  duplicate rule.
- **SC-003**: In 100% of tested Record + optional Target scopes, the system never
  contains more than one OPEN Duration, while two different Target scopes can
  each contain one.
- **SC-004**: In 100% of Duration integrity tests, no COMPLETED Event has an end
  before its start, and no INCOMPLETE Event has an invented end timestamp or can
  return to OPEN through unarchive, edit, or Undo.
- **SC-005**: In 100% of archive/unlink/history tests, prior Events and their
  historical display meaning remain available unless an explicitly confirmed
  permanent-delete scope includes them.
- **SC-006**: In 100% of stale revision, stale generation, invalid-input, and
  commit-failure tests, the result is deterministic and no newer or unrelated
  history is overwritten or partially changed.
- **SC-007**: A pure domain test suite can exercise all four behaviors and all
  result categories without requiring a UI, input channel, network, AI, or
  persistence technology.
- **SC-008**: A valid core Event request completes without waiting for network or
  AI availability, and the Engine's meaning is unchanged when those services
  are absent.

## Assumptions

- The approved architecture's UTC epoch-millisecond timestamps, immutable UUID
  identities, monotonic revisions, and deterministic sequence tie-break are
  domain semantics; persistence representation is a later feature.
- A source value is retained only to explain origin and support caller feedback;
  it does not authorize a different Event meaning.
- Archive, Record/Target management, local persistence, parser, backup/import,
  statistics, monetization, NFC, Widget, Quick Settings, and UI are separate
  features. This specification defines only the domain effects and contracts
  that those features must honor.
- The channel caller resolves user intent and natural-language ambiguity before
  submitting a command. This feature does not recognize Turkish text, perform
  fuzzy matching, or integrate AI.
- “Atomic” means the user-visible domain operation is all-or-nothing; the
  persistence mechanism that realizes it is specified by the Local Persistence
  feature.
- Permanent deletion is an exceptional, explicit management action and is not
  part of ordinary Event creation.

## Out of Scope

- Room, SQLite, database schema, migrations, repositories, or persistence code.
- NFC reading/writing/pairing, Widget, Quick Settings, notifications, Android
  lifecycle/background execution, and channel-specific duplicate suppression.
- UI screens, interaction layouts, user-facing feedback implementation, and
  natural-language parser or AI implementation.
- Backup/import, statistics, monetization/entitlement, cloud services, and
  integrations.
