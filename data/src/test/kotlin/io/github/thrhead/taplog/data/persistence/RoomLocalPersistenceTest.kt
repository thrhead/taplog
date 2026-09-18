package io.github.thrhead.taplog.data.persistence

import androidx.room.InvalidationTracker
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import io.github.thrhead.taplog.core.domain.*
import io.github.thrhead.taplog.core.domain.Record
import io.github.thrhead.taplog.core.engine.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Proxy
import java.util.concurrent.Callable

class RoomLocalPersistenceTest {
    // Catches omitted Event upserts, lossy fields, live-definition snapshots, and other-family writes.
    @Test
    fun writesAllEventFieldsAndOnlyTheirTypedPayloadColumns() {
        val expected = aggregate().let { state -> state.copy(events = state.events.map { event -> event.copy(
            occurredAt = EpochMillis(100), createdAt = EpochMillis(-100), updatedAt = EpochMillis(200),
            source = Source.APP, revision = Revision(8),
        ) }) }
        val before = expected.copy(events = emptyList())
        val rows = PersistenceMapper.toRows(before)
        val database = SnapshotDatabase(rows)
        val counter = expected.events[1]
        val input = expected.copy(events = expected.events.map { if (it.id == counter.id) it.copy(
            payload = EventPayload.Counter(Quantity.exact("1.2500")!!, UnitName("historical unit")),
        ) else it }.reversed())

        val persistence = RoomLocalPersistence(database)
        persistence.writeEvents(input, database.persistenceDao())

        assertEquals(expected, persistence.read())
        assertEquals(rows, database.committed.copy(events = emptyList()))
        val payloads = mapOf(
            "event-1" to listOf(null, null, null, null, null, null, null, null),
            "event-2" to listOf("1.25", "historical unit", null, null, null, null, null, null),
            "event-3" to listOf(null, null, 10L, null, "OPEN", null, null, null),
            "event-4" to listOf(null, null, null, null, null, null, "group", 2L),
        )
        database.committed.events.forEach { row ->
            assertEquals(payloads.getValue(row.eventId), listOf(row.counterQuantity, row.counterUnit,
                row.durationStartAt, row.durationEndAt, row.durationStatus, row.durationIncompleteReason,
                row.stateGroupId, row.stateGeneration))
        }
        assertEquals(listOf(1L, 2L, 3L, 4L), persistence.read().events.map { it.sequence.value })
        assertEquals("historical counter", persistence.read().events[1].snapshot.recordName)
        assertEquals(UnitName("historical unit"), persistence.read().events[1].snapshot.unit)
    }

    // Catches duplicate inserts on update and deletion of Event rows omitted from this phase.
    @Test
    fun eventUpdatesRetainIdentitySnapshotsAndOmittedHistory() {
        val before = aggregate()
        val updated = before.events[0].copy(occurredAt = EpochMillis(99), updatedAt = EpochMillis(101), revision = Revision(9))
        val database = SnapshotDatabase(PersistenceMapper.toRows(before))
        val persistence = RoomLocalPersistence(database)

        persistence.writeEvents(before.copy(events = listOf(updated)), database.persistenceDao())
        persistence.writeEvents(DomainState(), database.persistenceDao())

        assertEquals(before.copy(events = before.events.drop(1) + updated), persistence.read())
        assertEquals(4, database.committed.events.size)
        assertEquals(before.events[0].snapshot, persistence.read().events.last().snapshot)
    }

    // Catches writes before FK, generation, uniqueness, or aggregate validation.
    @Test
    fun invalidEventsFailBeforeAnyEventIsWritten() {
        val before = aggregate()
        val moment = before.events[0]
        val state = before.events[3]
        val invalidEvents = listOf(
            moment.copy(recordId = RecordId("missing")),
            moment.copy(targetId = TargetId("missing")),
            state.copy(payload = EventPayload.State(StateGroupId("missing"), DatasetGeneration(2))),
            state.copy(payload = EventPayload.State(StateGroupId("group"), DatasetGeneration(3))),
            moment.copy(sequence = Sequence(0)),
            moment.copy(sequence = before.nextSequence),
            moment.copy(revision = Revision(-1)),
        )
        val invalid = invalidEvents.map { event -> before.copy(events = before.events.map {
            if (it.id == event.id) event else it
        }) } + listOf(
            before.copy(events = before.events + moment.copy(id = EventId("duplicate-sequence"))),
            before.copy(events = before.events + moment.copy(sequence = Sequence(5))),
            before.copy(generation = DatasetGeneration(-1)),
        )
        invalid.forEach { input ->
            val rows = PersistenceMapper.toRows(before)
            val database = SnapshotDatabase(rows)
            assertThrows(MappingFailure::class.java) {
                RoomLocalPersistence(database).writeEvents(input, database.persistenceDao())
            }
            assertEquals(rows, database.committed)
        }
    }

