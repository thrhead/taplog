# T003 — Record and Target creation RED contract report

## Scope and result

Added the test-only JVM creation contract in
`core/src/test/kotlin/io/github/thrhead/taplog/core/engine/ManagementCreationTest.kt`.
No production file and no T004+ file was changed.

The suite deliberately calls the missing future `CreateRecord` and
`CreateTarget` core commands.  They are the management-definition creation
seam required by T007, intentionally distinct from the existing
`CreateRecordAndLog` record-plus-first-event operation.  No production stub
was added to make this contract compile.

## Contract covered

- Valid Moment, Counter, Duration, and State Record creation stores the exact
  Record at revision `0`, reports its revision, and creates no Event.
- Omitted Counter quantity remains the domain's literal default `1`; a valid
  optional Counter unit is preserved; blank units and zero, negative, or
  malformed quantities are rejected without a commit.
- Blank Record and Target names, and behavior-inapplicable fields, are rejected
  as `EngineResult.Invalid(ResultReason.INVALID_REQUEST)` without a commit.
- State creation rejects both missing and nonexistent State Group context as
  `EngineResult.Invalid(ResultReason.MISSING_STATE_GROUP)` without a commit.
- Caller-supplied Record/Target identities remain the stored identities at
  revision `0`; creation leaves `DomainState.generation` unchanged and refuses
  to replace an existing identity with
  `EngineResult.Conflict(ResultReason.STALE_REVISION)`.

The tests use `EventEngine` with a small compare-and-commit in-memory
`AtomicCommitBoundary`; there are no mocks.  Assertions use literal ids,
names, quantities, revisions, and stable existing `EngineResult`/
`ResultReason` values.  The mutation check is explicit: accepting any invalid
name/unit/quantity/field/state-group, creating an Event, replacing an id,
incrementing initial revisions, or changing dataset generation fails at least
one test.

## RED evidence

The final focused command was:

```text
GRADLE_USER_HOME=/tmp/taplog-gradle ./gradlew --no-daemon -Djava.net.preferIPv4Stack=true :core:test --tests 'io.github.thrhead.taplog.core.engine.ManagementCreationTest' --console=plain
```

Result: exit `1` in 27 seconds. `:core:compileTestKotlin` failed only with
unresolved `CreateRecord` references (lines 38, 53, 68, 82, 100, 115, 139,
159, 188, and 216) and unresolved `CreateTarget` references (lines 173, 192,
and 220). Production compilation completed first; no test executed because the
approved future commands do not exist before T007.

The final broad command was:

```text
GRADLE_USER_HOME=/tmp/taplog-gradle ./gradlew --no-daemon -Djava.net.preferIPv4Stack=true :core:test --console=plain
```

Result: exit `1` in 27 seconds with the same and only
`ManagementCreationTest.kt` unresolved `CreateRecord`/`CreateTarget` compiler
errors. Therefore the full core lane reached test compilation and did not run
any JVM tests; there were no unrelated executed-test failures to report.

An initial sandboxed focused invocation could not start Gradle because its
file-lock service could not determine a usable wildcard IP. The two final
commands above were rerun outside that sandbox restriction; their compiler
failures are the authoritative RED evidence.

## Files changed

- `core/src/test/kotlin/io/github/thrhead/taplog/core/engine/ManagementCreationTest.kt`
- `.superpowers/sdd/tasks/task-3-report.md`

## Self-review and concerns

`git diff --check` completed with no whitespace errors. The diff was reviewed
for task scope: it contains only T003 tests and this report, keeps
`EngineResult`, `ResultReason`, expected-context ownership, and
`AtomicCommitBoundary.commit(CommitOperation): Boolean` intact, and adds no
production or T004+ edits.

The intended unresolved-command RED state is the sole blocker. T007 must add
the `CreateRecord` and `CreateTarget` command/engine handling while preserving
these result categories and the existing atomic Boolean commit boundary; only
then can this suite compile and exercise its assertion-level contracts.

## Fix round 1/5 — review assertion strengthening

Addressed the three Important findings in `ManagementCreationTest.kt` only:

- The valid Counter-with-unit case now asserts the independently constructed
  literal `Quantity.parse("2")` is stored, in addition to `UnitName("cups")`.
- Added a separate `recordCreationRejectsExplicitCounterQuantityForNonCounterBehaviors`
  test for Moment, Duration, and State Records. Each supplies the explicit
  Counter quantity `2`; the State fixture supplies an otherwise valid State
  Group, so the quantity is the invalid field under test.
- Valid Record creation now asserts the stored `Lifecycle.ACTIVE` literal.
  The successful Target creation now additionally asserts literal stored name
  `"Bottle"` and `Lifecycle.ACTIVE`, independently of the input object.

### Focused verification

```text
GRADLE_USER_HOME=/tmp/taplog-gradle ./gradlew --no-daemon -Djava.net.preferIPv4Stack=true :core:test --tests 'io.github.thrhead.taplog.core.engine.ManagementCreationTest' --console=plain
```

Output/result: exit `1` in 29 seconds. `:core:compileKotlin` was
`UP-TO-DATE`; `:core:compileTestKotlin` failed only with unresolved
`CreateRecord` at lines 39, 55, 70, 85, 103, 118, 142, 172, 192, 221, and 252,
and unresolved `CreateTarget` at lines 206, 225, and 256 of
`ManagementCreationTest.kt`. Gradle reported `BUILD FAILED`; no JVM tests ran
because T007 has not introduced those commands.

### Broad verification

```text
GRADLE_USER_HOME=/tmp/taplog-gradle ./gradlew --no-daemon -Djava.net.preferIPv4Stack=true :core:test --console=plain
```

Output/result: exit `1` in 27 seconds. `:core:compileKotlin` was
`UP-TO-DATE`; `:core:compileTestKotlin` produced the identical and only 14
unresolved command references listed above, then Gradle reported `BUILD
FAILED`. No unrelated compilation or executed-test failure was reported.

The intentional missing T007 API remains the only blocker. The review fix adds
no production code and does not add `.kotlin/` or any other generated cache to
the commit.
