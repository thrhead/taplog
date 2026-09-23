# SDD ledger — plan: specs/005-record-target-management/plan.md

## Scope and recovery

This resumed execution completed T009 and stops at its boundary. T001–T009 are complete; T008's final commit was `a125933`, and T009's test commit is `3fe05be`. Existing branch `005-record-target-management` is the dedicated feature workspace. T010 is not started; wait for explicit user `CONTINUE` before proceeding.

## T007 preflight (recorded at that task's start)

| Check | Result |
|---|---|
| T007 interfaces against existing T003/T005 tests | Tests intentionally call `CreateRecord`, `CreateTarget`, `EventEngine.link`, and `EventEngine.relink`; current production seam is missing these APIs, causing the known compilation blockers. T007 supplies those APIs. |
| T007 against approved 003/004 contracts | Maintain EventEngine authority, Boolean AtomicCommitBoundary commit, pre-commit Conflict, commit-time StorageFailure, history/snapshot preservation, and no standalone Target deletion. |
| T006 deferred finding | Carry forward the name-based standalone Target deletion guard as a deferred minor for its appropriate verification task; T007 must not expand to fix it. |
| Scope guard | No T008 work or data/app/UI changes. |

## Rulings

- Ruling: Extend `EventEngine.unlink` with an optional expected relationship/dataset context and focused result-category tests — the 005 management contract requires stale link/unlink/delete assignment actions to return pre-commit Conflict, while this is a backwards-compatible core seam extension and needed by the approved assignment work — if this interpretation is wrong, it broadens T007's interface surface by one optional parameter and can be reverted without changing persistence contracts.
- Ruling: Cache the public management port as one process-scoped instance — the fixed app database has no exposed close lifecycle, and repeated composition must not create uncloseable Room instances — if this is wrong, callers that need separate same-process databases cannot use the singleton factory; the approved T008 API specifies one app persistence port and no database name parameter.

## T007

Complete in commits `d0ed7e6` and `b716f24`.

- Baseline T003/T005 compile blockers resolved: all 14 unresolved creation-command references and all 6 unresolved link/relink references now compile.
- Focused management tests: 15 initially passed; after review fixes, the focused creation and relationship classes passed with added stale-context and Boolean-rejection coverage.
- Full `:core:test`: initially exposed one existing T004 Target-unarchive revision failure (67 tests, 1 failed); corrected unarchive through `DefinitionManagement.unarchive`. Final suite: 71 tests, zero failures.
- Independent spec review: approved; scoped re-review found no open findings.
- Independent code-quality review: two findings (unlink expected context; missing new-path result tests) fixed in `b716f24`; scoped re-review approved with no open findings.
- T006 deferred minor: standalone Target deletion guard uses name-based detection; preserve this finding for the appropriate verification task. T007 did not alter deletion behavior.
- Task boundary: T007 complete. T008 not started.

## T008

Complete in commits `ed1881f` and `35ed87a`.

- Added public `ManagementPersistencePort` over the existing `AtomicCommitBoundary` and a public Context-in/port-out factory. The adapter delegates aggregate reads and Boolean commits unchanged; Room implementation details remain internal.
- Data unit tests passed: `:data:testDebugUnitTest` (BUILD SUCCESSFUL, 53s initially).
- Consumer compilation passed: `:app:compileDebugKotlin` (BUILD SUCCESSFUL, 26s initially).
- Final combined verification after the lifecycle fix: `:data:testDebugUnitTest :app:compileDebugKotlin` (BUILD SUCCESSFUL in 7s; 8 tasks executed, 17 up-to-date), plus `git diff --check`.
- Initial sandbox Gradle starts failed before tasks due wildcard-IP lock initialization; initial offline app compile could not resolve uncached dependencies. The same tests/compile were rerun successfully; no test remains blocked.
- Independent spec review: approved; scoped re-review approved with no findings.
- Independent code-quality review found repeated factory calls created uncloseable Room instances. The factory now uses a synchronized process-scoped holder; scoped re-review approved with no open findings.
- No schema, migration, Room visibility, persistence strategy, or Boolean commit semantics changed. No T009 test or task was started.
- Task boundary: T008 complete. T009 not started. T006 deferred name-based standalone Target deletion guard remains preserved for its appropriate verification task.

## T009 resumption reconciliation (2026-09-23)

- Verified `005-record-target-management` is the current branch, the working tree was clean at `a125933`, and `a125933` recorded T008 completion. Actual history includes T008 implementation commits `ed1881f` and `35ed87a`, followed by the T008 ledger/review commit `a125933`.
- Read `AGENTS.md`, the full T009 task text, the approved 005 spec/plan/contracts and the 004 persistence contract. The five 005 clarifications remain binding. Graft located the public management port/adapter, existing aggregate round-trip coverage, and `InMemoryLocalPersistence` fixture. No canonical specification or task-list artifact was changed.
- T009 adds only `data/src/test/kotlin/io/github/thrhead/taplog/data/persistence/ManagementPersistenceAdapterTest.kt`. It exercises complete aggregate round-trip; real `EventEngine` lifecycle and pair-deletion diffs through `ManagementPersistenceAdapter`; Boolean stale compare rejection with unchanged aggregate; and core `StorageFailure` mapping with no published proposal. The existing fixture provides atomic compare/write and failure injection; Room/repository behavior remains covered by 004.
- TDD applicability: this is test-only coverage for T008 and earlier behavior. There is no meaningful production RED phase without adding an unrequested behavior change. The first test execution exposed one incorrect expected fixture state; after aligning with core's lifecycle output, the controller's fresh focused run passed.
- Focused command: `GRADLE_USER_HOME=/tmp/taplog-gradle ./gradlew --no-daemon -Djava.net.preferIPv4Stack=true :data:testDebugUnitTest --tests 'io.github.thrhead.taplog.data.persistence.ManagementPersistenceAdapterTest' --console=plain` — exit 0, `BUILD SUCCESSFUL in 1m 25s`, 19 actionable tasks (3 executed, 16 up-to-date). `git diff --check HEAD~1 HEAD` passed for the task commit.
- Relevant regression command: `GRADLE_USER_HOME=/tmp/taplog-gradle ./gradlew --no-daemon -Djava.net.preferIPv4Stack=true :data:testDebugUnitTest --console=plain` — initial sandbox start exited 1 before Gradle task execution due wildcard-IP lock initialization; approved outside-sandbox rerun exited 0, `BUILD SUCCESSFUL in 29s`, 19 actionable tasks (1 executed, 18 up-to-date). SQLite native-access and Gradle deprecation warnings were non-failing.
- Independent spec-compliance review: APPROVED, no findings; reviewed exact scope, atomic Boolean semantics, lifecycle/deletion core ownership, preserved no-Target history, and no out-of-scope changes. Evidence: `spec-review-t009.md`.
- Independent code-quality review: APPROVED, no findings; tests exercise real adapter/core behavior with meaningful complete-state assertions and focused fixtures. Evidence: `quality-review-t009.md`.
- Task commit: `3fe05be test(data): cover management persistence adapter boundary` (only the named JVM test file). No known blocked tests at the T009 boundary. T010 has not started and must not start until explicit user `CONTINUE`.
