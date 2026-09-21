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
    fun permanentDeletionReopenRetainsTargetsAndUnrelatedNoTargetHistory() = withDatabaseName { name ->
        val before = aggregate()
        val expected = before.copy(
            relationships = before.relationships - (RecordId("counter") to TargetId("target")),
            events = before.events.filterNot { it.id == EventId("event-2") },
            bindings = emptyMap(),
        )
        withDatabase(name) { database ->
            seed(database, before)
            database.runInTransaction {
                RoomLocalPersistence(database).writePermanentDeletion(expected, database.persistenceDao())
            }
            assertEquals(expected, RoomLocalPersistence(database).read())
            assertEquals(3, database.persistenceDao().readEvents().size)
            assertEquals(null, database.persistenceDao().readEvent("event-2"))
            assertEquals(0, database.persistenceDao().readRecordTargets().size)
            assertEquals(1, database.persistenceDao().readTargets().size)
        }
        withDatabase(name) { database ->
            assertEquals(expected, RoomLocalPersistence(database).read())
            assertEquals(setOf("event-1", "event-3", "event-4"), database.persistenceDao().readEvents().map { it.eventId }.toSet())
            assertEquals("target", database.persistenceDao().readTargets().single().targetId)
        }
    }

    // Catches lost receipt context or unwanted Event FK constraints across normal reopen.
    @Test
    fun stagedUndoMetadataRetainsDeletedEventContextAndOmittedReceiptsAcrossReopen() = withDatabaseName { name ->
        val base = aggregate()
        val receipt = base.undoReceipts.values.single()
        val added = receipt.copy(receiptId = ReceiptId("retained-deleted"), eventId = EventId("deleted-event"), scope = null)
        val supplied = base.copy(undoReceipts = mapOf(added.receiptId to added))
        withDatabase(name) { database ->
            seed(database, base)
            val dao = database.persistenceDao()
            val history = dao.readEvents()
            val bindings = dao.readBindings()
            database.runInTransaction { RoomLocalPersistence(database).writeUndoMetadata(supplied, dao) }
            assertEquals(history, dao.readEvents())
            assertEquals(bindings, dao.readBindings())
            assertEquals(UndoReceiptEntity("retained-deleted", "deleted-event", 3, 7), dao.readUndoReceipt("retained-deleted"))
        }
        withDatabase(name) { database ->
            val expected = base.copy(undoReceipts = base.undoReceipts + (added.receiptId to added))
            val persistence = RoomLocalPersistence(database)
            assertEquals(expected, persistence.read())
            database.runInTransaction {
                persistence.writeUndoMetadata(supplied, database.persistenceDao())
                persistence.writeUndoMetadata(DomainState(), database.persistenceDao())
            }
            assertEquals(expected, persistence.read())
        }
    }

    // Catches transaction ownership moving into the partial Undo metadata phase.
    @Test
    fun callerTransactionRollsBackUndoReceiptAndInvalidationUpserts() = withDatabaseName { name ->
        val base = aggregate()
        val binding = base.bindings.values.single()
        val receipt = base.undoReceipts.values.single().copy(receiptId = ReceiptId("new"), eventId = EventId("absent"))
        val changed = base.copy(undoReceipts = mapOf(receipt.receiptId to receipt),
            bindings = mapOf(binding.bindingId to binding.copy(revision = Revision(6),
                undoInvalidations = listOf(UndoInvalidation(ReceiptId("another"), ResultReason.STALE_DATASET_GENERATION)))))
        withDatabase(name) { database ->
            seed(database, base)
            assertThrows(IllegalStateException::class.java) {
                database.runInTransaction {
                    RoomLocalPersistence(database).writeUndoMetadata(changed, database.persistenceDao())
                    error("Abort after Undo metadata phase")
                }
            }
            assertEquals(base, RoomLocalPersistence(database).read())
        }
        withDatabase(name) { assertEquals(base, RoomLocalPersistence(it).read()) }
    }

    // Catches lost snapshots/child rows across SQLite reopen and binding retargeting.
    @Test
    fun stagedBindingOrphaningRetainsSnapshotAndInvalidationAcrossReopen() = withDatabaseName { name ->
        val expected = aggregate()
        val binding = expected.bindings.values.single()
        val active = binding.copy(status = BindingStatus.ACTIVE, revision = Revision(4), undoInvalidations = emptyList())
        withDatabase(name) { database ->
            seed(database, expected.copy(bindings = mapOf(active.bindingId to active)))
            val dao = database.persistenceDao()
            val definitions = dao.readRecords()
            val history = dao.readEvents()
            database.runInTransaction { RoomLocalPersistence(database).writeBindings(expected, dao) }
            assertEquals(definitions, dao.readRecords())
            assertEquals(history, dao.readEvents())
            assertEquals(BindingEntity("binding", "counter", "target", "ORPHANED", 5, "old binding", null, "old target", null), dao.readBinding("binding"))
            assertEquals(listOf(BindingUndoInvalidationEntity("binding", "removed", "STALE_REVISION")), dao.readBindingUndoInvalidations())
        }
        withDatabase(name) { database ->
            val persistence = RoomLocalPersistence(database)
            assertEquals(expected, persistence.read())
            database.runInTransaction {
                persistence.writeBindings(expected, database.persistenceDao())
                persistence.writeBindings(DomainState(), database.persistenceDao())
            }
            assertThrows(MappingFailure::class.java) {
                database.runInTransaction {
                    persistence.writeBindings(expected.copy(bindings = mapOf(binding.bindingId to
                        binding.copy(targetId = null, revision = Revision(6)))), database.persistenceDao())
                }
            }
            assertEquals(expected, persistence.read())
        }
    }

    // Catches transaction ownership moving into a partial binding/child-metadata phase.
    @Test
    fun callerTransactionRollsBackBindingAndInvalidationUpserts() = withDatabaseName { name ->
        val expected = aggregate()
        val active = expected.copy(bindings = expected.bindings.mapValues { (_, binding) ->
            binding.copy(status = BindingStatus.ACTIVE, revision = Revision(4), undoInvalidations = emptyList()) })
        withDatabase(name) { database ->
            seed(database, active)
            assertThrows(IllegalStateException::class.java) {
                database.runInTransaction {
                    RoomLocalPersistence(database).writeBindings(expected, database.persistenceDao())
                    error("Abort after binding phase")
                }
            }
            assertEquals(active, RoomLocalPersistence(database).read())
        }
        withDatabase(name) { assertEquals(active, RoomLocalPersistence(it).read()) }
    }

    // Catches unlink cascades and scope broadening across normal Room close/reopen.
    @Test
    fun stagedRelationshipUnlinkRetainsDefinitionsAndExactHistoricalScopesAcrossReopen() = withDatabaseName { name ->
        val base = aggregate()
        val pair = base.relationships.keys.single()
        val otherTarget = Target(TargetId("other"), "Other", null)
        val otherPair = pair.first to otherTarget.id
        val sharedPair = RecordId("moment") to pair.second
        val counterHistory = base.events[1]
        val before = base.copy(targets = base.targets + (otherTarget.id to otherTarget), relationships = mapOf(
            pair to RecordTarget(pair.first, pair.second, true, Revision(4)),
            otherPair to RecordTarget(otherPair.first, otherPair.second, true, Revision(3)),
            sharedPair to RecordTarget(sharedPair.first, sharedPair.second, true, Revision(2))),
            events = base.events + counterHistory.copy(id = EventId("other-event"), targetId = otherTarget.id,
                sequence = Sequence(5), snapshot = counterHistory.snapshot.copy(targetName = "historical other")))
        val unlinked = before.copy(relationships = before.relationships +
            (pair to before.relationships.getValue(pair).copy(linked = false, revision = Revision(5))))
        withDatabase(name) { database ->
            seed(database, before)
            val dao = database.persistenceDao()
            val events = dao.readEvents()
            val records = dao.readRecords()
            val targets = dao.readTargets()
            database.runInTransaction { RoomLocalPersistence(database).writeRelationships(unlinked, dao) }
            assertEquals(events, dao.readEvents())
            assertEquals(records, dao.readRecords())
            assertEquals(targets, dao.readTargets())
            assertEquals(RecordTargetEntity("counter", "target", false, 5), dao.readRecordTarget("counter", "target"))
            assertEquals(listOf("event-2"), dao.readScopeEvents("counter", "target:target").map { it.eventId })
            assertEquals(listOf("other-event"), dao.readScopeEvents("counter", "target:other").map { it.eventId })
            assertEquals(listOf("event-1"), dao.readScopeEvents("moment", "no-target").map { it.eventId })
        }
        withDatabase(name) { database ->
            val persistence = RoomLocalPersistence(database)
            assertEquals(unlinked, persistence.read())
            database.runInTransaction {
                persistence.writeRelationships(unlinked, database.persistenceDao())
                persistence.writeRelationships(DomainState(), database.persistenceDao())
            }
            assertEquals(unlinked, persistence.read())
        }
    }

    // Catches ownership of the transaction moving into the staged phase.
    @Test
    fun callerTransactionRollsBackStagedRelationshipUnlink() = withDatabaseName { name ->
        val before = aggregate().let { it.copy(relationships = it.relationships.mapValues { (_, pair) -> pair.copy(linked = true) }) }
        val unlinked = before.copy(relationships = before.relationships.mapValues { (_, pair) -> pair.copy(linked = false, revision = Revision(5)) })
        withDatabase(name) { database ->
            seed(database, before)
            assertThrows(IllegalStateException::class.java) {
                database.runInTransaction {
                    RoomLocalPersistence(database).writeRelationships(unlinked, database.persistenceDao())
                    error("Abort after relationship phase")
                }
            }
            assertEquals(before, RoomLocalPersistence(database).read())
        }
        withDatabase(name) { assertEquals(before, RoomLocalPersistence(it).read()) }
    }

    // Catches revision regression or unsupported metadata overwriting an otherwise valid relationship batch.
    @Test
    fun invalidRelationshipRevisionsAndStoredMetadataFailWithoutChangingRows() = withDatabaseName { name ->
        val before = aggregate()
        withDatabase(name) { database ->
            seed(database, before)
            val dao = database.persistenceDao()
            val stored = dao.readRecordTargets()
            val pair = before.relationships.keys.single()
            val relationship = before.relationships.getValue(pair)
            listOf(relationship.copy(revision = Revision(3)), relationship.copy(linked = true),
                relationship.copy(revision = Revision(-1))).forEach { invalid ->
                assertThrows(MappingFailure::class.java) {
                    database.runInTransaction {
                        RoomLocalPersistence(database).writeRelationships(before.copy(relationships = mapOf(pair to invalid)), dao)
                    }
                }
                assertEquals(stored, dao.readRecordTargets())
            }
            dao.upsertMetadata(listOf(DatasetMetadataEntity(1, 7, 20, 2)))
            assertThrows(MappingFailure::class.java) {
                database.runInTransaction { RoomLocalPersistence(database).writeRelationships(before, dao) }
            }
            assertEquals(stored, dao.readRecordTargets())
            assertEquals(listOf(DatasetMetadataEntity(1, 7, 20, 2)), dao.readMetadataRows())
        }
    }

    // Catches scope collisions, wrong chronology/generation selection, or history loss on scope updates/reopen.
    @Test
    fun stagedStateScopesRecoverExclusivityAndRetainStaleHistoryAcrossNormalReopen() = withDatabaseName { name ->
        val expected = stateAggregate()
        val groupScope = StateScope(StateGroupId("group"), null)
        withDatabase(name) { database ->
            database.runInTransaction {
                val dao = database.persistenceDao()
                val persistence = RoomLocalPersistence(database)
                persistence.writeStateGroupsAndScopes(expected.copy(
                    stateGenerations = expected.stateGenerations.filterValues { it != DatasetGeneration(0) }), dao)
                persistence.writeRecordsAndTargets(expected, dao)
                persistence.writeEvents(expected.copy(events = expected.events.reversed()), dao)
                dao.upsertMetadata(PersistenceMapper.toRows(expected).metadata)
            }
            assertEquals(expected, RoomLocalPersistence(database).read())
        }
        val advanced = expected.copy(stateGenerations = expected.stateGenerations + (groupScope to DatasetGeneration(3)),
            currentStates = expected.currentStates - groupScope)
        withDatabase(name) { database ->
            val dao = database.persistenceDao()
            val persistence = RoomLocalPersistence(database)
            assertEquals(expected, persistence.read())
            assertEquals("active-latest", dao.readCurrentState("group", "no-target")!!.eventId)
            assertEquals("target", dao.readCurrentState("group", "target:no-target")!!.eventId)
            assertEquals("other", dao.readCurrentState("other", "no-target")!!.eventId)
            assertEquals(null, dao.readCurrentState("other", "target:no-target"))
            assertEquals(listOf("active-high-sequence", "active-first", "stale-newer"),
                dao.readScopeEvents("state-a", "no-target").map { it.eventId })
            assertEquals(listOf("active-latest"), dao.readScopeEvents("state-b", "no-target").map { it.eventId })
            database.runInTransaction { persistence.writeStateGroupsAndScopes(advanced, dao) }
            assertEquals(advanced, persistence.read())
            assertEquals(null, dao.readCurrentState("group", "no-target"))
            assertEquals(6, dao.readEvents().size)
            assertEquals(3, dao.readStateScopes().size)
        }
        val replacement = expected.events[4].copy(id = EventId("replacement"), recordId = RecordId("state-a"),
            occurredAt = EpochMillis(1000), sequence = Sequence(8), payload = EventPayload.State(StateGroupId("group"), DatasetGeneration(3)))
        val replaced = advanced.copy(events = advanced.events + replacement,
            currentStates = advanced.currentStates + (groupScope to RecordId("state-a")))
        withDatabase(name) { database ->
            val dao = database.persistenceDao()
            val persistence = RoomLocalPersistence(database)
            assertEquals(advanced, persistence.read())
            database.runInTransaction {
                persistence.writeStateGroupsAndScopes(replaced, dao)
                persistence.writeEvents(replaced, dao)
            }
        }
        withDatabase(name) { database ->
            val dao = database.persistenceDao()
            assertEquals(replaced, RoomLocalPersistence(database).read())
            assertEquals("replacement", dao.readCurrentState("group", "no-target")!!.eventId)
            assertEquals("target", dao.readCurrentState("group", "target:no-target")!!.eventId)
            assertEquals("other", dao.readCurrentState("other", "no-target")!!.eventId)
            assertEquals(7, dao.readEvents().size)
            assertEquals(3, dao.readStateScopes().size)
        }
    }

    // Catches silent reset-field loss in the staged writer and repair during normal reopen.
    @Test
    fun unsupportedStateResetMetadataFailsClosedWithoutChangingStoredStateFamilies() = withDatabaseName { name ->
        val state = stateAggregate()
        withDatabase(name) { database ->
            seed(database, state)
            val dao = database.persistenceDao()
            val scope = dao.readStateScope("group", "no-target")!!
            dao.upsertStateScopes(listOf(scope.copy(resetSequence = 9, resetAt = 100)))
        }
        repeat(2) {
            withDatabase(name) { database ->
                val dao = database.persistenceDao()
                val groups = dao.readStateGroups()
                val scopes = dao.readStateScopes()
                val events = dao.readEvents()
                val persistence = RoomLocalPersistence(database)
                assertThrows(MappingFailure::class.java) { persistence.read() }
                assertThrows(MappingFailure::class.java) {
                    database.runInTransaction { persistence.writeStateGroupsAndScopes(state.copy(
                        stateGroups = state.stateGroups.mapValues { (_, group) -> group.copy(name = "must not be written") }), dao) }
                }
                assertEquals(groups, dao.readStateGroups())
                assertEquals(scopes, dao.readStateScopes())
                assertEquals(events, dao.readEvents())
                assertEquals(9L, dao.readStateScope("group", "no-target")!!.resetSequence)
                assertEquals(100L, dao.readStateScope("group", "no-target")!!.resetAt)
            }
        }
    }

    // Catches a phase opening/publishing its own transaction instead of participating in the caller's.
    @Test
    fun callerAbortedStateGroupAndScopeWritesDoNotSurviveReopen() = withDatabaseName { name ->
        val state = stateAggregate()
        withDatabase(name) { database ->
            seed(database, state)
            assertThrows(IllegalStateException::class.java) {
                database.runInTransaction {
                    RoomLocalPersistence(database).writeStateGroupsAndScopes(state.copy(
                        stateGroups = state.stateGroups.mapValues { (_, group) -> group.copy(name = "edited") },
                        stateGenerations = state.stateGenerations + (StateScope(StateGroupId("group"), null) to DatasetGeneration(3)),
                        currentStates = state.currentStates - StateScope(StateGroupId("group"), null)), database.persistenceDao())
                    error("caller abort")
                }
            }
            assertEquals(state, RoomLocalPersistence(database).read())
        }
        withDatabase(name) { assertEquals(state, RoomLocalPersistence(it).read()) }
    }

    // Catches scope-key collisions, index loss on normal identity-hash reopen, and partial staged writes.
    @Test
    fun stagedDurationScopesRecoverAndStoredOpenConflictsRollBackAfterReopen() = withDatabaseName { name ->
        val expected = durationAggregate()
        withDatabase(name) { database ->
            seed(database, expected.copy(events = emptyList()))
            database.runInTransaction {
                RoomLocalPersistence(database).writeEvents(expected, database.persistenceDao())
            }
            assertEquals(expected, RoomLocalPersistence(database).read())
        }
        withDatabase(name) { database ->
            val dao = database.persistenceDao()
            val persistence = RoomLocalPersistence(database)
            assertEquals(expected, persistence.read())
            assertEquals("duration-1", dao.readOpenDuration("duration", "no-target")!!.eventId)
            assertEquals("duration-2", dao.readOpenDuration("duration", "target:no-target")!!.eventId)
            expected.events.take(2).forEach { open ->
                val editedTerminal = expected.events[3].copy(updatedAt = EpochMillis(999), revision = Revision(9))
                val competing = expected.events[2].copy(targetId = open.targetId,
                    snapshot = open.snapshot, payload = open.payload)
                // Existing-ID update reaches SQLite's index; a new-ID @Upsert can swallow insert conflicts.
                // Each input itself has one OPEN; the index must reject the stored competitor.
                assertThrows(android.database.sqlite.SQLiteConstraintException::class.java) {
                    database.runInTransaction {
                        persistence.writeEvents(expected.copy(events = listOf(editedTerminal)), dao)
                        persistence.writeEvents(expected.copy(events = listOf(competing)), dao)
                    }
                }
                assertEquals(expected, persistence.read())
            }
        }
        withDatabase(name) { assertEquals(expected, RoomLocalPersistence(it).read()) }
    }

    // Catches fabricated end times, terminal reopening, history deletion, and failure to free an OPEN scope.
    @Test
    fun suppliedDurationTerminationsAndReplacementOpensSurviveRestartWithoutChangingHistory() = withDatabaseName { name ->
        val before = durationAggregate()
        val completed = before.events[0].copy(updatedAt = EpochMillis(900), revision = Revision(4),
            payload = EventPayload.Duration(EpochMillis(-50), EpochMillis(700), DurationStatus.COMPLETED))
        val incomplete = before.events[1].copy(updatedAt = EpochMillis(901), revision = Revision(5),
            payload = EventPayload.Duration(EpochMillis(-50), null, DurationStatus.INCOMPLETE, "target-unlinked"))
        val terminals = before.copy(events = listOf(completed, incomplete) + before.events.drop(2))
        val replacementOpens = before.events.take(2).mapIndexed { index, event ->
            event.copy(id = EventId("replacement-$index"), sequence = Sequence(5L + index),
                occurredAt = EpochMillis(1000), payload = EventPayload.Duration(EpochMillis(1000)))
        }
        val expected = terminals.copy(events = terminals.events + replacementOpens)
        withDatabase(name) { database ->
            seed(database, before)
            database.runInTransaction {
                RoomLocalPersistence(database).writeEvents(before.copy(events = listOf(completed, incomplete)), database.persistenceDao())
            }
        }
        withDatabase(name) { database ->
            val dao = database.persistenceDao()
            val persistence = RoomLocalPersistence(database)
            assertEquals(terminals, persistence.read())
            assertEquals(null, dao.readOpenDuration("duration", "no-target"))
            assertEquals(null, dao.readOpenDuration("duration", "target:no-target"))
            assertEquals(listOf(700L, null, 60L, null), dao.readEvents().map { it.durationEndAt })
            assertEquals(listOf(null, "target-unlinked", null, "record-archived"), dao.readEvents().map { it.durationIncompleteReason })
            database.runInTransaction { persistence.writeEvents(expected.copy(events = replacementOpens), dao) }
            assertEquals(expected, persistence.read())
        }
        withDatabase(name) { database ->
            val persistence = RoomLocalPersistence(database)
            assertEquals(expected, persistence.read())
            database.runInTransaction { persistence.writeEvents(expected.copy(events = emptyList()), database.persistenceDao()) }
            assertEquals(expected, persistence.read())
            assertEquals("replacement-0", database.persistenceDao().readOpenDuration("duration", "no-target")!!.eventId)
            assertEquals("replacement-1", database.persistenceDao().readOpenDuration("duration", "target:no-target")!!.eventId)
        }
    }

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