    // Catches loss of nullable Counter units or supplied terminal Duration fields.
    @Test
    fun retainsUnitlessCounterAndSuppliedTerminalDurationPayloads() {
        val before = aggregate()
        val counter = before.events[1].copy(payload = EventPayload.Counter(Quantity.exact("0.125")!!, null),
            snapshot = before.events[1].snapshot.copy(unit = null))
        val completed = before.events[2].copy(payload = EventPayload.Duration(EpochMillis(10), EpochMillis(30), DurationStatus.COMPLETED))
        val incomplete = completed.copy(id = EventId("incomplete"), sequence = Sequence(5),
            payload = EventPayload.Duration(EpochMillis(10), null, DurationStatus.INCOMPLETE, "interrupted"))
        val expected = before.copy(events = listOf(before.events[0], counter, completed, before.events[3], incomplete))
        val database = SnapshotDatabase(PersistenceMapper.toRows(before.copy(events = emptyList())))
        val persistence = RoomLocalPersistence(database)

        persistence.writeEvents(expected, database.persistenceDao())

        assertEquals(expected, persistence.read())
        val rows = database.committed.events.associateBy { it.eventId }
        assertEquals("0.125", rows.getValue("event-2").counterQuantity)
        assertEquals(null, rows.getValue("event-2").counterUnit)
        assertEquals(listOf(10L, 30L, "COMPLETED", null), rows.getValue("event-3").let {
            listOf(it.durationStartAt, it.durationEndAt, it.durationStatus, it.durationIncompleteReason)
        })
        assertEquals(listOf(10L, null, "INCOMPLETE", "interrupted"), rows.getValue("incomplete").let {
            listOf(it.durationStartAt, it.durationEndAt, it.durationStatus, it.durationIncompleteReason)
        })
    }

    // Catches omitted definition upserts, lossy scalar mapping, and writes to history/other families.
    @Test
    fun writesRecordAndTargetFieldsWithoutTouchingAnyOtherFamily() {
        val before = aggregate()
        val expected = before.copy(
            records = before.records.mapValues { (_, record) -> record.copy(
                name = "updated ${record.id.value}", icon = "updated icon", lifecycle = Lifecycle.ARCHIVED,
                revision = Revision(42),
            ) } + (RecordId("new") to Record(RecordId("new"), "new counter", null, Behavior.COUNTER,
                unit = UnitName("cups"), defaultQuantity = Quantity.exact("2.5"), revision = Revision(9))),
            targets = before.targets.mapValues { (_, target) -> target.copy(
                name = "updated target", icon = "target icon", lifecycle = Lifecycle.ARCHIVED, revision = Revision(43),
            ) } + (TargetId("new-target") to Target(TargetId("new-target"), "new target", null, revision = Revision(10))),
        )
        val database = SnapshotDatabase(PersistenceMapper.toRows(before))
        val persistence = RoomLocalPersistence(database)

        val input = expected.copy(records = expected.records + (RecordId("new") to
            expected.records.getValue(RecordId("new")).copy(defaultQuantity = Quantity.exact("2.500"))))
        persistence.writeRecordsAndTargets(input, database.persistenceDao())

        assertEquals(expected, persistence.read())
        assertEquals("2.5", database.committed.records.single { it.recordId == "new" }.defaultQuantity)
        assertEquals("historical counter", persistence.read().events[1].snapshot.recordName)
        assertEquals(PersistenceMapper.toRows(before).events, database.committed.events)
    }

