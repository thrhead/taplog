# Tasks: TapLog Development Environment and Android Foundation

**Feature Branch**: `002-android-foundation` | **Date**: 2026-09-15 | **Spec**: [spec.md](spec.md) | **Plan**: [plan.md](plan.md)

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Project initialization, build wrapper, dependency catalog, and repository settings

- [X] T001 Create Gradle Wrapper files in `gradle/wrapper/gradle-wrapper.properties` pinning Gradle distribution 9.6.0 with SHA-256 checksum, and `gradle/wrapper/gradle-wrapper.jar`, `gradlew`, `gradlew.bat`
- [X] T002 [P] Create central version catalog in `gradle/libs.versions.toml` pinning exact versions: AGP 9.4.0, Gradle 9.6.0, KGP 2.3.21, KSP 2.3.6 (catalogued only), JDK 17, Compose BOM 2026.03.00, Activity Compose 1.13.0, JUnit 4.13.2, AndroidX Test Runner 1.7.0, AndroidX JUnit extension 1.3.0, Detekt 1.23.8, detekt-formatting 1.23.8
- [X] T003 [P] Configure central repositories (Google, MavenCentral, Gradle Plugin Portal) and module inclusions (`:core`, `:data`, `:app`) in `settings.gradle.kts`
- [X] T004 [P] Create shared build options and encoding properties in `gradle.properties`
- [X] T005 [P] Create repository ignore rules in `.gitignore` to exclude machine-specific paths and local build artifacts

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core build infrastructure, module structure (`:core`, `:data`), quality rules, and JVM test scaffolds

**⚠️ CRITICAL**: No user story implementation can begin until this phase is complete

- [X] T006 Configure root plugins and common build setup in `build.gradle.kts`
- [X] T007 [P] Create `:core` Kotlin/JVM module build definition in `core/build.gradle.kts` with JDK 17 toolchain, JUnit 4.13.2 test dependency, and zero Android dependencies
- [X] T008 [P] Create `:data` Android library module build definition in `data/build.gradle.kts` with `compileSdk` 36, `minSdk` 26, dependency `implementation(project(":core"))`, and zero Room/KSP/schema code
- [X] T009 [P] Create JVM test scaffolds in `core/src/test/kotlin/io/github/thrhead/taplog/core/CoreTestScaffoldTest.kt` and `data/src/test/kotlin/io/github/thrhead/taplog/data/DataTestScaffoldTest.kt`
- [ ] T010 Create Detekt static analysis and formatting configuration in `config/detekt/detekt.yml` and wire `foundationCheck` aggregate task in `build.gradle.kts`

**Checkpoint**: Base build system and module graph created — user story implementation can now begin

---

## Phase 3: User Story 1 - Start a Reproducible Development Workspace (Priority: P1) 🎯 MVP

**Goal**: A contributor can open a clean checkout in GitHub Codespaces and obtain the documented build environment without relying on an undocumented local installation.

**Independent Test**: From a clean Codespaces checkout, run `./gradlew --version` and `./gradlew --no-daemon clean foundationCheck` successfully without requiring an emulator or local tool installation.

### Implementation for User Story 1

- [ ] T011 [P] [US1] Create dev container Dockerfile in `.devcontainer/Dockerfile` provisioning JDK 17, Android SDK platform 36 (`platforms;android-36`), and Build Tools 36.0.0 (`build-tools;36.0.0`) without emulator or KVM requirements
- [ ] T012 [P] [US1] Create dev container configuration in `.devcontainer/devcontainer.json` referencing `.devcontainer/Dockerfile` and configuring workspace environment settings
- [ ] T013 [US1] Create push/PR CI workflow in `.github/workflows/android-foundation.yml` running `./gradlew --no-daemon clean foundationCheck` with read-only permissions and artifact reporting on failure
- [ ] T014 [US1] Add Codespaces setup and non-device verification instructions in `README.md`

**Checkpoint**: User Story 1 environment is reproducible and verifiable via Codespaces and CI

---

## Phase 4: User Story 2 - Build and Launch the Foundation App (Priority: P1)

**Goal**: A contributor can build, install, and launch a minimal TapLog application on an Android 8.0-compatible device or emulator, seeing a clearly non-product shell.

**Independent Test**: Build the app from clean checkout, install it on an API 26+ target (`./gradlew --no-daemon :app:installDebug`), launch `io.github.thrhead.taplog`, and confirm the neutral shell appears without crashing and exposes zero product data/workflows.

### Implementation for User Story 2

- [ ] T015 [US2] Create `:app` module build definition in `app/build.gradle.kts` declaring `applicationId = "io.github.thrhead.taplog"`, `minSdk = 26`, `compileSdk = 36`, `targetSdk = 36`, Compose BOM 2026.03.00, Activity Compose 1.13.0, Material 3, and module dependencies `:core` and `:data`
- [ ] T016 [P] [US2] Create application manifest in `app/src/main/AndroidManifest.xml` declaring package `io.github.thrhead.taplog`, display name `TapLog`, and single launcher activity
- [ ] T017 [P] [US2] Create application strings and theme resources in `app/src/main/res/values/strings.xml` and `app/src/main/res/values/themes.xml` for display name `TapLog` and non-product theme
- [ ] T018 [US2] Implement static neutral shell activity in `app/src/main/java/io/github/thrhead/taplog/MainActivity.kt` presenting a neutral Compose screen identifying `TapLog` with no product workflow or data
- [ ] T019 [P] [US2] Create local unit test scaffold in `app/src/test/kotlin/io/github/thrhead/taplog/AppTestScaffoldTest.kt` verifying `:app` JVM test lane
- [ ] T020 [US2] Create connected launch test in `app/src/androidTest/kotlin/io/github/thrhead/taplog/MainActivityLaunchTest.kt` verifying `MainActivity` launches on an API 26+ device/emulator without crashing

