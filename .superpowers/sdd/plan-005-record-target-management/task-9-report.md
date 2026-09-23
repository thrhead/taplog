# Task T009 report — management persistence adapter boundary tests

## Status

Complete. Added `ManagementPersistenceAdapterTest.kt` only. The test suite exercises the
management adapter over the existing test-owned `InMemoryLocalPersistence` atomic boundary;
the existing 004 Room/repository suite remains the coverage for Room internals.

## Coverage

- Aggregate read/commit round-trip across records, targets, relationships, events, state groups,
  state scopes/generations, bindings, receipts, and dataset metadata.
- Core-produced target-archive lifecycle diff: terminal Duration history, orphaned binding,
  invalidated Undo receipt, and preserved relationship.
- Core-produced confirmed pair deletion: removes only the selected relationship/history/binding/
  receipt while retaining the Record, both standalone Targets, another target scope, and no-target
  history.
- Stale expected-state compare/write returns `false` and leaves the newer aggregate intact.
- Adapter write rejection maps through `EventEngine` to `StorageFailure` and publishes no event.

## TDD note

T009 is test-only over behavior implemented in T008 and earlier core/data tasks, so no meaningful
production RED phase exists without manufacturing a failure. The initial focused test run instead
found one fixture-expectation error: archived lifecycle invalidates and removes the affected Undo
receipt while preserving its binding invalidation. After tracing `EventEngine.applyLifecycle`, the
assertion was corrected to the core-produced state.

## Verification

Initial sandboxed focused command could not start Gradle because
`FileLockContentionHandler` could not determine a usable wildcard IP. The outside-sandbox focused
run before the expectation correction executed 5 tests and reported the described single test
failure. The controller then verified the current shared file with:

```text
GRADLE_USER_HOME=/tmp/taplog-gradle ./gradlew --no-daemon -Djava.net.preferIPv4Stack=true :data:testDebugUnitTest --tests 'io.github.thrhead.taplog.data.persistence.ManagementPersistenceAdapterTest' --console=plain
```

Result: exit 0, `BUILD SUCCESSFUL in 1m 25s`, 19 actionable tasks (3 executed, 16 up-to-date).

`git diff --check` also exited 0 during self-review.

## Concerns

No production, schema, migration, persistence-contract, Spec Kit, or progress-ledger files were
changed. The JVM adapter tests deliberately use the existing in-memory atomic fixture; Room
transaction and mapping coverage remains in the prior 004 suite.