    // Catches normalization of nullable fields or recomputation of hasEvents from current history.
    @Test
    fun retainsNullableFieldsAndHasEventsEvenWhenHistoryHasBeenRemoved() {
        val records = listOf(
            Record(RecordId("moment"), "moment", null, Behavior.MOMENT, defaultQuantity = null, hasEvents = true),
            Record(RecordId("counter"), "counter", null, Behavior.COUNTER, defaultQuantity = Quantity.exact("0.125")),
        ).associateBy { it.id }
        val expected = DomainState(records = records, targets = mapOf(TargetId("target") to Target(TargetId("target"), "target", null)))
        val database = SnapshotDatabase(PersistenceMapper.toRows(DomainState()))
        val persistence = RoomLocalPersistence(database)

        persistence.writeRecordsAndTargets(expected, database.persistenceDao())

        assertEquals(expected, persistence.read())
        assertEquals(null, database.committed.records.single { it.recordId == "moment" }.defaultQuantity)
        assertEquals(null, database.committed.records.single { it.recordId == "counter" }.unit)
    }

    // Catches applying an aggregate deletion during this upsert-only phase.
    @Test
    fun missingDefinitionsInInputDoNotDeleteExistingRows() {
        val before = aggregate()
        val rows = PersistenceMapper.toRows(before)
        val database = SnapshotDatabase(rows)

        RoomLocalPersistence(database).writeRecordsAndTargets(DomainState(), database.persistenceDao())

        assertEquals(rows, database.committed)
    }

    // Catches a DAO write before complete mapper validation, including bad unrelated rows.
    @Test
    fun invalidAggregateFailsMappingBeforeAnyDefinitionIsWritten() {
        val before = aggregate()
        val record = before.records.getValue(RecordId("counter"))
        val invalid = listOf(
            before.copy(records = before.records + (record.id to record.copy(defaultQuantity = null))),
            before.copy(records = before.records + (record.id to record.copy(unit = UnitName(" ")))),
            before.copy(records = before.records + (record.id to record.copy(revision = Revision(-1)))),
            before.copy(records = before.records + (RecordId("wrong-key") to record)),
            before.copy(targets = before.targets.mapValues { (_, target) -> target.copy(revision = Revision(-1)) }),
            before.copy(targets = mapOf(TargetId("wrong-key") to before.targets.values.single())),
            before.copy(nextSequence = Sequence(0)),
        )
        invalid.forEach { state ->
            val database = SnapshotDatabase(PersistenceMapper.toRows(before))
            assertThrows(MappingFailure::class.java) {
                RoomLocalPersistence(database).writeRecordsAndTargets(state, database.persistenceDao())
            }
            assertEquals(PersistenceMapper.toRows(before), database.committed)
        }
    }

    @Test
    fun reconstructsEveryFamilyWithoutReplacingHistoricalSnapshotsOrScopes() {
        val expected = aggregate()
        val database = SnapshotDatabase(PersistenceMapper.toRows(expected))

        val actual = RoomLocalPersistence(database).read()

        assertEquals(expected, actual)
        assertEquals("historical counter", actual.events[1].snapshot.recordName)
        assertEquals(listOf(null, TargetId("target"), null, TargetId("target")), actual.events.map { it.targetId })
    }

    @Test
    fun allFamiliesComeFromOneSnapshotEvenIfANewerAggregateCommitsBetweenQueries() {
        val previous = aggregate()
        val latest = previous.copy(generation = DatasetGeneration(8), nextSequence = Sequence(30),
            records = previous.records.mapValues { (_, record) -> record.copy(name = "new committed name") })
        val database = SnapshotDatabase(PersistenceMapper.toRows(previous))
        database.afterQuery = { database.committed = PersistenceMapper.toRows(latest) }
        val persistence = RoomLocalPersistence(database)

        assertEquals(previous, persistence.read())
        assertEquals(latest, persistence.read())
        assertEquals(2, database.transactions)
    }

    @Test
    fun freshEmptyDatabaseStillRequiresAndReadsPersistedMetadata() {
        assertEquals(DomainState(), RoomLocalPersistence(
            SnapshotDatabase(PersistenceRows(metadata = listOf(DatasetMetadataEntity(1, 0, 1, 1)))),
        ).read())
        assertThrows(MappingFailure::class.java) { RoomLocalPersistence(SnapshotDatabase(PersistenceRows())).read() }
    }

