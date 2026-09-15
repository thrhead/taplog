<!--
Sync Impact Report
- Version change: unversioned template -> 1.0.0
- Modified principles: none; initial project constitution
- Added sections: Core Principles; Product and Platform Constraints; Development Workflow
- Removed sections: none
- Follow-up TODOs: none
-->
# TapLog Constitution

## Core Principles

### I. Event-First, Channel-Independent Core
TapLog's fundamental product primitive is **EVENT + TIMESTAMP**. App, Widget,
Quick Settings, NFC, and Natural Language are input channels to one Event system,
not separate Event systems or products. The Event Engine MUST remain independent
of channel-specific UI, platform dispatch, and interaction behavior so that a valid
Event request has the same domain meaning regardless of its origin.

Rationale: a unified, channel-independent core preserves consistent history and
allows channels to evolve without redefining the product's data semantics.

### II. Local-First Core and Optional Intelligence
V1 is Android-only and MUST provide core Event logging without an account or an
internet connection. Deterministic, local parsing MUST support the Natural Language
input path when AI is unavailable. AI MAY enhance supported devices, but MUST NOT be
required to create, interpret, or retain core Events.

Rationale: personal logging must remain available, private by default, and reliable
under ordinary offline conditions.

### III. Preserve History and Confirm Ambiguity
History is sacred: Events and their historical meaning MUST be preserved through
ordinary edits, corrections, archive operations, and lifecycle changes. Data may be
permanently removed only after the user explicitly chooses a clearly communicated,
irreversible permanent-deletion action. When user intent, matching, or action is
ambiguous, the system MUST obtain the minimum necessary confirmation rather than
silently mutating data or guessing incorrectly.

Rationale: TapLog is a record of real events; trust depends on accurate intent and
the user's control over irreversible loss.

### IV. Channel-Owned Interaction Safety
Duplicate suppression and interaction handling MUST be implemented by the individual
input/channel layer. The Event Engine MUST NOT apply a global debounce that rejects
a valid Event request. Channel behavior MUST preserve intentional consecutive user
actions, including repeated Counter increments.

Rationale: physical NFC reads, widget interactions, tiles, and in-app actions have
different failure and feedback characteristics; global suppression would corrupt
legitimate event history.

### V. Testable Boundaries and Scope Discipline
Domain and application behavior MUST be independently testable from Android UI and
framework code. Changes to domain or application behavior MUST follow test-driven
development. Modules and interfaces MUST have clear responsibilities; channel and
Android integration code MUST not absorb Event Engine semantics. V1 MUST NOT include
Future Scope items unless the PRD is explicitly amended to promote them.

Rationale: explicit boundaries protect product semantics, enable reliable tests, and
keep the first release focused.

## Product and Platform Constraints

All implementation work MUST be traceable to the current
`docs/product/TapLog_V1_PRD.md`; the PRD is authoritative for product behavior.
Technical TBDs in that PRD remain undecided and MUST NOT be converted into
architecture, persistence, encryption, parser, lifecycle, or UI commitments without
an approved feature design.

Product and onboarding copy MUST make honest, platform-aware claims. In particular,
copy MUST NOT promise unconditional NFC behavior across locked screens, Android
versions, devices, or OEM implementations. It may describe the intended interaction
without implying guarantees Android or OEM behavior cannot provide.

## Development Workflow

Work feature-by-feature and one independently verifiable implementation task at a
time. Use Spec Kit artifacts as the canonical feature specification, plan, and task
sources; do not create competing artifacts. Each implemented feature requires an
approved specification traceable to the PRD; a product behavior change requires an
approved PRD amendment before implementation.

Before implementation, identify the applicable principle and PRD requirement. Tests
for domain/application behavior are written first, observed to fail for the intended
reason, then implemented and refactored. Completion claims require proportionate,
executed verification and review of constitutional compliance, including local-first
operation, history preservation, ambiguity handling, and channel independence where
applicable.

## Governance

This constitution governs project-wide engineering and product constraints. It does
not replace the PRD, which defines feature behavior, nor does it decide PRD Technical
TBDs. In a conflict, the PRD governs product behavior; this constitution governs how
that behavior is designed and implemented without expanding V1 scope.

Amendments MUST document the reason, affected principles or sections, and any needed
updates to dependent Spec Kit artifacts. Constitutional compliance MUST be reviewed
for every feature specification, plan, task set, implementation review, and release
verification. A change that introduces a new principle or materially expands a rule
increments the MINOR version; a backward-incompatible removal or redefinition
increments MAJOR; clarifications and non-semantic wording changes increment PATCH.

**Version**: 1.0.0 | **Ratified**: 2026-09-15 | **Last Amended**: 2026-09-15