**Checkpoint**: Foundation shell app builds, installs, launches on API 26+, and passes connected launch test

---

## Phase 5: User Story 3 - Extend Clear, Testable Boundaries (Priority: P2)

**Goal**: A contributor beginning the next roadmap slice can place work in an unambiguous module and run the relevant baseline test and quality checks without importing Android platform code into the shared core.

**Independent Test**: Execute `./gradlew :core:dependencies :data:dependencies :app:dependencies` to verify `:core` has zero Android dependencies, `:data` depends only on `:core`, and `:app` depends on `:core` and `:data`, with all dependencies centrally governed.

### Implementation for User Story 3

- [ ] T021 [US3] Verify module dependency isolation in `core/build.gradle.kts`, `data/build.gradle.kts`, and `app/build.gradle.kts` enforcing `:core` platform independence and directed graph `:data` → `:core`, `:app` → `:core`,`:data`
- [ ] T022 [US3] Audit version declarations in `gradle/libs.versions.toml` to ensure zero dynamic or version range declarations exist

**Checkpoint**: Architectural boundaries are enforced and reviewable via Gradle dependency reports

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Verification and documentation alignment across the foundation slice

- [ ] T023 [P] Verify build and verification contract in `specs/002-android-foundation/contracts/verification-contract.md`
- [ ] T024 [P] Verify foundation validation quickstart guide in `specs/002-android-foundation/quickstart.md`
- [ ] T025 Execute foundation verification suite via `./gradlew --version`, `./gradlew --no-daemon clean foundationCheck`, and `./gradlew :core:dependencies :data:dependencies :app:dependencies` per `quickstart.md`

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — can start immediately
- **Foundational (Phase 2)**: Depends on Setup completion — BLOCKS all user story implementation
- **User Story 1 (Phase 3)**: Depends on Foundational phase completion
- **User Story 2 (Phase 4)**: Depends on Foundational phase completion and T015 module definition
- **User Story 3 (Phase 5)**: Depends on Phase 2, Phase 3, and Phase 4 module creations
- **Polish (Phase 6)**: Depends on all user story implementation tasks complete

### User Story Dependencies

- **User Story 1 (P1)**: Can start after Foundational (Phase 2) — No dependencies on other stories
- **User Story 2 (P1)**: Can start after Foundational (Phase 2) — Uses `:core` and `:data` modules from Phase 2
- **User Story 3 (P2)**: Can start after Phase 4 — Validates full module graph established by US1/US2

### Within Each User Story

- Build configuration before code/resources
- Application manifest and resources before activity implementation
- Activity implementation before connected instrumentation test
- Story complete and verified before moving to next priority

### Parallel Opportunities

- All Setup tasks marked `[P]` (T002, T003, T004, T005) can run in parallel
- Foundational tasks marked `[P]` (T007, T008, T009) can run in parallel
- US1 tasks marked `[P]` (T011, T012) can run in parallel
- US2 tasks marked `[P]` (T016, T017, T019) can run in parallel
- Polish documentation tasks marked `[P]` (T023, T024) can run in parallel

---

## Parallel Example: User Story 1

```bash
# Launch environment configuration tasks for User Story 1 in parallel:
Task: "Create dev container Dockerfile in .devcontainer/Dockerfile provisioning JDK 17, Android SDK platform 36, and Build Tools 36.0.0"
Task: "Create dev container configuration in .devcontainer/devcontainer.json referencing .devcontainer/Dockerfile"
```

---

## Parallel Example: User Story 2

```bash
# Launch app manifest, resources, and local unit test scaffold in parallel:
Task: "Create application manifest in app/src/main/AndroidManifest.xml"
Task: "Create application strings and theme resources in app/src/main/res/values/strings.xml and app/src/main/res/values/themes.xml"
Task: "Create local unit test scaffold in app/src/test/kotlin/io/github/thrhead/taplog/AppTestScaffoldTest.kt"
```

---

## Implementation Strategy

### MVP First (User Story 1 & 2 Foundation)

1. Complete Phase 1: Setup (T001–T005)
2. Complete Phase 2: Foundational (T006–T010)
3. Complete Phase 3: User Story 1 (T011–T014) — reproducible environment ready
4. Complete Phase 4: User Story 2 (T015–T020) — foundation app launches on API 26+
5. **STOP and VALIDATE**: Verify build, JVM test scaffolds, lint, Detekt, and connected launch test pass

### Incremental Delivery

1. Setup + Foundational → Build engine and module boundaries established
2. User Story 1 → Codespaces + CI automation active
3. User Story 2 → Installable/launchable shell app verified on device
4. User Story 3 → Module boundary isolation audited
5. Polish → Verification contract and quickstart validated
