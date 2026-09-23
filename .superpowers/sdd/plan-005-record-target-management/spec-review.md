# T007 specification and contract review

**Status: APPROVED**

Reviewed commit `d0ed7e6` against the canonical T007 task, its task brief, the
005 specification/plan/application contract, and the approved 003 Event Engine
and 004 persistence contracts.

## Findings

None. No Critical, Important, or Minor specification/contract findings.

## Evidence

- `core/src/main/kotlin/io/github/thrhead/taplog/core/engine/Commands.kt:20-23`
  adds only the required `CreateRecord` and `CreateTarget` commands, through
  the existing `Command`/`EventEngine` seam.
- `core/src/main/kotlin/io/github/thrhead/taplog/core/engine/EventEngine.kt:144-185`
  validates creation before its atomic `CommitOperation`, returns `Conflict`
  for stale pre-commit expected context, and maps a Boolean commit rejection to
  `StorageFailure` at `398-405`.  This preserves the approved 003/004 result
  split.
- `core/src/main/kotlin/io/github/thrhead/taplog/core/engine/EventEngine.kt:362-395`
  permits initial link only for active definitions and explicit relink only for
  an existing unlinked pair.  It changes only the relationship; historical
  Events, snapshots, bindings, State generations, and terminal effects remain
  untouched.
- `core/src/main/kotlin/io/github/thrhead/taplog/core/engine/DefinitionManagement.kt:11-24`
  establishes active, revision-zero, event-free definitions and advances only
  the relationship revision on relink.
- The only non-T007 behavior correction is
  `EventEngine.kt:246-249`: Target unarchive now calls the existing approved
  `DefinitionManagement.unarchive` helper, restoring the T004-required
  revision increment.  It does not broaden behavior.
- The commit changes exactly the three T007 core files.  It does not alter
  deletion APIs or tests, retains standalone Target deletion prohibition, and
  preserves the deferred T006 name-based deletion-guard finding in
  `.superpowers/sdd/tasks/progress.md:77` and the T007 ledger at
  `.superpowers/sdd/plan-005-record-target-management/progress.md:13,22`.
- `git diff --check eeb4dda d0ed7e6` completed cleanly.  The full offline
  `:core:test` verification command completed `BUILD SUCCESSFUL`; Gradle
  reported the compiled/test tasks up to date on this review run.  The T007
  report records the prior executed full-suite result as 67 tests, zero
  failures.

## Re-review: `d0ed7e6..b716f24`

**Outcome: ADDRESSED. No open findings.**

- `core/src/main/kotlin/io/github/thrhead/taplog/core/engine/EventEngine.kt:240-241`
  extends `unlink` with an optional `ExpectedContext`; existing callers retain
  their behavior through the default value.
- `EventEngine.kt:281-294` obtains the pair revision and applies the existing
  pre-commit `validateExpected` guard before constructing lifecycle effects or
  calling the commit boundary.  A stale relationship revision or dataset
  generation therefore returns `Conflict` without mutation, meeting FR-024 and
  the 005 management-application contract.
- `ManagementRelationshipTest.kt:107-162` proves stale link, relink, and
  unlink contexts produce `Conflict`, preserve state, and make zero commit
  attempts. `ManagementCreationTest.kt:264-309` and
  `ManagementRelationshipTest.kt:165-199` separately preserve the approved
  commit-time `false` to `StorageFailure` mapping for creation/link/relink.
- The fix retains the existing `CommitOperation` Boolean boundary and its
  `commitLifecycle` mapping, adds no persistence/schema/API layer, and does
  not touch deletion or historical lifecycle logic. `git diff --check d0ed7e6
  b716f24` completed cleanly; the changed files are the one core seam and its
  focused T003/T005 contract tests.
