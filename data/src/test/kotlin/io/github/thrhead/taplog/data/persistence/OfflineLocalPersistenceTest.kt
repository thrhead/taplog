package io.github.thrhead.taplog.data.persistence

import io.github.thrhead.taplog.core.domain.Behavior
import io.github.thrhead.taplog.core.domain.EpochMillis
import io.github.thrhead.taplog.core.domain.Record
import io.github.thrhead.taplog.core.domain.RecordId
import io.github.thrhead.taplog.core.domain.Source
import io.github.thrhead.taplog.core.engine.AcceptanceClock
import io.github.thrhead.taplog.core.engine.DomainState
import io.github.thrhead.taplog.core.engine.EventEngine
import io.github.thrhead.taplog.core.engine.LogMoment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineLocalPersistenceTest {
    @Test
    fun coreCommandsRemainOfflineWithOneThousandLocallyCommittedEvents() {
        val record = Record(RecordId("offline"), "Offline", null, Behavior.MOMENT)
        val persistence = InMemoryLocalPersistence(DomainState(records = mapOf(record.id to record)))
        val engine = EventEngine(persistence, FixedClock)

        repeat(1_000) { index ->
            assertTrue(engine.apply(LogMoment(record.id, occurredAt = EpochMillis(index.toLong()), source = Source.APP))
                is io.github.thrhead.taplog.core.engine.EngineResult.Applied)
        }

        assertEquals(1_000, persistence.read().events.size)
    }

    private object FixedClock : AcceptanceClock {
        override fun now() = EpochMillis(1_000)
    }
}
