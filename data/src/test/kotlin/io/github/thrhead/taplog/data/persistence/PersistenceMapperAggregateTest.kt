package io.github.thrhead.taplog.data.persistence

import io.github.thrhead.taplog.core.domain.*
import io.github.thrhead.taplog.core.engine.*
import org.junit.Assert.*
import org.junit.Test

class PersistenceMapperAggregateTest {
    @Test fun completeAggregateRoundTripsEveryFamilyAndHistoricalSnapshot() {
        val state = fixture()
        val rows = PersistenceMapper.toRows(state)
        assertEquals(listOf("MOMENT", "COUNTER", "DURATION", "DURATION", "DURATION", "STATE"), rows.events.map { it.behavior })
        assertEquals("1.25", rows.events[1].counterQuantity)
        assertEquals("no-target", rows.events[0].targetScopeKey)
        assertEquals("target:target:no-target", rows.events[1].targetScopeKey)
        assertEquals("old counter", rows.events[1].snapshotRecordName)
        assertEquals("old unit", rows.events[1].snapshotUnit)
        assertEquals("ORPHANED", rows.bindings.single().status)
        assertEquals("removed-receipt", rows.bindingUndoInvalidations.single().receiptId)
        assertEquals(DatasetMetadataEntity(1, 5, 20, 1), rows.metadata.single())
        assertEquals(canonicalFixture(), PersistenceMapper.fromRows(rows))
    }

    @Test fun emptyAggregateHasExactlyOneMetadataRow() {
        val rows = PersistenceMapper.toRows(DomainState())
        assertEquals(listOf(DatasetMetadataEntity(1, 0, 1, 1)), rows.metadata)
        assertEquals(DomainState(), PersistenceMapper.fromRows(rows))
    }

    @Test fun implicitInitialStateGenerationIsPersistedAsExplicitZero() {
        val state = fixture().copy(events = fixture().events.map { event ->
            if (event.payload is EventPayload.State) event.copy(payload = EventPayload.State(groupId, DatasetGeneration(0))) else event
        }, stateGenerations = emptyMap())
        val rows = PersistenceMapper.toRows(state)
        assertEquals(0L, rows.stateScopes.single().generation)
        val restored = PersistenceMapper.fromRows(rows)
        assertEquals(state.currentStates, restored.currentStates)
        assertEquals(mapOf(scope to DatasetGeneration(0)), restored.stateGenerations)
    }

    @Test fun historicalStateGenerationsAndSignedTimestampsArePreserved() {
        val state = canonicalFixture().copy(stateGenerations = mapOf(scope to DatasetGeneration(7)), currentStates = emptyMap(),
            events = canonicalFixture().events.map { it.copy(occurredAt = EpochMillis(Long.MIN_VALUE),
                createdAt = EpochMillis(Long.MAX_VALUE), updatedAt = EpochMillis(-1), revision = Revision(Long.MAX_VALUE)) },
            generation = DatasetGeneration(Long.MAX_VALUE), nextSequence = Sequence(Long.MAX_VALUE))
        assertEquals(state, PersistenceMapper.fromRows(PersistenceMapper.toRows(state)))
    }

    @Test fun deletedEventReceiptAndDeletedCurrentRecordRemainClassifiable() {
        val state = fixture().copy(currentStates = mapOf(scope to RecordId("deleted-record")),
            undoReceipts = mapOf(ReceiptId("receipt") to UndoReceipt(ReceiptId("receipt"), EventId("deleted-event"),
                Revision(8), DatasetGeneration(2), ScopeContext(StateScope(StateGroupId("deleted-group"), null), DatasetGeneration(3)))))
        assertEquals(state.copy(records = canonicalFixture().records, events = canonicalFixture().events),
            PersistenceMapper.fromRows(PersistenceMapper.toRows(state)))
    }

    @Test fun rowsRejectEveryMissingForeignKeyWithoutRepair() {
        val rows = PersistenceMapper.toRows(fixture())
        reject(rows.copy(records = rows.records.map { if (it.behavior == "STATE") it.copy(stateGroupId = "missing") else it }))
        reject(rows.copy(recordTargets = rows.recordTargets.map { it.copy(recordId = "missing") }))
        reject(rows.copy(recordTargets = rows.recordTargets.map { it.copy(targetId = "missing") }))
        reject(rows.copy(events = rows.events.map { it.copy(recordId = "missing") }))
        reject(rows.copy(events = rows.events.map { it.copy(targetId = "missing", targetScopeKey = "target:missing") }))
        reject(rows.copy(events = rows.events.map { if (it.behavior == "STATE") it.copy(stateGroupId = "missing") else it }))
        reject(rows.copy(bindings = rows.bindings.map { it.copy(recordId = "missing") }))
        reject(rows.copy(bindings = rows.bindings.map { it.copy(targetId = "missing") }))
        reject(rows.copy(stateScopes = rows.stateScopes.map { it.copy(stateGroupId = "missing") }))
        reject(rows.copy(stateScopes = rows.stateScopes.map { it.copy(targetScopeKey = "target:missing") }))
        reject(rows.copy(bindingUndoInvalidations = rows.bindingUndoInvalidations.map { it.copy(bindingId = "missing") }))
    }

