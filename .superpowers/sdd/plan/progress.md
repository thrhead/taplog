# SDD ledger — plan: /workspaces/taplog/specs/004-local-persistence/plan.md

## Setup and reconciliation

- Workspace: `/workspaces/taplog` on dedicated branch `004-local-persistence`; it is a normal checkout rather than a linked worktree. Ruling: use this explicitly approved feature workspace because the user directed continuation there when an additional worktree is unsuitable — cost if wrong: changes would share this feature checkout, not `main`.
- Checklist gate: `requirements.md` 16/16 checked; implementation may proceed.
- Existing task status: `tasks.md` has no checked implementation tasks. Existing `:data` scaffold is treated as pre-existing code and is not credited to a task until task-level verification and review pass.
- Baseline: `./gradlew :core:test` completed successfully after granting wrapper-cache access.
- Ruling: the SDD `task-brief` helper cannot parse this repository's `T001` bullet format (it only recognizes `# Task N` headings), so the controller creates an exact per-task brief from the canonical `tasks.md` entry in this plan workspace — cost if wrong: a transcription error could omit task scope; each brief cites its source task and is task-reviewed.

## Pre-flight interface/file overlap scan

| Tasks | Producer / consumer | Finding / ruling |
|---|---|---|
| T001–T004 | Gradle module → package/fixtures | Sequential; the existing scaffold makes T001/T002 verification-first. |
| T005–T010 | entities → DAOs/mapper/database | Shared entity and mapper interfaces; T005/T006 precede all consumers; T007 and T008a may proceed only after them. |
| T008a–T009/T018 | mapper primitives → mapper tests | Tests must be RED before their implementation increment. |
| T008–T011/T012/T013 | aggregate mapper → repository/tests | T011 consumes complete mapper; tests follow read path. |
| T014–T019 | repository behavior paths → lifecycle/restart tests | Sequential implementation in RoomLocalPersistence; T018 may be added after mapper behavior is present. |
| T020–T026 | lifecycle/deletion paths → JVM/Room tests | Sequential on RoomLocalPersistence, then independent test tracks. |
| T027–T031 | atomic adapter → failure/Room/core verification | T027/T028 precede all verification; core contracts remain untouched. |
| T032–T035 | database/migration registry → schema/tests | T032 precedes schema export, then migration tests. |
| T036–T040 | foundations and all integration work → final checks | T038/T039 only after full implementation and offline coverage. |

## Global constraints reviewed

- `:core` stays Android/framework independent; `:data` owns Room/SQLite and depends only on `:core`.
- Schema is v1 with an empty production migration matrix and no destructive fallback.
- Event storage is one typed-payload `EventEntity` row; historical snapshots are never reconstructed or rewritten.
- No global mutation revision; adapter stale/race rejection is Boolean `false`, mapped by the existing engine to `StorageFailure`.
- Delete behavior persists the core-produced `DeleteScope`/`DeleteImpact` state only; no adapter-side widening or Target deletion.

