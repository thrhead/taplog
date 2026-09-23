# T004 — Definition-management JVM contract tests

## Status

Implemented the test-only T004 contract in
`core/src/test/kotlin/io/github/thrhead/taplog/core/engine/ManagementDefinitionTest.kt`.
No production, T003, spec, plan, or task artifact was modified.

## Contract coverage

- Counter Record edits after an Event permit name, icon, and default-quantity
  changes, advance only the Record revision, preserve the Event identity and
  creation-time snapshot, retain behavior/unit, retain the Target, and leave
  dataset generation unchanged.
- Target name/icon edits after an Event advance only the Target revision and
  preserve the Event snapshot and Record.
- Post-Event Record behavior and Counter-unit changes throw before committing
  and leave the aggregate unchanged.
- Record and Target archive/unarchive lifecycle changes advance their own
  revisions and preserve the other definition.
- Missing Record/Target contexts return `Invalid(INVALID_REQUEST)` without a
  commit; stale Record-revision and dataset contexts return typed `Conflict`
  without a commit.
- A Boolean commit rejection for an otherwise valid definition edit remains
  `StorageFailure`, with no state mutation.

## TDD and verification evidence

Tests were written before any production change; this task intentionally makes
no production change. The initial focused command was:

```text
GRADLE_USER_HOME=/tmp/taplog-gradle ./gradlew --no-daemon \
  -Djava.net.preferIPv4Stack=true :core:test \
  --tests 'io.github.thrhead.taplog.core.engine.ManagementDefinitionTest' \
  --console=plain
```

Its sandboxed attempt stopped before Gradle compilation with `Could not
determine a usable wildcard IP`. The unrestricted rerun reached
`:core:compileTestKotlin`, but correctly failed before executing T004 because
the existing, intentionally RED T003 tests reference the not-yet-implemented
`CreateRecord` and `CreateTarget` symbols (14 unresolved references in
`ManagementCreationTest.kt`). This is the known T003/T007 dependency, not a
T004 regression. Therefore a Gradle GREEN run cannot be honestly claimed until
T007 implements those commands.

An isolated direct Kotlin-compiler attempt was also unavailable in this
environment: the cached compiler could not initialize because
`org.jetbrains.kotlin.gradle.internal.analyzer.CompilationErrorException` was
absent. It made no source changes. `git diff --check` completed successfully.

Source-level review identifies one intentional T004 RED contract against the
current engine: `unarchiveTarget` currently copies lifecycle without increasing
the Target revision, whereas this T004 contract requires revision 2 after
archive then unarchive. No fix was made because T004 is test-only.

## Changed files

- `core/src/test/kotlin/io/github/thrhead/taplog/core/engine/ManagementDefinitionTest.kt`
- `.superpowers/sdd/tasks/task-4-report.md`

## Self-review

- Scope is limited to the required T004 test file and its report.
- The tests use a compare-and-set in-memory `AtomicCommitBoundary`; they assert
  real `EventEngine` results and aggregate state rather than mock calls.
- Historical Event IDs and snapshots are asserted unchanged by ordinary edits.
- The pre-commit `Conflict` versus commit-time `StorageFailure` distinction is
  explicitly covered.
- T003 remains byte-for-byte untouched and its known RED compilation failure
  is distinguished above from the lifecycle-revision contract now captured by
  T004.
