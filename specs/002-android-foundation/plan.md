# Implementation Plan: TapLog Development Environment and Android Foundation

**Branch**: `002-android-foundation` | **Date**: 2026-09-15 | **Spec**: [spec.md](spec.md)

**Input**: [Approved foundation specification](spec.md), [constitution](../../.specify/memory/constitution.md), [approved architecture](../001-v1-architecture/plan.md), [roadmap](../001-v1-architecture/implementation-roadmap.md), and [AGENTS.md](../../AGENTS.md).

## Summary

Create the smallest reproducible Android project that builds from GitHub Codespaces, has the approved `:core` → `:data` → `:app` boundaries, and launches a neutral TapLog shell on API 26 or newer. The project pins the approved toolchain and dependency versions, provides JVM/Android test scaffolds, runs lint and static quality checks, and supplies a non-device CI lane. It delivers no product behavior or product data.

## Technical Context

**Language/Version**: Kotlin 2.3.21; Java toolchain 17; Gradle Wrapper 9.6.0; Android Gradle Plugin 9.4.0.

**Primary Dependencies**: Kotlin/JVM and Android build plugins; Kotlin Compose compiler plugin 2.3.21; KSP 2.3.6 declared but not applied; Activity Compose 1.13.0; Compose BOM 2026.03.00; Material 3 and Compose UI test artifacts from that BOM; JUnit 4.13.2; AndroidX Test Runner 1.7.0 and JUnit extension 1.3.0; Detekt 1.23.8 plus `detekt-formatting` 1.23.8. No Room, Navigation, ViewModel, DI framework, Coroutines, Billing, NFC, or parser dependency is allowed.

**Storage**: N/A. No database, preferences, files, schema, migrations, or product data are created in this slice.

**Testing**: Kotlin/JUnit JVM scaffolds in `:core`, `:data`, and `:app`; a connected Compose launch scaffold in `:app`. Android lint and Detekt run without a device. Connected tests and manual launch require an API-26-or-newer emulator or physical device.

**Target Platform**: Android app with `minSdk` 26, `compileSdk` 36, `targetSdk` 36, Build Tools 36.0.0; GitHub Codespaces and GitHub Actions for non-device verification.

**Project Type**: Local-first Android mobile application; no backend or external product service.

**Performance Goals**: Five clean build-install-launch attempts on an API-26-compatible target display the static shell without a crash. Codespaces/CI run only compilation, JVM tests, lint, and static quality checks.

**Constraints**: Exact, centrally declared versions; no dynamic dependency versions; checked-in Gradle Wrapper; `:core` has no Android dependency; no emulator/KVM requirement in Codespaces or CI; no feature/channel module; no product workflow or persistent data.

**Scale/Scope**: Three modules, one application process, one neutral Activity, and test/build scaffolding only. Product domain, persistence, channels, billing, backup, statistics, and release publishing are deferred to their approved roadmap slices.

## Constitution Check

*GATE: Passed before Phase 0 research and re-checked after Phase 1 design.*

| Principle | Design response | Status |
| --- | --- | --- |
| I. Event-first, channel-independent core | `:core` is platform independent but contains no Event Engine or domain semantics in this slice. | Pass |
| II. Local-first core | No account, network product dependency, backend, or cloud state is introduced. | Pass |
| III. Preserve history and confirm ambiguity | No product data is created, stored, edited, or removed. | Pass |
| IV. Channel-owned interaction safety | No input channel or debounce behavior is introduced. | Pass |
| V. Testable boundaries and scope discipline | Pure JVM `:core`, directed module dependencies, separate connected test lane, and no later-slice code. | Pass |

No constitutional exception or complexity justification is required. The plan remains traceable to roadmap slice 1 and does not decide a PRD technical TBD outside its approved feature scope.

## Project Structure

### Documentation (this feature)

