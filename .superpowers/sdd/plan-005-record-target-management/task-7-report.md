# T007 implementation report

## Status

Complete. T007 is implemented in core and committed as `d0ed7e6` (`feat(core): add validated definition creation and relink`). No T008, data, app, UI, or progress-ledger work was started.

## Contract inputs reviewed

- `.superpowers/sdd/plan-005-record-target-management/task-7-brief.md`
- `specs/003-core-event-engine/contracts/event-engine.md`
- `specs/004-local-persistence/contracts/persistence.md`
- T003 `ManagementCreationTest`
- T005 `ManagementRelationshipTest`
- Relevant 005 plan, spec, data-model, research, and management-application contract sections

## RED evidence

Command:

```text
GRADLE_USER_HOME=/tmp/taplog-gradle ./gradlew --no-daemon -Djava.net.preferIPv4Stack=true :core:test --tests 'io.github.thrhead.taplog.core.engine.ManagementCreationTest' --tests 'io.github.thrhead.taplog.core.engine.ManagementRelationshipTest' --offline --console=plain
```

The first sandboxed invocation could not initialize Gradle's lock listener (`Could not determine a usable wildcard IP for this machine`), so the identical approved command was rerun outside the sandbox to obtain product RED evidence.

Result: `:core:compileTestKotlin FAILED`, exit 1. The compiler reported the expected 20 missing API references:

- 14 unresolved `CreateRecord` / `CreateTarget` references in `ManagementCreationTest` at lines 39, 55, 70, 85, 103, 118, 142, 172, 192, 206, 221, 225, 252, and 256.
- 6 unresolved `EventEngine.link` / `EventEngine.relink` references in `ManagementRelationshipTest` at lines 25, 30, 31, 32, 33, and 94.

Representative output:

```text
> Task :core:compileTestKotlin FAILED
e: .../ManagementCreationTest.kt:39:30 Unresolved reference 'CreateRecord'.
e: .../ManagementCreationTest.kt:206:40 Unresolved reference 'CreateTarget'.
e: .../ManagementRelationshipTest.kt:25:53 Unresolved reference 'link'.
e: .../ManagementRelationshipTest.kt:94:53 Unresolved reference 'relink'.
BUILD FAILED in 32s
```

## Implementation

- Added `CreateRecord` and `CreateTarget` commands with `source` and optional `ExpectedContext`.
- Added pure `DefinitionManagement` creation, link, and relink transformations.
- Routed creation through `EventEngine.apply` and the existing `AtomicCommitBoundary`.
- Kept the existing Boolean commit contract: `false` maps to `EngineResult.StorageFailure`.
- Added pre-commit expected revision and dataset-generation checks; stale supplied context maps to `Conflict`.
- Validated required names, all four behaviors, behavior-specific fields, positive Counter quantity, optional valid Counter unit, and existing State Group membership.
- Preserved caller-assigned IDs while establishing active, revision-zero, event-free definitions.
- Implemented link for an absent active pair and explicit relink only for an existing inactive pair.
- Relink changes only `RecordTarget.linked` and `RecordTarget.revision`; Events, snapshots, bindings, State generations, terminal lifecycle state, definition revisions, and dataset generation are untouched.
- Left `EngineResult` and `ResultReason` unchanged because their approved categories and reasons already cover T007.

## GREEN evidence

### Focused T003/T005 tests

Final command: same focused command shown in RED evidence.

```text
> Task :core:test
BUILD SUCCESSFUL in 17s
4 actionable tasks: 1 executed, 3 up-to-date
```

The two classes contain 15 passing tests: 12 creation tests and 3 relationship tests.

### Full core suite and baseline T004 finding

Initial full-suite command:

```text
GRADLE_USER_HOME=/tmp/taplog-gradle ./gradlew --no-daemon -Djava.net.preferIPv4Stack=true :core:test --offline --console=plain
```

The first full run reached tests after T007 restored compilation and exposed one existing T004 contract failure:

```text
ManagementDefinitionTest > recordAndTargetArchiveUnarchiveAdvanceOnlyTheirOwnRevisions FAILED
java.lang.AssertionError: expected:<Revision(value=2)> but was:<Revision(value=1)>
67 tests completed, 1 failed
```

