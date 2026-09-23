# SDD ledger — plan: /workspaces/taplog/specs/005-record-target-management/tasks.md

## Setup and reconciliation

- Workspace: `/workspaces/taplog` on dedicated branch `005-record-target-management`; it is a normal checkout, and the user explicitly required this branch. No merge or push will be performed.
- Canonical inputs read: `spec.md`, `plan.md`, `research.md`, `data-model.md`, `contracts/management-application.md`, `contracts/management-ui.md`, `quickstart.md`, `tasks.md`, and `AGENTS.md`.
- Global constraints recorded: preserve 003/004 contracts; `:core` owns semantics/effects; `:data` owns persistence; `:app` cannot reach Room/DAO internals; `AtomicCommitBoundary.commit(): Boolean` is unchanged; pre-commit stale guards may be `Conflict`; commit-time Boolean rejection is `StorageFailure`; no standalone permanent Target deletion; only permanent deletion confirms; explicit relink does not revive orphaned historical bindings.
- T047 remains an evidence-gated task. No participants, measurements, or outcome will be invented; if representative-user sessions cannot occur in this environment, it will be marked blocked with exact required evidence.
- Baseline status: branch is clean at `c96f19f` before implementation; baseline test command is pending.

## Pre-flight overlap and consistency scan

| Tasks | Shared producer/consumer or file/interface | Finding and ruling |
|---|---|---|
| T001 → T002 | Gradle/package skeleton | Sequential setup; T001 dependency wiring precedes T002 scaffold verification. |
| T003–T006 → T007 | Core contracts → EventEngine/DefinitionManagement | Independent red tests; T007 must satisfy all four and remains the only transaction-facing core engine. |
| T007 → T008–T010 | Core aggregate/result seam → data port and boundary tests | Sequential; adapter must expose no Room/DAO internals and preserve Boolean rejection semantics. |
| T010 → T011–T018 | Foundation → Record app flow | Phase gate; app tests precede implementation files, then UI/resources/instrumentation. |
| T010 → T019–T025 | Foundation → Target app flow | Phase gate; standalone Target deletion remains absent. |
| T010/T011/T019 → T026–T032 | Core/data plus read models → assignment | Assignment changes only future eligibility; no historical reactivation. |
| T011–T032 → T033–T040 | Story result/state interfaces → shared mapping/navigation | Shared app files are intentionally sequential; no parallel edits to MainActivity/navigation/strings/common state. |
| T032 → T041; T041/T042 → T043–T046 | Restart/offline evidence → review/final verification | Final checks consume all implementation and verification artifacts; T046 follows T047 per approved order. |
| T044/T045 → T047 | Automated flow verification → usability validation | T047 uses implemented flows and must report real participant evidence or blocked status. |
| Every task | Task text vs. specified files/tests | No self-contradiction found; tasks require tests before corresponding production changes and prohibit excluded scope. |

Ruling: execute strictly in task order despite `[P]` markers because the user explicitly requires T001–T047 in dependency order and prohibits concurrent edits to shared contracts/files.

## Task status

- No 005 implementation task is complete yet.

Task T001: complete (commit ff5a402; focused `:app:compileDebugKotlin` passed; independent spec/quality review PASS with no findings). Worker initially stalled twice; bounded controller completion used the existing two-file edit and preserved the requested review gate.

Task T002: complete (commit 8a24266 plus evidence update; 30 approved zero-byte Kotlin placeholders; fresh focused compile and `:core:test :data:test :app:test` both BUILD SUCCESSFUL; independent spec/quality review PASS, with the initial verification caveat resolved by fresh online runs). No T003 work started.

Task T003: minor (deferred): the zero, negative, and malformed quantity cases all normalize through `Quantity.parse` to the same invalid zero value, so the test proves rejection of invalid domain quantity rather than separate raw-input parsing behavior.

Task T003: fix round 1/5 (3 addressed, 0 open — explicit Counter quantity preservation; behavior-inapplicable quantity rejection; literal active lifecycle and Target name assertions; commits 8661579..3e955b5).

