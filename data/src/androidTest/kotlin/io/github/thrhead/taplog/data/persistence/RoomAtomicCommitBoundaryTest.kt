package io.github.thrhead.taplog.data.persistence

import android.database.sqlite.SQLiteConstraintException
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.thrhead.taplog.core.domain.*
import io.github.thrhead.taplog.core.engine.DomainState
import io.github.thrhead.taplog.core.engine.ScopeContext
import io.github.thrhead.taplog.core.engine.StateScope
import io.github.thrhead.taplog.core.engine.UndoReceipt
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/** US1 storage integration only; these tests never invoke the pending atomic commit adapter. */
@RunWith(AndroidJUnit4::class)
class RoomAtomicCommitBoundaryTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    // Catches omitted initialization, duplicate metadata on open, or counter reset on reopen.
    @Test
    fun singletonMetadataIsInitializedOnceAndPreservedByNormalIdentityHashReopen() = withName { name ->
        withDatabase(name) { database ->
            val dao = database.persistenceDao()
            assertEquals(listOf(DatasetMetadataEntity(1, 0, 1, 1)), dao.readMetadataRows())
            dao.upsertMetadata(listOf(DatasetMetadataEntity(1, 9, 80, 1)))
            dao.upsertMetadata(listOf(DatasetMetadataEntity(1, 10, 81, 1)))
            assertEquals(listOf(DatasetMetadataEntity(1, 10, 81, 1)), dao.readMetadataRows())
        }
        withDatabase(name) { database ->
            assertEquals(listOf(DatasetMetadataEntity(1, 10, 81, 1)), database.persistenceDao().readMetadataRows())
            val state = RoomLocalPersistence(database).read()
            assertEquals(DatasetGeneration(10), state.generation)
            assertEquals(Sequence(81), state.nextSequence)
        }
    }

    // The schema's key alone permits other keys; the complete aggregate mapper owns cardinality.
    @Test
    fun extraMetadataRowFailsAggregateReadWithoutRepairingStoredRows() = withName { name ->
        withDatabase(name) { database ->
            val dao = database.persistenceDao()
            dao.upsertMetadata(listOf(DatasetMetadataEntity(2, 0, 1, 1)))
            assertThrows(MappingFailure::class.java) { RoomLocalPersistence(database).read() }
            assertEquals(listOf(1, 2), dao.readMetadataRows().map { it.singletonKey })
        }
        withDatabase(name) { database ->
            assertThrows(MappingFailure::class.java) { RoomLocalPersistence(database).read() }
            assertEquals(listOf(1, 2), database.persistenceDao().readMetadataRows().map { it.singletonKey })
        }
    }

    @Test
    fun missingMetadataFailsAggregateReadAndOpenDoesNotReinitializeIt() = withName { name ->
        withDatabase(name) { database ->
            database.openHelper.writableDatabase.execSQL("DELETE FROM dataset_metadata")
            assertThrows(MappingFailure::class.java) { RoomLocalPersistence(database).read() }
            assertTrue(database.persistenceDao().readMetadataRows().isEmpty())
        }
        withDatabase(name) { database ->
            assertThrows(MappingFailure::class.java) { RoomLocalPersistence(database).read() }
            assertTrue(database.persistenceDao().readMetadataRows().isEmpty())
        }
    }

    // Catches missing FK declarations/enforcement for each actual parent relationship.
    @Test
    fun everyDeclaredForeignKeyRejectsItsMissingParent() = withDatabase { database ->
        seed(database, aggregate())
        val dao = database.persistenceDao()
        val record = dao.readRecord("state")!!
        val event = dao.readEvent("state-a-latest")!!
        val binding = dao.readBinding("binding-a")!!
        val cases: List<Pair<String, () -> Unit>> = listOf(
            "Record to State Group" to { dao.upsertRecords(listOf(record.copy(recordId = "invalid", stateGroupId = "missing"))) },
            "Relationship to Record" to { dao.upsertRecordTargets(listOf(RecordTargetEntity("missing", "a", true, 1))) },
            "Relationship to Target" to { dao.upsertRecordTargets(listOf(RecordTargetEntity("moment", "missing", true, 1))) },
            "Event to Record" to { dao.upsertEvents(listOf(event.copy(eventId = "invalid", sequence = 90, recordId = "missing"))) },
            "Event to Target" to { dao.upsertEvents(listOf(event.copy(eventId = "invalid", sequence = 90, targetId = "missing", targetScopeKey = "target:missing"))) },
            "Event to State Group" to { dao.upsertEvents(listOf(event.copy(eventId = "invalid", sequence = 90, stateGroupId = "missing"))) },
            "State scope to State Group" to { dao.upsertStateScopes(listOf(StateScopeEntity("missing", "no-target", 0, null))) },
            "Binding to Record" to { dao.upsertBindings(listOf(binding.copy(bindingId = "invalid", recordId = "missing"))) },
            "Binding to Target" to { dao.upsertBindings(listOf(binding.copy(bindingId = "invalid", targetId = "missing"))) },
            "Invalidation to Binding" to { dao.upsertBindingUndoInvalidations(listOf(BindingUndoInvalidationEntity("missing", "receipt", "STALE_REVISION"))) },
        )
        cases.forEach { (relationship, write) ->
            assertThrows(relationship, SQLiteConstraintException::class.java) { write() }
        }
        assertEquals(aggregate(), RoomLocalPersistence(database).read())
    }

    // Catches accidental cascade/delete behavior for referenced historical definitions.
    @Test
    fun referencedDefinitionsAndBindingCannotBeDeletedThroughForeignKeyCascades() = withDatabase { database ->
        seed(database, aggregate())
        val dao = database.persistenceDao()
        assertThrows(SQLiteConstraintException::class.java) { dao.deleteRecords(listOf(dao.readRecord("moment")!!)) }
        assertThrows(SQLiteConstraintException::class.java) { dao.deleteTargets(listOf(dao.readTarget("a")!!)) }
        assertThrows(SQLiteConstraintException::class.java) { dao.deleteStateGroups(listOf(dao.readStateGroup("group")!!)) }
        assertThrows(SQLiteConstraintException::class.java) { dao.deleteBindings(listOf(dao.readBinding("binding-a")!!)) }
        assertEquals(aggregate(), RoomLocalPersistence(database).read())
    }

    // Checks Room-declared indexes and the separately owned onOpen partial index.
    @Test
    fun configuredOpenInstallsChronologyScopeAndPartialUniqueIndexes() = withDatabase { database ->
        val sqlite = database.openHelper.writableDatabase
        sqlite.query("PRAGMA foreign_keys").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(1, cursor.getInt(0))
        }
        val indexes = mutableMapOf<String, Pair<Boolean, Boolean>>()
        sqlite.query("PRAGMA index_list(`events`)").use { cursor ->
            while (cursor.moveToNext()) indexes[cursor.getString(1)] = (cursor.getInt(2) == 1) to (cursor.getInt(4) == 1)
        }
        val expected = mapOf(
            "index_events_sequence" to (listOf("sequence") to true),
            "index_events_occurredAt_sequence" to (listOf("occurredAt", "sequence") to false),
            "index_events_recordId_targetId_occurredAt_sequence" to (listOf("recordId", "targetId", "occurredAt", "sequence") to false),
            "index_events_recordId_targetScopeKey_durationStatus" to (listOf("recordId", "targetScopeKey", "durationStatus") to false),
            "index_events_stateGroupId_targetScopeKey_stateGeneration_occurredAt_sequence" to
                (listOf("stateGroupId", "targetScopeKey", "stateGeneration", "occurredAt", "sequence") to false),
            "index_events_targetId" to (listOf("targetId") to false),
            "index_events_durationStatus" to (listOf("durationStatus") to false),
            "index_events_open_duration_scope" to (listOf("recordId", "targetScopeKey") to true),
        )
        expected.forEach { (name, definition) ->
            assertEquals(name, definition.second to (name == "index_events_open_duration_scope"), indexes[name])
            val columns = mutableListOf<String>()
            sqlite.query("PRAGMA index_info(`$name`)").use { cursor ->
                while (cursor.moveToNext()) columns += cursor.getString(2)
            }
            assertEquals(name, definition.first, columns)
        }
        // Normal Room identity-hash open is supported; explicit generated validation of this
        // extra partial index remains the T010/T037 caveat. Do not call that validator here.
        sqlite.query("SELECT sql FROM sqlite_master WHERE name='index_events_open_duration_scope'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(
                "CREATE UNIQUE INDEX `index_events_open_duration_scope` ON `events` " +
                    "(`recordId`, `targetScopeKey`) WHERE `behavior` = 'DURATION' AND `durationStatus` = 'OPEN'",
                cursor.getString(0),
            )
        }
    }

    @Test
    fun sequenceAndOpenDurationUniquenessRejectConflictsWithoutRestrictingOtherScopesOrHistory() = withName { name ->
        withDatabase(name) { database ->
            seed(database, aggregate())
            assertIndexConflicts(database)
        }
        withDatabase(name) { database ->
            assertIndexConflicts(database)
            assertEquals(aggregate(), RoomLocalPersistence(database).read())
            assertEquals(listOf("duration-none", "duration-completed"), database.persistenceDao()
                .readScopeEvents("duration", "no-target").map { it.eventId })
            assertEquals("duration-a", database.persistenceDao().readOpenDuration("duration", "target:a")!!.eventId)
            assertEquals("duration-other", database.persistenceDao().readOpenDuration("other-duration", "no-target")!!.eventId)
        }
    }

    // Catches a missing DAO family, payload loss, snapshot reconstruction from live definitions,
    // or entity instances escaping instead of reconstructed core values.
    @Test
    fun completeAggregateRoundTripsAllFamiliesAndRemainsDetachedFromSubsequentEntityWrites() = withName { name ->
        val expected = aggregate()
        withDatabase(name) { database ->
            seed(database, expected)
            val captured = RoomLocalPersistence(database).read()
            assertEquals(expected, captured)
            val dao = database.persistenceDao()
            dao.upsertRecords(listOf(dao.readRecord("counter")!!.copy(name = "edited live counter", unit = "edited unit")))
            dao.upsertTargets(listOf(dao.readTarget("a")!!.copy(name = "edited live target")))
            dao.upsertEvents(listOf(dao.readEvent("counter")!!.copy(counterQuantity = "9.5", revision = 8)))
            assertEquals(expected, captured)
            assertEquals("old counter", captured.events.first().snapshot.recordName)
            assertEquals(EventPayload.Counter(Quantity.exact("1.25")!!, UnitName("old unit")), captured.events.first().payload)
            val reread = RoomLocalPersistence(database).read()
            assertEquals("edited live counter", reread.records.getValue(RecordId("counter")).name)
            assertEquals("edited live target", reread.targets.getValue(TargetId("a")).name)
            assertEquals(EventPayload.Counter(Quantity.exact("9.5")!!, UnitName("old unit")), reread.events.first().payload)
            assertEquals("old counter", reread.events.first().snapshot.recordName)
            assertEquals("old target a", reread.events.first().snapshot.targetName)
            // Restore the test-owned seed so reopen independently checks the original aggregate.
            seed(database, expected)
        }
        withDatabase(name) { database ->
            assertEquals(expected, RoomLocalPersistence(database).read())
            assertEquals("retained-deleted-state", RoomLocalPersistence(database).read().currentStates
                .getValue(StateScope(StateGroupId("group"), TargetId("a"))).value)
        }
    }

    @Test
    fun canonicalEventRowsContainOnlyTheirTypedPayloadColumns() = withDatabase { database ->
        seed(database, aggregate())
        val dao = database.persistenceDao()
        assertEquals(20, dao.readEvents().size)
        val cases = mapOf(
            "moment-none" to listOf(null, null, null, null, null, null, null, null),
            "counter" to listOf("1.25", "old unit", null, null, null, null, null, null),
            "duration-none" to listOf(null, null, 200L, null, "OPEN", null, null, null),
            "duration-completed" to listOf(null, null, 205L, 210L, "COMPLETED", null, null, null),
            "duration-incomplete" to listOf(null, null, 205L, 210L, "INCOMPLETE", "interrupted", null, null),
            "state-a-latest" to listOf(null, null, null, null, null, null, "group", 2L),
        )
        cases.forEach { (id, expected) ->
            val row = dao.readEvent(id)!!
            assertEquals(id, expected, listOf(row.counterQuantity, row.counterUnit, row.durationStartAt,
                row.durationEndAt, row.durationStatus, row.durationIncompleteReason, row.stateGroupId, row.stateGeneration))
            assertEquals(1, dao.readEvents().count { it.eventId == id })
        }
        database.openHelper.writableDatabase.query("SELECT name FROM sqlite_master WHERE type='table' AND name LIKE '%payload%'")
            .use { cursor -> assertFalse("Events have no separate payload table", cursor.moveToFirst()) }
        assertEquals(aggregate(), RoomLocalPersistence(database).read())
    }

    // Catches missing Record/scope predicates, null-scope substitution, or lost sequence tie-breaks.
    @Test
    fun chronologicalQueriesUseExactRecordAndTargetScopeAndPersistSequenceTieOrder() = withName { name ->
        withDatabase(name) { database -> seed(database, aggregate()); assertChronology(database) }
        withDatabase(name) { database -> assertChronology(database) }
    }

    // Catches completed/incomplete rows selected as current or scope predicates omitted.
    @Test
    fun currentDurationQueriesExcludeTerminalHistoryAndNeverCrossScopes() = withDatabase { database ->
        seed(database, aggregate())
        val dao = database.persistenceDao()
        assertEquals("duration-none", dao.readOpenDuration("duration", "no-target")!!.eventId)
        assertEquals("duration-a", dao.readOpenDuration("duration", "target:a")!!.eventId)
        assertNull(dao.readOpenDuration("duration", "target:no-target"))
        assertEquals("duration-other", dao.readOpenDuration("other-duration", "no-target")!!.eventId)
        assertNull(dao.readOpenDuration("moment", "no-target"))
        assertNull(dao.readOpenDuration("missing", "no-target"))
        assertNull(dao.readOpenDuration("duration", "target:missing"))
    }

    // Catches the generation join being removed, old later history becoming current, or bad tie order.
    @Test
    fun currentStateQueriesUseExactGroupScopeAndActiveGeneration() = withName { name ->
        withDatabase(name) { database -> seed(database, aggregate()); assertCurrentState(database) }
        withDatabase(name) { database -> assertCurrentState(database) }
    }

    @Test
    fun bindingQueriesUseExactNullableScopeAndOrderIds() = withDatabase { database ->
        seed(database, aggregate())
        val dao = database.persistenceDao()
        assertEquals(listOf("binding-none"), dao.readScopeBindings("moment", null).map { it.bindingId })
        assertEquals(listOf("binding-a", "binding-z"), dao.readScopeBindings("moment", "a").map { it.bindingId })
        assertEquals(listOf("binding-b"), dao.readScopeBindings("moment", "no-target").map { it.bindingId })
        assertEquals(listOf("binding-other"), dao.readScopeBindings("other-moment", null).map { it.bindingId })
        assertTrue(dao.readScopeBindings("moment", "missing").isEmpty())
        assertTrue(dao.readScopeBindings("missing", null).isEmpty())
        assertEquals(listOf("removed-a", "removed-z"), dao.readBindingUndoInvalidations("binding-a").map { it.receiptId })
        assertTrue(dao.readBindingUndoInvalidations("binding-none").isEmpty())
    }

    private fun assertIndexConflicts(database: TapLogDatabase) {
        val dao = database.persistenceDao()
        val moment = dao.readEvent("moment-none")!!
        assertThrows(SQLiteConstraintException::class.java) { dao.upsertEvents(listOf(moment.copy(eventId = "duplicate-sequence"))) }
        listOf("duration-none", "duration-a").forEach { id ->
            assertThrows(SQLiteConstraintException::class.java) {
                dao.upsertEvents(listOf(dao.readEvent(id)!!.copy(eventId = "duplicate-open", sequence = 90)))
            }
        }
        assertEquals(20, dao.readEvents().size)
    }

    private fun assertChronology(database: TapLogDatabase) {
        val dao = database.persistenceDao()
        assertEquals(listOf("moment-a-early", "moment-a-later", "moment-b", "moment-none"), dao.readRecordEvents("moment").map { it.eventId })
        assertEquals(listOf("moment-none"), dao.readScopeEvents("moment", "no-target").map { it.eventId })
        assertEquals(listOf("moment-a-early", "moment-a-later"), dao.readScopeEvents("moment", "target:a").map { it.eventId })
        // A literal Target ID equal to the sentinel remains distinct from nullable no-Target.
        assertEquals(listOf("moment-b"), dao.readScopeEvents("moment", "target:no-target").map { it.eventId })
        assertEquals(listOf("other-moment"), dao.readRecordEvents("other-moment").map { it.eventId })
        assertTrue(dao.readScopeEvents("moment", "target:missing").isEmpty())
        assertTrue(dao.readRecordEvents("missing").isEmpty())
        assertEquals(listOf(1L, 3L, 4L, 5L, 6L, 2L, 7L, 8L, 9L, 10L, 11L, 12L, 13L, 14L,
            15L, 16L, 17L, 18L, 19L, 20L), dao.readEvents().map { it.sequence })
    }

    private fun assertCurrentState(database: TapLogDatabase) {
        val dao = database.persistenceDao()
        assertEquals("state-none-active", dao.readCurrentState("group", "no-target")!!.eventId)
        assertEquals("state-a-latest", dao.readCurrentState("group", "target:a")!!.eventId)
        assertEquals("state-b", dao.readCurrentState("group", "target:no-target")!!.eventId)
        assertEquals("state-other", dao.readCurrentState("other-group", "target:a")!!.eventId)
        assertNull(dao.readCurrentState("group", "target:missing"))
        assertNull(dao.readCurrentState("missing", "no-target"))
        assertNull(dao.readCurrentState("other-group", "no-target"))
        assertEquals("state-a-stale-late", dao.readScopeEvents("state", "target:a").last().eventId)
    }

    private fun seed(database: TapLogDatabase, state: DomainState) {
        val rows = PersistenceMapper.toRows(state)
        database.runInTransaction {
            val dao = database.persistenceDao()
            dao.upsertStateGroups(rows.stateGroups)
            dao.upsertRecords(rows.records)
            dao.upsertTargets(rows.targets)
            dao.upsertRecordTargets(rows.recordTargets)
            dao.upsertEvents(rows.events.reversed())
            dao.upsertStateScopes(rows.stateScopes)
            dao.upsertBindings(rows.bindings.reversed())
            dao.upsertBindingUndoInvalidations(rows.bindingUndoInvalidations.reversed())
            dao.upsertUndoReceipts(rows.undoReceipts)
            dao.upsertMetadata(rows.metadata)
        }
    }

    private fun aggregate(): DomainState {
        val a = TargetId("a")
        val b = TargetId("no-target")
        val group = StateGroupId("group")
        val otherGroup = StateGroupId("other-group")
        val records = listOf(
            Record(RecordId("moment"), "live moment", "live icon", Behavior.MOMENT, revision = Revision(5), hasEvents = true),
            Record(RecordId("other-moment"), "other moment", null, Behavior.MOMENT, hasEvents = true),
            Record(RecordId("counter"), "live counter", "counter icon", Behavior.COUNTER, Lifecycle.ARCHIVED,
                UnitName("live unit"), Quantity.exact("2.5"), revision = Revision(7), hasEvents = true),
            Record(RecordId("duration"), "live duration", null, Behavior.DURATION, hasEvents = true),
            Record(RecordId("other-duration"), "other duration", null, Behavior.DURATION, hasEvents = true),
            Record(RecordId("state"), "live state", null, Behavior.STATE, stateGroupId = group, hasEvents = true),
            Record(RecordId("other-state"), "other state", null, Behavior.STATE, stateGroupId = otherGroup, hasEvents = true),
        ).associateBy { it.id }
        val events = listOf(
            event("counter", "counter", 1, -1000, EventPayload.Counter(Quantity.exact("1.25")!!, UnitName("old unit")), a),
            event("moment-a-early", "moment", 3, 100, EventPayload.Moment, a),
            event("moment-a-later", "moment", 4, 100, EventPayload.Moment, a),
            event("moment-b", "moment", 5, 100, EventPayload.Moment, b),
            event("other-moment", "other-moment", 6, 100, EventPayload.Moment),
            event("moment-none", "moment", 2, 101, EventPayload.Moment),
            event("duration-none", "duration", 7, 200, EventPayload.Duration(EpochMillis(200))),
            event("duration-a", "duration", 8, 200, EventPayload.Duration(EpochMillis(200)), a),
            event("duration-b", "duration", 9, 200, EventPayload.Duration(EpochMillis(190), EpochMillis(200), DurationStatus.COMPLETED), b),
            event("duration-other", "other-duration", 10, 200, EventPayload.Duration(EpochMillis(200))),
            event("duration-completed", "duration", 11, 210, EventPayload.Duration(EpochMillis(205), EpochMillis(210), DurationStatus.COMPLETED)),
            event("duration-incomplete", "duration", 12, 210, EventPayload.Duration(EpochMillis(205), EpochMillis(210), DurationStatus.INCOMPLETE, "interrupted"), a),
            event("state-none-old", "state", 13, 300, EventPayload.State(group, DatasetGeneration(1))),
            event("state-a-old", "state", 14, 300, EventPayload.State(group, DatasetGeneration(1)), a),
            event("state-a-early", "state", 15, 310, EventPayload.State(group, DatasetGeneration(2)), a),
            event("state-a-latest", "state", 16, 310, EventPayload.State(group, DatasetGeneration(2)), a),
            event("state-b", "state", 17, 310, EventPayload.State(group, DatasetGeneration(3)), b),
            event("state-other", "other-state", 18, 320, EventPayload.State(otherGroup, DatasetGeneration(2)), a),
            event("state-none-active", "state", 19, 330, EventPayload.State(group, DatasetGeneration(2))),
            event("state-a-stale-late", "state", 20, 1000, EventPayload.State(group, DatasetGeneration(1)), a),
        )
        val aScope = StateScope(group, a)
        val generations = mapOf(StateScope(group, null) to DatasetGeneration(2), aScope to DatasetGeneration(2),
            StateScope(group, b) to DatasetGeneration(3), StateScope(otherGroup, a) to DatasetGeneration(2))
        fun binding(id: String, record: String, target: TargetId?) = BindingLifecycle(
            BindingId(id), RecordId(record), target, BindingStatus.ORPHANED, Revision(6),
            DisplaySnapshot("old binding", "binding icon", target?.let { "old target ${it.value}" }, null),
            if (id == "binding-a") listOf(UndoInvalidation(ReceiptId("removed-a"), ResultReason.STALE_REVISION),
                UndoInvalidation(ReceiptId("removed-z"), ResultReason.STALE_REVISION)) else emptyList(),
        )
        val bindings = listOf(binding("binding-z", "moment", a), binding("binding-b", "moment", b),
            binding("binding-none", "moment", null), binding("binding-a", "moment", a),
            binding("binding-other", "other-moment", null)).associateBy { it.bindingId }
        val receipts = listOf(
            UndoReceipt(ReceiptId("receipt-state"), EventId("state-a-latest"), Revision(3), DatasetGeneration(7), ScopeContext(aScope, DatasetGeneration(2))),
            UndoReceipt(ReceiptId("receipt-old"), EventId("retained-deleted-event"), Revision(4), DatasetGeneration(6)),
        ).associateBy { it.receiptId }
        return DomainState(records, mapOf(a to Target(a, "live target a", "target icon", revision = Revision(8)),
            b to Target(b, "live target b", null, Lifecycle.ARCHIVED, Revision(9))),
            mapOf((RecordId("counter") to a) to RecordTarget(RecordId("counter"), a, false, Revision(4)),
                (RecordId("moment") to b) to RecordTarget(RecordId("moment"), b, true, Revision(2))),
            events, mapOf(group to StateGroup(group, "live group", Revision(2)), otherGroup to StateGroup(otherGroup, "other group", Revision(3))),
            DatasetGeneration(7), Sequence(80), mapOf(aScope to RecordId("retained-deleted-state")), generations, bindings, receipts)
    }

    private fun event(id: String, record: String, sequence: Long, occurredAt: Long, payload: EventPayload, target: TargetId? = null): Event {
        val behavior = when (payload) {
            EventPayload.Moment -> Behavior.MOMENT
            is EventPayload.Counter -> Behavior.COUNTER
            is EventPayload.Duration -> Behavior.DURATION
            is EventPayload.State -> Behavior.STATE
        }
        return Event(EventId(id), RecordId(record), target, behavior, EpochMillis(occurredAt), EpochMillis(-2000),
            EpochMillis(2000), Sequence(sequence), Source.NFC, Revision(3),
            EventSnapshot("old $record", "old record icon", target?.let { "old target ${it.value}" },
                target?.let { "old target icon" }, behavior, if (payload is EventPayload.Counter) UnitName("old unit") else null), payload)
    }

    private fun withDatabase(block: (TapLogDatabase) -> Unit) = withName { name -> withDatabase(name, block) }

    private fun withDatabase(name: String, block: (TapLogDatabase) -> Unit) {
        val database = TapLogDatabase.builder(context, name).allowMainThreadQueries().build()
        try { block(database) } finally { database.close() }
    }

    private fun withName(block: (String) -> Unit) {
        val name = "taplog-t013-${UUID.randomUUID()}.db"
        try { block(name) } finally { context.deleteDatabase(name) }
    }
}
