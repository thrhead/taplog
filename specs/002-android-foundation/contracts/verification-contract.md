# Build and Verification Contract

This contract is the shared interface for contributors, Codespaces, and GitHub Actions. It deliberately exposes only foundation checks and no product command.

## Preconditions

| Environment | Required capability | Not required |
| --- | --- | --- |
| Codespaces | Checked-out repository, JDK 17, Android SDK platform 36 and Build Tools 36.0.0 | `adb`, emulator, AVD, `/dev/kvm`, NFC device |
| GitHub Actions | Ubuntu runner, JDK 17, Android SDK platform 36 and Build Tools 36.0.0 | Secrets, emulator, signing/publishing credentials |
| Local device lane | API-26-or-newer device/emulator with `adb` available | Network product service, NFC, widget, or product data |

## Commands

| Command | Supported environment | Contractual outcome |
| --- | --- | --- |
| `./gradlew --version` | All | Uses checked-in Gradle 9.6.0 and JDK 17. |
| `./gradlew --no-daemon clean foundationCheck` | Codespaces, CI, local | Builds app debug artifact, runs `:core:test`, `:data:test`, `:app:testDebugUnitTest`, Android lint, Detekt, and formatting rules without a device. |
| `./gradlew :core:dependencies :data:dependencies :app:dependencies` | Codespaces, CI, local | Makes module dependency direction reviewable; `:core` must have no Android artifact. |
| `./gradlew --no-daemon :app:connectedDebugAndroidTest` | Local device/emulator only | The neutral launcher shell starts and its test passes. |
| `./gradlew --no-daemon :app:installDebug` | Local device/emulator only | The package installs for manual launch validation. |

## Failure behavior

- A missing local device/`adb` makes only connected/manual commands unavailable; it must not fail `foundationCheck` or the CI job.
- Any wrapper, toolchain resolution, compilation, test, lint, Detekt, or formatting failure makes `foundationCheck` fail non-zero.
- CI runs no connected/device test, no release build, no publishing step, and no product action. It uploads lint/Detekt reports only when its verification fails.

## Boundary acceptance

The module graph is acceptable only when `:core` remains Kotlin/JVM and Android free, `:data` depends on `:core` only, and `:app` depends on both lower layers. The app is acceptable only when its visible shell is static and creates no product data or product interaction.
