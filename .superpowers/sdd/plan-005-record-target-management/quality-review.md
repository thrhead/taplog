# T007 quality review — `eeb4dda..d0ed7e6`

## FINDINGS

### Important — unlink cannot enforce the required pre-commit relationship/dataset conflict

- **Location:** `core/src/main/kotlin/io/github/thrhead/taplog/core/engine/EventEngine.kt:236-240`
- **Issue:** T007 adds `ExpectedContext` to `link` and `relink`, but `unlink` still
  has only `(recordId, targetId)`. An assignment use case therefore cannot supply
  the displayed relationship revision or dataset generation for unlink, and the
  engine cannot return the required command-level `Conflict` before attempting a
  commit. A stale assignment screen can issue an unlink against newer relationship
  state; the only protection left is a Boolean boundary rejection, which is
  surfaced as `StorageFailure` rather than the required stale-context conflict.
- **Impact:** This violates the feature's expected-context invariant for
  relationship mutations and prevents the app layer from implementing the
  deterministic stale-refresh behavior promised by the management contract.
- **Required correction:** Add optional `ExpectedContext` to `unlink`, validate
  the current `RecordTarget.revision` and `DomainState.generation` before
  lifecycle mutation, and add stale revision/dataset tests proving no commit.

### Minor — new creation/relationship commit and conflict paths are untested

- **Location:** `core/src/test/kotlin/io/github/thrhead/taplog/core/engine/ManagementCreationTest.kt:10-280`,
  `core/src/test/kotlin/io/github/thrhead/taplog/core/engine/ManagementRelationshipTest.kt:10-166`
- **Issue:** The existing contract tests cover successful creation/relink and
  input validation, but do not exercise the new `CreateRecord`/`CreateTarget` or
  `link`/`relink` paths with stale expected dataset/relationship context or a
  rejecting `AtomicCommitBoundary`.
- **Impact:** The T007-specific guarantees that stale context returns `Conflict`
  before commit and that a Boolean commit rejection returns `StorageFailure` can
  regress without a focused failing test.
- **Required correction:** Add focused tests for those result categories and
  assert state is unchanged for each non-Applied result.

## Review notes

- The new creation validation and normalization, pair-only relink transition,
  history preservation, and Boolean commit handling otherwise match the reviewed
  T007 requirements.
- `git diff --check eeb4dda d0ed7e6` completed without whitespace errors.

## Re-review — `d0ed7e6..b716f24`

### Outcome: APPROVED

- **Prior Important finding — addressed.** `EventEngine.unlink` now accepts an
  optional `ExpectedContext` and routes it into `lifecycle`. For a target-scoped
  lifecycle operation, `lifecycle` obtains the current `RecordTarget.revision`
  and invokes `validateExpected` before computing any lifecycle effect or
  calling the commit boundary. `staleRelationshipContextsConflictBeforeLinkRelinkOrUnlinkCommit`
  proves stale unlink revision and dataset contexts return `Conflict`, preserve
  state, and make zero commit attempts.
- **Prior Minor finding — addressed.** The creation tests now cover stale
  dataset conflicts and rejected Boolean commits for both Record and Target
  creation. The relationship tests cover stale link/relink contexts and
  rejected Boolean commits for both link and relink. Each verifies unchanged
  state and the expected commit-attempt count; the existing lifecycle test
  already covers rejected unlink commits.
- **Scoped regression check.** The added defaulted parameter preserves existing
  unlink callers. `archiveRecord` continues to invoke the same lifecycle path
  with no expected context, so its behavior is unchanged. The diff does not
  alter relationship state transitions, lifecycle effects, history, or commit
  result mapping.
- `git diff --check d0ed7e6 b716f24` completed without whitespace errors.
