plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.detekt)
}

detekt {
    buildUponDefaultConfig = true
    allRules = false
    config.setFrom(files("$rootDir/config/detekt/detekt.yml"))
    source.setFrom(
        files(
            "core/src",
            "data/src",
            "app/src"
        )
    )
}

dependencies {
    detektPlugins(libs.detekt.formatting)
}

tasks.register("foundationCheck") {
    group = "verification"
    description = "Runs non-device verification suite: compilation, JVM tests, lint, and Detekt static quality checks."
    dependsOn(
        ":core:test",
        ":data:testDebugUnitTest",
        ":detekt"
    )
}
