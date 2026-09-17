# Local Persistence Validation Quickstart

## Prerequisites

- Android SDK/API 26+ and the repository Gradle wrapper.
- No network, account, UI, parser, NFC, Widget, or Quick Settings service is required.

## Commands

```text
./gradlew :data:testDebugUnitTest
./gradlew :data:connectedDebugAndroidTest
./gradlew :data:lintDebug :detekt
```

## Required validation scenarios

1. Build a `DomainState` containing Record, Target, relationship, State Group, binding, Undo receipt, all four Event payloads, snapshots, generations, and metadata. Persist and read it; assert value-for-value mapping.
2. Commit Moment, Counter, Duration, State, and `CreateRecordAndLog`; kill/reopen the database and assert Event identity, sequence, revision, payload, and snapshot are unchanged.
3. Inject a failure after each logical write phase; assert no partial rows are visible and the result is `StorageFailure`.
4. Run two concurrent commits with the same expected revision; assert exactly one applies and the other observes stale context without overwriting newer data.
5. Archive/unlink a scope; assert history remains, OPEN Duration becomes terminal INCOMPLETE, State generation advances, binding becomes ORPHANED, and related Undo metadata is invalidated.
6. Confirm a bounded permanent delete; assert only the selected graph is removed and unrelated/no-Target history remains.
7. Migrate each supported prior schema and inject a failed migration; assert the migrated data is valid and failed migration leaves the previous database usable.