    @Test fun rowsRejectMissingAndExtraneousTypedPayloadColumns() {
        val rows = PersistenceMapper.toRows(fixture())
        val invalid = listOf(
            rows.events[0].copy(counterUnit = "unexpected"), rows.events[0].copy(durationEndAt = 1),
            rows.events[0].copy(stateGeneration = 0), rows.events[1].copy(counterQuantity = null),
            rows.events[1].copy(durationIncompleteReason = "unexpected"), rows.events[1].copy(stateGroupId = "group"),
            rows.events[2].copy(durationStartAt = null), rows.events[2].copy(durationStatus = null),
            rows.events[2].copy(counterQuantity = "1"), rows.events[5].copy(stateGeneration = null),
            rows.events[5].copy(stateGroupId = null), rows.events[5].copy(durationStartAt = 0))
        invalid.forEach { bad -> reject(rows.copy(events = rows.events.map { if (it.eventId == bad.eventId) bad else it })) }
    }

    @Test fun rowsRejectInvalidDurationTerminalCombinationsAndDuplicateOpenScope() {
        val rows = PersistenceMapper.toRows(fixture())
        val open = rows.events[2]
        listOf(open.copy(durationEndAt = 8), open.copy(durationIncompleteReason = "bad"),
            open.copy(durationStatus = "COMPLETED"), open.copy(durationStatus = "COMPLETED", durationEndAt = 1),
            open.copy(durationStatus = "INCOMPLETE"), open.copy(durationStatus = "INCOMPLETE", durationIncompleteReason = " "),
            open.copy(durationStatus = "INCOMPLETE", durationEndAt = 8, durationIncompleteReason = "bad"))
            .forEach { bad -> reject(rows.copy(events = rows.events.map { if (it.eventId == bad.eventId) bad else it })) }
        reject(rows.copy(events = rows.events + open.copy(eventId = "duplicate-open", sequence = 19)))
    }

    @Test fun rowsRejectDiscriminatorSnapshotUnitQuantityAndScopeDisagreement() {
        val rows = PersistenceMapper.toRows(fixture())
        listOf(rows.events[0].copy(behavior = "COUNTER"), rows.events[0].copy(snapshotBehavior = "STATE"),
            rows.events[0].copy(targetScopeKey = "target:target:no-target"), rows.events[1].copy(snapshotUnit = "other"),
            rows.events[1].copy(counterQuantity = "0"), rows.events[1].copy(counterQuantity = "1.0"),
            rows.events[1].copy(counterUnit = " "), rows.events[5].copy(stateGeneration = 4),
            rows.events[5].copy(stateGroupId = "other-group"))
            .forEach { bad -> reject(rows.copy(events = rows.events.map { if (it.eventId == bad.eventId) bad else it })) }
    }

    @Test fun rowsRejectUnknownEnumsAndInvalidCounterDefinitions() {
        val rows = PersistenceMapper.toRows(fixture())
        reject(rows.copy(records = rows.records.map { it.copy(behavior = "UNKNOWN") }))
        reject(rows.copy(targets = rows.targets.map { it.copy(lifecycle = "UNKNOWN") }))
        reject(rows.copy(bindings = rows.bindings.map { it.copy(status = "UNKNOWN") }))
        reject(rows.copy(bindingUndoInvalidations = rows.bindingUndoInvalidations.map { it.copy(reason = "UNKNOWN") }))
        reject(rows.copy(events = rows.events.map { it.copy(source = "UNKNOWN") }))
        listOf("0", "-1", "1.00").forEach { quantity ->
            reject(rows.copy(records = rows.records.map { if (it.behavior == "COUNTER") it.copy(defaultQuantity = quantity) else it }))
        }
        reject(rows.copy(records = rows.records.map { if (it.behavior == "COUNTER") it.copy(defaultQuantity = null) else it }))
        reject(rows.copy(records = rows.records.map { if (it.behavior == "COUNTER") it.copy(unit = " ") else it }))
        reject(rows.copy(records = rows.records.map { if (it.behavior == "STATE") it.copy(stateGroupId = null) else it }))
    }

