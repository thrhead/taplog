package io.github.thrhead.taplog.data.persistence

import io.github.thrhead.taplog.core.engine.CommitOperation
import io.github.thrhead.taplog.core.engine.DomainState
import io.github.thrhead.taplog.core.domain.Sequence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors

class FailureInjectionTest {
    @Test
    fun twentyEventCommitsFailWithoutPartialState() = repeatFailures { before, updated ->
        updated.copy(nextSequence = before.nextSequence)
    }

    @Test
    fun twentyRecordAndEventCommitsFailWithoutPartialState() = repeatFailures { before, updated ->
        updated.copy(generation = before.generation)
    }

    @Test
    fun twentyArchiveUnlinkCommitsFailWithoutPartialState() = repeatFailures { before, updated ->
        updated.copy(nextSequence = Sequence(2))
    }

    @Test
    fun twentyPermanentDeleteCommitsFailWithoutPartialState() = repeatFailures { before, updated ->
        updated.copy(records = emptyMap(), events = emptyList())
    }

    @Test
    fun concurrentSameExpectedContextHasExactlyOneWinner() {
        val persistence = InMemoryLocalPersistence()
        val before = persistence.read()
        val start = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(20)
        try {
            val results = (0 until 20).map { index ->
                executor.submit<Boolean> {
                    start.await()
                    persistence.commit(CommitOperation(before, before.copy(nextSequence = Sequence(index.toLong() + 2))))
                }
            }
            start.countDown()
            assertEquals(1, results.count { it.get() })
            assertTrue(persistence.read().nextSequence.value >= 2)
        } finally {
            executor.shutdownNow()
        }
    }

    private fun repeatFailures(transform: (DomainState, DomainState) -> DomainState) {
        repeat(20) {
            val persistence = InMemoryLocalPersistence()
            val before = persistence.read()
            val updated = transform(before, before.copy(nextSequence = Sequence(2)))
            persistence.failNextCommit()
            assertFalse(persistence.commit(CommitOperation(before, updated)))
            assertEquals(before, persistence.read())
            assertTrue(persistence.commit(CommitOperation(before, updated)))
        }
    }
}
