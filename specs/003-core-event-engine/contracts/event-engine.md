# Event Engine Contract

The Event Engine is the channel-independent boundary between validated input
intent and committed TapLog domain history. It contains no Android UI, channel
objects, Android intents, raw NFC data, or natural-language parsing.

## Request flow

```text
channel adapter validates interaction/intent
        ↓
domain command + source + optional expected context
        ↓
Event Engine evaluates current domain state and invariants
        ↓
transaction-facing commit boundary
        ↓
Applied result, then channel-owned feedback
```

The caller may construct a command outside the commit operation. The Engine
re-evaluates current state when applying it. A channel may suppress a physical or
interaction duplicate before command creation, but the Engine never applies a
global debounce.

## Source representation

`source` is a closed origin category sufficient for history and caller feedback:
`APP`, `WIDGET`, `QUICK_SETTINGS`, `NFC`, or `NATURAL_LANGUAGE`. It is metadata,
not a behavior selector. Channel-specific identifiers may be carried separately
as opaque caller context when needed for feedback, but they do not change Event
meaning or become domain Record/Target identity.

## Commands

- `LogMoment(recordId, targetId?, occurredAt?, source, expectedContext?)`
- `AddCounter(recordId, targetId?, quantity?, occurredAt?, source,
  expectedContext?)`
- `StartDuration(recordId, targetId?, occurredAt?, source, expectedContext?)`
- `FinishDuration(openEventId, endedAt, source, expectedContext?)`
- `ToggleDuration(recordId, targetId?, occurredAt?, source, expectedContext?)`
- `SetState(recordId, targetId?, occurredAt?, source, expectedContext?)`
- `CreateRecordAndLog(resolvedRecord, firstEvent, source,
  expectedContext?)`
- `EditEvent(eventId, permittedChanges, source, expectedContext)`
- `DeleteEvent(eventId, source, expectedContext)`
- `Undo(receiptId, source, expectedContext)`

`occurredAt?` defaults to input acceptance time when omitted. Commands carry
validated values and may include expected Event revision, dataset generation,
scope generation, or other lifecycle context. They do not carry UI state or raw
natural-language text.

## Result contract

Every command produces exactly one result category:

| Result | Meaning | Mutation guarantee |
|---|---|---|
| `Applied` | Event or lifecycle change committed; affected ids/revisions returned | Commit completed; feedback failure cannot undo it |
| `NeedsConfirmation` | Ambiguous intent/match, permanent-delete impact, or other required confirmation | No domain mutation |
| `Conflict` | Expected revision, dataset generation, Duration, State, or scope context is stale | Newer state is preserved; no overwrite |
| `Invalid` | Current request violates a domain invariant | No partial mutation |
| `StorageFailure` | Commit boundary could not complete | User data is treated as unchanged |

Stable reason codes accompany non-Applied results. Minimum reasons include
`INACTIVE_SCOPE`, `UNLINKED_RELATIONSHIP`, `BEHAVIOR_MISMATCH`,
`INVALID_QUANTITY`, `UNIT_MISMATCH`, `NEGATIVE_DURATION`, `OPEN_DURATION_EXISTS`,
`TERMINAL_DURATION`, `MISSING_STATE_GROUP`, `STALE_REVISION`,
`STALE_DATASET_GENERATION`, `STALE_SCOPE_CONTEXT`,
`CONFIRMATION_REQUIRED`, and `PERMANENT_DELETE_IMPACT`.

## Transaction-facing semantics

- Moment/Counter/Duration/State Event creation commits all related Event and
  snapshot data as one operation.
- `CreateRecordAndLog` commits the new Record and first Event together.
- Archive/unlink applies definition/relationship lifecycle, Duration terminal
  transition, State generation reset, binding orphan effect, and related Undo
  invalidation together.
- Hard delete first returns impact requiring confirmation; after confirmation,
  the confirmed deletion scope commits together.
- No non-Applied result may claim a committed Event. The persistence adapter's
  implementation and rollback mechanism belong to the Local Persistence slice.

## Binding lifecycle contract

Channel bindings are transaction-facing metadata, not Event Engine-owned channel
behavior. A binding has an immutable `bindingId`, an immutable Record/Target
scope reference, an active/orphaned lifecycle state, a revision, and a
last-known display snapshot containing the bound Record/Target identity, name,
and icon. Archive or unlink atomically changes the affected binding to
`ORPHANED`, retains that last-known snapshot, and invalidates related Undo
receipts. Unarchive does not reactivate or automatically rebind it. The core
Engine emits this lifecycle effect through the transaction boundary; a later
channel slice owns rebind decisions and channel feedback.

## Duration and State commands

- Starting fails if the scoped Record is inactive/unlinked or already has an
  OPEN Duration.
- Finishing/toggling an OPEN Duration requires `endedAt >= startAt` and produces
  COMPLETED. A finish earlier than start is Invalid.
- Archive/unlink uses a deterministic non-time completion reason and produces
  INCOMPLETE with no end timestamp.
- State setting creates an Event even when the state matches the current value.
  Currentness is recalculated by scope, generation, occurredAt, and sequence.
- A terminal Duration or cleared State cannot be resurrected by unarchive, edit,
  or stale Undo.

## Corrections and Undo

An UndoReceipt includes the affected Event revision, required scope versions, and
dataset generation. If any required context changed, Undo returns Conflict and
the caller routes the user to ordinary Timeline correction. Undo is not an audit
log and is not part of this feature's backup behavior.

## Explicit boundary exclusions

The Engine does not implement duplicate suppression, NFC pairing/dispatch,
Widget callbacks, Quick Settings TileService, notifications, parser/fuzzy
matching, AI, UI, Room/SQLite, backup/import, statistics, monetization, channel
feedback, or Android lifecycle/background work. Those layers adapt to this
contract and may not redefine its Event semantics. After a successful commit,
the transaction boundary/caller owns post-commit feedback; feedback failure
cannot change an already returned `Applied` result.
