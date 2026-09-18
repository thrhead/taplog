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
    // Catches lost lifecycle/snapshot/child metadata and accidental writes to unrelated families.
    @Test
    fun bindingWritesPersistSuppliedOrphaningAndHistoricalDisplayWithoutOtherMutations() {
        val state = aggregate()
        val binding = state.bindings.values.single()
        val active = binding.copy(status = BindingStatus.ACTIVE, revision = Revision(4), undoInvalidations = emptyList())
        val unrelated = active.copy(bindingId = BindingId("other"), recordId = RecordId("moment"), targetId = null,
            lastKnownDisplay = DisplaySnapshot("historic moment", "old icon", null, null))
        val before = state.copy(bindings = mapOf(active.bindingId to active, unrelated.bindingId to unrelated))
        val original = PersistenceMapper.toRows(before)
        val database = SnapshotDatabase(original)
        val supplied = state.copy(bindings = mapOf(binding.bindingId to binding), records = state.records.mapValues { (_, record) ->
            record.copy(name = "edited live definition") })
        RoomLocalPersistence(database).writeBindings(supplied, database.persistenceDao())
        assertEquals(listOf(BindingEntity("binding", "counter", "target", "ORPHANED", 5, "old binding", null, "old target", null),
            BindingEntity("other", "moment", null, "ACTIVE", 4, "historic moment", "old icon", null, null)), database.committed.bindings)
        assertEquals(listOf(BindingUndoInvalidationEntity("binding", "removed", "STALE_REVISION")), database.committed.bindingUndoInvalidations)
        assertEquals(original, database.committed.copy(bindings = original.bindings, bindingUndoInvalidations = original.bindingUndoInvalidations))
        RoomLocalPersistence(database).writeBindings(DomainState(), database.persistenceDao())
        assertEquals(before.copy(bindings = before.bindings + (binding.bindingId to binding)), RoomLocalPersistence(database).read())
    }

    // Catches failure to create active bindings, including the exact no-Target scope.
    @Test
    fun bindingWritesCreateActiveAndOrphanedRowsWithCanonicalSnapshots() {
        val state = aggregate()
        val binding = state.bindings.values.single()
        val active = binding.copy(bindingId = BindingId("new"), targetId = null, status = BindingStatus.ACTIVE,
            revision = Revision(0), lastKnownDisplay = DisplaySnapshot("historic counter", "old", null, null), undoInvalidations = emptyList())
        val expected = state.copy(bindings = state.bindings + (active.bindingId to active))
        val database = SnapshotDatabase(PersistenceMapper.toRows(state.copy(bindings = emptyMap())))
        RoomLocalPersistence(database).writeBindings(expected, database.persistenceDao())
        assertEquals(expected, RoomLocalPersistence(database).read())
        assertEquals(BindingEntity("new", "counter", null, "ACTIVE", 0, "historic counter", "old", null, null),
            database.committed.bindings.single { it.bindingId == "new" })
    }

    // Catches stale updates and identity retargeting before any valid earlier binding can be written.
    @Test
    fun bindingRevisionGuardsPermitReplayAndAdvanceButRejectStaleChangesAndRetargeting() {
        val state = aggregate()
        val binding = state.bindings.values.single()
        val database = SnapshotDatabase(PersistenceMapper.toRows(state))
        RoomLocalPersistence(database).writeBindings(state, database.persistenceDao())
        val advanced = binding.copy(revision = Revision(8), lastKnownDisplay = DisplaySnapshot("supplied historical update", null, "old target", null))
        val updated = state.copy(bindings = mapOf(binding.bindingId to advanced))
        RoomLocalPersistence(database).writeBindings(updated, database.persistenceDao())
        assertEquals(advanced, RoomLocalPersistence(database).read().bindings.getValue(binding.bindingId))
        val before = database.committed
        val first = binding.copy(bindingId = BindingId("first"), revision = Revision(0))
        listOf(advanced.copy(revision = Revision(7)), advanced.copy(status = BindingStatus.ACTIVE),
            advanced.copy(lastKnownDisplay = binding.lastKnownDisplay),
            advanced.copy(undoInvalidations = advanced.undoInvalidations + UndoInvalidation(ReceiptId("another"), ResultReason.STALE_REVISION)),
            advanced.copy(recordId = RecordId("moment"), revision = Revision(9)),
            advanced.copy(targetId = null, revision = Revision(9))).forEach { invalid ->
            assertThrows(MappingFailure::class.java) {
                RoomLocalPersistence(database).writeBindings(updated.copy(bindings = linkedMapOf(first.bindingId to first, invalid.bindingId to invalid)), database.persistenceDao())
            }
            assertEquals(before, database.committed)
        }
    }

    // Catches invalid supplied aggregates and missing stored parents before a binding/child write.
    @Test
    fun invalidBindingAggregatesAndStoredParentsRejectWithoutChanges() {
        val state = aggregate()
        val binding = state.bindings.values.single()
        val original = PersistenceMapper.toRows(state)
        listOf(state.copy(records = emptyMap()), state.copy(targets = emptyMap()),
            state.copy(bindings = mapOf(BindingId("wrong") to binding)),
            state.copy(bindings = mapOf(binding.bindingId to binding.copy(revision = Revision(-1)))),
            state.copy(bindings = mapOf(binding.bindingId to binding.copy(undoInvalidations = binding.undoInvalidations + binding.undoInvalidations))),
            state.copy(generation = DatasetGeneration(-1))).forEach { invalid ->
            val database = SnapshotDatabase(original)
            assertThrows(MappingFailure::class.java) { RoomLocalPersistence(database).writeBindings(invalid, database.persistenceDao()) }
            assertEquals(original, database.committed)
        }
        listOf(original.copy(records = emptyList()), original.copy(targets = emptyList()),
            original.copy(bindings = original.bindings.map { it.copy(revision = -1) }),
            original.copy(bindings = original.bindings.map { it.copy(status = "UNKNOWN") })).forEach { stored ->
            val database = SnapshotDatabase(stored)
            assertThrows(MappingFailure::class.java) { RoomLocalPersistence(database).writeBindings(state, database.persistenceDao()) }
            assertEquals(stored, database.committed)
        }
    }

    // Catches clearing or ignoring unsupported Dataset/reset/Undo adapter context on write.
    @Test
    fun unsupportedStoredContextRejectsBindingsBeforeAnyUpsert() {
        val state = aggregate()
        val original = PersistenceMapper.toRows(state)
        val invalidMetadata = listOf(emptyList(), listOf(DatasetMetadataEntity(1, 7, 20, 2)),
            listOf(DatasetMetadataEntity(2, 7, 20, 1)), listOf(DatasetMetadataEntity(1, -1, 20, 1)),
            listOf(DatasetMetadataEntity(1, 7, 0, 1)), original.metadata + DatasetMetadataEntity(2, 7, 20, 1))
        val invalidRows = invalidMetadata.map { original.copy(metadata = it) } + listOf(
            original.copy(stateScopes = original.stateScopes.map { it.copy(resetSequence = 9) }),
            original.copy(stateScopes = original.stateScopes.map { it.copy(resetAt = 100) }),
            original.copy(undoReceipts = original.undoReceipts.map { it.copy(consumed = true) }),
            original.copy(bindingUndoInvalidations = original.bindingUndoInvalidations.map { it.copy(reason = "UNKNOWN") }))
        invalidRows.forEach { stored ->
            val database = SnapshotDatabase(stored)
            assertThrows(MappingFailure::class.java) { RoomLocalPersistence(database).writeBindings(state, database.persistenceDao()) }
            assertEquals(stored, database.committed)
        }
    }

    // Catches scope broadening, identity loss, and accidental mutation of history or definitions.
    @Test
    fun relationshipWritesUnlinkOnlyTheExactPairAndRetainAllOtherFamilies() {
        val state = aggregate()
        val target = TargetId("other")
        val originalPair = RecordId("counter") to TargetId("target")
        val addedPair = RecordId("counter") to target
        val sharedPair = RecordId("moment") to TargetId("target")
        val linked = state.copy(targets = state.targets + (target to Target(target, "Other", null)),
            relationships = mapOf(originalPair to RecordTarget(originalPair.first, originalPair.second, true, Revision(4)),
                sharedPair to RecordTarget(sharedPair.first, sharedPair.second, true, Revision(9))))
        val original = PersistenceMapper.toRows(linked)
        val database = SnapshotDatabase(original)
        val unlinked = linked.copy(relationships = linked.relationships +
            (originalPair to linked.relationships.getValue(originalPair).copy(linked = false, revision = Revision(5))) +
            (addedPair to RecordTarget(addedPair.first, addedPair.second, true, Revision(0))))
        RoomLocalPersistence(database).writeRelationships(unlinked, database.persistenceDao())
        assertEquals(listOf(RecordTargetEntity("counter", "target", false, 5),
            RecordTargetEntity("moment", "target", true, 9), RecordTargetEntity("counter", "other", true, 0)),
            database.committed.recordTargets)
        assertEquals(original, database.committed.copy(recordTargets = original.recordTargets))
        assertEquals(unlinked, RoomLocalPersistence(SnapshotDatabase(database.committed)).read())
        RoomLocalPersistence(database).writeRelationships(unlinked.copy(relationships = emptyMap()), database.persistenceDao())
        RoomLocalPersistence(database).writeRelationships(DomainState(), database.persistenceDao())
        assertEquals(unlinked, RoomLocalPersistence(database).read())
    }

    // Catches regression, same-revision lifecycle edits, and partial writes before a later invalid pair.
    @Test
    fun relationshipRevisionsAreMonotonicWhileIdenticalReplaysAreAccepted() {
        val state = aggregate()
        val pair = state.relationships.keys.single()
        val relationship = state.relationships.getValue(pair)
        val database = SnapshotDatabase(PersistenceMapper.toRows(state))
        RoomLocalPersistence(database).writeRelationships(state, database.persistenceDao())
        val advanced = state.copy(relationships = mapOf(pair to relationship.copy(revision = Revision(8))))
        RoomLocalPersistence(database).writeRelationships(advanced, database.persistenceDao())
        val before = database.committed
        val firstPair = RecordId("moment") to pair.second
        listOf(relationship.copy(revision = Revision(7)), relationship.copy(linked = true, revision = Revision(8))).forEach { invalid ->
            val supplied = advanced.copy(relationships = linkedMapOf(
                firstPair to RecordTarget(firstPair.first, firstPair.second, true, Revision(1)), pair to invalid))
            assertThrows(MappingFailure::class.java) {
                RoomLocalPersistence(database).writeRelationships(supplied, database.persistenceDao())
            }
            assertEquals(before, database.committed)
        }
        val relinked = advanced.copy(relationships = mapOf(pair to relationship.copy(linked = true, revision = Revision(9))))
        RoomLocalPersistence(database).writeRelationships(relinked, database.persistenceDao())
        assertEquals(relinked, RoomLocalPersistence(database).read())
    }

    // Catches absent supplied/stored parents, mismatched scope keys, and negative revisions before writes.
    @Test
    fun invalidRelationshipsFailBeforeAnyRelationshipWrite() {
        val state = aggregate()
        val pair = state.relationships.keys.single()
        val relationship = state.relationships.getValue(pair)
        val original = PersistenceMapper.toRows(state)
        val invalidStates = listOf(state.copy(records = emptyMap()), state.copy(targets = emptyMap()),
            state.copy(relationships = mapOf(pair to relationship.copy(revision = Revision(-1)))),
            state.copy(relationships = mapOf((RecordId("moment") to pair.second) to relationship)),
            state.copy(generation = DatasetGeneration(-1)))
        invalidStates.forEach { invalid ->
            val database = SnapshotDatabase(original)
            assertThrows(MappingFailure::class.java) { RoomLocalPersistence(database).writeRelationships(invalid, database.persistenceDao()) }
            assertEquals(original, database.committed)
        }
        listOf(original.copy(records = emptyList()), original.copy(targets = emptyList()),
            original.copy(recordTargets = listOf(RecordTargetEntity("counter", "target", false, -1)))).forEach { stored ->
            val database = SnapshotDatabase(stored)
            assertThrows(MappingFailure::class.java) { RoomLocalPersistence(database).writeRelationships(state, database.persistenceDao()) }
            assertEquals(stored, database.committed)
        }
    }

    // Catches mutation under unsupported stored Dataset context instead of silently repairing it.
    @Test
    fun unsupportedStoredMetadataRejectsRelationshipWritesWithoutChanges() {
        val state = aggregate()
        val original = PersistenceMapper.toRows(state)
        listOf(emptyList(), listOf(DatasetMetadataEntity(1, 7, 20, 2)),
            listOf(DatasetMetadataEntity(2, 7, 20, 1)),
            listOf(DatasetMetadataEntity(1, -1, 20, 1)), listOf(DatasetMetadataEntity(1, 7, 0, 1)),
            listOf(DatasetMetadataEntity(1, 7, 20, 1), DatasetMetadataEntity(2, 7, 20, 1))).forEach { metadata ->
            val stored = original.copy(metadata = metadata)
            val database = SnapshotDatabase(stored)
            assertThrows(MappingFailure::class.java) { RoomLocalPersistence(database).writeRelationships(state, database.persistenceDao()) }
            assertEquals(stored, database.committed)
        }
    }

    // Catches missing State upserts, scope collisions, and accidental writes to other families.
    @Test
    fun stateGroupAndScopeWritesRetainHistoryAndRecoverExclusivePointers() {
        val expected = stateAggregate()
        val original = PersistenceMapper.toRows(expected)
        val database = SnapshotDatabase(original.copy(stateGroups = emptyList(), stateScopes = emptyList()))
        RoomLocalPersistence(database).writeStateGroupsAndScopes(expected, database.persistenceDao())
        assertEquals(original, database.committed)
        assertEquals(expected, RoomLocalPersistence(SnapshotDatabase(database.committed)).read())
        assertEquals(listOf(
            StateScopeEntity("group", "no-target", 2, "state-b"),
            StateScopeEntity("group", "target:no-target", 0, "state-a"),
            StateScopeEntity("other", "no-target", 0, "other-state"),
        ), database.committed.stateScopes)

        val updated = expected.copy(
            stateGroups = expected.stateGroups.mapValues { (_, group) -> group.copy(name = "edited", revision = Revision(8)) },
            currentStates = expected.currentStates + (StateScope(StateGroupId("group"), null) to RecordId("state-a")),
        )
        RoomLocalPersistence(database).writeStateGroupsAndScopes(updated, database.persistenceDao())
        assertEquals(updated, RoomLocalPersistence(SnapshotDatabase(database.committed)).read())
        assertEquals(original, database.committed.copy(stateGroups = original.stateGroups, stateScopes = original.stateScopes))
        RoomLocalPersistence(database).writeStateGroupsAndScopes(DomainState(), database.persistenceDao())
        assertEquals(updated, RoomLocalPersistence(database).read())
    }

    // Catches failure to materialize a current scope's implicit zero, or invented scopes for history only.
    @Test
    fun implicitZeroAndRetainedContextualIdsSurviveStateScopeRecovery() {
        val state = stateAggregate().let { state -> state.copy(
            stateGenerations = state.stateGenerations.filterValues { it != DatasetGeneration(0) },
            currentStates = state.currentStates + (StateScope(StateGroupId("other"), null) to RecordId("deleted")),
        ) }
        val database = SnapshotDatabase(PersistenceMapper.toRows(state).copy(stateScopes = emptyList()))
        RoomLocalPersistence(database).writeStateGroupsAndScopes(state, database.persistenceDao())
        val recovered = RoomLocalPersistence(SnapshotDatabase(database.committed)).read()
        assertEquals(DatasetGeneration(0), recovered.stateGenerations[StateScope(StateGroupId("group"), TargetId("no-target"))])
        assertEquals(RecordId("deleted"), recovered.currentStates[StateScope(StateGroupId("other"), null)])
        val historyOnly = state.copy(currentStates = emptyMap(), stateGenerations = emptyMap(),
            events = state.events.filter { (it.payload as EventPayload.State).generation == DatasetGeneration(0) })
        val emptyScopes = SnapshotDatabase(PersistenceMapper.toRows(historyOnly))
        RoomLocalPersistence(emptyScopes).writeStateGroupsAndScopes(historyOnly, emptyScopes.persistenceDao())
        assertEquals(emptyList<StateScopeEntity>(), emptyScopes.committed.stateScopes)
        assertEquals(historyOnly, RoomLocalPersistence(emptyScopes).read())
    }

    // Catches partial writes before canonical scope/generation validation.
    @Test
    fun invalidStateGenerationFailsBeforeAnyStateFamilyWrite() {
        val state = stateAggregate()
        val rows = PersistenceMapper.toRows(state)
        val database = SnapshotDatabase(rows)
        val invalid = state.copy(
            stateGroups = state.stateGroups.mapValues { (_, group) -> group.copy(name = "must not be written") },
            stateGenerations = state.stateGenerations + (StateScope(StateGroupId("group"), null) to DatasetGeneration(1)))
        assertThrows(MappingFailure::class.java) {
            RoomLocalPersistence(database).writeStateGroupsAndScopes(invalid, database.persistenceDao())
        }
        assertEquals(rows, database.committed)
    }

    // Catches silent clearing of unsupported reset context during State upsert or recovery.
    @Test
    fun unsupportedResetMetadataRejectsReadsAndWritesWithoutChangingStoredRows() {
        val state = stateAggregate()
        val rows = PersistenceMapper.toRows(state)
        listOf(Sequence(9).value to null, null to EpochMillis(100).value, 9L to 100L).forEach { (sequence, at) ->
            val unsupported = rows.copy(stateScopes = rows.stateScopes.mapIndexed { index, scope ->
                if (index == 0) scope.copy(resetSequence = sequence, resetAt = at) else scope
            })
            val database = SnapshotDatabase(unsupported)
            assertThrows(MappingFailure::class.java) { RoomLocalPersistence(database).read() }
            assertThrows(MappingFailure::class.java) {
                RoomLocalPersistence(database).writeStateGroupsAndScopes(state.copy(
                    stateGroups = state.stateGroups.mapValues { (_, group) -> group.copy(name = "must not be written") }),
                    database.persistenceDao())
            }
            assertEquals(unsupported, database.committed)
            assertThrows(MappingFailure::class.java) { RoomLocalPersistence(SnapshotDatabase(database.committed)).read() }
        }
    }
    // Catches null/real Target scope collisions and recovery that reopens or timestamps terminal history.
    @Test
    fun durationRecoveryRetainsExactScopesAndTerminalHistoryAfterStagedUpdates() {
        val before = durationAggregate()
        val database = SnapshotDatabase(PersistenceMapper.toRows(before.copy(events = emptyList())))
        RoomLocalPersistence(database).writeEvents(before, database.persistenceDao())
        assertEquals(before, RoomLocalPersistence(database).read())
        val rows = database.committed.events.associateBy { it.eventId }
        assertEquals("no-target", rows.getValue("duration-1").targetScopeKey)
        assertEquals("target:no-target", rows.getValue("duration-2").targetScopeKey)

        val completed = before.events[0].copy(updatedAt = EpochMillis(900), revision = Revision(4),
            payload = EventPayload.Duration(EpochMillis(-50), EpochMillis(700), DurationStatus.COMPLETED))
        val incomplete = before.events[1].copy(updatedAt = EpochMillis(901), revision = Revision(5),
            payload = EventPayload.Duration(EpochMillis(-50), null, DurationStatus.INCOMPLETE, "target-unlinked"))
        RoomLocalPersistence(database).writeEvents(before.copy(events = listOf(completed, incomplete)), database.persistenceDao())

        // A fresh repository/database boundary consumes the retained scalar snapshot, not live definitions.
        val recoveredDatabase = SnapshotDatabase(database.committed)
        val persistence = RoomLocalPersistence(recoveredDatabase)
        val expected = before.copy(events = listOf(completed, incomplete) + before.events.drop(2))
        assertEquals(expected, persistence.read())
        persistence.writeEvents(expected.copy(events = emptyList()), recoveredDatabase.persistenceDao())
        assertEquals(expected, persistence.read())
        assertEquals(listOf("COMPLETED", "INCOMPLETE", "COMPLETED", "INCOMPLETE"),
            recoveredDatabase.committed.events.map { it.durationStatus })
        assertEquals(listOf(700L, null, 60L, null), recoveredDatabase.committed.events.map { it.durationEndAt })
        assertEquals(listOf(null, "target-unlinked", null, "record-archived"),
            recoveredDatabase.committed.events.map { it.durationIncompleteReason })
    }

    // Catches omission of aggregate OPEN uniqueness validation before any Event write.
    @Test
    fun competingOpenDurationsInEitherExactScopeFailBeforeAnyWrite() {
        val before = durationAggregate()
        before.events.take(2).forEach { open ->
            val database = SnapshotDatabase(PersistenceMapper.toRows(before))
            val competing = open.copy(id = EventId("competing"), sequence = Sequence(5))
            assertThrows(MappingFailure::class.java) {
                RoomLocalPersistence(database).writeEvents(before.copy(events = before.events + competing), database.persistenceDao())
            }
            assertEquals(PersistenceMapper.toRows(before), database.committed)
        }
    }

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
                "readRecords" -> if (snapshot == null) return@proxyWithArguments committed.records
                "readTargets" -> if (snapshot == null) return@proxyWithArguments committed.targets
                "readRecordTargets" -> if (snapshot == null) return@proxyWithArguments committed.recordTargets
                "readStateGroups" -> if (snapshot == null) return@proxyWithArguments committed.stateGroups
                "readEvents" -> if (snapshot == null) return@proxyWithArguments committed.events
                "readBindings" -> if (snapshot == null) return@proxyWithArguments committed.bindings
                "readBindingUndoInvalidations" -> if (snapshot == null) return@proxyWithArguments committed.bindingUndoInvalidations
                "readUndoReceipts" -> if (snapshot == null) return@proxyWithArguments committed.undoReceipts
                "upsertBindings" -> {
                    @Suppress("UNCHECKED_CAST")
                    val rows = arguments!![0] as List<BindingEntity>
                    committed = committed.copy(bindings = (committed.bindings.associateBy { it.bindingId } +
                        rows.associateBy { it.bindingId }).values.toList())
                    return@proxyWithArguments null
                }
                "upsertBindingUndoInvalidations" -> {
                    @Suppress("UNCHECKED_CAST")
                    val rows = arguments!![0] as List<BindingUndoInvalidationEntity>
                    committed = committed.copy(bindingUndoInvalidations = (committed.bindingUndoInvalidations.associateBy { it.bindingId to it.receiptId } +
                        rows.associateBy { it.bindingId to it.receiptId }).values.toList())
                    return@proxyWithArguments null
                }
                "readMetadataRows" -> if (snapshot == null) return@proxyWithArguments committed.metadata
                "readRecord" -> return@proxyWithArguments committed.records.singleOrNull { it.recordId == arguments!![0] }
                "readTarget" -> return@proxyWithArguments committed.targets.singleOrNull { it.targetId == arguments!![0] }
                "readRecordTarget" -> return@proxyWithArguments committed.recordTargets.singleOrNull {
                    it.recordId == arguments!![0] && it.targetId == arguments[1]
                }
                "upsertRecordTargets" -> {
                    @Suppress("UNCHECKED_CAST")
                    val rows = arguments!![0] as List<RecordTargetEntity>
                    committed = committed.copy(recordTargets = (committed.recordTargets.associateBy { it.recordId to it.targetId } +
                        rows.associateBy { it.recordId to it.targetId }).values.toList())
                    return@proxyWithArguments null
                }
                "readStateScopes" -> if (snapshot == null) return@proxyWithArguments committed.stateScopes
                "upsertStateGroups" -> {
                    @Suppress("UNCHECKED_CAST")
                    val rows = arguments!![0] as List<StateGroupEntity>
                    committed = committed.copy(stateGroups = (committed.stateGroups.associateBy { it.stateGroupId } +
                        rows.associateBy { it.stateGroupId }).values.toList())
                    return@proxyWithArguments null
                }
                "upsertStateScopes" -> {
                    @Suppress("UNCHECKED_CAST")
                    val rows = arguments!![0] as List<StateScopeEntity>
                    committed = committed.copy(stateScopes = (committed.stateScopes.associateBy { it.stateGroupId to it.targetScopeKey } +
                        rows.associateBy { it.stateGroupId to it.targetScopeKey }).values.toList())
                    return@proxyWithArguments null
                }
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

    private fun stateAggregate(): DomainState {
        val group = StateGroupId("group")
        val other = StateGroupId("other")
        val target = TargetId("no-target")
        val records = listOf(
            Record(RecordId("state-a"), "A", null, Behavior.STATE, stateGroupId = group, hasEvents = true),
            Record(RecordId("state-b"), "B", null, Behavior.STATE, stateGroupId = group, hasEvents = true),
            Record(RecordId("other-state"), "Other", null, Behavior.STATE, stateGroupId = other, hasEvents = true),
        ).associateBy { it.id }
        fun event(id: String, record: String, scopeTarget: TargetId?, generation: Long, at: Long, sequence: Long) = Event(
            EventId(id), RecordId(record), scopeTarget, Behavior.STATE, EpochMillis(at), EpochMillis(1), EpochMillis(2),
            Sequence(sequence), Source.APP, Revision(3), EventSnapshot("historic $record", null,
                if (scopeTarget == null) null else "historic target", null, Behavior.STATE, null),
            EventPayload.State(records.getValue(RecordId(record)).stateGroupId!!, DatasetGeneration(generation)),
        )
        return DomainState(records = records, targets = mapOf(target to Target(target, "Target", null)),
            stateGroups = mapOf(group to StateGroup(group, "Group", Revision(4)), other to StateGroup(other, "Other", Revision(5))),
            events = listOf(event("target", "state-a", target, 0, 100, 5),
                event("other", "other-state", null, 0, 100, 6),
                event("active-high-sequence", "state-a", null, 2, 109, 7),
                event("active-first", "state-a", null, 2, 110, 2),
                event("active-latest", "state-b", null, 2, 110, 3),
                event("stale-newer", "state-a", null, 1, 999, 1)),
            nextSequence = Sequence(20),
            currentStates = mapOf(StateScope(group, null) to RecordId("state-b"), StateScope(group, target) to RecordId("state-a"),
                StateScope(other, null) to RecordId("other-state")),
            stateGenerations = mapOf(StateScope(group, null) to DatasetGeneration(2), StateScope(group, target) to DatasetGeneration(0),
                StateScope(other, null) to DatasetGeneration(0)))
    }

    private fun durationAggregate(): DomainState {
        val record = Record(RecordId("duration"), "current duration", null, Behavior.DURATION, hasEvents = true)
        val target = Target(TargetId("no-target"), "current target", null)
        fun event(sequence: Long, targetId: TargetId?, payload: EventPayload.Duration) = Event(
            EventId("duration-$sequence"), record.id, targetId, Behavior.DURATION, EpochMillis(100),
            EpochMillis(-100), EpochMillis(200), Sequence(sequence), Source.NFC, Revision(3),
            EventSnapshot("historic duration", "old", if (targetId == null) null else "historic target", null,
                Behavior.DURATION, null), payload,
        )
        return DomainState(records = mapOf(record.id to record), targets = mapOf(target.id to target),
            events = listOf(
                event(1, null, EventPayload.Duration(EpochMillis(-50))),
                event(2, target.id, EventPayload.Duration(EpochMillis(-50))),
                event(3, null, EventPayload.Duration(EpochMillis(10), EpochMillis(60), DurationStatus.COMPLETED)),
                event(4, null, EventPayload.Duration(EpochMillis(20), null, DurationStatus.INCOMPLETE, "record-archived")),
            ), nextSequence = Sequence(10))
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
