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

        override fun persistenceDao(): PersistenceDao = proxy(PersistenceDao::class.java) { method ->
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
