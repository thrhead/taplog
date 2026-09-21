package io.github.thrhead.taplog.data.persistence

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.thrhead.taplog.core.domain.Behavior
import io.github.thrhead.taplog.core.domain.EpochMillis
import io.github.thrhead.taplog.core.domain.Record
import io.github.thrhead.taplog.core.domain.RecordId
import io.github.thrhead.taplog.core.domain.Source
import io.github.thrhead.taplog.core.engine.AcceptanceClock
import io.github.thrhead.taplog.core.engine.CommitOperation
import io.github.thrhead.taplog.core.engine.DomainState
import io.github.thrhead.taplog.core.engine.EngineResult
import io.github.thrhead.taplog.core.engine.EventEngine
import io.github.thrhead.taplog.core.engine.LogMoment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class OfflineLocalPersistenceTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun roomLocalPersistenceReopensOneThousandOfflineEventsWithoutExternalServices() = withName { name ->
        val record = Record(RecordId("offline"), "Offline", null, Behavior.MOMENT)
        val initial = DomainState(records = mapOf(record.id to record))
        withDatabase(name) { database ->
            val boundary = RoomAtomicCommitBoundary(database)
            assertTrue(boundary.commit(CommitOperation(boundary.read(), initial)))
            val engine = EventEngine(boundary, FixedClock)
            repeat(1_000) { index ->
                assertTrue(engine.apply(LogMoment(record.id, occurredAt = EpochMillis(index.toLong()), source = Source.APP))
                    is EngineResult.Applied)
            }
            assertEquals(1_000, boundary.read().events.size)
        }
        withDatabase(name) { database ->
            assertEquals(1_000, RoomAtomicCommitBoundary(database).read().events.size)
        }
    }

    private object FixedClock : AcceptanceClock {
        override fun now() = EpochMillis(1_000)
    }

    private fun withDatabase(name: String, block: (TapLogDatabase) -> Unit) {
        val database = TapLogDatabase.builder(context, name).allowMainThreadQueries().build()
        try { block(database) } finally { database.close() }
    }

    private fun withName(block: (String) -> Unit) {
        val name = "taplog-offline-${UUID.randomUUID()}.db"
        try { block(name) } finally { context.deleteDatabase(name) }
    }
}
