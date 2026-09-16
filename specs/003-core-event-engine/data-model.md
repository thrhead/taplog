# Data Model: TapLog Core Domain + Event Engine

This is the domain model and integrity contract for the feature. It is not a
Room/SQLite schema or persistence implementation.

## Value semantics

- **Identity**: Record, Target, relationship, State Group, Event, and receipts
  use immutable unique identities. Deleted identities are never reused.
- **Time**: `occurredAt` is the real-world Event time. `createdAt` is when the
  Event was accepted/created, and `updatedAt` is the latest modification time.
  Domain time is UTC epoch-millisecond. An explicit occurrence time is retained;
  otherwise acceptance time supplies it.
- **Ordering**: Events with equal occurredAt use a monotonic sequence tie-break.
  Sequence is part of history and is preserved by future import.
- **Revision**: Mutations monotonically advance the affected revision. Dataset
  generation changes invalidate stale external callbacks and Undo receipts.
- **Scope key**: An optional Target means two scopes: the concrete Target scope
  and a distinct no-Target scope. The no-Target key must not collide with a real
  Target identity.

## Entities

| Entity | Required domain data | Relationships and rules |
|---|---|---|
| Record | id, name, icon, behavior, lifecycle, optional unit, default quantity, optional State Group, revision | Reusable definition. Behavior and Counter unit lock after first Event; default quantity may change. |
| Target | id, name, icon, lifecycle, revision | Independent reusable context; may relate to many Records. |
| RecordTarget | Record id, Target id, linked state, revision | Optional relationship. Unlink blocks new Events for that pair but preserves both sides and history. |
| State Group | id, name, revision | Groups mutually exclusive State Records. |
| Event | id, Record id, optional Target id, behavior, occurredAt, createdAt, updatedAt, sequence, source, revision, snapshot | Fundamental historical occurrence. References remain meaningful through ordinary archive/unlink. |
| Event snapshot | Record/Target name and icon, behavior, optional unit, and other display meaning at creation | Never rewritten by later definition edits. |
| Counter data | positive exact quantity and optional unit | Valid only for Counter; omitted quantity resolves to Record default. |
| Duration data | startAt, optional endAt, status, incompleteReason | Valid only for Duration; status rules below. |
| State data | State Group id, lifecycle generation, Target scope | Valid only for State; currentness is scoped and generation-aware. |
| State scope | Group id, Target scope, generation, reset sequence/time | Reset invalidates current state without deleting older Events. |
| Lifecycle effect | affected scopes, terminal Duration changes, State resets, binding orphan snapshots, Undo invalidations | Transaction-facing result of archive/unlink; channel implementation is later. |
| Undo receipt | affected Event/operation, expected revision, before image/scope versions, dataset generation, consumed state | Limited correction aid; stale context conflicts; not an audit log. |

## Behavior invariants

### Moment

One valid command creates one timestamped Event. It has no Counter quantity,
Duration timestamps/status, or State scope payload.

### Counter

Quantity must be positive, finite, and exactly representable as an integer or
decimal. Record default quantity starts at 1. An explicit quantity applies only
to the new Event. A Record's one optional unit is fixed once the first Event is
created; unit conversion is not a domain operation.

### Duration

| Status | Required values | Terminal? |
|---|---|---|
| OPEN | startAt; no endAt; no incompleteReason | No |
| COMPLETED | startAt; endAt; endAt >= startAt; no incompleteReason | Yes |
| INCOMPLETE | startAt; no endAt; non-empty incompleteReason | Yes |

There can be at most one OPEN Duration per Record + Target scope. Different
Target scopes and the no-Target scope may each have one. Finish earlier than
start is invalid. Archive/unlink changes OPEN to INCOMPLETE without inventing an
end time. Unarchive, edit, normal restart, or Undo cannot make a terminal
Duration OPEN.

### State

Each State Event belongs to one State Group and optional Target scope. Within a
group + scope + active generation, the latest `(occurredAt, sequence)` is the
sole current State. Setting the same State again creates a new Event. Archive or
unlink resets only the affected scope by advancing its generation; older Events
remain in history and cannot become current again. An older Event edit does not
move its generation into the current generation.

## Lifecycle and history effects

- Archive removes a definition from active creation choices but retains its
  identity and Events.
- Unlink removes only the Record–Target creation context; it does not archive
  either definition or delete history.
- Archive/unlink atomically applies all affected domain effects: OPEN Duration →
  INCOMPLETE, State generation reset, binding → ORPHANED with last-known display
  snapshot, and related Undo invalidation.
- Unarchive restores active definition eligibility only. It does not reopen
  Duration, restore cleared State, or automatically rebind a channel binding.
- Permanent delete requires explicit impact confirmation, then removes only the
  confirmed definitions, associated Event history/snapshots, bindings, Undo
  metadata, and other related metadata. Unrelated Target and no-Target history
  remains.

## Invalid combinations

The domain rejects behavior-specific fields on the wrong behavior, missing
required fields, inactive or unlinked creation scope, invalid Counter quantity or
unit, negative Duration interval, a second OPEN Duration in one scope, missing
State Group, and State scope mismatch. Rejection must not partially mutate data.
