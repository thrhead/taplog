package io.github.thrhead.taplog.data.persistence

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class DependencyBoundaryTest {
    @Test
    fun dataOwnsRoomAndCoreRemainsFrameworkIndependent() {
        val dataBuild = File("build.gradle.kts").readText()
        val coreBuild = File("../core/build.gradle.kts").readText()
        val coreSources = File("../core/src").walkTopDown().filter { it.isFile && it.extension == "kt" }
            .flatMap { it.readLines().asSequence() }.toList()

        assertTrue(dataBuild.contains("implementation(project(\":core\"))"))
        assertTrue(dataBuild.contains("libs.room.runtime"))
        assertFalse(coreBuild.contains("com.android"))
        assertFalse(coreSources.any { it.contains("android.") || it.contains("androidx.room") })
        assertFalse(coreSources.any { it.contains("io.github.thrhead.taplog.data.persistence") })
    }

    @Test
    fun excludedProductAreasAreNotIntroducedByThePersistenceSlice() {
        val dataSources = File("src").walkTopDown().filter { it.isFile && it.extension == "kt" }
            .flatMap { it.readLines().asSequence() }.toList()
        listOf("nfc", "widget", "quicksettings", "parser", "backup", "sync", "cloud", "monetization", "ai")
            .forEach { area -> assertFalse(dataSources.any { it.lowercase().contains("$area.") || it.lowercase().contains("$area/") }) }
    }
}