    @Test
    fun queryFailureIsRetryableAndDoesNotModifyCommittedRows() {
        val rows = PersistenceMapper.toRows(aggregate())
        val database = SnapshotDatabase(rows)
        val cause = IllegalStateException("query unavailable")
        database.queryFailure = cause
        val persistence = RoomLocalPersistence(database)

        val failure = assertThrows(DatabaseReadFailure::class.java) { persistence.read() }
        assertSame(cause, failure.cause)
        assertTrue(failure.retryable)
        assertEquals(rows, database.committed)
        database.queryFailure = null
        assertEquals(aggregate(), persistence.read())
    }

    @Test
    fun transactionFailureIsTypedAsAReadFailure() {
        val database = SnapshotDatabase(PersistenceMapper.toRows(aggregate()))
        val cause = IllegalStateException("transaction unavailable")
        database.transactionFailure = cause

        val failure = assertThrows(DatabaseReadFailure::class.java) { RoomLocalPersistence(database).read() }
        assertSame(cause, failure.cause)
    }

    @Test
    fun databaseOpenFailureIsRetryableAndDoesNotAttemptAggregateQueries() {
        val database = SnapshotDatabase(PersistenceMapper.toRows(aggregate()))
        val cause = IllegalStateException("open unavailable")
        database.openFailure = cause

        val failure = assertThrows(DatabaseOpenFailure::class.java) { RoomLocalPersistence(database).read() }
        assertSame(cause, failure.cause)
        assertTrue(failure.retryable)
        assertEquals(0, database.transactions)
        database.openFailure = null
        assertEquals(aggregate(), RoomLocalPersistence(database).read())
    }

    @Test
    fun typedOpenFailuresPreserveTheirCategoryAndCause() {
        val database = SnapshotDatabase(PersistenceMapper.toRows(aggregate()))
        val failure = DatabaseOpenFailure("foreign keys unavailable")
        database.openFailure = failure

        assertSame(failure, assertThrows(DatabaseOpenFailure::class.java) { RoomLocalPersistence(database).read() })
    }

    @Test
    fun invalidRowsRemainMappingFailuresWithoutBeingRepairedOrDropped() {
        val rows = PersistenceMapper.toRows(aggregate()).let { rows ->
            rows.copy(events = rows.events.map { if (it.behavior == "COUNTER") it.copy(counterQuantity = "bad") else it })
        }
        val database = SnapshotDatabase(rows)

        val failure = assertThrows(MappingFailure::class.java) { RoomLocalPersistence(database).read() }
        assertEquals(false, failure.retryable)
        assertEquals(rows, database.committed)
    }

    /** Test-owned Room boundary double: queries see the pinned committed aggregate only in a transaction. */
    private class SnapshotDatabase(var committed: PersistenceRows) : TapLogDatabase() {
        var transactions = 0
        var afterQuery: (() -> Unit)? = null
        var queryFailure: RuntimeException? = null
        var transactionFailure: RuntimeException? = null
        var openFailure: RuntimeException? = null
        private var snapshot: PersistenceRows? = null

        override fun <V> runInTransaction(body: Callable<V>): V {
            transactionFailure?.let { throw it }
            check(snapshot == null) { "Unexpected nested aggregate transaction" }
            transactions++
            snapshot = committed
            return try { body.call() } finally { snapshot = null }
        }

        override val openHelper: SupportSQLiteOpenHelper
            get() = proxy(SupportSQLiteOpenHelper::class.java) { method ->
                check(method == "getWritableDatabase")
                openFailure?.let { throw it }
                proxy(SupportSQLiteDatabase::class.java) { error("No direct SQLite access expected") }
            }

