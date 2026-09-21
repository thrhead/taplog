package io.github.thrhead.taplog.data.persistence

import androidx.room.RoomOpenDelegate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.sql.SQLException

class TapLogDatabaseInitializationTest {
    @Test
    fun creationSeedsExactlyOneMetadataRowWithInitialCounters() {
        withCreatedSchema { connection, _ ->
            PersistenceDatabaseCallback.onCreate(connection)

            connection.prepare("SELECT * FROM dataset_metadata").use { statement ->
                assertTrue(statement.step())
                assertEquals(1L, statement.getLong(0))
                assertEquals(0L, statement.getLong(1))
                assertEquals(1L, statement.getLong(2))
                assertEquals(1L, statement.getLong(3))
                assertEquals(false, statement.step())
            }
        }
    }

    @Test
    fun openingInstallsPartialIndexAfterFreshSchemaValidationAndEnforcesForeignKeys() {
        withCreatedSchema { connection, delegate ->
            PersistenceDatabaseCallback.onCreate(connection)
            assertTrue(delegate.onValidateSchema(connection).isValid)
            PersistenceDatabaseCallback.onOpen(connection)

            assertEquals(1L, scalar(connection, "PRAGMA foreign_keys"))
            connection.prepare("PRAGMA index_list(events)").use { statement ->
                var found = false
                while (statement.step()) {
                    if (statement.getText(1) == "index_events_open_duration_scope") {
                        found = true
                        assertEquals(1L, statement.getLong(2))
                        assertEquals(1L, statement.getLong(4))
                    }
                }
                assertTrue(found)
            }
            assertThrows(SQLException::class.java) {
                connection.execute("INSERT INTO record_targets VALUES ('missing-record','missing-target',1,1)")
            }
        }
    }

    @Test
    fun repeatedOpenPreservesMetadataAndEventRowsAndRejectsOnlyCompetingOpenDurations() {
        withCreatedSchema { connection, _ ->
            PersistenceDatabaseCallback.onCreate(connection)
            PersistenceDatabaseCallback.onOpen(connection)
            connection.execute("UPDATE dataset_metadata SET datasetGeneration=5,nextSequence=20")
            connection.execute(
                "INSERT INTO records(recordId,name,behavior,lifecycle,revision,hasEvents) " +
                    "VALUES ('record','Duration','DURATION','ACTIVE',1,1)",
            )
            insertDuration(connection, "first", 1, "OPEN")
            insertDuration(connection, "completed-one", 2, "COMPLETED")
            insertDuration(connection, "completed-two", 3, "COMPLETED")
            insertDuration(connection, "incomplete", 4, "INCOMPLETE")
            PersistenceDatabaseCallback.onOpen(connection)

            assertEquals(1L, scalar(connection, "SELECT COUNT(*) FROM dataset_metadata"))
            assertEquals(5L, scalar(connection, "SELECT datasetGeneration FROM dataset_metadata"))
            assertEquals(20L, scalar(connection, "SELECT nextSequence FROM dataset_metadata"))
            assertEquals(4L, scalar(connection, "SELECT COUNT(*) FROM events"))
            assertThrows(SQLException::class.java) { insertDuration(connection, "competing", 5, "OPEN") }
            connection.execute("UPDATE events SET durationStatus='COMPLETED' WHERE eventId='first'")
            insertDuration(connection, "next", 5, "OPEN")
            assertEquals(5L, scalar(connection, "SELECT COUNT(*) FROM events"))
        }
    }

    @Test
    fun openingRejectsAnExistingFullIndexWithTheManagedPartialIndexName() {
        withCreatedSchema { connection, _ ->
            PersistenceDatabaseCallback.onCreate(connection)
            connection.execute(
                "CREATE UNIQUE INDEX index_events_open_duration_scope ON events(recordId,targetScopeKey)",
            )

            assertThrows(DatabaseOpenFailure::class.java) { PersistenceDatabaseCallback.onOpen(connection) }
        }
    }

    private fun withCreatedSchema(block: (JdbcSQLiteConnection, RoomOpenDelegate) -> Unit) {
        JdbcSQLiteConnection().use { connection ->
            val implementation = Class.forName("io.github.thrhead.taplog.data.persistence.TapLogDatabase_Impl")
            val database = implementation.getDeclaredConstructor().newInstance()
            val method = implementation.getDeclaredMethod("createOpenDelegate").apply { isAccessible = true }
            val delegate = method.invoke(database) as RoomOpenDelegate
            delegate.createAllTables(connection)
            block(connection, delegate)
        }
    }

    private fun scalar(connection: JdbcSQLiteConnection, sql: String): Long =
        connection.prepare(sql).use { statement ->
            assertTrue(statement.step())
            statement.getLong(0)
        }

    private fun insertDuration(connection: JdbcSQLiteConnection, id: String, sequence: Long, status: String) {
        connection.execute(
            "INSERT INTO events(eventId,recordId,targetScopeKey,behavior,occurredAt,createdAt,updatedAt," +
                "sequence,source,revision,snapshotRecordName,snapshotBehavior,durationStartAt,durationStatus) " +
                "VALUES ('$id','record','no-target','DURATION',1,1,1,$sequence,'APP',1," +
                "'Duration','DURATION',1,'$status')",
        )
    }
}