Root cause: `unarchiveTarget` bypassed `DefinitionManagement.unarchive` and changed lifecycle without incrementing the Target revision. The already-present T004 test was the RED regression case. The minimum correction routes that path through the approved pure helper.

Focused regression command:

```text
GRADLE_USER_HOME=/tmp/taplog-gradle ./gradlew --no-daemon -Djava.net.preferIPv4Stack=true :core:test --tests 'io.github.thrhead.taplog.core.engine.ManagementDefinitionTest.recordAndTargetArchiveUnarchiveAdvanceOnlyTheirOwnRevisions' --offline --console=plain
```

```text
> Task :core:test
BUILD SUCCESSFUL in 36s
4 actionable tasks: 4 executed
```

Final full-suite result:

```text
> Task :core:test
BUILD SUCCESSFUL in 18s
4 actionable tasks: 1 executed, 3 up-to-date
```

The generated JUnit results contain 67 tests and zero failures.

## Final checks

- `git diff --check`: exit 0 before commit.
- Implementation commit: `d0ed7e6`.
- Branch after implementation commit: `005-record-target-management`, ahead of origin by one commit.
- Production scope: only `Commands.kt`, `DefinitionManagement.kt`, and `EventEngine.kt` changed.

## Concerns

- Gradle emits existing Kotlin and Gradle deprecation warnings during clean compilation; they do not fail verification.
- The requested full-suite gate required correcting the pre-existing T004 Target-unarchive revision path once the missing T007 APIs allowed tests to compile. No T006 deletion guard behavior was changed.

## Code-quality review fix — round 1

### Review verification

The independent review finding was verified against `spec.md` acceptance scenario 4.2 and FR-024. A relationship unlink is a mutating assignment operation, so stale relationship revision or dataset generation must return `Conflict` before lifecycle effects or commit. The prior two-argument `unlink` API could not receive that context.

The review's test-coverage finding was also valid: creation, link, and relink success/validation behavior was covered, but their new pre-commit Conflict and Boolean commit-rejection paths were not.

### RED

Tests were added first for:

- stale relationship revision and dataset generation on unlink, with zero commit attempts and unchanged state;
- stale context on link/relink, with zero commit attempts and unchanged state;
- stale dataset context on CreateRecord/CreateTarget, with zero commit attempts and unchanged state;
- rejecting commits on CreateRecord/CreateTarget/link/relink, returning StorageFailure and preserving state.

Focused command:

```text
GRADLE_USER_HOME=/tmp/taplog-gradle ./gradlew --no-daemon -Djava.net.preferIPv4Stack=true :core:test --tests 'io.github.thrhead.taplog.core.engine.ManagementCreationTest' --tests 'io.github.thrhead.taplog.core.engine.ManagementRelationshipTest' --offline --console=plain
```

Expected RED result:

```text
> Task :core:compileTestKotlin FAILED
ManagementRelationshipTest.kt:144:17 Too many arguments for
  'fun unlink(recordId: RecordId, targetId: TargetId): EngineResult'.
ManagementRelationshipTest.kt:152:17 Too many arguments for
  'fun unlink(recordId: RecordId, targetId: TargetId): EngineResult'.
BUILD FAILED in 27s
```

### Fix

- Added `expected: ExpectedContext? = null` to `EventEngine.unlink`, preserving all existing two-argument callers.
- Passed unlink context into the existing lifecycle path so the relationship revision and dataset generation are checked against the same `DomainState` read used to calculate and commit lifecycle effects.
- Reused the T007 `validateExpected` result mapping: pre-commit mismatch returns `Conflict`; `AtomicCommitBoundary.commit(...) == false` remains `StorageFailure`.
- Added four focused management tests and commit-attempt assertions. No deletion behavior changed.

### GREEN

Focused management result:

```text
> Task :core:test
BUILD SUCCESSFUL in 40s
4 actionable tasks: 4 executed
```

Full core command:

```text
GRADLE_USER_HOME=/tmp/taplog-gradle ./gradlew --no-daemon -Djava.net.preferIPv4Stack=true :core:test --offline --console=plain
```

Full core result:

```text
> Task :core:test
BUILD SUCCESSFUL in 18s
4 actionable tasks: 1 executed, 3 up-to-date
```

The final JUnit output contains 71 tests and zero failures. The review fix is committed as `b716f24` (`fix(core): guard management writes with expected context`).
