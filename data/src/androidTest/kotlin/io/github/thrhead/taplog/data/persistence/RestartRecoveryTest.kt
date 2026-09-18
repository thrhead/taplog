package io.github.thrhead.taplog.data.persistence

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.thrhead.taplog.core.domain.*
import io.github.thrhead.taplog.core.domain.Record
import io.github.thrhead.taplog.core.engine.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/**
 * Tests caller-owned SQLite transactions over the available staged persistence phases.
 * An exception deterministically models interruption; Room unwinds/rolls back before
 * close/reopen. This is neither a real process kill nor T027's public commit/CAS adapter.
 */
@RunWith(AndroidJUnit4::class)
class RestartRecoveryTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val group = StateGroupId("group")
    private val target = TargetId("no-target")
    private val scope = StateScope(group, null)

    // Catches dropped/rewritten Events, live-definition snapshot reconstruction, and
    // sequence-only/insertion-order reads. Each successful transaction is counted.
    @Test
    fun fourHundredSequentialBehaviorCommitsPreserveEveryEventFieldAfterReopen() = withName { name ->
        var expected = definitions()
        val successful = mutableMapOf<Behavior, Int>()
        withDatabase(name) { database ->
            stagedTransaction(database, expected)
            listOf(Behavior.MOMENT, Behavior.COUNTER, Behavior.DURATION, Behavior.STATE).forEachIndexed { rank, behavior ->
                repeat(100) { index ->
                    val number = index + 1
                    val recordId = if (behavior == Behavior.DURATION) RecordId("duration-$number")
                        else RecordId(behavior.name.lowercase())
                    val record = if (behavior == Behavior.DURATION)
                        Record(recordId, "live duration $number", null, behavior, hasEvents = true)
                        else expected.records.getValue(recordId).copy(hasEvents = true)
                    // Reverse behavior time blocks; neighboring Events tie on time.
                    val at = 10_000L * (3 - rank) + index / 2
                    val payload = when (behavior) {
                        Behavior.MOMENT -> EventPayload.Moment
                        Behavior.COUNTER -> EventPayload.Counter(Quantity.exact("$number.25")!!, UnitName("historic cups"))
                        Behavior.DURATION -> EventPayload.Duration(EpochMillis(at))
                        Behavior.STATE -> EventPayload.State(group, DatasetGeneration(2))
                    }
                    val event = historicEvent("${behavior.name}-$number", recordId, behavior,
                        rank * 100L + number, at, payload, if (index % 2 == 0) null else target)
                    val eventScope = StateScope(group, event.targetId)
                    expected = expected.copy(records = expected.records + (recordId to record),
                        events = expected.events + event, nextSequence = Sequence(rank * 100L + number + 1),
                        currentStates = if (behavior == Behavior.STATE) expected.currentStates + (eventScope to recordId)
                            else expected.currentStates)
                    stagedTransaction(database, expected.copy(events = expected.events.reversed()))
                    // Do not count a silently dropped @Upsert as a successful behavior commit.
                    assertEquals(event.id.value, database.persistenceDao().readEvent(event.id.value)!!.eventId)
                    successful[behavior] = (successful[behavior] ?: 0) + 1
                }
            }
            assertEquals(mapOf(Behavior.MOMENT to 100, Behavior.COUNTER to 100,
                Behavior.DURATION to 100, Behavior.STATE to 100), successful)
        }
        // Independent fixture order: State, Duration, Counter, Moment, then ties by sequence.
        val orderedIds = listOf("STATE", "DURATION", "COUNTER", "MOMENT").flatMap { behavior ->
            (1..100).map { "$behavior-$it" }
        }
        val byId = expected.events.associateBy { it.id.value }
        expected = expected.copy(events = orderedIds.map(byId::getValue))
        repeat(2) {
            withDatabase(name) { database ->
                val actual = RoomLocalPersistence(database).read()
                assertEquals(expected, actual)
                assertEquals(400, actual.events.size)
                assertEquals(orderedIds, actual.events.map { it.id.value })
                assertExactEvents(expected.events, actual.events)
                assertEquals(Sequence(401), actual.nextSequence)
                (1..100).forEach { number ->
                    val row = database.persistenceDao().readOpenDuration("duration-$number",
                        if (number % 2 == 1) "no-target" else "target:no-target")!!
                    assertEquals("DURATION-$number", row.eventId)
                    assertEquals("OPEN", row.durationStatus)
                    assertEquals(null, row.durationEndAt)
                    assertEquals(null, row.durationIncompleteReason)
                }
                assertEquals("STATE-99", database.persistenceDao().readCurrentState("group", "no-target")!!.eventId)
                assertEquals("STATE-100", database.persistenceDao().readCurrentState("group", "target:no-target")!!.eventId)
                assertEquals(mapOf(scope to RecordId("state"), StateScope(group, target) to RecordId("state")),
                    actual.currentStates)
            }
        }
    }

    @Test fun momentInterruptionRecoversOnlyCompleteOldOrNewState() = interruptionMatrix(Behavior.MOMENT)
    @Test fun counterInterruptionRecoversOnlyCompleteOldOrNewState() = interruptionMatrix(Behavior.COUNTER)
    @Test fun durationInterruptionRetainsOpenOrCompleteReplacementWithoutInventedEnds() = interruptionMatrix(Behavior.DURATION)
    @Test fun stateInterruptionRetainsActiveGenerationAndExclusivePointer() = interruptionMatrix(Behavior.STATE)

    // Catches a durable Record with a missing initial Event (or the opposite) at every boundary.
    @Test
    fun createRecordAndLogInterruptionNeverRecoversHalfTheCompoundOperation() {
        val before = baseline()
        val record = Record(RecordId("created"), "created moment", "created icon", Behavior.MOMENT)
        val initial = Event(EventId("created-event"), record.id, null, Behavior.MOMENT,
            EpochMillis(20), EpochMillis(100), EpochMillis(100), Sequence(5), Source.APP, Revision(0),
            EventSnapshot("created moment", "created icon", null, null, Behavior.MOMENT, null), EventPayload.Moment)
        val expected = before.copy(records = before.records + (record.id to record.copy(hasEvents = true, revision = Revision(1))),
            events = before.events + initial, nextSequence = Sequence(6))
        // Capture the actual engine proposal in memory; this boundary does not persist or
        // implement CAS. The separate real Room transaction below is the test subject.
        var proposed: DomainState? = null
        val proposalBoundary = object : AtomicCommitBoundary {
            override fun read() = before
            override fun commit(operation: CommitOperation): Boolean {
                assertEquals(before, operation.expected)
                proposed = operation.state
                return true
            }
        }
        val engine = EventEngine(proposalBoundary, object : AcceptanceClock {
            override fun now() = EpochMillis(100)
        }, { EventId("created-event") })
        assertTrue(engine.apply(CreateRecordAndLog(record, occurredAt = EpochMillis(20),
            source = Source.APP, confirmed = true)) is EngineResult.Applied)
        assertEquals(expected, proposed)
        verifyInterruptions(before, expected)
    }

    private fun interruptionMatrix(behavior: Behavior) {
        val before = baseline()
        val recordId = if (behavior == Behavior.STATE) RecordId("state-b") else RecordId(behavior.name.lowercase())
        val payload = when (behavior) {
            Behavior.MOMENT -> EventPayload.Moment
            Behavior.COUNTER -> EventPayload.Counter(Quantity.exact("7.25")!!, UnitName("historic cups"))
            Behavior.DURATION -> EventPayload.Duration(EpochMillis(20))
            Behavior.STATE -> EventPayload.State(group, DatasetGeneration(3))
        }
        val event = historicEvent("replacement", recordId, behavior, 5, 20, payload)
        val history = if (behavior == Behavior.DURATION) before.events.map {
            if (it.behavior == Behavior.DURATION) it.copy(updatedAt = EpochMillis(90), revision = Revision(4),
                payload = EventPayload.Duration(EpochMillis(10), EpochMillis(15), DurationStatus.COMPLETED)) else it
        } else before.events
        val after = before.copy(records = before.records + (recordId to before.records.getValue(recordId).copy(
            name = "edited live definition", revision = Revision(8), hasEvents = true)),
            events = history + event, nextSequence = Sequence(6),
            currentStates = if (behavior == Behavior.STATE) before.currentStates + (scope to recordId) else before.currentStates,
            stateGenerations = if (behavior == Behavior.STATE) before.stateGenerations + (scope to DatasetGeneration(3))
                else before.stateGenerations)
        verifyInterruptions(before, after)
    }

    private fun verifyInterruptions(before: DomainState, after: DomainState) {
        Interruption.entries.forEach { point ->
            withName { name ->
                withDatabase(name) { database ->
                    stagedTransaction(database, before)
                    val failure = assertThrows(SimulatedInterruption::class.java) {
                        stagedTransaction(database, after, point)
                    }
                    assertEquals(point, failure.point)
                }
                // Pre-commit interruption has a known OLD result; response loss after
                // transaction return has a known NEW result. Reopening twice detects repair.
                val expected = if (point == Interruption.AFTER_COMMIT) after else before
                repeat(2) {
                    withDatabase(name) { database ->
                        val recovered = RoomLocalPersistence(database).read()
                        assertTrue("$point must recover a complete old or new aggregate", recovered == before || recovered == after)
                        assertEquals("$point durability boundary", expected, recovered)
                        assertExactEvents(expected.events, recovered.events)
                        assertActiveDurationAndState(database, expected)
                        val created = recovered.records[RecordId("created")]
                        assertEquals(created != null, recovered.events.any { it.recordId == RecordId("created") })
                    }
                }
            }
        }
    }

    private fun assertActiveDurationAndState(database: TapLogDatabase, expected: DomainState) {
        val dao = database.persistenceDao()
        val open = expected.events.single { (it.payload as? EventPayload.Duration)?.status == DurationStatus.OPEN }
        val openRow = dao.readOpenDuration("duration", "no-target")!!
        assertEquals(open.id.value, openRow.eventId)
        assertEquals((open.payload as EventPayload.Duration).startAt.value, openRow.durationStartAt)
        assertEquals(null, openRow.durationEndAt)
        assertEquals(null, openRow.durationIncompleteReason)
        assertEquals(null, dao.readOpenDuration("duration", "target:no-target"))
        val current = dao.readCurrentState("group", "no-target")!!
        assertEquals(expected.currentStates.getValue(scope).value, current.recordId)
        assertEquals(expected.stateGenerations.getValue(scope).value, current.stateGeneration)
        assertEquals(if (expected.stateGenerations.getValue(scope) == DatasetGeneration(3)) "replacement" else "STATE-old",
            current.eventId)
        assertEquals(null, dao.readCurrentState("group", "target:no-target"))
        expected.events.filter { (it.payload as? EventPayload.Duration)?.status == DurationStatus.COMPLETED }.forEach {
            assertEquals(15L, dao.readEvent(it.id.value)!!.durationEndAt)
        }
    }

    private fun assertExactEvents(expected: List<Event>, actual: List<Event>) {
        assertEquals(expected.size, actual.size)
        expected.zip(actual).forEach { (want, got) ->
            val label = want.id.value
            assertEquals("$label identity", want.id, got.id)
            assertEquals("$label Record", want.recordId, got.recordId)
            assertEquals("$label Target", want.targetId, got.targetId)
            assertEquals("$label behavior", want.behavior, got.behavior)
            assertEquals("$label occurred", want.occurredAt, got.occurredAt)
            assertEquals("$label created", want.createdAt, got.createdAt)
            assertEquals("$label updated", want.updatedAt, got.updatedAt)
            assertEquals("$label payload", want.payload, got.payload)
            assertEquals("$label snapshot", want.snapshot, got.snapshot)
            assertEquals("$label source", want.source, got.source)
            assertEquals("$label sequence", want.sequence, got.sequence)
            assertEquals("$label revision", want.revision, got.revision)
        }
    }

    private fun definitions(): DomainState {
        val records = listOf(
            Record(RecordId("moment"), "live moment", null, Behavior.MOMENT),
            Record(RecordId("counter"), "live counter", null, Behavior.COUNTER,
                unit = UnitName("live cups"), defaultQuantity = Quantity.exact("2")),
            Record(RecordId("duration"), "live duration", null, Behavior.DURATION),
            Record(RecordId("state"), "live state", null, Behavior.STATE, stateGroupId = group),
            Record(RecordId("state-b"), "live state B", null, Behavior.STATE, stateGroupId = group),
        ).associateBy { it.id }
        return DomainState(records = records, targets = mapOf(target to Target(target, "live target", "live icon")),
            stateGroups = mapOf(group to StateGroup(group, "Group")), generation = DatasetGeneration(7),
            stateGenerations = mapOf(scope to DatasetGeneration(2), StateScope(group, target) to DatasetGeneration(2)))
    }

    private fun baseline(): DomainState {
        val definitions = definitions()
        return definitions.copy(records = definitions.records.mapValues { (_, record) -> record.copy(hasEvents = true) },
            events = listOf(
                historicEvent("MOMENT-old", RecordId("moment"), Behavior.MOMENT, 1, 10, EventPayload.Moment),
                historicEvent("COUNTER-old", RecordId("counter"), Behavior.COUNTER, 2, 10,
                    EventPayload.Counter(Quantity.exact("1.25")!!, UnitName("historic cups")), target),
                historicEvent("DURATION-old", RecordId("duration"), Behavior.DURATION, 3, 10,
                    EventPayload.Duration(EpochMillis(10))),
                historicEvent("STATE-old", RecordId("state"), Behavior.STATE, 4, 10,
                    EventPayload.State(group, DatasetGeneration(2))),
            ), nextSequence = Sequence(5), currentStates = mapOf(scope to RecordId("state")),
            relationships = mapOf((RecordId("counter") to target) to RecordTarget(RecordId("counter"), target, true)))
    }

    private fun historicEvent(id: String, record: RecordId, behavior: Behavior, sequence: Long, at: Long,
        payload: EventPayload, targetId: TargetId? = null) = Event(
        EventId(id), record, targetId, behavior, EpochMillis(at), EpochMillis(-100 + sequence), EpochMillis(500 + sequence),
        Sequence(sequence), if (sequence % 2L == 0L) Source.NFC else Source.APP, Revision(3),
        EventSnapshot("historic ${record.value}", "historic icon", targetId?.let { "historic target" },
            targetId?.let { "historic target icon" }, behavior, if (behavior == Behavior.COUNTER) UnitName("historic cups") else null), payload)

    private enum class Interruption {
        BEFORE_TRANSACTION, AFTER_BEGIN, AFTER_STATE_PHASE, AFTER_DEFINITIONS_PHASE,
        AFTER_RELATIONSHIPS, AFTER_EVENTS_PHASE, AFTER_METADATA, BEFORE_COMMIT, AFTER_COMMIT,
    }

    private class SimulatedInterruption(val point: Interruption) : RuntimeException()

    private fun stagedTransaction(database: TapLogDatabase, state: DomainState, interruption: Interruption? = null) {
        fun interrupt(point: Interruption) { if (interruption == point) throw SimulatedInterruption(point) }
        interrupt(Interruption.BEFORE_TRANSACTION)
        database.runInTransaction {
            interrupt(Interruption.AFTER_BEGIN)
            val persistence = RoomLocalPersistence(database)
            val dao = database.persistenceDao()
            persistence.writeStateGroupsAndScopes(state, dao)
            interrupt(Interruption.AFTER_STATE_PHASE)
            persistence.writeRecordsAndTargets(state, dao)
            interrupt(Interruption.AFTER_DEFINITIONS_PHASE)
            val rows = PersistenceMapper.toRows(state)
            if (rows.recordTargets.isNotEmpty()) dao.upsertRecordTargets(rows.recordTargets)
            interrupt(Interruption.AFTER_RELATIONSHIPS)
            // Duration terminal edits precede replacement OPENs in the supplied fixture.
            persistence.writeEvents(state, dao)
            interrupt(Interruption.AFTER_EVENTS_PHASE)
            dao.upsertMetadata(rows.metadata)
            interrupt(Interruption.AFTER_METADATA)
            interrupt(Interruption.BEFORE_COMMIT)
        }
        interrupt(Interruption.AFTER_COMMIT)
    }

    private fun withDatabase(name: String, block: (TapLogDatabase) -> Unit) {
        val database = TapLogDatabase.builder(context, name).allowMainThreadQueries().build()
        try { block(database) } finally { database.close() }
    }

    private fun withName(block: (String) -> Unit) {
        val name = "taplog-t019-${UUID.randomUUID()}.db"
        try { block(name) } finally { context.deleteDatabase(name) }
    }
}
