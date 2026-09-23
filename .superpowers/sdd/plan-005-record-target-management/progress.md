# SDD ledger — plan: specs/005-record-target-management/plan.md

## Scope and recovery

This resumed execution is limited to T007. T006 is already complete per git history (`eeb4dda`). Do not begin T008. Existing branch `005-record-target-management` is the isolated feature workspace.

## Preflight

| Check | Result |
|---|---|
| T007 interfaces against existing T003/T005 tests | Tests intentionally call `CreateRecord`, `CreateTarget`, `EventEngine.link`, and `EventEngine.relink`; current production seam is missing these APIs, causing the known compilation blockers. T007 supplies those APIs. |
| T007 against approved 003/004 contracts | Maintain EventEngine authority, Boolean AtomicCommitBoundary commit, pre-commit Conflict, commit-time StorageFailure, history/snapshot preservation, and no standalone Target deletion. |
| T006 deferred finding | Carry forward the name-based standalone Target deletion guard as a deferred minor for its appropriate verification task; T007 must not expand to fix it. |
| Scope guard | No T008 work or data/app/UI changes. |

## Rulings

- Ruling: Extend `EventEngine.unlink` with an optional expected relationship/dataset context and focused result-category tests — the 005 management contract requires stale link/unlink/delete assignment actions to return pre-commit Conflict, while this is a backwards-compatible core seam extension and needed by the approved assignment work — if this interpretation is wrong, it broadens T007's interface surface by one optional parameter and can be reverted without changing persistence contracts.

## T007

Complete in commits `d0ed7e6` and `b716f24`.

- Baseline T003/T005 compile blockers resolved: all 14 unresolved creation-command references and all 6 unresolved link/relink references now compile.
- Focused management tests: 15 initially passed; after review fixes, the focused creation and relationship classes passed with added stale-context and Boolean-rejection coverage.
- Full `:core:test`: initially exposed one existing T004 Target-unarchive revision failure (67 tests, 1 failed); corrected unarchive through `DefinitionManagement.unarchive`. Final suite: 71 tests, zero failures.
- Independent spec review: approved; scoped re-review found no open findings.
- Independent code-quality review: two findings (unlink expected context; missing new-path result tests) fixed in `b716f24`; scoped re-review approved with no open findings.
- T006 deferred minor: standalone Target deletion guard uses name-based detection; preserve this finding for the appropriate verification task. T007 did not alter deletion behavior.
- Task boundary: T007 complete. T008 not started.
