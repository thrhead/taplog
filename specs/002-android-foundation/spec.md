# Feature Specification: TapLog Development Environment and Android Foundation

**Feature Branch**: `002-android-foundation` (feature directory is independent of the git branch)

**Created**: 2026-09-15

**Status**: Ready for planning

**Input**: User description: "Create the minimum reproducible development environment and Android foundation before product features, following the approved architecture and roadmap."

## Scope and Sources

This is roadmap slice 1: the smallest foundation that lets the team build, launch,
and verify an otherwise empty TapLog Android application. It is constrained by the
[PRD](../../docs/product/TapLog_V1_PRD.md), especially its Android 8.0 minimum, the
[constitution](../../.specify/memory/constitution.md), and the approved
[technical architecture](../001-v1-architecture/plan.md),
[research](../001-v1-architecture/research.md), and
[implementation roadmap](../001-v1-architecture/implementation-roadmap.md).

The feature establishes infrastructure only. It does not deliver a Record, Event,
Target, Event Engine behavior, persistence schema, NFC, widget, Quick Settings,
parser, backup, statistics, monetization, or any other V1 product workflow.

## Approved Foundation Baseline

The following fixed baseline resolves the architecture's instruction to lock a
compatible toolchain in this first build slice. It selects the current stable
platform required for Google Play submission without adopting Android 17 preview
APIs:

| Concern | Approved value |
| --- | --- |
| Application display name | `TapLog` |
| Application/package identifier | `io.github.thrhead.taplog` |
| Minimum Android version | API 26 (Android 8.0) |
| Compile and target Android version | API 36 (Android 16) |
| Java Development Kit | JDK 17 |
| Android Gradle Plugin | 9.4.0 |
| Gradle Wrapper | 9.6.0 |
| Kotlin Gradle Plugin | 2.3.21 |
| Kotlin Symbol Processing | 2.3.6 |
| Android SDK Build Tools | 36.0.0 |

All versions are exact initial values, not version ranges. A later change requires
a separately reviewed dependency/toolchain update and compatible verification.

## User Scenarios & Testing

### User Story 1 - Start a Reproducible Development Workspace (Priority: P1)

A contributor can open a clean checkout in GitHub Codespaces and obtain the
documented build environment without relying on an undocumented local installation.

**Why this priority**: A reproducible workspace is required before the team can
reliably implement or review any V1 slice.

**Independent Test**: From a clean Codespaces checkout, follow the repository
instructions and run the documented verification command successfully.

**Acceptance Scenarios**:

1. **Given** a clean checkout opened in GitHub Codespaces, **When** the workspace is created, **Then** it provides the pinned Java and Android build prerequisites needed for the project verification commands.
2. **Given** a contributor who has not installed Android tools locally, **When** they follow the repository setup instructions, **Then** they can build and run the JVM test scaffold from Codespaces.
3. **Given** that Codespaces has no usable Android emulator hardware acceleration, **When** the contributor follows the verification instructions, **Then** the instructions distinguish Codespaces checks from checks that require a local emulator or physical device.

---

### User Story 2 - Build and Launch the Foundation App (Priority: P1)

A contributor can build, install, and launch a minimal TapLog application on an
Android 8.0-compatible device or emulator, seeing a clearly non-product shell.

**Why this priority**: It proves that the chosen environment and application
foundation are viable without pretending that a product capability exists.

**Independent Test**: Build the app from a clean checkout, install it on an API
26-compatible target, launch it, and confirm that the non-product shell appears.

**Acceptance Scenarios**:

1. **Given** a clean checkout and the documented supported device/emulator environment, **When** the contributor builds and installs the app, **Then** installation and launch succeed on an API 26-compatible target.
2. **Given** the launched foundation app, **When** it is opened, **Then** it presents only a neutral shell that identifies TapLog and does not expose a product workflow or create product data.
3. **Given** a device below the approved minimum Android version, **When** installation is attempted, **Then** the application is not presented as supported.

---

### User Story 3 - Extend Clear, Testable Boundaries (Priority: P2)

A contributor beginning the next roadmap slice can place work in an unambiguous
module and run the relevant baseline test and quality checks without importing
Android platform code into the shared core.

**Why this priority**: Clear boundaries preserve the channel-independent Event
Engine design before feature work begins.

**Independent Test**: Inspect the module dependency graph, run the baseline test
lanes, and verify that the core module has no Android dependency.

**Acceptance Scenarios**:

1. **Given** the completed foundation, **When** the module dependency graph is inspected, **Then** `:core` is platform-independent, `:data` depends on `:core`, and `:app` depends on both `:core` and `:data`.
2. **Given** the baseline project, **When** JVM tests and Android test scaffolds are invoked through their documented commands, **Then** each lane is discoverable and produces an unambiguous result.
3. **Given** a declared third-party library, **When** its version is inspected, **Then** it is centrally governed, fixed rather than dynamically selected, and reviewable with the rest of the build definition.

### Edge Cases

- The project must remain buildable in Codespaces when no Android emulator, `adb`, or device is available; device-dependent checks must report that prerequisite rather than failing ambiguously.
- A fresh checkout must not depend on machine-specific paths, globally installed Gradle, or untracked generated files.
- A failed static-analysis, formatting, JVM-test, or Android-build check must cause the corresponding documented quality command to fail clearly.
- The foundation must not add a placeholder data model or UI action that could be mistaken for Record or Event behavior.

