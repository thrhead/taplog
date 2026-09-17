# Persistence Contract

Package: `io.github.thrhead.taplog.data.persistence`

```kotlin
interface LocalPersistence : AtomicCommitBoundary {
    override fun read(): DomainState
    override fun commit(operation: CommitOperation): Boolean
}
```

`read()` returns the latest fully committed `DomainState`. It must not expose Room entities or Android types. `commit()` is the only write path used by `EventEngine` and applies the complete `CommitOperation` atomically.

## Commit semantics

1. Begin one Room transaction and acquire the repository write serialization mechanism.
2. Read current dataset metadata and all rows needed for the aggregate.
3. Compare `operation.expected` against current entity revisions, DatasetGeneration, State generations, sequence boundary, and lifecycle context.
4. If the expected context is stale, make no writes and return `false`. With the current approved Boolean port, `EventEngine` reports this as `StorageFailure`; command-level stale guards remain the path that reports `Conflict`.
5. Validate mapped state and lifecycle effect, then replace/update the affected rows in one transaction.
6. Advance DatasetGeneration/nextSequence exactly as represented by the committed state and commit the transaction.
7. Return `true` only after transaction success. Any database, mapping, constraint, or migration exception is surfaced to the adapter caller as a storage failure; no partial state is visible.

The existing `EventEngine` remains responsible for mapping a `false` compare result to `StorageFailure` and for preserving `Applied` only after a successful commit. Its pre-commit expected-context checks report `Conflict`. A future core contract may add a typed commit outcome for race conflicts, but this feature does not redesign the approved Boolean port. Post-commit channel feedback is outside this contract.

## Failure and restart contract

- Crash before transaction commit: previous state is readable after restart.
- Crash after transaction commit: complete new state is readable after restart.
- Failed mapping, invariant validation, migration, or database write: old valid state remains available and the caller receives `StorageFailure`.
- Corrupt rows are never silently dropped or guessed into a valid state.
