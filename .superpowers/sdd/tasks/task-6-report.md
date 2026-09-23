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

## Fix round 1 — public Target-deletion contract and type collision

Added `publicDeletionSurfaceProvidesRecordScopesAndNoStandaloneTargetDeletion`.
It inspects only `EventEngine`'s public methods declared by the class and
requires the sole public deletion entry point to be `deleteScope`, whose first
parameter is `RecordId`. Thus a public standalone `deleteTarget(TargetId)` or
another public deletion method fails the contract. The existing tests continue
to assert that each permitted deletion scope retains Target definitions.

The test was added before the compiler-fix edit. The controller's focused run
then reported these new T006 compilation errors, separate from the known
baseline T003/T005 blockers: passing domain `Target` where Kotlin resolved the
annotation `Target?` at lines 92, 93, 95, 97, 98, and 100, followed by
unresolved `id`, `name`, and `icon` accesses at lines 133, 135, 139. The test
now imports the domain class as `DomainTarget` and uses that alias for both
fixture construction and nullable helper parameters.

Focused command rerun after the fix:

```text
GRADLE_USER_HOME=/tmp/taplog-gradle ./gradlew --no-daemon \
  -Djava.net.preferIPv4Stack=true :core:test \
  --tests 'io.github.thrhead.taplog.core.engine.ManagementDeletionTest' \
  --offline --console=plain
```

The worker invocation reached `:core:processTestResources` after
`:core:compileKotlin`, `:core:classes`, and `:core:jar` were up-to-date, then
the harness returned without a completion line. The controller then reran the
same focused command after the type fix. It exited 1 with `BUILD FAILED` at
`:core:compileTestKotlin`, reporting exactly 14 known T003
`CreateRecord`/`CreateTarget` unresolved references and 6 known T005
`EventEngine.link`/`relink` unresolved references. It reported no
`ManagementDeletionTest.kt` diagnostics and no tests ran. Thus the explicit
`DomainTarget` alias resolves the T006 type collision; runtime RED/GREEN still
cannot be claimed until T007 restores shared test compilation.

`git diff --check` after this fix produced no whitespace output (exit 0).

## Fix round 2 — JVM mangling-safe deletion API contract

The public deletion assertion now normalizes each declared `EventEngine` JVM
method name by stripping the Kotlin value-class mangling suffix and `$default`
bridge suffix before comparing the API set. It therefore accepts the permitted
`deleteScope-uYup2DM` and generated `deleteScope-uYup2DM$default` methods as
one `deleteScope` API, without relying on erased JVM parameter classes.

It also checks `Command`'s Java sealed permitted subclasses and rejects any
subclass whose simple name identifies both deletion and Target handling. This
guards a future command-based standalone Target delete in addition to a direct
public `EventEngine` method. The check relies only on Java 17 sealed-class
reflection, the project runtime level; no production reflection support was
added.

Focused command after this test-only change:

```text
GRADLE_USER_HOME=/tmp/taplog-gradle ./gradlew --no-daemon \
  -Djava.net.preferIPv4Stack=true :core:test \
  --tests 'io.github.thrhead.taplog.core.engine.ManagementDeletionTest' \
  --offline --console=plain
```

Actual worker output downloaded Gradle 9.6.0 and started the single-use daemon,
then the harness returned before compilation or test output. The earlier
controller focused run after the type fix remains the completed shared compile
evidence: exit 1 at `:core:compileTestKotlin`, exactly 14 T003 and 6 T005
unresolved references, no T006 diagnostics, and no tests executed. No PASS is
claimed.

## Fix round 3 — explicit JVM method-name mapping

The round-two controller compile found three T006 errors on the function
reference `deletionMethods.map(::normalizedJvmMethodName)`: its elements are
reflection `Method` objects, while the normalizer accepts a `String`. The test
now maps explicitly with `method.name`, preserving the normalized public-method
and sealed-command deletion guards.

The controller's pre-fix focused command reached `:core:compileTestKotlin` and
reported those three line-17 T006 inference/inapplicable-reference errors plus
the known 14 T003 and 6 T005 unresolved references; it then reported `BUILD
FAILED`, and no test method ran. The worker reran the same focused command
after the one-line fix; its actual output reached `:core:processTestResources`
after `:core:compileKotlin`, `:core:classes`, and `:core:jar` were up-to-date,
then the harness returned before test compilation completed. No new T006
diagnostic or PASS is claimed. The required shared compile verification remains
blocked until T007.