```text
specs/002-android-foundation/
├── plan.md                         # This implementation plan
├── research.md                     # Toolchain and environment decisions
├── data-model.md                   # Explicit no-product-data model
├── quickstart.md                   # Codespaces, CI, and device verification guide
├── contracts/
│   └── verification-contract.md    # Stable contributor/CI command contract
└── tasks.md                        # Created later by $speckit-tasks
```

### Planned repository layout

```text
.
├── .devcontainer/
│   ├── devcontainer.json            # Codespaces configuration; invokes Dockerfile
│   └── Dockerfile                   # JDK 17 and SDK platform/build tools 36 provisioning
├── .github/workflows/
│   └── android-foundation.yml       # Push/PR, non-device CI verification only
├── app/
│   ├── build.gradle.kts             # Application SDK values, Compose shell, project deps
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml  # Application/launcher declaration only
│       │   ├── java/io/github/thrhead/taplog/MainActivity.kt # Static neutral shell
│       │   └── res/values/          # App name and non-product theme resources
│       ├── test/kotlin/.../AppTestScaffoldTest.kt
│       └── androidTest/kotlin/.../MainActivityLaunchTest.kt
├── core/
│   ├── build.gradle.kts             # Kotlin/JVM only; zero Android plugins/dependencies
│   └── src/test/kotlin/.../CoreTestScaffoldTest.kt
├── data/
│   ├── build.gradle.kts             # Android library; depends only on :core
│   └── src/test/kotlin/.../DataTestScaffoldTest.kt
├── gradle/
│   ├── libs.versions.toml           # Every declared plugin/library version and alias
│   └── wrapper/                     # Wrapper JAR and 9.6.0 distribution + checksum
├── config/detekt/detekt.yml         # Static-analysis and formatting rule configuration
├── build.gradle.kts                 # Root plugin aliases/configuration only
├── settings.gradle.kts              # Repositories and exactly :core, :data, :app
├── gradle.properties                # Shared deterministic build properties only
├── gradlew / gradlew.bat            # Checked-in wrapper entry points
└── README.md                        # Codespaces and device verification instructions
```

**Structure Decision**: Retain the approved three-module architecture. `:core` will be a Kotlin/JVM module and intentionally has no production domain source in this slice. `:data` is an Android library boundary, has only the `:core` edge, and contains no Room/KSP/schema code. `:app` is the sole Android application and owns the non-product Compose shell. No fourth, feature, channel, DI, or test-helper module is introduced.

## Module and File Responsibilities

| Location | Responsibility | Must not contain |
| --- | --- | --- |
| `settings.gradle.kts` | Repository policy, version catalog reference, and exactly three included modules. | Feature modules or project behavior. |
| Root `build.gradle.kts` / `gradle.properties` | Shared plugin declarations, test/quality aggregate task, Java 17/toolchain and deterministic Gradle settings. | Application SDK declarations, product code, or module-specific dependencies. |
| `gradle/libs.versions.toml` | Fixed aliases for all declared plugins/libraries, including the approved toolchain baseline and no ranges. | Unused future product libraries. |
| `core/build.gradle.kts` | Kotlin/JVM compilation and pure JUnit test lane. | Android plugin/classes, persistence, Event/Record/Target types. |
| `data/build.gradle.kts` | Android-library boundary and `implementation(project(":core"))`. | Room, KSP application, database/schema/DAO/migration, backup code. |
| `app/build.gradle.kts` | Application identity, API 26/36 SDK values, Compose enablement, static-shell UI and test dependencies, `:core`/`:data` edges. | Navigation, ViewModel, data access, channels, product actions, release/publishing setup. |
| `MainActivity.kt` and app resources | One static, accessible “TapLog” shell; launcher and theme. | Record/Event/Target terminology, state, action, storage, or feature navigation. |
| Test scaffold files | Prove the respective JVM/connected test lanes discover and run; the app connected test verifies the neutral shell launches. | Product-domain assertions, data fixtures, or behavior contracts. |
| `.devcontainer/*` | Reproducible Codespaces image and exact Android SDK provisioning. | Emulator/system image/AVD, KVM, app behavior. |
| `.github/workflows/android-foundation.yml` | Least-privilege push/PR non-device verification using the wrapper. | Secrets, publishing, release, emulator, or connected tests. |
| `config/detekt/detekt.yml` | Checked-in lint/static-analysis/formatting policy. | Suppression baseline or broad exclusions. |

