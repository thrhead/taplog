# T006 — Record/Target deletion JVM contract tests

## Status

Added the T006 test-only deletion contract in
`core/src/test/kotlin/io/github/thrhead/taplog/core/engine/ManagementDeletionTest.kt`.
No production code, prior task test, Spec Kit artifact, or T007 work changed.

## Contract coverage

- `DeletionScope.preview` returns exact non-null Record–Target and null-target
  Record-wide Event impacts.
- Missing confirmation (including the cancellation path represented by
  `confirmed = false`) returns `NeedsConfirmation(PERMANENT_DELETE_IMPACT)`
  and leaves the aggregate exactly unchanged.
- Confirmed pair deletion removes only its Event, relationship, binding, and
  Undo receipt while retaining the Record, both Target definitions, the other
  pair, same-Record no-Target history, and unrelated Target/no-Target history.
- Confirmed Record-wide deletion removes every scope of that Record while
  retaining both standalone Target definitions and unrelated Target/no-Target
  Event, binding, and Undo metadata.

The feature intentionally exposes no standalone Target-deletion operation.
The tests therefore assert the observable contract boundary: every permitted
deletion scope retains Target definitions. A source-absence assertion would
not be a behavioral JVM contract test.

## TDD and verification evidence

Tests were written before any production change; T006 intentionally has no
production change. The required focused command was attempted first:

```text
GRADLE_USER_HOME=/tmp/taplog-gradle ./gradlew --no-daemon \
  -Djava.net.preferIPv4Stack=true :core:test \
  --tests 'io.github.thrhead.taplog.core.engine.ManagementDeletionTest' \
  --offline --console=plain
```

The sandboxed attempt ended before Gradle initialized, reporting
`Could not determine a usable wildcard IP for this machine`. The approved
unrestricted attempt reached `:core:classes` and `:core:jar`, but the harness
returned before `:core:compileTestKotlin` produced a completion line, compiler
diagnostic, or test result. No runtime RED or GREEN is claimed.

The controller's fresh baseline before this task is the authoritative shared
compile gate: `:core:compileTestKotlin` exited 1 before test execution with
exactly 14 unresolved T003 `CreateRecord`/`CreateTarget` references and 6 T005
`EventEngine.link`/`relink` references, all scheduled for T007. The added T006
file introduces no missing production surface; its diagnostics could not be
observed independently because the shared compile gate did not complete in
this worker's focused rerun.

`git diff --check` was run after the edits and reported no whitespace errors.

## Changed files

- `core/src/test/kotlin/io/github/thrhead/taplog/core/engine/ManagementDeletionTest.kt`
- `.superpowers/sdd/tasks/task-6-report.md`

## Self-review

- Uses the real `EventEngine`, `DeletionScope`, and a compare-and-set in-memory
  `AtomicCommitBoundary`; no mocks or duplicated deletion logic.
- Expected IDs and aggregate states are literal fixtures, so widening a scope,
  mutating without confirmation, removing Target definitions, or retaining
  deleted related metadata causes an assertion failure once T007 restores
  shared test compilation.
- Test scope stays within T006 and preserves all existing tests unchanged.

## Blockers

- T007 must implement the existing T003 creation and T005 link/relink forward
  contracts before a shared core test compile can execute this test class.
