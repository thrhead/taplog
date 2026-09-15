# Foundation Validation Quickstart

This guide validates the Android foundation after implementation. It does not authorize or test a TapLog product feature.

## Codespaces

1. Open a clean checkout in GitHub Codespaces and wait for the dev container to finish provisioning JDK 17, Android platform 36, and Build Tools 36.0.0.
2. Confirm the wrapper/toolchain:

   ```sh
   ./gradlew --version
   ```

   Expected: Gradle 9.6.0 running on JDK 17.

3. Run the device-independent foundation lane:

   ```sh
   ./gradlew --no-daemon clean foundationCheck
   ```

   Expected: debug app compilation, JVM scaffolds, lint, and Detekt/formatting checks pass. No `adb`, emulator, or device must be required.

4. Inspect the dependency boundaries:

   ```sh
   ./gradlew :core:dependencies :data:dependencies :app:dependencies
   ```

   Expected: `:core` has no Android dependency; `:data` depends on `:core`; and `:app` depends on both. Verify catalog entries contain no dynamic versions.

## GitHub Actions

Open a pull request or push a branch. The Android foundation workflow must run the same `foundationCheck` command with least-privilege read-only permissions. A failed quality/build lane is visible as a failed job; connected tests and publishing do not run.

## API-26-or-newer device or emulator

1. Start an API-26-or-newer emulator or connect a physical device, then verify that `adb devices` lists it.
2. Run the connected launch scaffold:

   ```sh
   ./gradlew --no-daemon :app:connectedDebugAndroidTest
   ```

3. Repeat the following five times from a clean build state:

   ```sh
   ./gradlew --no-daemon :app:installDebug
   ```

   Launch `io.github.thrhead.taplog`. Expected: a static, accessible TapLog shell appears without crashing. It offers no Record, Event, Target, NFC, widget, Quick Settings, parser, backup, statistics, or purchase interaction.

## Troubleshooting boundaries

- Missing `adb` or an unavailable device is expected in Codespaces; do not add an emulator/AVD/KVM workaround to make the non-device lane pass.
- A toolchain mismatch is corrected only by the checked-in wrapper, version catalog, and devcontainer/CI SDK package configuration; do not use a global Gradle installation or change the approved version set ad hoc.
- A Detekt incompatibility is a foundation quality-lane defect to resolve within the approved static-analysis scope; it does not authorize adding product code or changing the module architecture.