    @Test fun rowsRejectNegativeMonotonicValuesAndReusedSequence() {
        val rows = PersistenceMapper.toRows(fixture())
        reject(rows.copy(records = rows.records.map { it.copy(revision = -1) }))
        reject(rows.copy(targets = rows.targets.map { it.copy(revision = -1) }))
        reject(rows.copy(recordTargets = rows.recordTargets.map { it.copy(revision = -1) }))
        reject(rows.copy(stateGroups = rows.stateGroups.map { it.copy(revision = -1) }))
        reject(rows.copy(bindings = rows.bindings.map { it.copy(revision = -1) }))
        reject(rows.copy(events = rows.events.map { it.copy(revision = -1) }))
        reject(rows.copy(events = rows.events.map { it.copy(sequence = 0) }))
        reject(rows.copy(events = rows.events.map { it.copy(sequence = 1) }))
        reject(rows.copy(stateScopes = rows.stateScopes.map { it.copy(generation = -1) }))
        reject(rows.copy(undoReceipts = rows.undoReceipts.map { it.copy(expectedEventRevision = -1) }))
        reject(rows.copy(undoReceipts = rows.undoReceipts.map { it.copy(expectedDatasetGeneration = -1) }))
        reject(rows.copy(undoReceipts = rows.undoReceipts.map { it.copy(expectedScopeGeneration = -1) }))
        reject(rows.copy(metadata = listOf(rows.metadata.single().copy(datasetGeneration = -1))))
        reject(rows.copy(metadata = listOf(rows.metadata.single().copy(nextSequence = 6))))
    }

    @Test fun rowsRejectDuplicatePrimaryKeysAndInvalidMetadataCardinality() {
        val rows = PersistenceMapper.toRows(fixture())
        reject(rows.copy(records = rows.records + rows.records.first()))
        reject(rows.copy(targets = rows.targets + rows.targets.first()))
        reject(rows.copy(recordTargets = rows.recordTargets + rows.recordTargets.first()))
        reject(rows.copy(stateGroups = rows.stateGroups + rows.stateGroups.first()))
        reject(rows.copy(events = rows.events + rows.events.first()))
        reject(rows.copy(stateScopes = rows.stateScopes + rows.stateScopes.first()))
        reject(rows.copy(bindings = rows.bindings + rows.bindings.first()))
        reject(rows.copy(bindingUndoInvalidations = rows.bindingUndoInvalidations + rows.bindingUndoInvalidations.first()))
        reject(rows.copy(undoReceipts = rows.undoReceipts + rows.undoReceipts.first()))
        reject(rows.copy(metadata = emptyList()))
        reject(rows.copy(metadata = rows.metadata + rows.metadata.first()))
        reject(rows.copy(metadata = listOf(rows.metadata.single().copy(singletonKey = 2))))
        reject(rows.copy(metadata = listOf(rows.metadata.single().copy(schemaVersion = 2))))
    }

    @Test fun unsupportedAdapterMetadataAndPartialUndoScopeFailClosed() {
        val rows = PersistenceMapper.toRows(fixture())
        reject(rows.copy(stateScopes = rows.stateScopes.map { it.copy(resetSequence = 1) }))
        reject(rows.copy(stateScopes = rows.stateScopes.map { it.copy(resetAt = 1) }))
        listOf(rows.undoReceipts.single().copy(operation = "create"), rows.undoReceipts.single().copy(beforeImageJson = "{}"),
            rows.undoReceipts.single().copy(consumed = true), rows.undoReceipts.single().copy(invalidationReason = "reason"),
            rows.undoReceipts.single().copy(stateGroupId = null), rows.undoReceipts.single().copy(targetScopeKey = null),
            rows.undoReceipts.single().copy(expectedScopeGeneration = null))
            .forEach { reject(rows.copy(undoReceipts = listOf(it))) }
    }

