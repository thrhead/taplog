# Research: TapLog Core Domain + Event Engine

**Date**: 2026-09-16
**Sources**: PRD, constitution, approved V1 architecture, data model, Event
Engine contract, and V1 implementation roadmap.

## Decision 1: One command boundary for every input channel

- **Decision**: App, Widget, Quick Settings, NFC, and Natural Language adapters
  produce the same validated domain commands. The Engine accepts commands, not
  Android intents, UI objects, raw NFC payloads, or natural-language text.
- **Rationale**: PRD §§1, 20, 23 and constitution Principle I require one Event
  system. This prevents channel-specific semantics from fragmenting history.
- **Alternatives considered**: Separate Event handlers per channel (rejected;
  duplicates product semantics) and a generic raw-input Engine (rejected;
  expands the Engine into parser/platform responsibilities).

## Decision 2: Event is a historical occurrence with explicit time semantics

- **Decision**: Each Event keeps immutable identity, Record/optional Target
  references, behavior, occurredAt, createdAt, updatedAt, source, revision,
  sequence, and a creation-time snapshot. Explicit occurredAt wins over request
  processing time; absent occurredAt uses input acceptance time.
- **Rationale**: PRD §§5, 12, 32, 43 and the approved data model distinguish the
  real occurrence from system bookkeeping and preserve historical meaning.
- **Alternatives considered**: One system timestamp (rejected; late logging
  becomes historically inaccurate) and live Record/Target display lookup
  (rejected; renames would rewrite history).

## Decision 3: Exact decimal Counter quantities and one fixed unit

- **Decision**: Counter quantity is positive, finite, and exact; a Record has an
  optional single unit, default quantity initially 1, and no automatic unit
  conversion. Event quantities are retained independently from later defaults.
- **Rationale**: PRD §44.1 item 8 and the approved data model explicitly reject
  zero/negative quantities, floating-point ambiguity, and implicit conversion.
- **Alternatives considered**: Integer-only counters (rejected; decimal use is
  approved), floating point (rejected; precision can corrupt history), and
  per-Event unit conversion (rejected; no approved conversion semantics).

## Decision 4: Duration and State use scoped lifecycle invariants

- **Decision**: One OPEN Duration is allowed per Record + optional Target scope;
  no-Target is a separate scope. Duration is OPEN → COMPLETED or OPEN →
  INCOMPLETE, with INCOMPLETE terminal and no invented end time. One current
  State exists per State Group + optional Target scope and lifecycle generation.
- **Rationale**: PRD §§32 and 44.1 items 2–4 plus the approved data model settle
  concurrency, terminality, reset, and equal-time ordering.
- **Alternatives considered**: One global Duration per Record (rejected;
  different Targets may run concurrently), reopening on unarchive (rejected;
  invents continuity), and no-op repeated State (rejected; each report is an
  Event).

## Decision 5: Lifecycle effects are explicit transaction-facing outcomes

- **Decision**: Archive/unlink effects include open-Duration completion as
  INCOMPLETE, State generation reset, binding orphaning with last-known snapshot,
  and related Undo invalidation. Hard delete is explicit, impact-confirmed, and
  scoped. These effects are described by the core contract but implemented by
  later management/persistence slices.
- **Rationale**: PRD §§18, 28–32 and the approved Event Engine contract require
  history preservation while preventing new use of inactive definitions.
- **Alternatives considered**: Automatically restore effects on unarchive
  (rejected; PRD says no automatic rebind/state resurrection) and ordinary
  delete as hard delete (rejected; archive is the normal delete behavior).

## Decision 6: Deterministic result categories with optimistic context checks

- **Decision**: Every command returns exactly one of Applied,
  NeedsConfirmation, Conflict, Invalid, or StorageFailure. Expected revisions,
  lifecycle context, and dataset generation are optional command guards; stale
  guards return Conflict. Applied is possible only after commit.
- **Rationale**: The approved Event Engine contract and constitution Principle
  III make ambiguity and stale correction explicit rather than silent mutation.
- **Alternatives considered**: Boolean success/failure (rejected; callers need
  actionable distinction) and silent last-write-wins (rejected; loses newer
  history).

## Deferred decisions

Room/SQLite mappings, transaction implementation, Android lifecycle, channel
duplicate policy, UI feedback, parser/AI, backup/import, statistics, entitlement,
and performance benchmarks belong to their approved roadmap slices. They do not
block this domain contract.
