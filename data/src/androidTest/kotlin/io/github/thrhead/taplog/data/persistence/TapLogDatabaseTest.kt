package io.github.thrhead.taplog.data.persistence

import android.database.sqlite.SQLiteConstraintException
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
class TapLogDatabaseTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun freshCreationInitializesMetadataForeignKeysAndThePartialIndex() = withDatabaseName { name ->
        open(name).use { database ->
            assertEquals(listOf(DatasetMetadataEntity(1, 0, 1, 1)), database.persistenceDao().readMetadataRows())
            val sqlite = database.openHelper.writableDatabase
            sqlite.query("PRAGMA foreign_keys").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(1, cursor.getInt(0))
            }
            sqlite.query(
                "SELECT sql FROM sqlite_master WHERE name = 'index_events_open_duration_scope'",
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(
                    "CREATE UNIQUE INDEX `index_events_open_duration_scope` ON `events` " +
                        "(`recordId`, `targetScopeKey`) WHERE `behavior` = 'DURATION' AND `durationStatus` = 'OPEN'",
                    cursor.getString(0),
                )
            }
            assertThrows(SQLiteConstraintException::class.java) {
                database.persistenceDao().upsertRecordTargets(
                    listOf(RecordTargetEntity("absent-record", "absent-target", true, 1)),
                )
            }
        }
    }

    @Test
    fun identityHashReopenPreservesMetadataAndHistoryAndStillEnforcesOpenUniqueness() = withDatabaseName { name ->
        open(name).use { database ->
            val dao = database.persistenceDao()
            dao.upsertRecords(listOf(durationRecord()))
            dao.upsertMetadata(listOf(DatasetMetadataEntity(1, 7, 10, 1)))
            dao.upsertEvents(listOf(durationEvent("open", 1, "OPEN")))
            dao.upsertEvents(listOf(durationEvent("done-one", 2, "COMPLETED")))
            dao.upsertEvents(listOf(durationEvent("done-two", 3, "COMPLETED")))
        }

        open(name).use { database ->
            val dao = database.persistenceDao()
            assertEquals(listOf(DatasetMetadataEntity(1, 7, 10, 1)), dao.readMetadataRows())
            assertEquals(3, dao.readEvents().size)
            assertEquals("open", dao.readOpenDuration("record", "no-target")!!.eventId)
            assertThrows(SQLiteConstraintException::class.java) {
                dao.upsertEvents(listOf(durationEvent("competing", 4, "OPEN")))
            }
            assertEquals(3, dao.readEvents().size)
        }
    }

    @Test
    fun unsupportedVersionFailsWithoutResettingThePriorDatabase() = withDatabaseName { name ->
        open(name).use { database ->
            database.persistenceDao().upsertMetadata(listOf(DatasetMetadataEntity(1, 9, 20, 1)))
        }
        SQLiteDatabase.openDatabase(context.getDatabasePath(name).path, null, SQLiteDatabase.OPEN_READWRITE).use {
            it.version = 2
        }

        open(name).use { database ->
            assertThrows(IllegalStateException::class.java) { database.persistenceDao().readMetadataRows() }
        }

        SQLiteDatabase.openDatabase(context.getDatabasePath(name).path, null, SQLiteDatabase.OPEN_READONLY).use {
            assertEquals(2, it.version)
            it.rawQuery("SELECT datasetGeneration,nextSequence FROM dataset_metadata WHERE singletonKey=1", null)
                .use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    assertEquals(9L, cursor.getLong(0))
                    assertEquals(20L, cursor.getLong(1))
                }
        }
    }

    private fun open(name: String): TapLogDatabase = TapLogDatabase.builder(context, name)
        .allowMainThreadQueries()
        .build()

    private fun <T> TapLogDatabase.use(block: (TapLogDatabase) -> T): T = try {
        block(this)
    } finally {
        close()
    }

    private fun withDatabaseName(block: (String) -> Unit) {
        val name = "taplog-t010-${UUID.randomUUID()}.db"
        try {
            block(name)
        } finally {
            context.deleteDatabase(name)
        }
    }

    private fun durationRecord() = RecordEntity(
        "record", "Duration", null, "DURATION", "ACTIVE", null, null, null, 1, true,
    )

    private fun durationEvent(id: String, sequence: Long, status: String) = EventEntity(
        eventId = id,
        recordId = "record",
        targetId = null,
        targetScopeKey = "no-target",
        behavior = "DURATION",
        occurredAt = 1,
        createdAt = 1,
        updatedAt = 1,
        sequence = sequence,
        source = "APP",
        revision = 1,
        snapshotRecordName = "Duration",
        snapshotRecordIcon = null,
        snapshotTargetName = null,
        snapshotTargetIcon = null,
        snapshotBehavior = "DURATION",
        snapshotUnit = null,
        durationStartAt = 1,
        durationEndAt = if (status == "COMPLETED") 2 else null,
        durationStatus = status,
    )
}