    @Test fun domainMapKeysMustAgreeWithContainedIds() {
        val state = fixture()
        failure { PersistenceMapper.toRows(state.copy(records = state.records.withWrongFirstKey(RecordId("wrong")))) }
        failure { PersistenceMapper.toRows(state.copy(targets = state.targets.withWrongFirstKey(TargetId("wrong")))) }
        failure { PersistenceMapper.toRows(state.copy(relationships = state.relationships.withWrongFirstKey(RecordId("wrong") to targetId))) }
        failure { PersistenceMapper.toRows(state.copy(stateGroups = state.stateGroups.withWrongFirstKey(StateGroupId("wrong")))) }
        failure { PersistenceMapper.toRows(state.copy(bindings = state.bindings.withWrongFirstKey(BindingId("wrong")))) }
        failure { PersistenceMapper.toRows(state.copy(undoReceipts = state.undoReceipts.withWrongFirstKey(ReceiptId("wrong")))) }
    }

    private fun reject(rows: PersistenceRows) = failure { PersistenceMapper.fromRows(rows) }
    private fun <K, V> Map<K, V>.withWrongFirstKey(wrongKey: K): Map<K, V> =
        (this - keys.first()) + (wrongKey to values.first())
    private fun failure(block: () -> Any?) {
        try { block(); fail("Expected MappingFailure") } catch (_: MappingFailure) { }
    }

    private val targetId = TargetId("target:no-target")
    private val groupId = StateGroupId("group")
    private val scope = StateScope(groupId, targetId)
    private fun canonicalFixture(): DomainState = fixture("1.25", "2")
    private fun fixture(quantity: String = "1.2500", defaultQuantity: String = "2.00"): DomainState {
        val records = listOf(Record(RecordId("moment"), "new moment", null, Behavior.MOMENT, hasEvents = true),
            Record(RecordId("counter"), "new counter", "new", Behavior.COUNTER, Lifecycle.ARCHIVED,
                UnitName("new unit"), Quantity.exact(defaultQuantity), revision = Revision(7), hasEvents = true),
            Record(RecordId("duration"), "duration", null, Behavior.DURATION, hasEvents = true),
            Record(RecordId("state"), "state", null, Behavior.STATE, stateGroupId = groupId, hasEvents = true))
        fun event(id: String, record: String, behavior: Behavior, sequence: Long, payload: EventPayload, target: TargetId? = null): Event =
            Event(EventId(id), RecordId(record), target, behavior, EpochMillis(-10 + sequence), EpochMillis(100), EpochMillis(102),
                Sequence(sequence), Source.NFC, Revision(3), EventSnapshot("old $record", "old icon",
                    if (target == null) null else "old target", if (target == null) null else "target icon", behavior,
                    if (behavior == Behavior.COUNTER) UnitName("old unit") else null), payload)
        val events = listOf(event("moment-event", "moment", Behavior.MOMENT, 1, EventPayload.Moment),
            event("counter-event", "counter", Behavior.COUNTER, 2, EventPayload.Counter(Quantity.exact(quantity)!!, UnitName("old unit")), targetId),
            event("open", "duration", Behavior.DURATION, 3, EventPayload.Duration(EpochMillis(5))),
            event("completed", "duration", Behavior.DURATION, 4, EventPayload.Duration(EpochMillis(5), EpochMillis(9), DurationStatus.COMPLETED)),
            event("incomplete", "duration", Behavior.DURATION, 5, EventPayload.Duration(EpochMillis(5), status = DurationStatus.INCOMPLETE, incompleteReason = "scope became inactive"), targetId),
            event("state-event", "state", Behavior.STATE, 6, EventPayload.State(groupId, DatasetGeneration(2)), targetId))
        val binding = BindingLifecycle(BindingId("binding"), RecordId("counter"), targetId, BindingStatus.ORPHANED, Revision(9),
            DisplaySnapshot("binding old record", "old", "binding old target", null),
            listOf(UndoInvalidation(ReceiptId("removed-receipt"), ResultReason.STALE_REVISION)))
        val receipt = UndoReceipt(ReceiptId("receipt"), EventId("state-event"), Revision(3), DatasetGeneration(5), ScopeContext(scope, DatasetGeneration(2)))
        return DomainState(records.associateBy { it.id }, mapOf(targetId to Target(targetId, "new target", "new", Lifecycle.ARCHIVED, Revision(4))),
            mapOf((RecordId("counter") to targetId) to RecordTarget(RecordId("counter"), targetId, false, Revision(6))), events,
            mapOf(groupId to StateGroup(groupId, "group", Revision(8)), StateGroupId("other-group") to StateGroup(StateGroupId("other-group"), "other")),
            DatasetGeneration(5), Sequence(20), mapOf(scope to RecordId("state")), mapOf(scope to DatasetGeneration(2)),
            mapOf(binding.bindingId to binding), mapOf(receipt.receiptId to receipt))
    }
}
