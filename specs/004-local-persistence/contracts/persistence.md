# Persistence Contract

Package: `io.github.thrhead.taplog.data.persistence`

```kotlin
interface LocalPersistence : AtomicCommitBoundary {
    override fun read(): DomainState
    override fun commit(operation: CommitOperation): Boolean
}
```

`read()` returns the latest fully committed `DomainState` or throws a typed `PersistenceFailure`; it must not expose Room entities or Android types. `commit()` is the only write path used by `EventEngine` and applies the complete `CommitOperation` atomically.

```kotlin
sealed class PersistenceFailure(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
class DatabaseOpenFailure(message: String, cause: Throwable? = null) : PersistenceFailure(message, cause)
class DatabaseReadFailure(message: String, cause: Throwable? = null) : PersistenceFailure(message, cause)
class MigrationFailure(message: String, cause: Throwable? = null) : PersistenceFailure(message, cause)
class CorruptRowFailure(message: String, cause: Throwable? = null) : PersistenceFailure(message, cause)
class MappingFailure(message: String, cause: Throwable? = null) : PersistenceFailure(message, cause)
```

These are persistence-facing conceptual categories; their concrete package/API shape is an implementation detail inside `:data` and must not enter `:core`.

Retryability classification is deterministic:

| Category | Retryability | Rule |
|---|---|---|
| `DatabaseReadFailure` | Retryable | A later read may retry without rewriting persisted data. |
| `DatabaseOpenFailure` | Retryable | A later open may retry without deleting, resetting, or replacing the database. |
| `MigrationFailure` | Non-retryable | Do not retry until an approved migration or external data repair is available. |
| `CorruptRowFailure` | Non-retryable | Do not retry until an approved data replacement or repair is available; never silently repair. |
| `MappingFailure` | Non-retryable | Do not retry unchanged invalid data; the invalid row/state must be corrected externally. |

Commit-time persistence failures remain Boolean `false` and are surfaced by the Event Engine as `StorageFailure`; they are not assigned a new typed commit result. An uncertain post-commit caller retry must use the old expected context and may not blindly duplicate the operation.

## Commit semantics

1. Begin one Room transaction and acquire the repository write serialization mechanism.
2. Read current dataset metadata and all rows needed for the aggregate.
3. Compare `operation.expected` against current entity revisions, DatasetGeneration, State generations, sequence boundary, and lifecycle context. Permanent-delete impact is owned by core `DeleteScope`/`DeleteImpact`; the current `CommitOperation` carries the resulting `DomainState`, not a second adapter-level impact object.
4. If the expected context is stale, make no writes and return `false`. With the current approved Boolean port, `EventEngine` reports this as `StorageFailure`; command-level stale guards remain the only path that reports `Conflict`.
5. Validate mapped state and lifecycle effect, then replace/update the affected rows in one transaction. For permanent delete, the adapter persists exactly the state produced by core `DeleteScope`/`DeleteImpact`; it does not recompute, widen, or invent a deletion impact.
6. Advance DatasetGeneration/nextSequence exactly as represented by the committed state and commit the transaction.
7. Return `true` only after transaction success. A stale compare returns `false`; commit-time database, mapping, constraint, or write failures also return `false` and are surfaced by the Event Engine as `StorageFailure`. No partial state is visible. Open/read/migration failures throw the typed `PersistenceFailure` categories above because the Boolean commit port cannot represent repository-open errors.

The existing `EventEngine` remains responsible for mapping a `false` compare result to `StorageFailure` and for preserving `Applied` only after a successful commit. Its pre-commit expected-context checks report `Conflict`. A future core contract may add a typed commit outcome for race conflicts, but this feature does not redesign the approved Boolean port. Post-commit channel feedback is outside this contract.

## Failure and restart contract

- Supported interruption points are before transaction begin, after compare and before writes, after each logical write phase, after metadata update before SQLite commit, and after commit before the caller observes the Boolean.
- Crash/process kill before SQLite commit: the previous complete state is readable after reopening the same database.
- Crash/process kill after SQLite commit, including before the caller observes `true`: the complete new state is readable after reopening; a retry with the old expected context is stale and cannot duplicate the operation.
- Room transaction rollback, failed mapping, invariant validation, or database write: no partial rows are visible, the old valid state remains available, and the caller receives Engine `StorageFailure` when the failure occurs during commit.
- Failed migration/open: the migration transaction is rolled back; the adapter does not delete/reset/replace the old database, and a later open may retry the prior valid v1 database while the failed attempt throws `MigrationFailure` or `DatabaseOpenFailure`. Retrying the prior valid open does not retry an unsupported or failed migration until its approved migration/data prerequisite is available.
- Corrupt rows are never silently dropped, guessed into a valid state, or rewritten. `CorruptRowFailure` prevents `read()` from returning an invalid aggregate.

## Query contract

- Chronological Event queries filter by the requested Record and optional exact Target scope, include no-Target Events only when the requested scope is no-Target or a core-defined Record-wide query explicitly requests all scopes, and order ascending by `(occurredAt, sequence)`.
- Equal `occurredAt` values are always ordered by persisted monotonic `sequence`; ordering is stable across close/reopen.
- Current Duration queries filter by exact Record + optional Target scope and return at most one `OPEN` row; `COMPLETED`/`INCOMPLETE` rows remain history and are not current.
- Current State queries filter by exact State Group + optional Target scope + active generation and return the latest `(occurredAt, sequence)` Event only; older generations never become current.
- A Target-scoped query never returns another Target's Events; a no-Target query never substitutes a real Target scope.

## Test adapter ownership

The fake/in-memory `LocalPersistence` implementation is test-only under `data/src/test`. It is the owner of pure JVM repository-contract, query, atomicity, offline, and failure-injection tests; Room entities, Android types, and production fallback adapters do not leak into `:core`. Android/Room tests own database-open, migration, corrupt-row, SQLite constraint, and crash/reopen behavior.
