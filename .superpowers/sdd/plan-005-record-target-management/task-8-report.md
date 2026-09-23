# T008 implementation report

## Status

Complete. T008 exposes the minimum management persistence boundary without adding a schema,
repository contract, result taxonomy, or app-visible Room type. `ManagementPersistencePort`
inherits the existing `AtomicCommitBoundary`; the internal adapter delegates `read()` and the
Boolean `commit()` unchanged. A public Context-based factory composes the internal database and
Room boundary while returning only the port.

No T009 tests, task-list changes, progress-ledger changes, or files outside the two canonical T008
implementation files were made. New boundary tests remain owned by T009 as directed, so T008 used
the existing data JVM suite and consumer compilation instead of adding a test-first case.

## Commit

`ed1881f` — `feat(data): expose management persistence boundary`

The commit contains only:

- `data/src/main/kotlin/io/github/thrhead/taplog/data/persistence/ManagementPersistencePort.kt`
- `data/src/main/kotlin/io/github/thrhead/taplog/data/persistence/ManagementPersistenceAdapter.kt`

## Actual test results

1. Initial sandboxed `:data:testDebugUnitTest` and compilation attempts both exited 1 before task
   execution because Gradle could not initialize `FileLockContentionHandler`:
   `Could not determine a usable wildcard IP for this machine`. The requested commands were rerun
   outside the sandbox.
2. `GRADLE_USER_HOME=/tmp/taplog-gradle ./gradlew -Djava.net.preferIPv4Stack=true
   :data:testDebugUnitTest --console=plain` exited 0: `BUILD SUCCESSFUL in 53s`, 19 actionable tasks
   (8 executed, 11 up-to-date).
3. The first offline app-consumer compilation exited 1 because the newly configured Compose and
   AndroidX artifacts were not cached. This was a dependency-cache baseline block, before Kotlin
   compilation, rather than a source failure.
4. `GRADLE_USER_HOME=/tmp/taplog-gradle ./gradlew -Djava.net.preferIPv4Stack=true
   :app:compileDebugKotlin --console=plain` downloaded the missing artifacts and exited 0:
   `BUILD SUCCESSFUL in 26s`, 17 actionable tasks (1 executed, 16 up-to-date).
5. Fresh final verification with `GRADLE_USER_HOME=/tmp/taplog-gradle ./gradlew
   -Djava.net.preferIPv4Stack=true :data:testDebugUnitTest :app:compileDebugKotlin
   --console=plain` exited 0: `BUILD SUCCESSFUL in 4s`, 25 actionable tasks (2 executed,
   23 up-to-date). This run recompiled `:data`; the unchanged data tests and app compilation were
   up-to-date after their successful runs above.
6. `git diff --check` exited 0 immediately before the implementation commit.

## Concerns

- No architectural concern remains. The public port adds no methods to the existing core boundary,
  and commit rejection still returns `false` for `EventEngine` to map to `StorageFailure`.
- Gradle reports existing deprecation warnings, and the SQLite JVM test dependency reports a Java
  native-access warning. Neither warning failed verification.
- The production factory owns a fixed `taplog.db` name and returns a process-lifetime boundary; no
  database lifecycle API was added because it is outside the approved minimal application port.

## Code-quality review fix — round 1

Status: complete. Review confirmed that repeated factory calls built independent uncloseable Room
database instances. The factory now delegates to a private process-scoped holder whose synchronized
initializer creates and caches exactly one `ManagementPersistencePort`. The public API remains
`Context` in and `ManagementPersistencePort` out; the adapter, database, Room boundary, DAOs, and
entities remain internal. No close API, schema change, persistence-contract expansion, or T009 test
was added.

Fix commit: `35ed87a` — `fix(data): reuse management database instance`.

Verification command:

```text
GRADLE_USER_HOME=/tmp/taplog-gradle ./gradlew -Djava.net.preferIPv4Stack=true :data:testDebugUnitTest :app:compileDebugKotlin --console=plain
```

Actual result: exit 0, `BUILD SUCCESSFUL in 7s`, 25 actionable tasks (8 executed, 17 up-to-date).
The run recompiled `:data`, recompiled `:app`, and executed `:data:testDebugUnitTest`. Existing
SQLite native-access and Gradle deprecation warnings remained non-failing. `git diff --check`
exited 0 immediately before the fix commit.

Remaining concern: no implementation concern remains from this review. Direct identity coverage of
repeated factory calls remains with T009, which exclusively owns the new data boundary tests.
