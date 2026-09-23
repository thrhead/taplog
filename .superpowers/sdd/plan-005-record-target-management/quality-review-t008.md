# T008 code-quality review — `f987a45..ed1881f`

## Result: FINDINGS

### Medium — factory creates a new uncloseable Room database for every caller

- **Location:** `data/src/main/kotlin/io/github/thrhead/taplog/data/persistence/ManagementPersistenceAdapter.kt:11`
- **Evidence:** `createManagementPersistencePort()` calls `TapLogDatabase.builder(...).build()` on every invocation, then returns an interface that has no lifecycle/close operation. `TapLogDatabase.builder()` uses the application context, so the context is retained safely, but it does not provide process-scoped instance reuse.
- **Impact:** Calling the public factory from more than one application composition site (or after an Activity/ViewModel recreation) creates independent live `RoomDatabase` instances for the same `taplog.db`. Their executors/open helpers cannot be closed through `ManagementPersistencePort`; over time this leaks resources and makes the intended process-lifetime database ownership unenforceable.
- **Required resolution:** Make the factory return a single process-scoped port/database instance, or expose an ownership/lifecycle arrangement that makes repeated construction impossible at normal app composition sites. Preserve the public port-only API and the fixed `taplog.db` name.

## Checks with no finding

- The public API is limited to `ManagementPersistencePort` and `createManagementPersistencePort(Context)`; `TapLogDatabase`, `RoomAtomicCommitBoundary`, `RoomLocalPersistence`, entities, and DAOs remain Kotlin `internal` within `:data`.
- `:app` depends directly on both `:data` and `:core`, so the inherited public `AtomicCommitBoundary`, `DomainState`, and `CommitOperation` types are resolvable without exposing Room APIs.
- The factory delegates context handling to `TapLogDatabase.builder()`, which immediately uses `context.applicationContext`; its non-null Kotlin parameter does not retain an Activity context.
- `ManagementPersistenceAdapter.read()` and `commit()` delegate unchanged. In particular, a `false` commit remains `false`, allowing `EventEngine` to map it to `StorageFailure`.
- The fixed `taplog.db` name is the only production database-name definition in the reviewed range; no schema, migration, DAO, or persistence-contract change was introduced.
- `git diff --check f987a45 ed1881f` completed successfully.

## Review scope

Reviewed the immutable `f987a45..ed1881f` diff and the related public/internal APIs. The working tree contained later modifications while this review ran; they were not included in this assessment.

---

## Remediation re-review — `ed1881f..35ed87a`

## Result: APPROVED

### Prior finding: addressed

- **Location:** `data/src/main/kotlin/io/github/thrhead/taplog/data/persistence/ManagementPersistenceAdapter.kt:11-25`
- `createManagementPersistencePort()` now obtains its port from the private `ManagementPersistenceHolder`. Every read and write of `instance` occurs within the `@Synchronized get()` method, so concurrent callers cannot construct or observe competing ports. The successful creation is assigned before the method returns, giving later callers the same fully initialized boundary.
- The holder keeps one Room database for the Android process. Process death drops only the in-memory holder; reopening uses the unchanged `taplog.db`, which preserves the intended persisted aggregate lifecycle.
- No public lifecycle API, Room implementation type, DAO, entity, or new persistence contract was exposed. The public factory signature and `ManagementPersistencePort` remain unchanged, while `read()` and Boolean `commit()` still delegate exactly to the existing atomic boundary.
- `git diff --check ed1881f 35ed87a` completed successfully. No open findings in this scoped remediation review.