Task T003: complete (commits a0bba09..3e955b5, independent spec compliance and code quality approved after scoped re-review; intentional RED remains only for T007's missing `CreateRecord`/`CreateTarget` commands). T004 not started.

## T004 resumption reconciliation (2026-09-23)

- Verified actual branch `005-record-target-management`, clean working tree, and commits `8661579` (`test(core): define management creation contract`) and `3e955b5` (`test(core): strengthen creation contract assertions`). The T003 ledger and git evidence agree; its only intentional RED is the missing T007 `CreateRecord`/`CreateTarget` surface.
- Verified the existing T004 test file is absent. `task-brief` could not parse the Spec Kit checklist because it requires `# Task N` headings and created an empty workspace brief.
- Ruling: replace only the workspace-local T004 brief with the verbatim T004 checklist requirement plus binding approved constraints; preserve `tasks.md` unchanged. Cost if wrong: a worker could receive a narrowed task; mitigated by retaining the exact task sentence and directing review to the approved artifacts.

Task T004: fix round 1/5 (3 addressed, 0 open — Target edit now protects dataset generation and relationship state; Target archive/unarchive protects Record, relationship, and generation state; report has exact blocked-test evidence; commits 2ca265a..2ca35a5).

Task T004: complete (commits 3e955b5..2ca35a5, independent spec compliance and task-quality review approved after scoped re-review). Fresh controller verification: `:core:test --tests ManagementDefinitionTest` exited 1 at shared `:core:compileTestKotlin` with exactly 14 unresolved `CreateRecord`/`CreateTarget` references in `ManagementCreationTest.kt`; it never ran T004 and has no T004 diagnostic. This is the preserved intentional T003/T007 RED dependency. T004 additionally contains the intended, not-yet-green Target-unarchive revision contract; no production fix is in scope before T007/T010.

## T005 resumption reconciliation (2026-09-23)

- Verified branch `005-record-target-management`, clean at `2ca35a5`, and T004 is complete per its commits, report, and approved independent re-review.
- Graft confirms current core has `EventEngine.unlink(recordId, targetId)`, but no public link/relink operation. The approved T005 checklist and spec require link, unlink, explicit relink; plan architecture/T007 detailed task text are inconsistent about whether initial link must be implemented alongside relink.
- Ruling: T005 will encode both required link and relink behavior as tests through the existing EventEngine seam; this follows the explicit task and FR-015 rather than silently dropping active-pair linking. Cost if wrong: T007 may need to implement the link operation in addition to the detailed checklist wording; this is a required product behavior already described elsewhere in the approved plan/spec.

Task T005: minor (deferred): lifecycle assertions verify `Applied` and resulting aggregate effects, but do not explicitly lock the `Applied` revision/effect payload; reviewer assessed this as a strengthening opportunity, not a blocker.

Task T005: fix round 1/5 (2 addressed, 0 open — active/nonexistent Record rejection; future Event rejected after unlink; commits da712fa..da3bdbe).

Task T005: complete (commits 2ca35a5..da3bdbe, independent spec compliance and code quality approved after scoped re-review). Fresh controller run exited 1 at `:core:compileTestKotlin`; no tests executed. It reported 14 preserved T003/T007 `CreateRecord`/`CreateTarget` references and the intentional T005 missing `EventEngine.link`/`relink` symbols scheduled for T007. All T003 tests remain unchanged. Minor finding about explicit Applied payload assertions is deferred as recorded above.

## T006 resumption reconciliation (2026-09-23)

- Verified branch `005-record-target-management`, clean working tree, and HEAD `d93ddc9` (`docs(sdd): record T005 verification evidence`). `da712fa` adds T005's relationship contract tests and `da3bdbe` its fix-round coverage; `d93ddc9` records controller verification. The T005 report contains the initial/re-review evidence and the independent spec/quality review and scoped re-review are recorded by its commit range and prior task completion ledger. T005 is complete; no T006 source had been written.
- Fresh baseline command `GRADLE_USER_HOME=/tmp/taplog-gradle ./gradlew --no-daemon -Djava.net.preferIPv4Stack=true :core:compileTestKotlin --offline --console=plain` exited 1 before test execution. It reproduced exactly 14 T003 `CreateRecord`/`CreateTarget` unresolved references and 6 T005 `EventEngine.link`/`relink` unresolved references, all scheduled for T007; there are no T006 diagnostics in the baseline.
- Approved T006 scope is test-only: exact null-target Record-wide versus non-null Record–Target deletion, `NeedsConfirmation`, cancel/missing confirmation no-op, unrelated Target/no-Target history preservation, and unavailable standalone Target deletion. The approved spec/plan/app contract preserve core as deletion-scope authority and forbid standalone Target deletion.
- `task-brief` cannot extract checklist tasks because the task list uses `T006` bullets without `# Task 6` headings. A workspace-local brief preserves the T006 sentence verbatim plus approved boundaries; canonical Spec Kit artifacts remain unchanged.

Task T006: fix round 1/5 (initial independent review found an unproven standalone Target-deletion prohibition; controller's focused run also exposed six new T006 type-resolution errors plus unresolved properties at lines 133/135/139, in addition to the 20 known T003/T005 blockers; commits 4b1e9d0..2151682).

Task T006: fix round 1 re-review (0 addressed, 1 open — reflection assertion fails against Kotlin's mangled `deleteScope` JVM name and generated `$default` method; fix round 2 started from 2151682).

Task T006: fix round 2/5 (original JVM-reflection finding and Method-to-name mapping compile errors addressed; 0 Important findings open after scoped re-review; commits 2151682..03344e3).

Task T006: minor (deferred): the standalone Target-deletion negative API assertion detects public deletion methods and command subtype names containing both `delete` and `target`; a differently named future command such as `RemoveTarget` could evade the name-based guard. Reviewer assessed this as nonblocking robustness scope; current production/API surface has no standalone Target deletion.

Task T006: complete (commits d93ddc9..03344e3; independent spec-compliance and code-quality review approved after scoped re-review). Focused `:core:test --tests ManagementDeletionTest` remains blocked at shared `:core:compileTestKotlin` by exactly 14 T003 `CreateRecord`/`CreateTarget` and 6 T005 `EventEngine.link`/`relink` references; no T006 diagnostics remain, and no test methods ran. No full suite PASS is claimed. T007 not started.
