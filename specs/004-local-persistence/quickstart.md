# Local Persistence Validation Quickstart

## Prerequisites

- Android SDK/API 26+ and the repository Gradle wrapper.
- No network, account, UI, parser, NFC, Widget, or Quick Settings service is required.
- The `:data` Android library is included and depends only on `:core`; the initial database schema is v1.
- The checked-in Room schema export is `data/schemas/io.github.thrhead.taplog.data.persistence.TapLogDatabase/1.json`; this feature registers no production upgrade edge.

## Commands

```text
./gradlew :data:testDebugUnitTest
./gradlew :data:connectedDebugAndroidTest
./gradlew :data:lintDebug :detekt
```

## Required validation scenarios

1. Build a `DomainState` containing Record, Target, relationship, State Group, binding, Undo receipt, all four Event payloads, snapshots, generations, and metadata. Persist and read it; assert value-for-value mapping from the single Event row with discriminator and typed nullable payload columns.
2. For SC-001, commit each of Moment, Counter, Duration, and State at least 100 times sequentially (400 behavior commits total); kill/reopen the database and assert 100% of Event identity, timestamp, sequence, revision, payload, source, and snapshot values are unchanged.
3. For each atomic operation family (Event, Record+Event, archive/unlink, permanent delete), execute at least 20 deterministic failure-injection attempts after the logical write phases; assert that 100% of attempts expose no partial rows and produce the expected deterministic failure category (`StorageFailure` for commit-time failures).
4. Query chronology, exact Record/Target/no-Target scope, current OPEN Duration, and current State; assert ascending `(occurredAt, sequence)` ordering, equal-time sequence ordering, scope isolation, OPEN-only Duration selection, and active-generation State filtering after reopen.
5. Run two concurrent commits with the same expected revision; assert exactly one applies and the other returns false/`StorageFailure` without overwriting newer data, while command-level stale checks still return `Conflict`.
6. Archive/unlink a scope; assert history remains, OPEN Duration becomes terminal INCOMPLETE, State generation advances, binding becomes ORPHANED, and related Undo metadata is invalidated.
7. Preview and confirm the core `DeleteImpact(recordId, targetId?, eventIds)` for: Record-wide deletion, Record–Target pair deletion, events without Target, shared relationships, and unrelated history. Assert exact row retention/deletion; do not perform standalone Target deletion.
8. Seed invalid/corrupt Room rows and assert typed `CorruptRowFailure`/`MappingFailure`, their non-retryable classification, no silent repair or row dropping, and no invalid `DomainState` returned; separately verify retryable `DatabaseReadFailure`/`DatabaseOpenFailure` paths do not rewrite data.
9. Create a fresh v1 database and verify the production migration matrix is empty. Run a test-only failing migration transaction; assert rollback, old-v1 usability on a later open, and typed migration/open failure.
10. Interrupt commits before begin, after compare, after each write phase, before SQLite commit, and after commit before result observation; reopen and assert exactly old-or-new complete state, never partial rows. Retry with the old expected context and assert no duplicate commit.
11. Run `OfflineLocalPersistenceTest`: execute core commands against the in-memory adapter and Room adapter with no account, network, server, AI, or channel dependency; assert local read/commit succeeds while external services are unavailable.
