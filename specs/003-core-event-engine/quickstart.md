# Quickstart Validation: Core Domain + Event Engine

This guide validates the feature's domain contract without Android UI, input
channels, network, AI, or persistence technology. It is intended for the pure
JVM test lane in `:core`.

## Prerequisites

- A clean checkout with the approved Android foundation slice available.
- Gradle Wrapper available from the repository root.
- The implementation has added the domain/engine tests under `core/src/test`.

## Commands

Run the focused core tests:

```bash
./gradlew :core:test
```

Run the repository test lane:

```bash
./gradlew test
```

## Required scenario matrix

The focused suite must prove:

1. Equivalent Moment and Counter commands from APP, WIDGET,
   QUICK_SETTINGS, NFC, and NATURAL_LANGUAGE produce the same domain meaning;
   source is retained only as metadata.
2. Two intentional Counter commands create two Events, while zero, negative,
   non-finite, and inexact quantities return Invalid.
3. Explicit historical timestamps are retained; omitted timestamps use input
   acceptance time; created/updated bookkeeping remains distinct.
4. One OPEN Duration is allowed per Record + optional Target scope; different
   Target scopes and no-Target scope may be open concurrently.
5. OPEN → COMPLETED requires an end at or after start. Archive/unlink produces
   OPEN → INCOMPLETE with no end timestamp, and terminal states cannot reopen.
6. State Group + Target scope has one current State; repeated same-State input
   creates a new Event; reset generation prevents historical resurrection.
7. Renaming or changing an icon preserves Event snapshots. Archive/unlink
   preserve history and expose lifecycle effects; unarchive does not reverse
   them automatically.
8. Stale revision, scope, and dataset generation return Conflict; invalid
   combinations return Invalid; ambiguity/delete impact returns
   NeedsConfirmation; commit failure returns StorageFailure.
9. A valid committed Event remains Applied if post-commit feedback is absent or
   fails, and the Engine never performs global debounce.

## Expected outcome

All scenarios pass deterministically with no Android framework or external
service. Later Local Persistence tests must repeat the same cases through the
real transaction boundary; channel tests must prove adapters translate into
these commands without changing their meaning.

## Handoff boundaries

- Slice 3 supplies the transaction/persistence implementation and atomicity
  tests; it must preserve this model's snapshots, revisions, sequences, and
  lifecycle effects.
- Slice 4/5 supplies management and complete Duration/State integration; it must
  preserve the same command/result and generation semantics.
- Slices 6–14 supply channels and parser; they own interaction handling and
  feedback but must not add Event Engine debounce or alternate Event systems.
