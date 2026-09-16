package io.github.thrhead.taplog.core.engine

import io.github.thrhead.taplog.core.domain.Behavior
import io.github.thrhead.taplog.core.domain.EpochMillis
import io.github.thrhead.taplog.core.domain.Event
import io.github.thrhead.taplog.core.domain.EventId
import io.github.thrhead.taplog.core.domain.EventPayload
import io.github.thrhead.taplog.core.domain.EventSnapshot
import io.github.thrhead.taplog.core.domain.Lifecycle
import io.github.thrhead.taplog.core.domain.RecordId
import io.github.thrhead.taplog.core.domain.Revision
import io.github.thrhead.taplog.core.domain.ResultReason
import io.github.thrhead.taplog.core.domain.Sequence
import java.util.UUID

class EventEngine(private val boundary: AtomicCommitBoundary, private val clock: AcceptanceClock,
    private val idGenerator: () -> EventId = { EventId(UUID.randomUUID().toString()) }) {
    fun apply(command: Command): EngineResult {
        val before = boundary.read()
        val recordId = when (command) { is LogMoment -> command.recordId; is AddCounter -> command.recordId }
        val record = before.records[recordId]
            ?: return EngineResult.Invalid(ResultReason.INACTIVE_SCOPE)
        if (record.lifecycle != Lifecycle.ACTIVE) return EngineResult.Invalid(ResultReason.INACTIVE_SCOPE)
        val targetId = when (command) { is LogMoment -> command.targetId; is AddCounter -> command.targetId }
        if (targetId != null && (before.targets[targetId]?.lifecycle != Lifecycle.ACTIVE ||
                before.relationships[record.id to targetId]?.linked != true))
            return EngineResult.Invalid(if (before.relationships[record.id to targetId]?.linked == false) ResultReason.UNLINKED_RELATIONSHIP else ResultReason.INACTIVE_SCOPE)
        val occurred = when (command) { is LogMoment -> command.occurredAt; is AddCounter -> command.occurredAt } ?: clock.now()
        val payload = when (command) {
            is LogMoment -> if (record.behavior != Behavior.MOMENT) return EngineResult.Invalid(ResultReason.BEHAVIOR_MISMATCH) else EventPayload.Moment
            is AddCounter -> {
                if (record.behavior != Behavior.COUNTER) return EngineResult.Invalid(ResultReason.BEHAVIOR_MISMATCH)
                val q = command.quantity ?: record.defaultQuantity ?: return EngineResult.Invalid(ResultReason.INVALID_QUANTITY)
                if (!q.isValid || q.value.signum() <= 0) return EngineResult.Invalid(ResultReason.INVALID_QUANTITY)
                if (record.unit?.isValid == false) return EngineResult.Invalid(ResultReason.UNIT_MISMATCH)
                EventPayload.Counter(q, record.unit)
            }
        }
        val target = targetId?.let { before.targets[it] }
        val now = clock.now(); val event = Event(idGenerator(), record.id, targetId, record.behavior, occurred, now, now,
            before.nextSequence, command.source, Revision(0), EventSnapshot(record.name, record.icon, target?.name, target?.icon, record.behavior, record.unit), payload)
        val updatedRecord = record.copy(hasEvents = true, revision = Revision(record.revision.value + 1))
        val updated = before.copy(records = before.records + (record.id to updatedRecord),
            events = before.events + event, nextSequence = Sequence(before.nextSequence.value + 1))
        return if (boundary.commit(CommitOperation(updated))) EngineResult.Applied(listOf(event.id), mapOf(record.id.value to updatedRecord.revision)) else EngineResult.StorageFailure
    }
}
