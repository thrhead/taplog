package io.github.thrhead.taplog.data.persistence

import io.github.thrhead.taplog.core.domain.DatasetGeneration
import io.github.thrhead.taplog.core.domain.Behavior
import io.github.thrhead.taplog.core.domain.BindingId
import io.github.thrhead.taplog.core.domain.BindingLifecycle
import io.github.thrhead.taplog.core.domain.DisplaySnapshot
import io.github.thrhead.taplog.core.domain.Record
import io.github.thrhead.taplog.core.domain.RecordId
import io.github.thrhead.taplog.core.domain.ReceiptId
import io.github.thrhead.taplog.core.domain.ResultReason
import io.github.thrhead.taplog.core.domain.Sequence
import io.github.thrhead.taplog.core.domain.UndoInvalidation
import io.github.thrhead.taplog.core.engine.CommitOperation
import io.github.thrhead.taplog.core.engine.DomainState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TestFixturesTest {
    @Test
    fun successfulCommitPublishesTheCompleteState() {
        val persistence = InMemoryLocalPersistence()
        val before = persistence.read()
        val updated = before.copy(generation = DatasetGeneration(3), nextSequence = Sequence(8))

        assertTrue(persistence.commit(CommitOperation(before, updated)))
        assertEquals(updated, persistence.read())
    }

    @Test
    fun staleCommitCannotOverwriteANewerState() {
        val persistence = InMemoryLocalPersistence()
        val before = persistence.read()
        val newer = before.copy(nextSequence = Sequence(2))
        assertTrue(persistence.commit(CommitOperation(before, newer)))

        assertFalse(persistence.commit(CommitOperation(before, before.copy(nextSequence = Sequence(3)))))
        assertEquals(newer, persistence.read())
    }

    @Test
    fun injectedFailureLeavesOldStateAndOnlyRejectsOneCommit() {
        val persistence = InMemoryLocalPersistence()
        val before = persistence.read()
        val updated = before.copy(nextSequence = Sequence(2))
        persistence.failNextCommit()

        assertFalse(persistence.commit(CommitOperation(before, updated)))
        assertEquals(before, persistence.read())
        assertTrue(persistence.commit(CommitOperation(before, updated)))
        assertEquals(updated, persistence.read())
    }

    @Test
    fun changingInitialCollectionsDoesNotChangeCommittedState() {
        val record = Record(RecordId("moment"), "Moment", null, Behavior.MOMENT)
        val records = mutableMapOf(record.id to record)
        val persistence = InMemoryLocalPersistence(DomainState(records = records))

        records.clear()

        assertEquals(setOf(RecordId("moment")), persistence.read().records.keys)
    }

    @Test
    fun changingCommitCollectionsDoesNotChangeCommittedState() {
        val record = Record(RecordId("moment"), "Moment", null, Behavior.MOMENT)
        val records = mutableMapOf(record.id to record)
        val persistence = InMemoryLocalPersistence()
        assertTrue(persistence.commit(CommitOperation(persistence.read(), DomainState(records = records))))

        records.clear()

        assertEquals(setOf(RecordId("moment")), persistence.read().records.keys)
    }

    @Test
    fun readsDoNotExposeStoredMaps() {
        val record = Record(RecordId("moment"), "Moment", null, Behavior.MOMENT)
        val binding = BindingLifecycle(
            BindingId("binding"), record.id, null,
            lastKnownDisplay = DisplaySnapshot("Moment", null, null, null),
            undoInvalidations = mutableListOf(),
        )
        val persistence = InMemoryLocalPersistence(DomainState(
            records = mutableMapOf(record.id to record),
            bindings = mutableMapOf(binding.bindingId to binding),
        ))
        val read = persistence.read()

        (read.records as MutableMap).clear()
        (read.bindings as MutableMap).clear()

        assertEquals(setOf(RecordId("moment")), persistence.read().records.keys)
        assertEquals(setOf(BindingId("binding")), persistence.read().bindings.keys)
    }

    @Test
    fun readsDoNotExposeNestedBindingLists() {
        val invalidation = UndoInvalidation(ReceiptId("receipt"), ResultReason.STALE_REVISION)
        val binding = BindingLifecycle(
            BindingId("binding"), RecordId("moment"), null,
            lastKnownDisplay = DisplaySnapshot("Moment", null, null, null),
            undoInvalidations = mutableListOf(invalidation),
        )
        val persistence = InMemoryLocalPersistence(DomainState(bindings = mapOf(binding.bindingId to binding)))

        (persistence.read().bindings.getValue(binding.bindingId).undoInvalidations as MutableList).clear()

        assertEquals(
            listOf(UndoInvalidation(ReceiptId("receipt"), ResultReason.STALE_REVISION)),
            persistence.read().bindings.getValue(binding.bindingId).undoInvalidations,
        )
    }
}
