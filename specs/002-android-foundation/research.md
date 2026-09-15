# Research: Android Foundation Decisions

## Toolchain lock

**Decision**: Use API 26 minimum; API 36 compile and target; JDK 17; Android Gradle Plugin 9.4.0; Gradle 9.6.0; Build Tools 36.0.0; Kotlin/Kotlin Compose compiler 2.3.21; and KSP 2.3.6.

**Rationale**: The feature specification locks these values. AGP 9.4.0 declares Gradle 9.6.0, Build Tools 36.0.0, and JDK 17 as compatible defaults, and Android 16/API 36 is the stable Play submission baseline. The wrapper prevents a local Gradle installation from changing the build.

**Alternatives considered**: API 37/Android 17 is preview and would exceed the approved platform decision. Lower AGP/Gradle versions would not satisfy the approved locked baseline. A newer JDK adds no foundation value.

**Sources**: [AGP 9.4.0 compatibility](https://developer.android.com/build/releases/agp-9-4-0-release-notes), [Android 16 SDK setup](https://developer.android.com/about/versions/16/setup-sdk), [Gradle Wrapper guidance](https://developer.android.com/build/releases/about-agp).

## Compose shell dependency set

**Decision**: Use Activity Compose 1.13.0 with the stable Compose BOM 2026.03.00, Material 3, and only the Compose UI test artifacts required to test the static shell.

**Rationale**: Compose/Material is part of the approved architecture. BOM 2026.03.00 provides a stable Compose 1.10.x family compatible with API 36. Current newer Compose releases require `compileSdk` 37, which this feature must not introduce.

**Alternatives considered**: A non-Compose Activity contradicts the approved UI direction. BOM 2026.08.00 and later would broaden the approved SDK baseline. No navigation, lifecycle/ViewModel, or adaptive dependency is necessary for a static shell.

**Sources**: [Compose setup and compatibility](https://developer.android.com/develop/ui/compose/setup-compose-dependencies-and-compiler), [Activity 1.13.0](https://developer.android.com/jetpack/androidx/releases/activity), [Compose BOM](https://developer.android.com/develop/ui/compose/bom).

## Test scaffold dependencies

**Decision**: Use JUnit 4.13.2, AndroidX Test Runner 1.7.0, AndroidX JUnit extension 1.3.0, and the pinned Compose BOM's UI test artifacts.

**Rationale**: This gives the foundation one pure JVM test lane per module and one connected launch test without introducing application/domain fixtures or a separate test module.

**Alternatives considered**: Espresso and test orchestration are not necessary to validate a static Compose shell. Adding them would increase dependency surface without an approved feature behavior.

**Source**: [AndroidX Test releases](https://developer.android.com/jetpack/androidx/releases/test).

## KSP boundary

**Decision**: Declare KSP 2.3.6 in the catalog but do not apply it in this slice.

**Rationale**: The approved architecture requires KSP to be part of the locked toolchain, but this foundation has no annotation processor, generated code, Room schema, or migration. Applying it would add build behavior with no approved need.

**Alternatives considered**: Applying KSP to `:data` early is rejected because it would invite unauthorized Room/schema work. Omitting it entirely violates the approved locked-toolchain baseline.

**Risk**: Future Room/KSP adoption must prove symbol processing and Android compilation under AGP 9 before acceptance; KSP has known AGP built-in-Kotlin edge cases. That validation belongs to the persistence slice.

**Sources**: [KSP Gradle configurations](https://github.com/google/ksp#ksp-gradle-configurations-reference), [KSP issue 2857](https://github.com/google/ksp/issues/2857).

## Static quality and formatting

**Decision**: Use Android lint plus Detekt 1.23.8 with checked-in formatting rules; do not adopt Detekt 2.x alpha.

**Rationale**: The approved architecture's quickstart names `lint detekt`, while the feature requires both static analysis and a formatting check. The final 1.x Detekt release provides a non-preview baseline; configured formatting rules give a single repeatable quality lane.

**Alternatives considered**: Detekt 2.x is pre-release and requires AGP 9 workarounds. A second independent formatter would duplicate scope and configuration.

**Validation**: The foundation's first implementation task must run `detekt` and the formatting rules with AGP 9.4/Kotlin 2.3.21 before the lane is accepted.

**Source**: [Detekt stable 1.x setup](https://github.com/detekt/detekt).

## Codespaces and CI

**Decision**: Provision JDK 17 and exactly `platforms;android-36` plus `build-tools;36.0.0` in `.devcontainer/Dockerfile`; no AVD, system image, or KVM. Run a least-privilege GitHub Actions workflow for push and pull request using the same wrapper command as Codespaces.

**Rationale**: Image-time provisioning makes a clean Codespaces workspace repeatable. The approved architecture records that Codespaces lacks KVM, so device tests must remain external. CI is limited to the non-device verification contract.

**Alternatives considered**: Installing SDK packages after workspace creation reduces reproducibility. Running an emulator in Codespaces/CI contradicts the approved validation boundary. Publishing/release jobs are outside the feature.

**Sources**: [Dev container configuration](https://docs.github.com/en/codespaces/setting-up-your-project-for-codespaces/adding-a-dev-container-configuration/introduction-to-dev-containers), [sdkmanager packages](https://developer.android.com/tools/sdkmanager), [Gradle Actions setup](https://github.com/gradle/actions/blob/main/docs/setup-gradle.md).
