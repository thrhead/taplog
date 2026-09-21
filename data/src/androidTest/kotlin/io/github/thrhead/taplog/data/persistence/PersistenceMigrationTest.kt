package io.github.thrhead.taplog.data.persistence

import android.database.sqlite.SQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class PersistenceMigrationTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun freshV1CreationUsesTheEmptyProductionMigrationMatrix() = withName { name ->
        assertTrue(TapLogDatabase.productionMigrations.isEmpty())
        withDatabase(name) { database ->
            assertEquals(1, database.openHelper.writableDatabase.version)
            assertEquals(listOf(DatasetMetadataEntity(1, 0, 1, 1)), database.persistenceDao().readMetadataRows())
        }
    }

    @Test
    fun unsupportedVersionFailsClosedWithoutResettingThePriorDatabase() = withName { name ->
        withDatabase(name) { database ->
            database.persistenceDao().upsertMetadata(listOf(DatasetMetadataEntity(1, 9, 20, 1)))
        }
        SQLiteDatabase.openDatabase(context.getDatabasePath(name).path, null, SQLiteDatabase.OPEN_READWRITE).use {
            it.version = 2
        }

        withDatabase(name) { database ->
            assertThrows(DatabaseOpenFailure::class.java) { RoomLocalPersistence(database).read() }
        }
        SQLiteDatabase.openDatabase(context.getDatabasePath(name).path, null, SQLiteDatabase.OPEN_READONLY).use {
            assertEquals(2, it.version)
            it.rawQuery("SELECT datasetGeneration,nextSequence FROM dataset_metadata", null).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(9L, cursor.getLong(0))
                assertEquals(20L, cursor.getLong(1))
            }
        }
    }

    @Test
    fun corruptRowsFailMappingWithoutRepairOrDroppingStoredData() = withName { name ->
        withDatabase(name) { database ->
            database.openHelper.writableDatabase.execSQL(
                "INSERT INTO dataset_metadata(singletonKey,datasetGeneration,nextSequence,schemaVersion) VALUES (2,0,1,1)",
            )
            assertThrows(MappingFailure::class.java) { RoomLocalPersistence(database).read() }
            assertEquals(2, database.persistenceDao().readMetadataRows().size)
        }
    }

    private fun withDatabase(name: String, block: (TapLogDatabase) -> Unit) {
        val database = TapLogDatabase.builder(context, name).allowMainThreadQueries().build()
        try { block(database) } finally { database.close() }
    }

    private fun withName(block: (String) -> Unit) {
        val name = "taplog-migration-${UUID.randomUUID()}.db"
        try { block(name) } finally { context.deleteDatabase(name) }
    }
}
