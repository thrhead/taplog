# Implementation Plan: TapLog Core Domain + Event Engine

**Branch**: `003-core-event-engine` | **Date**: 2026-09-16 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/003-core-event-engine/spec.md`

## Summary

Define the channel-independent core domain and Event Engine for TapLog's single
EVENT + TIMESTAMP primitive. The implementation will establish typed Records,
Targets, relationships, four behavior families, historical Event snapshots,
timestamp/source/quantity semantics, lifecycle invariants, deterministic command
results, revision/generation conflict checks, and transaction-facing lifecycle
effects. It will live in the existing `:core` module and be verified with pure
JVM tests; Room, persistence, Android channels, parser, UI, and backup remain
outside this slice.

## Technical Context

**Language/Version**: Kotlin/JVM; version set inherited from the approved V1
architecture and foundation slice.

**Primary Dependencies**: Kotlin standard library and the existing core test
tooling; no Android framework, UI, database, network, AI, or channel dependency.

**Storage**: N/A in this feature. The Engine consumes a transaction-facing
domain state/commit boundary; the Room/SQLite realization belongs to the Local
Persistence slice.

**Testing**: Pure JVM unit tests in `core/src/test`, with deterministic clocks,
identifiers, sequences, and in-memory fakes for command-state scenarios.

**Target Platform**: Android V1 through an Android-independent core boundary;
minimum platform API 26 is an application constraint, not a core dependency.

**Project Type**: Local-first Android application with a platform-independent
domain/application core.

**Performance Goals**: A valid core Event request must not wait for network or
AI. Domain evaluation and command construction must be suitable for a short,
synchronous user action; exact latency targets are deferred to implementation
benchmarking.

**Constraints**: EVENT + TIMESTAMP is the primitive; no global debounce;
history is preserved by ordinary lifecycle changes; Duration completion cannot
invent time; Counter quantities are positive exact decimals; stale commands do
not overwrite newer state; no persistence or channel behavior is implemented.

**Scale/Scope**: One user's local Event history; four behaviors; optional
Record–Target context; one OPEN Duration per Record + optional Target scope; one
current State per State Group + optional Target scope.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Plan response | Status |
|---|---|---|
| I. Event-first, channel-independent core | Commands and results are shared across App, Widget, Quick Settings, NFC, and Natural Language adapters; source is metadata only. | PASS |
| II. Local-first core and optional intelligence | Core creation has no network, account, server, or AI dependency; parser/AI are excluded. | PASS |
| III. Preserve history and confirm ambiguity | Snapshots, terminal Duration states, State generations, revisions, and explicit hard-delete confirmation are specified. | PASS |
| IV. Channel-owned interaction safety | The Engine has no debounce; intentional repeated Counter commands remain distinct. | PASS |
| V. Testable boundaries and scope discipline | All domain behavior is pure-JVM testable; persistence/channels/UI are separate slices. | PASS |

No constitution violations require an exception.

## Project Structure

### Documentation (this feature)

```text
specs/003-core-event-engine/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
└── contracts/
    └── event-engine.md
```

### Source Code (repository root)

```text
core/
├── src/main/kotlin/io/github/thrhead/taplog/core/
│   ├── domain/          # Record, Target, relationships, behaviors, Events
│   │   └── LifecycleMetadata.kt # canonical binding lifecycle contract
│   └── engine/          # commands, results, invariants, lifecycle effects, transaction boundary
└── src/test/kotlin/io/github/thrhead/taplog/core/
    ├── domain/          # value and invariant tests
    └── engine/          # command/result and cross-channel tests
```

**Structure Decision**: Extend the existing Android-independent `:core` module
with focused domain and engine packages. The core exposes command/result and
state/transaction boundaries; `LifecycleMetadata.kt` and
`contracts/event-engine.md` define the canonical binding lifecycle contract.
`:data` later supplies persistence and `:app` later supplies channel adapters,
rebind decisions, and post-commit feedback. No channel-specific source package
or separate Event system is introduced.

## Phase 0: Research Decisions

Research decisions are recorded in [research.md](research.md). All Technical
Context choices are resolved from the approved PRD, constitution, architecture
plan, data model, Event Engine contract, and roadmap Slice 2/Slice 5.

## Phase 1: Design Outputs

- [data-model.md](data-model.md) defines domain entities, value semantics,
  relationships, snapshots, lifecycle transitions, and invariants.
- [contracts/event-engine.md](contracts/event-engine.md) defines commands,
  source metadata, deterministic result/error semantics, transaction boundaries,
  lifecycle effects, and the Engine boundary.
- [quickstart.md](quickstart.md) defines runnable JVM validation scenarios and
  the handoff checks for later persistence/channel slices.

## Constitution Check — Post-Design

| Gate | Evidence in Phase 1 artifacts | Status |
|---|---|---|
| Shared Event meaning | `contracts/event-engine.md` defines one command boundary and source-neutral semantics. | PASS |
| History preservation | `data-model.md` defines snapshots, immutable identities, archive effects, and terminal states. | PASS |
| No global duplicate suppression | Engine contract explicitly accepts intentional consecutive Counter commands. | PASS |
| Deterministic safety | Stable result categories/reasons and expected revision/generation checks are defined. | PASS |
| Scope discipline | Quickstart and contracts exclude Room, channels, UI, parser, backup, statistics, monetization, and AI implementation. | PASS |

No violations or unresolved design gates remain.

## Complexity Tracking

No constitution violations.
