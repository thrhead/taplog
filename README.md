# TapLog

TapLog is a local-first Android lifecycle and event logging application.

## Architecture and Foundation

The project establishes a strict three-module architecture:
- `:core`: Pure Kotlin/JVM domain, application rules, and contracts (zero Android dependencies).
- `:data`: Android library for local persistence adapters (depends only on `:core`).
- `:app`: Android application composition root and Jetpack Compose UI (depends on `:core` and `:data`).

### Toolchain Baseline
- Minimum SDK: API 26 (Android 8.0)
- Compile & Target SDK: API 36 (Android 16)
- Java Development Kit: JDK 17
- Gradle: 9.6.0
- Android Gradle Plugin (AGP): 9.4.0
- Kotlin Gradle Plugin: 2.3.21

---

## Development Environment (GitHub Codespaces)

The repository provides a pre-configured dev container environment for GitHub Codespaces containing:
- JDK 17
- Android SDK Platform 36 (`platforms;android-36`)
- Android SDK Build-Tools 36.0.0 (`build-tools;36.0.0`)

No local Android SDK or emulator installation is required to develop, compile, test, or lint the application in Codespaces.

---

## Verification Commands

### 1. Codespaces / Local (Non-Device Verification)

Run these commands in GitHub Codespaces or a local environment with the documented JDK and SDK prerequisites, without requiring an emulator, ADB, or physical device. GitHub Actions validates the Gradle Wrapper and runs `./gradlew --no-daemon clean foundationCheck`; the version and dependency reports below are contributor checks, not separate CI steps.

- **Check Toolchain Version**:
  ```bash
  ./gradlew --version
  ```
  *Expected result*: Confirms Gradle 9.6.0 running on JDK 17.

- **Run Foundation Verification Suite**:
  ```bash
  ./gradlew --no-daemon clean foundationCheck
  ```
  *Expected result*: Compiles all modules, executes JVM unit test scaffolds, runs Android lint, and executes Detekt static analysis and formatting checks.

- **Check Dependency Boundaries**:
  ```bash
  ./gradlew :core:dependencies :data:dependencies :app:dependencies
  ```
  *Expected result*: Confirms `:core` has zero Android dependencies, `:data` depends only on `:core`, and `:app` depends on `:core` and `:data`.

### 2. Connected Device / Emulator Verification (Local Only)

Running instrumented UI tests or launching the application requires a physical Android device or local Android emulator running API 26 (Android 8.0) or higher with ADB connected. These checks are **not** run in Codespaces or CI.

- **Run Connected Launch Tests**:
  ```bash
  ./gradlew --no-daemon :app:connectedDebugAndroidTest
  ```

- **Install and Launch Foundation Shell**:
  ```bash
  ./gradlew --no-daemon :app:installDebug
  ```

---

## Development Rules

- **Domain and application behavior is test-driven**: Pure JVM unit tests in `:core` verify domain semantics before integration.
- **Scope discipline**: Features are implemented slice-by-slice according to the approved architecture roadmap.