## Requirements

### Functional Requirements

- **FR-001**: The repository MUST define a reproducible GitHub Codespaces environment containing the pinned prerequisites required to run the project’s JVM build, tests, and static quality checks.
- **FR-002**: The repository MUST document the supported Codespaces workflow and separately identify verification that requires a local Android emulator or physical device; an Android emulator MUST NOT be a Codespaces prerequisite.
- **FR-003**: The project MUST use a checked-in build wrapper and fixed, mutually compatible versions for its build system, language tooling, Android build tooling, annotation-processing tooling where introduced, and JDK; it MUST NOT use dynamic dependency or plugin versions.
- **FR-004**: The project MUST declare `minSdk` as API 26 (Android 8.0), consistent with the PRD and approved architecture.
- **FR-005**: The project MUST declare the approved fixed toolchain: `compileSdk` 36, `targetSdk` 36, JDK 17, Android Gradle Plugin 9.4.0, Gradle Wrapper 9.6.0, Kotlin Gradle Plugin 2.3.21, Kotlin Symbol Processing 2.3.6, and Android SDK Build Tools 36.0.0.
- **FR-006**: The application display name MUST be `TapLog` and its stable application/package identifier MUST be `io.github.thrhead.taplog`.
- **FR-007**: The build MUST establish exactly the approved base module boundaries: `:core` for platform-independent domain/application code, `:data` for future persistence adapters depending on `:core`, and `:app` for Android composition and presentation depending on both; no channel-specific module is introduced.
- **FR-008**: The initial `:core` module MUST have no Android framework dependency and MUST support pure JVM unit testing.
- **FR-009**: The initial `:data` module MUST contain no product persistence schema, database, or migration; it exists only to establish the approved future boundary and test lane.
- **FR-010**: The initial `:app` module MUST build, install, and launch a minimal non-product TapLog shell on an API 26-compatible device or emulator; it MUST not create, display, or mutate Record, Event, Target, NFC, parser, backup, statistics, entitlement, widget, or Quick Settings data.
- **FR-011**: The project MUST provide baseline JVM and Android test scaffolds, with documented commands that distinguish checks runnable in Codespaces from connected-device checks.
- **FR-012**: The project MUST provide the static-analysis and formatting checks required by the approved architecture, including Android lint and the selected static quality tool, as repeatable build commands.
- **FR-013**: The project MUST centrally manage declared dependency and plugin versions and make the resolved dependency set reviewable; dependency additions must follow the approved strategy of adding no library before a concrete need.
- **FR-014**: The project MUST provide the CI baseline required by the approved architecture: a clean, non-interactive checkout runs the same Codespaces-compatible build, JVM-test, lint, and static-quality commands and reports failures.
- **FR-015**: Repository instructions MUST state that future domain/application behavior follows test-first development and that device/instrumentation checks require the documented external device or emulator environment.

### Key Entities

- **Development workspace**: The reproducible contributor environment and documented commands needed to verify the foundation.
- **Build toolchain**: The locked set of compatible language, build, Android SDK, and JDK versions used by every automated build.
- **Base module**: One of the approved `:core`, `:data`, or `:app` boundaries, with a defined dependency direction and responsibility.
- **Foundation application**: The installable, launchable TapLog shell with no product behavior or product data.

## Success Criteria

### Measurable Outcomes

- **SC-001**: A clean GitHub Codespaces checkout completes the documented build, JVM-test, lint, and static-quality command sequence without manual tool installation or machine-specific configuration.
- **SC-002**: On an API 26-compatible device or emulator, 100% of five consecutive clean build-install-launch attempts show the minimal TapLog shell without a crash.
- **SC-003**: Inspection of the baseline dependency graph confirms that all three required modules are present, `:core` has zero Android dependencies, and no channel-specific module exists.
- **SC-004**: All declared build and library versions are fixed and centrally discoverable; the baseline contains zero dynamic version declarations.
- **SC-005**: A contributor can determine, from repository documentation alone and within 10 minutes, which verification commands run in Codespaces and which require a connected Android environment.
- **SC-006**: The launched shell exposes zero Record, Event, Target, NFC, widget, Quick Settings, parser, backup, statistics, or monetization interactions.

## Assumptions

- The approved roadmap’s first slice is the authoritative scope boundary; later slices remain unimplemented.
- GitHub Codespaces is used for compilation, pure JVM tests, and static checks; local Android Studio or a physical device remains available for connected Android verification.
- The foundation may establish empty test scaffolds and dependency governance but does not authorize product domain types, storage schema, network behavior, or channel integration.
- API 36 is the stable current Google Play submission baseline; API 37 is not adopted because it remains an Android 17 preview platform at this feature's approval date.
- `io.github.thrhead.taplog` is based on the public namespace of the repository owner and must remain stable after the first published build.

## Explicit Exclusions

- Record, Event, Target, behavior, Event Engine, persistence, migration, and any product data behavior.
- NFC, widget, Quick Settings, notifications, parser/AI, backup/restore, statistics, Play Billing/monetization, and every other V1 feature beyond the roadmap’s first slice.
- Additional modules, a DI framework, cloud backend, analytics, production release workflow, or any behavior not required to establish the approved foundation.