        override fun persistenceDao(): PersistenceDao = proxyWithArguments(PersistenceDao::class.java) { method, arguments ->
            when (method) {
                "upsertRecords" -> {
                    @Suppress("UNCHECKED_CAST")
                    val rows = arguments!![0] as List<RecordEntity>
                    val records = committed.records.associateBy { it.recordId } + rows.associateBy { it.recordId }
                    committed = committed.copy(records = records.values.toList())
                    return@proxyWithArguments null
                }
                "upsertTargets" -> {
                    @Suppress("UNCHECKED_CAST")
                    val rows = arguments!![0] as List<TargetEntity>
                    val targets = committed.targets.associateBy { it.targetId } + rows.associateBy { it.targetId }
                    committed = committed.copy(targets = targets.values.toList())
                    return@proxyWithArguments null
                }
                "upsertEvents" -> {
                    @Suppress("UNCHECKED_CAST")
                    val rows = arguments!![0] as List<EventEntity>
                    val events = committed.events.associateBy { it.eventId } + rows.associateBy { it.eventId }
                    committed = committed.copy(events = events.values.sortedWith(compareBy({ it.occurredAt }, { it.sequence })))
                    return@proxyWithArguments null
                }
            }
            queryFailure?.let { throw it }
            val rows = snapshot ?: error("Query outside complete aggregate snapshot")
            val result = when (method) {
                "readRecords" -> rows.records
                "readTargets" -> rows.targets
                "readRecordTargets" -> rows.recordTargets
                "readStateGroups" -> rows.stateGroups
                "readEvents" -> rows.events
                "readStateScopes" -> rows.stateScopes
                "readBindings" -> rows.bindings
                "readBindingUndoInvalidations" -> rows.bindingUndoInvalidations
                "readUndoReceipts" -> rows.undoReceipts
                "readMetadataRows" -> rows.metadata
                else -> error("Unexpected DAO operation: $method")
            }
            afterQuery?.invoke()
            result
        }

        override fun createInvalidationTracker(): InvalidationTracker = error("Test owns the transaction boundary")
        override fun clearAllTables(): Unit = error("Read must not reset persisted rows")

        private fun <T : Any> proxy(type: Class<T>, block: (String) -> Any?): T = requireNotNull(type.cast(
            Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { _, method, _ -> block(method.name) },
        ))

        private fun <T : Any> proxyWithArguments(type: Class<T>, block: (String, Array<out Any?>?) -> Any?): T = requireNotNull(type.cast(
            Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { _, method, arguments -> block(method.name, arguments) },
        ))
    }

    private fun aggregate(): DomainState {
        val target = TargetId("target")
        val group = StateGroupId("group")
        val scope = StateScope(group, target)
        val records = listOf(
            Record(RecordId("moment"), "current moment", null, Behavior.MOMENT, hasEvents = true),
            Record(RecordId("counter"), "current counter", null, Behavior.COUNTER, unit = UnitName("current unit"),
                defaultQuantity = Quantity.exact("2"), hasEvents = true),
            Record(RecordId("duration"), "current duration", null, Behavior.DURATION, hasEvents = true),
            Record(RecordId("state"), "current state", null, Behavior.STATE, stateGroupId = group, hasEvents = true),
        ).associateBy { it.id }
        fun event(record: String, behavior: Behavior, sequence: Long, payload: EventPayload, targetId: TargetId?): Event =
            Event(EventId("event-$sequence"), RecordId(record), targetId, behavior, EpochMillis(10), EpochMillis(-2),
                EpochMillis(20), Sequence(sequence), Source.NFC, Revision(3),
                EventSnapshot("historical $record", "old", if (targetId == null) null else "historical target", null,
                    behavior, if (behavior == Behavior.COUNTER) UnitName("historical unit") else null), payload)
        val events = listOf(
            event("moment", Behavior.MOMENT, 1, EventPayload.Moment, null),
            event("counter", Behavior.COUNTER, 2, EventPayload.Counter(Quantity.exact("1.25")!!, UnitName("historical unit")), target),
            event("duration", Behavior.DURATION, 3, EventPayload.Duration(EpochMillis(10)), null),
            event("state", Behavior.STATE, 4, EventPayload.State(group, DatasetGeneration(2)), target),
        )
        val binding = BindingLifecycle(BindingId("binding"), RecordId("counter"), target, BindingStatus.ORPHANED,
            Revision(5), DisplaySnapshot("old binding", null, "old target", null),
            listOf(UndoInvalidation(ReceiptId("removed"), ResultReason.STALE_REVISION)))
        val receipt = UndoReceipt(ReceiptId("receipt"), EventId("event-4"), Revision(3), DatasetGeneration(7),
            ScopeContext(scope, DatasetGeneration(2)))
        return DomainState(records, mapOf(target to Target(target, "current target", null)),
            mapOf((RecordId("counter") to target) to RecordTarget(RecordId("counter"), target, false, Revision(4))),
            events, mapOf(group to StateGroup(group, "state group")), DatasetGeneration(7), Sequence(20),
            mapOf(scope to RecordId("state")), mapOf(scope to DatasetGeneration(2)),
            mapOf(binding.bindingId to binding), mapOf(receipt.receiptId to receipt))
    }
}
