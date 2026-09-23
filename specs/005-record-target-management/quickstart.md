# Quickstart Validation: Record + Target Management

## Prerequisites

- JDK 17 and the checked-in Gradle Wrapper.
- Repository checkout on branch 005-record-target-management.
- API-26+ emulator/device for connected Compose tests; pure JVM lanes need no device or network.

## Device-independent checks

    ./gradlew :core:test :data:test :app:test

Expected: core management, data boundary, app orchestration, result mapping, and state-holder tests pass with no Android or network dependency.

## Connected management checks

    ./gradlew :app:connectedDebugAndroidTest

Expected critical flows:

1. Open management from the app shell; active and archived empty/list states render.
2. Create Moment, Counter, Duration, and State Records with behavior-valid fields; omitted Counter default is 1.
3. Create/edit/archive/unarchive a Target; no standalone permanent-delete action is offered, and an attempted standalone deletion cannot mutate persisted data.
4. Edit a Record before and after an Event; name/icon/default quantity changes succeed, behavior/unit changes after the first Event show deterministic rejection.
5. Link, unlink, and explicitly relink an active Record–Target pair. New Events become eligible after relink while historical orphaned bindings and snapshots remain unchanged.
6. Open permanent deletion and verify exact core impact; cancel leaves state unchanged, confirmation commits only the requested scope.
7. Verify that pre-commit stale expected context produces Conflict, while a commit-time Boolean rejection is surfaced as StorageFailure; no typed commit-conflict result is introduced.

## Restart validation

Run the management flow, force-stop/reopen the app, and verify committed definitions, relationship revisions, lifecycle effects, orphaned bindings, and historical snapshots are restored. Verify an interrupted/cancelled confirmation does not appear as a partial mutation.

## Boundary validation

    ./gradlew :core:dependencies :data:dependencies :app:dependencies

Review that :core remains Android-free, :app contains no DAO/Room access, and all management writes route through the approved core/data boundary. Reuse 004 persistence and restart tests for aggregate atomicity rather than duplicating their full coverage in Compose.

## Expected exclusions

This quickstart does not validate Home, Timeline, Event execution UX, NFC, Widget, Quick Settings, natural-language parsing, backup/import/export, statistics, monetization, AI, or cloud/sync.
