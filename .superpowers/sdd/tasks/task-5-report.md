# T005 — Relationship-management JVM contract tests

## Status

Implemented the T005 test-only contract in
`core/src/test/kotlin/io/github/thrhead/taplog/core/engine/ManagementRelationshipTest.kt`.
No production code, T003/T004 test, Spec Kit artifact, app/data file, or T006
file was changed.

## Contract coverage

- Link accepts an active Record and active Target, creates a linked pair at
  revision zero, and makes that pair eligible for a new Moment Event.
- Link rejects archived and nonexistent Targets as `Invalid(INACTIVE_SCOPE)`
  without mutating the successfully linked aggregate.
- Unlink advances only the selected relationship revision, blocks its future
  eligibility through the existing engine, marks an open Duration terminal,
  orphans affected bindings, invalidates affected Undo receipts, and resets an
  affected State scope/generation without changing the other pair.
- Explicit relink of an unlinked pair advances its revision, allows a new
  Event, and preserves the pre-existing historical Event IDs/snapshots,
  terminal Duration, State generation, and orphaned binding unchanged.

## TDD and verification evidence

Tests were added before any production change; T005 deliberately makes no
production change. The focused command was:

```text
GRADLE_USER_HOME=/tmp/taplog-gradle ./gradlew --no-daemon \
  -Djava.net.preferIPv4Stack=true :core:test \
  --tests 'io.github.thrhead.taplog.core.engine.ManagementRelationshipTest' \
  --offline --console=plain
```

The first sandboxed attempt exited before Gradle setup because its distribution
download was blocked (`java.net.SocketException: Operation not permitted`). An
unrestricted run downloaded Gradle/dependencies; an offline unrestricted
rerun reached `:core:compileTestKotlin` and reported `BUILD FAILED in 30s`
(exit 1). No T005 test executed because shared test compilation stopped at the
known T003/T007 blocker: 14 unresolved references in
`ManagementCreationTest.kt` to `CreateRecord` (lines 39, 55, 70, 85, 103, 118,
142, 172, 192, 221, and 252) and `CreateTarget` (lines 206, 225, and 256).

The same compile output also records the intentionally RED T005 seams:
`EventEngine.link` at T005 lines 23, 28, and 29, and `EventEngine.relink` at
line 86. These missing operations are required by the approved T005 ruling
and scheduled for T007. Since T003 blocks shared compilation, this task does
not claim the T005 tests executed or independently observed RED at runtime.

`git diff --check` completed successfully (exit 0).

## Changed files

- `core/src/test/kotlin/io/github/thrhead/taplog/core/engine/ManagementRelationshipTest.kt`
- `.superpowers/sdd/tasks/task-5-report.md`

## Self-review

- The tests use real `EventEngine` behavior and a compare-and-set in-memory
  `AtomicCommitBoundary`, not mocked calls.
- Expectations use fixed IDs, revisions, snapshots, state generations, and
  terminal/orphan lifecycle values so mutations to relationship handling are
  observable.
- Link/relink follow the approved existing `unlink(recordId, targetId)` engine
  style; no new command or parallel engine is proposed.
- The contract deliberately preserves T003 unchanged. T007 must provide the
  four missing compile symbols without weakening these tests.

## Concerns

- Current shared-core compilation cannot prove the unlink assertions execute
  until T007 resolves the existing T003 creation references. The new link and
  relink references are intentional forward contracts, not production fixes in
  T005.
