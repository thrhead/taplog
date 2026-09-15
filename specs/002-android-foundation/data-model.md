# Foundation Data Model

## Scope decision

This slice creates **no runtime product data model**. It must not add Record, Event, Target, behavior, Event Engine commands, persistence entities, Room database/DAO/schema, migrations, preferences, backup files, entitlement records, or analytics events.

## Build-time configuration surfaces

These are source-controlled build descriptors, not user data or persisted runtime entities.

| Surface | Required attributes | Relationships and validation |
| --- | --- | --- |
| Toolchain catalog | Exact AGP, Gradle, Kotlin, KSP, JDK, Android SDK/Build Tools, test, UI, and quality-tool versions | One catalog supplies every declared alias; no dynamic/range version; versionless Compose artifacts must be constrained by the pinned BOM. |
| Module descriptor | Module name, plugin kind, project dependencies | Exactly `:core`, `:data`, `:app`; `data → core`; `app → core,data`; no edge points toward `app`; `core` applies no Android plugin/dependency. |
| Application identity | Display name, namespace/application ID, minimum/compile/target SDK | `TapLog`; `io.github.thrhead.taplog`; 26/36/36 respectively; declared only by `:app`. |
| Verification lane | Command, environment, expected outcome | Codespaces/CI lane has no device requirement; connected lane requires API 26+ device/emulator; a lane never creates product data. |

## Lifecycle and migration

There are no runtime state transitions, database migrations, retention rules, or user-data relationships in this slice. Future data-model work begins only in the approved Local Persistence roadmap slice after the Core Domain + Event Engine foundation is specified and implemented.