Task 1: fix round 1/5 (1 addressed, 0 open — Gradle verification; commits 262e763..4417865)
Task 1: complete (commits f3131e3..4417865, review clean)
Task 2: complete (commits 77a82b3..e17483b, review clean; focused `:data:testDebugUnitTest` evidence in task report)
Task 3: complete (commits 666267a..bba4e67, review clean; setup-only task, prior focused Gradle lane unchanged)
Task 4: complete (commits 6145b01..e2c6593, review clean)
Task 5: complete (commits 603c445..884b250, review clean)
Ruling: Room cannot declare a partial index predicate with `@Index`; do not add a full unique marker because that would reject terminal Duration history. T006 defines the exact partial-index SQL; T010 must install it and T037 must validate it — cost if wrong: an unvalidated callback-installed index could affect Room schema compatibility.
Ruling: omit FKs from `StateScopeEntity.currentRecordId` and UndoReceipt scope context to lifetime-owned Record/StateScope rows because core `DeleteScope` can retain that metadata while deleting the referenced Record and the adapter must persist the exact core-produced state — cost if wrong: malformed non-core data needs mapper validation rather than SQLite FK rejection.
Ruling: omit the `UndoReceiptEntity.eventId` FK because core single-event delete/Undo removes its Event while retaining the receipt; retain scalar eventId plus lookup index so adapter persistence does not rewrite valid core state — cost if wrong: malformed receipt rows are detected by mapping, not SQLite referential enforcement.
Task 6: fix round 1/5 (1 addressed, 0 open — Undo receipt Event FK; commits 1f8eb2c..6e392e0)
Task 6: complete (commits 4ba4912..6e392e0, review clean)
Task 7: complete (commits ab9d890..daa91a5, review clean)
Ruling: canonical quantity persistence preserves numeric value via plain-decimal normalization; BigDecimal scale is not historical domain meaning, so T008 aggregate tests must assert numeric quantity equivalence while requiring exact preservation of all other fields — cost if wrong: scale-sensitive equality checks could reject valid canonical persisted data or changing core equality would broaden scope.
Task 8a: complete (commits e144b81..f51449e, review clean)
Task 8: complete (commits 1cababf..0278dd3, review clean)
Task 9: minor (deferred): focused-run console transcript was not retained separately, but fresh full-lane XML proves the canonical 6-test suite and aggregate suite pass.
Task 9: fix round 1/5 (1 addressed, 0 open — test delegation, verification evidence; commits ce72fef..14d25f2)
Task 9: complete (commits 9c8b784..14d25f2, review clean)
Ruling: install T006’s partial OPEN Duration index in Room `onOpen`, after normal Room identity validation, because Room’s generated validator rejects the extra partial index if it exists before validation; normal identity-hash reopen remains supported, while missing-master/explicit-validator paths remain fail-closed and are carried to T037/US5 — cost if wrong: a future migration or repaired identity path could fail to open until an approved index-validation strategy exists.
Task 10: complete (commits 5a9b8a8..6dbcab6, review clean)
Task 11: complete (commits ffc9116..06646d7, review clean; Android runtime deferred because no device)
Task 12: fix round 1/5 (4 addressed, 0 open — query ownership/scope/payload coverage; commits 2c17746..cdd9677)
Task 12: complete (commits 287e3a1..cdd9677, review clean)
Task 13: fix round 1/5 (2 addressed, 0 open — ordering/uniqueness probes; commits c9be548..12fb3d1)
Task 13: complete (commits 5223c69..12fb3d1, review clean; Android runtime deferred because no device)
Task 14: complete (commits 16d78b8..0ead7b8, review clean; Android runtime deferred because no device)
Task 15: complete (commits 4537578..c89e49f, review clean; Android runtime deferred because no device)
Task 16: complete (commits cda1e15..8cc9e98, review clean; Android runtime deferred because no device)
Task 17: complete (commits bc0b2a2..9d8292d, review clean; Android runtime deferred because no device)
Task 18: fix round 1/5 (3 addressed, 0 open — monotonicity/State/Counter coverage; commits bdd79e1..f79e4cc)
Task 18: fix round 2/5 (1 addressed, 0 open — valid StateGroup mismatch fixture; commits f79e4cc..5b7d23d)
Task 18: complete (commits 7287e74..5b7d23d, review clean)
Task 19: complete (commits ffadc96..191fd71, review clean; Android runtime/process-kill deferred because no device)
Ruling: T020 relationship revisions are monotonic; identical state/revision replay is idempotent, while a changed linked value requires a strictly higher revision. The phase preflights all supplied pairs and stored metadata/parents before relationship-only upserts; unlink retains the exact pair row with `linked=false`, and omitted pairs/history/definitions remain untouched — cost if wrong: accepting stale relationship state or inferring lifecycle effects would violate core semantics.
Task 20: complete (commit d64e7b6, review clean; Android runtime/reopen/rollback deferred because no device)
Ruling: T021 treats binding identity/scope as immutable. Supplied orphaned status, last-known display snapshot, revision, and invalidation metadata are persisted as core-produced state; changed fields require a strictly higher binding revision, identical replay is idempotent, and omitted/unrelated rows remain untouched — cost if wrong: retargeting a binding or reconstructing its display would corrupt historical lifecycle state.
Task 21: complete (commit ef3972f9247d4081bb491adafd5c63b80e083208, review clean; Android runtime/reopen/rollback deferred because no device)
Ruling: T022 preserves adapter-owned Undo operation/before-image/consumed/invalidation-reason fields across core `DomainState` projections and context advances because the core receipt type cannot author them. Operation/before-image pairs, invalidation enums, scope triples, and numeric revisions remain validated fail-closed; absent Event references are allowed. This resolves the task/data-model requirement without schema or core changes.
Task 22: fix round 1/5 (1 addressed — consumed/invalid/before-image retention; commits a279eeb..c4c7bc2)
Task 22: complete (commits a279eeb..c4c7bc2, review clean; Android runtime/reopen/rollback deferred because no device)
Task 23: complete (commit 7d6539d, review clean; focused and full `:data:testDebugUnitTest` passed)
Task 24: complete (working implementation and contract test; full `:data:testDebugUnitTest` passed; review clean)
Task 25: complete (contract tests added; focused and full `:data:testDebugUnitTest` passed; review clean)
Task 26: complete (Room deletion/reopen coverage added; Android test source compiles; connected test deferred: no connected devices)
Task 27: complete (dedicated Room atomic boundary added; JVM suite and Android-test compilation passed; device execution deferred: no connected devices)
Task 28: complete (stale expected-state compare returns Boolean `false`; no core contract/global revision changes; review clean)
Task 29: complete (JVM failure/concurrency contract coverage added; 20 attempts per operation family; full `:data:testDebugUnitTest` passed)
Task 29a: complete (explicit stale UndoReceipt Android-boundary regression added; Android-test source compiles; device execution deferred: no connected devices)
Task 30: complete (Room rollback/concurrency coverage added; Android-test source compiles; device execution deferred: no connected devices)
Task 31: complete (existing `:core:test` suite passed; core contracts unchanged)