## Dependency and Build Policy

- Repositories are limited to Google, Maven Central, and the Gradle Plugin Portal where applicable; dependency resolution is centralized in settings.
- The Gradle Wrapper is the only allowed Gradle entry point. `gradle-wrapper.properties` pins distribution 9.6.0 and its SHA-256 checksum.
- The version catalog declares AGP 9.4.0, Kotlin/Kotlin Compose compiler 2.3.21, KSP 2.3.6, JDK 17 toolchain, Compose BOM 2026.03.00, Activity Compose 1.13.0, JUnit 4.13.2, AndroidX Test Runner 1.7.0, AndroidX JUnit extension 1.3.0, Detekt 1.23.8, and `detekt-formatting` 1.23.8. Versionless Compose artifact declarations are allowed only when constrained by the pinned BOM.
- KSP is catalogued to honor the approved toolchain but is not applied to any module until a separately specified processor need exists. This prevents unauthorized generated code, schema, or KSP/AGP interaction in this foundation.
- Compose BOM 2026.03.00 is used because later Compose BOMs require `compileSdk` 37; raising the project above approved API 36 is out of scope.
- Detekt 1.23.8 is the latest final release; its formatting rules form the formatting check. Detekt 2.x alpha is excluded. The first implementation task verifies it under the approved AGP/Kotlin baseline before accepting the quality lane.
- Every GitHub Action is pinned to a reviewed immutable commit SHA with a nearby release-version comment. CI uses one Gradle cache mechanism only and performs no authenticated/publishing operation.

## Verification Strategy

The following command contract is defined in [verification-contract.md](contracts/verification-contract.md) and repeated in the README. All use `./gradlew`; none rely on a globally installed Gradle.

| Lane | Command | Environment | Expected result |
| --- | --- | --- | --- |
| Toolchain | `./gradlew --version` | Codespaces, CI, local | Reports Gradle 9.6.0 running on JDK 17. |
| Foundation | `./gradlew --no-daemon clean foundationCheck` | Codespaces, CI, local | Runs app debug assembly, all JVM scaffolds, Android lint, and Detekt/formatting checks without `adb`. |
| Dependency boundary | `./gradlew :core:dependencies :data:dependencies :app:dependencies` | Codespaces, local | Confirms only `data → core` and `app → core,data`; `core` has no Android artifact. |
| Connected | `./gradlew --no-daemon :app:connectedDebugAndroidTest` | API-26+ emulator/device only | Launch scaffold passes; not run in Codespaces or CI. |
| Manual launch | `./gradlew --no-daemon :app:installDebug` | API-26+ emulator/device only | `io.github.thrhead.taplog` installs and its neutral shell launches. |

The Android/device acceptance run consists of five clean build-install-launch attempts. A missing device or `adb` is a documented prerequisite failure, not a failed Codespaces/CI build.

## Implementation Sequence

1. Add reproducibility files: wrapper, centralized settings/version catalog, root build configuration, deterministic properties, ignore rules, and module includes.
2. Create the three empty architectural modules with the directed dependency graph; add only non-product JVM test scaffolds.
3. Create `:app` manifest, resources, Compose static shell, local test scaffold, and connected launch test.
4. Add checked-in Detekt/formatting configuration and the `foundationCheck` aggregate task; prove the device-independent command contract.
5. Add the Codespaces Dockerfile/configuration and README guidance, ensuring exact JDK/SDK packages are image-provisioned and no emulator is required.
6. Add the least-privilege GitHub Actions workflow using the same non-device command contract; upload lint/Detekt reports only on failure.
7. Verify dependency direction, run the Codespaces/CI lane, then run the API-26+ physical/emulator launch matrix before declaring this slice complete.

## Complexity Tracking

No constitutional violations or extra complexity are introduced.
