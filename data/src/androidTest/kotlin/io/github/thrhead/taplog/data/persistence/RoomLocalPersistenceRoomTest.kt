package io.github.thrhead.taplog.data.persistence

import android.database.sqlite.SQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.thrhead.taplog.core.domain.*
import io.github.thrhead.taplog.core.domain.Record
import io.github.thrhead.taplog.core.engine.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class RoomLocalPersistenceRoomTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun stagedEventRowsSurviveReopenWithHistoricalSnapshotsAndExactChronology() = withDatabaseName { name ->
        val expected = aggregate().let { state -> state.copy(events = state.events.map { it.copy(
            occurredAt = EpochMillis(100), createdAt = EpochMillis(-100), updatedAt = EpochMillis(200),
            source = Source.APP, revision = Revision(8),
        ) }) }
        withDatabase(name) { database ->
            seed(database, expected.copy(events = emptyList()))
            database.runInTransaction {
                RoomLocalPersistence(database).writeEvents(expected.copy(events = expected.events.reversed()), database.persistenceDao())
            }
            assertEquals(expected, RoomLocalPersistence(database).read())
            assertEquals(listOf(1L, 2L, 3L, 4L), database.persistenceDao().readEvents().map { it.sequence })
            val counter = database.persistenceDao().readEvent("event-2")!!
            assertEquals("1.25", counter.counterQuantity)
            assertEquals("old unit", counter.counterUnit)
            assertEquals("old counter", counter.snapshotRecordName)
            assertEquals("old target", counter.snapshotTargetName)
        }
        withDatabase(name) { database ->
            assertEquals(expected, RoomLocalPersistence(database).read())
            database.runInTransaction {
                RoomLocalPersistence(database).writeEvents(DomainState(), database.persistenceDao())
            }
            assertEquals(expected, RoomLocalPersistence(database).read())
        }
    }

    @Test
    fun callerTransactionRollsBackStagedEventUpdates() = withDatabaseName { name ->
        val before = aggregate()
        val edited = before.copy(events = before.events.map { it.copy(updatedAt = EpochMillis(99), revision = Revision(9)) })
        withDatabase(name) { database ->
            seed(database, before)
            assertThrows(IllegalStateException::class.java) {
                database.runInTransaction {
                    RoomLocalPersistence(database).writeEvents(edited, database.persistenceDao())
                    error("Abort after Event phase")
                }
            }
            assertEquals(before, RoomLocalPersistence(database).read())
        }
        withDatabase(name) { assertEquals(before, RoomLocalPersistence(it).read()) }
    }

    @Test
    fun recordAndTargetUpdatesSurviveReopenWithOriginalHistory() = withDatabaseName { name ->
        val before = aggregate()
        val expected = before.copy(
            records = before.records.mapValues { (_, record) -> record.copy(
                name = "edited ${record.id.value}", icon = "edited icon", revision = Revision(20),
            ) } + (RecordId("new-counter") to Record(RecordId("new-counter"), "new counter", null,
                Behavior.COUNTER, unit = UnitName("cups"), defaultQuantity = Quantity.exact("2.5"))),
            targets = before.targets.mapValues { (_, target) -> target.copy(
                name = "edited target", icon = "edited target icon", revision = Revision(21),
            ) } + (TargetId("new-target") to Target(TargetId("new-target"), "new target", null,
                lifecycle = Lifecycle.ARCHIVED, revision = Revision(3))),
        )
        withDatabase(name) { database ->
            seed(database, before)
            val history = database.persistenceDao().readEvents()
            database.runInTransaction {
                val input = expected.copy(records = expected.records + (RecordId("new-counter") to
                    expected.records.getValue(RecordId("new-counter")).copy(defaultQuantity = Quantity.exact("2.500"))))
                RoomLocalPersistence(database).writeRecordsAndTargets(input, database.persistenceDao())
            }
            assertEquals(expected, RoomLocalPersistence(database).read())
            assertEquals(history, database.persistenceDao().readEvents())
            assertEquals("2.5", database.persistenceDao().readRecord("new-counter")!!.defaultQuantity)
        }
        withDatabase(name) { database ->
            val actual = RoomLocalPersistence(database).read()
            assertEquals(expected, actual)
            assertEquals("old counter", actual.events[1].snapshot.recordName)
            assertEquals(UnitName("old unit"), (actual.events[1].payload as EventPayload.Counter).unit)
        }
    }

    @Test
    fun callerTransactionRollsBackRecordAndTargetWritePhase() = withDatabaseName { name ->
        val before = aggregate()
        val edited = before.copy(
            records = before.records.mapValues { (_, record) -> record.copy(name = "partial record", revision = Revision(20)) },
            targets = before.targets.mapValues { (_, target) -> target.copy(name = "partial target", revision = Revision(21)) },
        )
        withDatabase(name) { database ->
            seed(database, before)
            assertThrows(IllegalStateException::class.java) {
                database.runInTransaction {
                    RoomLocalPersistence(database).writeRecordsAndTargets(edited, database.persistenceDao())
                    error("Abort after both definition families")
                }
            }
            assertEquals(before, RoomLocalPersistence(database).read())
        }
        withDatabase(name) { assertEquals(before, RoomLocalPersistence(it).read()) }
    }

    @Test
    fun completeAggregateAndEqualTimeChronologySurviveReopen() = withDatabaseName { name ->
        val expected = aggregate()
        withDatabase(name) { database ->
            seed(database, expected)
            assertEquals(expected, RoomLocalPersistence(database).read())
        }
        withDatabase(name) { database ->
            val actual = RoomLocalPersistence(database).read()
            assertEquals(expected, actual)
            assertEquals(listOf(1L, 2L, 3L, 4L), actual.events.map { it.sequence.value })
            assertEquals("old counter", actual.events[1].snapshot.recordName)
            assertEquals(RecordId("deleted-current-record"), actual.currentStates.values.single())
        }
    }

    @Test
    fun rolledBackChangesNeverBecomeTheReadableAggregate() = withDatabaseName { name ->
        withDatabase(name) { database ->
            val expected = aggregate()
            seed(database, expected)
            assertThrows(IllegalStateException::class.java) {
                database.runInTransaction {
                    database.openHelper.writableDatabase.execSQL("UPDATE records SET name='partial name'")
                    database.persistenceDao().upsertMetadata(listOf(DatasetMetadataEntity(1, 99, 100, 1)))
                    error("Abort before SQLite commit")
                }
            }
            assertEquals(expected, RoomLocalPersistence(database).read())
        }
        withDatabase(name) { assertEquals(aggregate(), RoomLocalPersistence(it).read()) }
    }

    @Test
    fun malformedRowsFailWithoutRewritingOrDroppingHistory() = withDatabaseName { name ->
        withDatabase(name) { database ->
            seed(database, aggregate())
            database.openHelper.writableDatabase.execSQL(
                "UPDATE events SET counterQuantity='invalid' WHERE behavior='COUNTER'",
            )

            assertThrows(MappingFailure::class.java) { RoomLocalPersistence(database).read() }
            assertEquals(4, database.persistenceDao().readEvents().size)
            assertEquals("invalid", database.persistenceDao().readEvents().single { it.behavior == "COUNTER" }.counterQuantity)
        }
        withDatabase(name) { database ->
            assertThrows(MappingFailure::class.java) { RoomLocalPersistence(database).read() }
            assertEquals(4, database.persistenceDao().readEvents().size)
        }
    }

    @Test
    fun unsupportedDatabaseVersionFailsOpenWithoutResettingPersistedMetadata() = withDatabaseName { name ->
        withDatabase(name) { seed(it, aggregate()) }
        SQLiteDatabase.openDatabase(context.getDatabasePath(name).path, null, SQLiteDatabase.OPEN_READWRITE).use {
            it.version = 2
        }
        withDatabase(name) { database ->
            assertThrows(DatabaseOpenFailure::class.java) { RoomLocalPersistence(database).read() }
        }
        SQLiteDatabase.openDatabase(context.getDatabasePath(name).path, null, SQLiteDatabase.OPEN_READONLY).use { sqlite ->
            assertEquals(2, sqlite.version)
            sqlite.rawQuery("SELECT datasetGeneration,nextSequence FROM dataset_metadata", null).use { cursor ->
                cursor.moveToFirst()
                assertEquals(7L, cursor.getLong(0))
                assertEquals(20L, cursor.getLong(1))
            }
        }
    }

    @Test
    fun freshDatabaseReadsTheInitializedEmptyAggregate() = withDatabaseName { name ->
        withDatabase(name) { assertEquals(DomainState(), RoomLocalPersistence(it).read()) }
    }

    private fun seed(database: TapLogDatabase, state: DomainState) {
        val rows = PersistenceMapper.toRows(state)
        database.runInTransaction {
            val dao = database.persistenceDao()
            dao.upsertStateGroups(rows.stateGroups)
            dao.upsertRecords(rows.records)
            dao.upsertTargets(rows.targets)
            dao.upsertRecordTargets(rows.recordTargets)
            // Insert against chronological order to verify persisted ordering, including ties.
            dao.upsertEvents(rows.events.reversed())
            dao.upsertStateScopes(rows.stateScopes)
            dao.upsertBindings(rows.bindings)
            dao.upsertBindingUndoInvalidations(rows.bindingUndoInvalidations)
            dao.upsertUndoReceipts(rows.undoReceipts)
            dao.upsertMetadata(rows.metadata)
        }
    }

    private fun withDatabase(name: String, block: (TapLogDatabase) -> Unit) {
        val database = TapLogDatabase.builder(context, name).allowMainThreadQueries().build()
        try { block(database) } finally { database.close() }
    }

    private fun withDatabaseName(block: (String) -> Unit) {
        val name = "taplog-t011-${UUID.randomUUID()}.db"
        try { block(name) } finally { context.deleteDatabase(name) }
    }

    private fun aggregate(): DomainState {
        val target = TargetId("target")
        val group = StateGroupId("group")
        val scope = StateScope(group, target)
        val records = listOf(
            Record(RecordId("moment"), "new moment", null, Behavior.MOMENT, hasEvents = true),
            Record(RecordId("counter"), "new counter", null, Behavior.COUNTER, unit = UnitName("new unit"),
                defaultQuantity = Quantity.exact("2"), hasEvents = true),
            Record(RecordId("duration"), "new duration", null, Behavior.DURATION, hasEvents = true),
            Record(RecordId("state"), "new state", null, Behavior.STATE, stateGroupId = group, hasEvents = true),
        ).associateBy { it.id }
        fun event(record: String, behavior: Behavior, sequence: Long, payload: EventPayload, targetId: TargetId?): Event =
            Event(EventId("event-$sequence"), RecordId(record), targetId, behavior, EpochMillis(10), EpochMillis(-2),
                EpochMillis(20), Sequence(sequence), Source.NFC, Revision(3),
                EventSnapshot("old $record", "old", if (targetId == null) null else "old target", null,
                    behavior, if (behavior == Behavior.COUNTER) UnitName("old unit") else null), payload)
        val events = listOf(
            event("moment", Behavior.MOMENT, 1, EventPayload.Moment, null),
            event("counter", Behavior.COUNTER, 2, EventPayload.Counter(Quantity.exact("1.25")!!, UnitName("old unit")), target),
            event("duration", Behavior.DURATION, 3, EventPayload.Duration(EpochMillis(10)), null),
            event("state", Behavior.STATE, 4, EventPayload.State(group, DatasetGeneration(2)), target),
        )
        val binding = BindingLifecycle(BindingId("binding"), RecordId("counter"), target, BindingStatus.ORPHANED,
            Revision(5), DisplaySnapshot("old binding", null, "old target", null),
            listOf(UndoInvalidation(ReceiptId("removed"), ResultReason.STALE_REVISION)))
        val receipt = UndoReceipt(ReceiptId("receipt"), EventId("event-4"), Revision(3), DatasetGeneration(7),
            ScopeContext(scope, DatasetGeneration(2)))
        return DomainState(records, mapOf(target to Target(target, "new target", null)),
            mapOf((RecordId("counter") to target) to RecordTarget(RecordId("counter"), target, false, Revision(4))),
            events, mapOf(group to StateGroup(group, "state group")), DatasetGeneration(7), Sequence(20),
            mapOf(scope to RecordId("deleted-current-record")), mapOf(scope to DatasetGeneration(2)),
            mapOf(binding.bindingId to binding), mapOf(receipt.receiptId to receipt))
    }
}
