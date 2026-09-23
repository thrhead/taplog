package io.github.thrhead.taplog.core.engine

import io.github.thrhead.taplog.core.domain.*
import java.util.UUID

class EventEngine(private val boundary: AtomicCommitBoundary, private val clock: AcceptanceClock,
    private val idGenerator: () -> EventId = { EventId(UUID.randomUUID().toString()) }) {
    private data class LifecycleUpdate(val state: DomainState, val effect: LifecycleEffect)

    fun apply(command: Command): EngineResult {
        val before = boundary.read()
        return when (command) {
            is CreateRecord -> createRecord(before, command)
            is CreateTarget -> createTarget(before, command)
            is FinishDuration -> finish(before, command)
            is EditEvent -> editEvent(before, command)
            is DeleteEvent -> deleteEvent(before, command)
            is Undo -> undo(before, command)
            is CreateRecordAndLog -> createRecordAndLog(before, command)
            else -> createOrToggle(before, command)
        }
    }

    private fun createOrToggle(before: DomainState, command: Command): EngineResult {
        val recordId = when (command) {
            is LogMoment -> command.recordId; is AddCounter -> command.recordId
            is StartDuration -> command.recordId; is ToggleDuration -> command.recordId
            is SetState -> command.recordId
            else -> return invalid(ResultReason.INVALID_REQUEST)
        }
        val record = before.records[recordId] ?: return invalid(ResultReason.INACTIVE_SCOPE)
        val targetId = when (command) {
            is LogMoment -> command.targetId; is AddCounter -> command.targetId
            is StartDuration -> command.targetId; is ToggleDuration -> command.targetId
            is SetState -> command.targetId
            else -> null
        }
        validateScope(before, record, targetId, command.expected)?.let { return it }
        return when (command) {
            is LogMoment -> if (record.behavior != Behavior.MOMENT) invalid(ResultReason.BEHAVIOR_MISMATCH)
                else createSimple(before, record, targetId, command.occurredAt, command.source, EventPayload.Moment, Behavior.MOMENT)
            is AddCounter -> if (record.behavior != Behavior.COUNTER) invalid(ResultReason.BEHAVIOR_MISMATCH) else {
                val q = command.quantity ?: record.defaultQuantity
                when {
                    q == null || !q.isValid -> invalid(ResultReason.INVALID_QUANTITY)
                    record.unit?.isValid == false -> invalid(ResultReason.UNIT_MISMATCH)
                    else -> createSimple(before, record, targetId, command.occurredAt, command.source,
                        EventPayload.Counter(q, record.unit), Behavior.COUNTER)
                }
            }
            is StartDuration -> start(before, record, targetId, command.occurredAt, command.source)
            is ToggleDuration -> openDuration(before, record.id, targetId)?.let {
                finish(before, FinishDuration(it.id, command.occurredAt ?: clock.now(), command.source, command.expected))
            } ?: start(before, record, targetId, command.occurredAt, command.source)
            is SetState -> setState(before, record, targetId, command.occurredAt, command.source)
            else -> invalid(ResultReason.INVALID_REQUEST)
        }
    }

    private fun start(before: DomainState, record: Record, targetId: TargetId?, occurredAt: EpochMillis?, source: Source): EngineResult {
        if (record.behavior != Behavior.DURATION) return invalid(ResultReason.BEHAVIOR_MISMATCH)
        if (openDuration(before, record.id, targetId) != null) return invalid(ResultReason.OPEN_DURATION_EXISTS)
        val accepted = clock.now(); val start = occurredAt ?: accepted
        return createSimple(before, record, targetId, start, source, EventPayload.Duration(start), Behavior.DURATION, accepted)
    }

    private fun finish(before: DomainState, command: FinishDuration): EngineResult {
        val existing = before.events.firstOrNull { it.id == command.openEventId }
            ?: return invalid(ResultReason.INVALID_REQUEST)
        val payload = existing.payload as? EventPayload.Duration ?: return invalid(ResultReason.BEHAVIOR_MISMATCH)
        if (payload.status != DurationStatus.OPEN) return invalid(ResultReason.TERMINAL_DURATION)
        if (command.expected?.revision != null && command.expected.revision != existing.revision) return conflict(ResultReason.STALE_REVISION)
        if (command.expected?.datasetGeneration != null && command.expected.datasetGeneration != before.generation)
            return conflict(ResultReason.STALE_DATASET_GENERATION)
        if (command.endedAt < payload.startAt) return invalid(ResultReason.NEGATIVE_DURATION)
        val updated = existing.copy(updatedAt = clock.now(), revision = Revision(existing.revision.value + 1),
            payload = payload.copy(endAt = command.endedAt, status = DurationStatus.COMPLETED))
        return commit(before, before.copy(events = before.events.map { if (it.id == existing.id) updated else it }),
            listOf(existing.id), existing.recordId to updated.revision)
    }

    private fun setState(before: DomainState, record: Record, targetId: TargetId?, occurredAt: EpochMillis?, source: Source): EngineResult {
        if (record.behavior != Behavior.STATE) return invalid(ResultReason.BEHAVIOR_MISMATCH)
        val group = record.stateGroupId ?: return invalid(ResultReason.MISSING_STATE_GROUP)
        if (group !in before.stateGroups) return invalid(ResultReason.MISSING_STATE_GROUP)
        val scope = StateScope(group, targetId)
        val generation = before.stateGenerations[scope] ?: DatasetGeneration(0)
        return createSimple(before, record, targetId, occurredAt, source, EventPayload.State(group, generation), Behavior.STATE) { state, event ->
            val candidates = state.events.filter { (it.payload as? EventPayload.State)?.let { p ->
                p.stateGroupId == group && p.generation == generation && it.targetId == targetId
            } == true }
            val latest = candidates.maxWithOrNull(compareBy<Event>({ it.occurredAt }, { it.sequence }))
            state.copy(currentStates = state.currentStates + (scope to (latest ?: event).recordId))
        }
    }

    private fun createSimple(before: DomainState, record: Record, targetId: TargetId?, occurredAt: EpochMillis?, source: Source,
        payload: EventPayload, behavior: Behavior, acceptedOverride: EpochMillis? = null,
        stateTransform: ((DomainState, Event) -> DomainState)? = null): EngineResult {
        val accepted = acceptedOverride ?: clock.now(); val target = targetId?.let(before.targets::get)
        val event = Event(idGenerator(), record.id, targetId, behavior, occurredAt ?: accepted, accepted, accepted,
            before.nextSequence, source, Revision(0),
            EventSnapshot(record.name, record.icon, target?.name, target?.icon, behavior, record.unit), payload)
        val updatedRecord = record.copy(hasEvents = true, revision = Revision(record.revision.value + 1))
        var state = before.copy(records = before.records + (record.id to updatedRecord), events = before.events + event,
            nextSequence = Sequence(before.nextSequence.value + 1))
        state = stateTransform?.invoke(state, event) ?: state
        return commit(before, state, listOf(event.id), record.id to updatedRecord.revision)
    }

    private fun createRecordAndLog(before: DomainState, command: CreateRecordAndLog): EngineResult {
        if (!command.confirmed) return EngineResult.NeedsConfirmation(ResultReason.CONFIRMATION_REQUIRED)
        if (command.record.id in before.records) return conflict(ResultReason.STALE_REVISION)
        validateTarget(before, command.record.id, command.targetId)?.let { return it }
        when (command.record.behavior) {
            Behavior.COUNTER -> when {
                command.record.defaultQuantity == null || !command.record.defaultQuantity.isValid ->
                    return invalid(ResultReason.INVALID_QUANTITY)
                command.record.unit?.isValid == false -> return invalid(ResultReason.UNIT_MISMATCH)
            }
            Behavior.STATE -> {
                val groupId = command.record.stateGroupId ?: return invalid(ResultReason.MISSING_STATE_GROUP)
                if (groupId !in before.stateGroups) return invalid(ResultReason.MISSING_STATE_GROUP)
            }
            else -> Unit
        }
        val accepted = clock.now(); val record = command.record.copy(hasEvents = true,
            revision = Revision(command.record.revision.value + 1)); val target = command.targetId?.let(before.targets::get)
        val event = Event(idGenerator(), record.id, command.targetId, record.behavior, command.occurredAt ?: accepted,
            accepted, accepted, before.nextSequence, command.source, Revision(0),
            EventSnapshot(record.name, record.icon, target?.name, target?.icon, record.behavior, record.unit),
            defaultPayload(record, command.occurredAt ?: accepted))
        return commit(before, before.copy(records = before.records + (record.id to record), events = before.events + event,
            nextSequence = Sequence(before.nextSequence.value + 1)), listOf(event.id), record.id to record.revision)
    }

    private fun defaultPayload(record: Record, at: EpochMillis): EventPayload = when (record.behavior) {
        Behavior.MOMENT -> EventPayload.Moment
        Behavior.COUNTER -> EventPayload.Counter(record.defaultQuantity ?: Quantity.exact("1")!!, record.unit)
        Behavior.DURATION -> EventPayload.Duration(at)
        Behavior.STATE -> EventPayload.State(record.stateGroupId ?: StateGroupId("missing"), DatasetGeneration(0))
    }

    private fun createRecord(before: DomainState, command: CreateRecord): EngineResult {
        if (command.record.id in before.records) return conflict(ResultReason.STALE_REVISION)
        validateExpected(before, null, command.expected)?.let { return it }
        validateRecordForCreation(before, command.record)?.let { return it }
        val record = DefinitionManagement.create(command.record)
        val updated = before.copy(records = before.records + (record.id to record))
        return commitDefinition(before, updated, record.id.value, record.revision)
    }

    private fun createTarget(before: DomainState, command: CreateTarget): EngineResult {
        if (command.target.id in before.targets) return conflict(ResultReason.STALE_REVISION)
        validateExpected(before, null, command.expected)?.let { return it }
        if (command.target.name.isBlank()) return invalid(ResultReason.INVALID_REQUEST)
        val target = DefinitionManagement.create(command.target)
        val updated = before.copy(targets = before.targets + (target.id to target))
        return commitDefinition(before, updated, target.id.value, target.revision)
    }

    private fun validateRecordForCreation(before: DomainState, record: Record): EngineResult? {
        if (record.name.isBlank()) return invalid(ResultReason.INVALID_REQUEST)
        val defaultQuantity = Quantity.exact("1")
        return when (record.behavior) {
            Behavior.COUNTER -> when {
                record.stateGroupId != null -> invalid(ResultReason.INVALID_REQUEST)
                record.defaultQuantity == null || !record.defaultQuantity.isValid ->
                    invalid(ResultReason.INVALID_QUANTITY)
                record.unit?.isValid == false -> invalid(ResultReason.UNIT_MISMATCH)
                else -> null
            }
            Behavior.STATE -> when {
                record.unit != null || record.defaultQuantity != defaultQuantity ->
                    invalid(ResultReason.INVALID_REQUEST)
                record.stateGroupId == null || record.stateGroupId !in before.stateGroups ->
                    invalid(ResultReason.MISSING_STATE_GROUP)
                else -> null
            }
            Behavior.MOMENT, Behavior.DURATION ->
                if (record.unit != null || record.defaultQuantity != defaultQuantity || record.stateGroupId != null)
                    invalid(ResultReason.INVALID_REQUEST)
                else null
        }
    }

    private fun editEvent(before: DomainState, command: EditEvent): EngineResult {
        val event = before.events.firstOrNull { it.id == command.eventId } ?: return invalid(ResultReason.INVALID_REQUEST)
        if (command.expected?.revision != null && command.expected.revision != event.revision) return conflict(ResultReason.STALE_REVISION)
        if (event.behavior == Behavior.DURATION || event.behavior == Behavior.STATE) return invalid(ResultReason.INVALID_REQUEST)
        val changes = command.changes
        val payload = when (event.behavior) {
            Behavior.MOMENT -> if (changes.quantity != null) return invalid(ResultReason.BEHAVIOR_MISMATCH) else event.payload
            Behavior.COUNTER -> {
                val q = changes.quantity ?: (event.payload as EventPayload.Counter).quantity
                if (!q.isValid) return invalid(ResultReason.INVALID_QUANTITY)
                EventPayload.Counter(q, (event.payload as EventPayload.Counter).unit)
            }
            else -> event.payload
        }
        val updated = event.copy(occurredAt = changes.occurredAt ?: event.occurredAt, updatedAt = clock.now(),
            revision = Revision(event.revision.value + 1), payload = payload)
        return commit(before, before.copy(events = before.events.map { if (it.id == event.id) updated else it }),
            listOf(event.id), event.recordId to updated.revision)
    }

    private fun deleteEvent(before: DomainState, command: DeleteEvent): EngineResult {
        val event = before.events.firstOrNull { it.id == command.eventId } ?: return invalid(ResultReason.INVALID_REQUEST)
        if (!command.confirmed) return EngineResult.NeedsConfirmation(ResultReason.PERMANENT_DELETE_IMPACT)
        if (command.expected?.revision != null && command.expected.revision != event.revision) return conflict(ResultReason.STALE_REVISION)
        val events = before.events.filterNot { it.id == event.id }; val record = before.records.getValue(event.recordId)
        val updatedRecord = record.copy(hasEvents = record.hasEvents || before.events.any { it.recordId == record.id },
            revision = Revision(record.revision.value + 1))
        return commit(before, before.copy(events = events, records = before.records + (record.id to updatedRecord)),
            emptyList(), record.id to updatedRecord.revision)
    }

    private fun undo(before: DomainState, command: Undo): EngineResult {
        val event = before.events.firstOrNull { it.id == command.receipt.eventId } ?: return invalid(ResultReason.INVALID_REQUEST)
        if (event.revision != command.receipt.eventRevision || before.generation != command.receipt.datasetGeneration)
            return conflict(ResultReason.STALE_REVISION)
        val scope = command.receipt.scope
        if (scope != null && before.stateGenerations[scope.scope] != scope.generation)
            return conflict(ResultReason.STALE_SCOPE_CONTEXT)
        return deleteEvent(before, DeleteEvent(event.id, command.source, command.expected, confirmed = true))
    }

    fun editRecord(recordId: RecordId, change: RecordEdit): EngineResult = mutateDefinition { state ->
        val record = state.records[recordId] ?: return@mutateDefinition null
        state.copy(records = state.records + (recordId to DefinitionManagement.edit(record, change)))
    }
    fun archiveRecord(recordId: RecordId): EngineResult = lifecycle(recordId, null, Lifecycle.ARCHIVED)
    fun unarchiveRecord(recordId: RecordId): EngineResult = mutateDefinition { state ->
        state.records[recordId]?.let { state.copy(records = state.records + (recordId to DefinitionManagement.unarchive(it))) }
    }
    fun link(recordId: RecordId, targetId: TargetId, expected: ExpectedContext? = null): EngineResult =
        mutateRelationship(recordId, targetId, expected, requireExisting = false)
    fun relink(recordId: RecordId, targetId: TargetId, expected: ExpectedContext? = null): EngineResult =
        mutateRelationship(recordId, targetId, expected, requireExisting = true)
    fun unlink(recordId: RecordId, targetId: TargetId, expected: ExpectedContext? = null): EngineResult =
        lifecycle(recordId, targetId, null, expected)
    fun editTarget(targetId: TargetId, change: TargetEdit): EngineResult = mutateDefinition { state ->
        val target = state.targets[targetId] ?: return@mutateDefinition null
        state.copy(targets = state.targets + (targetId to DefinitionManagement.edit(target, change)))
    }
    fun archiveTarget(targetId: TargetId): EngineResult = lifecycleTarget(targetId, Lifecycle.ARCHIVED)
    fun unarchiveTarget(targetId: TargetId): EngineResult = mutateTarget(targetId)
    private fun mutateTarget(targetId: TargetId): EngineResult = mutateDefinition { state ->
        val target = state.targets[targetId] ?: return@mutateDefinition null
        state.copy(targets = state.targets + (targetId to DefinitionManagement.unarchive(target)))
    }
    private fun lifecycleTarget(targetId: TargetId, lifecycle: Lifecycle): EngineResult {
        val before = boundary.read()
        val target = before.targets[targetId] ?: return invalid(ResultReason.INVALID_REQUEST)
        val records = before.records.values.filter { before.relationships[it.id to targetId]?.linked == true }
        var next = before.copy(targets = before.targets + (targetId to target.copy(lifecycle = lifecycle,
            revision = Revision(target.revision.value + 1))))
        var effect = LifecycleEffect()
        records.forEach { record ->
            val update = applyLifecycle(next, record.id, targetId, lifecycle)
            next = update.state
            effect = effect + update.effect
        }
        return commitLifecycle(before, next, effect)
    }
    fun deleteScope(recordId: RecordId, targetId: TargetId? = null, confirmed: Boolean = false): EngineResult {
        val before = boundary.read(); val impact = DeletionScope.preview(before, recordId, targetId)
        if (!confirmed) return EngineResult.NeedsConfirmation(ResultReason.PERMANENT_DELETE_IMPACT)
        val ids = impact.eventIds.toSet()
        val events = before.events.filterNot { it.id in ids }
        val record = before.records[recordId] ?: return invalid(ResultReason.INVALID_REQUEST)
        val records = if (targetId == null) before.records - recordId else before.records + (recordId to record.copy(hasEvents = events.any { it.recordId == recordId }))
        val relationships = before.relationships.filterKeys { it.first != recordId || (targetId != null && it.second != targetId) }
        val bindings = before.bindings.filterValues { it.recordId != recordId || (targetId != null && it.targetId != targetId) }
        val undoReceipts = before.undoReceipts.filterValues { it.eventId !in ids }
        return commit(before, before.copy(records = records, events = events, relationships = relationships, bindings = bindings,
            undoReceipts = undoReceipts), emptyList(),
            recordId to (records[recordId]?.revision ?: Revision(0)))
    }

    private fun lifecycle(
        recordId: RecordId,
        targetId: TargetId?,
        lifecycle: Lifecycle?,
        expected: ExpectedContext? = null,
    ): EngineResult {
        val before = boundary.read()
        val record = before.records[recordId] ?: return invalid(ResultReason.INVALID_REQUEST)
        val relationshipRevision = targetId?.let { before.relationships[recordId to it]?.revision }
        validateExpected(before, relationshipRevision, expected)?.let { return it }
        var next = if (lifecycle != null) before.copy(records = before.records + (recordId to record.copy(lifecycle = lifecycle,
            revision = Revision(record.revision.value + 1)))) else before
        val update = applyLifecycle(next, recordId, targetId, lifecycle)
        return commitLifecycle(before, update.state, update.effect)
    }

    private fun applyLifecycle(state: DomainState, recordId: RecordId, targetId: TargetId?, lifecycle: Lifecycle?): LifecycleUpdate {
        var next = state
        val affected = next.events.filter { it.recordId == recordId && (targetId == null || it.targetId == targetId) }
        val openDurationIds = affected.filter { (it.payload as? EventPayload.Duration)?.status == DurationStatus.OPEN }
            .map { it.id }.toSet()
        val updatedEvents = next.events.map { event ->
            if (event in affected && event.payload is EventPayload.Duration &&
                (event.payload as EventPayload.Duration).status == DurationStatus.OPEN)
                event.copy(updatedAt = clock.now(), revision = Revision(event.revision.value + 1),
                    payload = (event.payload as EventPayload.Duration).copy(status = DurationStatus.INCOMPLETE,
                        incompleteReason = "scope became inactive"))
            else event
        }
        val scopes = affected.mapNotNull { event ->
            (event.payload as? EventPayload.State)?.let { StateScope(it.stateGroupId, event.targetId) }
        }.distinct()
        val generations = next.stateGenerations.toMutableMap(); val currents = next.currentStates.toMutableMap()
        scopes.forEach { scope ->
            generations[scope] = DatasetGeneration((generations[scope]?.value ?: 0) + 1); currents.remove(scope)
        }
        val affectedEventIds = affected.map { it.id }.toSet()
        val invalidatedReceipts = next.undoReceipts.values.filter { it.eventId in affectedEventIds }
        val bindings = next.bindings.mapValues { (_, binding) ->
            if (binding.recordId == recordId && (targetId == null || binding.targetId == targetId))
                binding.copy(status = BindingStatus.ORPHANED, revision = Revision(binding.revision.value + 1),
                    undoInvalidations = binding.undoInvalidations + invalidatedReceipts.map {
                        UndoInvalidation(it.receiptId, ResultReason.STALE_REVISION)
                    })
            else binding
        }
        val orphanedBindings = bindings.values.filter { binding ->
            binding.recordId == recordId && (targetId == null || binding.targetId == targetId)
        }
        val updated = next.copy(events = updatedEvents, stateGenerations = generations, currentStates = currents,
            bindings = bindings,
            undoReceipts = next.undoReceipts - invalidatedReceipts.map { it.receiptId }.toSet(),
            relationships = if (targetId != null && lifecycle == null && next.relationships[recordId to targetId] != null)
                next.relationships + ((recordId to targetId) to next.relationships.getValue(recordId to targetId).copy(
                    linked = false, revision = Revision(next.relationships.getValue(recordId to targetId).revision.value + 1)))
            else next.relationships)
        return LifecycleUpdate(updated, LifecycleEffect(
            durationEvents = updatedEvents.filter { it.id in openDurationIds },
            resetScopes = scopes,
            orphanedBindings = orphanedBindings,
            invalidatedUndo = invalidatedReceipts.map { UndoInvalidation(it.receiptId, ResultReason.STALE_REVISION) },
        ))
    }

    private operator fun LifecycleEffect.plus(other: LifecycleEffect) = LifecycleEffect(
        durationEvents = durationEvents + other.durationEvents,
        resetScopes = resetScopes + other.resetScopes,
        orphanedBindings = orphanedBindings + other.orphanedBindings,
        invalidatedUndo = invalidatedUndo + other.invalidatedUndo,
    )

    private fun commitLifecycle(before: DomainState, updated: DomainState, effect: LifecycleEffect): EngineResult {
        val changedRecord = updated.records.keys.firstOrNull { updated.records[it] != before.records[it] }
        val revisions = changedRecord?.let { recordId -> mapOf(recordId.value to updated.records.getValue(recordId).revision) }
            ?: emptyMap()
        return if (boundary.commit(CommitOperation(before, updated, effect)))
            EngineResult.Applied(revisions = revisions, lifecycleEffect = effect)
        else EngineResult.StorageFailure
    }

    private fun mutateDefinition(transform: (DomainState) -> DomainState?): EngineResult {
        val before = boundary.read(); val updated = transform(before) ?: return invalid(ResultReason.INVALID_REQUEST)
        val changedRecord = updated.records.keys.firstOrNull { updated.records[it] != before.records[it] }
        val revision = changedRecord?.let { it to (updated.records[it]?.revision ?: Revision(0)) }
        return if (revision != null) commit(before, updated, emptyList(), revision)
        else if (boundary.commit(CommitOperation(before, updated))) EngineResult.Applied()
        else EngineResult.StorageFailure
    }

    private fun mutateRelationship(
        recordId: RecordId,
        targetId: TargetId,
        expected: ExpectedContext?,
        requireExisting: Boolean,
    ): EngineResult {
        val before = boundary.read()
        if (before.records[recordId]?.lifecycle != Lifecycle.ACTIVE ||
            before.targets[targetId]?.lifecycle != Lifecycle.ACTIVE
        ) return invalid(ResultReason.INACTIVE_SCOPE)
        val relationship = before.relationships[recordId to targetId]
        validateExpected(before, relationship?.revision, expected)?.let { return it }
        val updatedRelationship = when {
            requireExisting && relationship?.linked == false -> DefinitionManagement.relink(relationship)
            !requireExisting && relationship == null -> DefinitionManagement.link(recordId, targetId)
            else -> return invalid(ResultReason.INVALID_REQUEST)
        }
        val updated = before.copy(
            relationships = before.relationships + ((recordId to targetId) to updatedRelationship),
        )
        return if (boundary.commit(CommitOperation(before, updated))) EngineResult.Applied()
        else EngineResult.StorageFailure
    }

    private fun validateExpected(
        before: DomainState,
        actualRevision: Revision?,
        expected: ExpectedContext?,
    ): EngineResult? {
        if (expected?.revision != null && expected.revision != actualRevision)
            return conflict(ResultReason.STALE_REVISION)
        if (expected?.datasetGeneration != null && expected.datasetGeneration != before.generation)
            return conflict(ResultReason.STALE_DATASET_GENERATION)
        return null
    }

    private fun commitDefinition(
        before: DomainState,
        updated: DomainState,
        id: String,
        revision: Revision,
    ): EngineResult = if (boundary.commit(CommitOperation(before, updated)))
        EngineResult.Applied(revisions = mapOf(id to revision))
    else EngineResult.StorageFailure

    private fun validateScope(before: DomainState, record: Record, targetId: TargetId?, expected: ExpectedContext?): EngineResult? {
        if (record.lifecycle != Lifecycle.ACTIVE) return invalid(ResultReason.INACTIVE_SCOPE)
        if (expected?.revision != null && expected.revision != record.revision) return conflict(ResultReason.STALE_REVISION)
        if (expected?.datasetGeneration != null && expected.datasetGeneration != before.generation) return conflict(ResultReason.STALE_DATASET_GENERATION)
        if (expected?.scopeGeneration != null && targetId != null &&
            expected.scopeGeneration != before.stateGenerations[StateScope(record.stateGroupId ?: StateGroupId(""), targetId)])
            return conflict(ResultReason.STALE_SCOPE_CONTEXT)
        return validateTarget(before, record.id, targetId)
    }
    private fun validateTarget(before: DomainState, recordId: RecordId, targetId: TargetId?): EngineResult? =
        if (targetId != null && before.targets[targetId]?.lifecycle != Lifecycle.ACTIVE) invalid(ResultReason.INACTIVE_SCOPE)
        else if (targetId != null && before.relationships[recordId to targetId]?.linked != true)
            invalid(ResultReason.UNLINKED_RELATIONSHIP) else null
    private fun openDuration(state: DomainState, recordId: RecordId, targetId: TargetId?): Event? =
        state.events.lastOrNull { it.recordId == recordId && it.targetId == targetId &&
            (it.payload as? EventPayload.Duration)?.status == DurationStatus.OPEN }
    private fun commit(before: DomainState, updated: DomainState, ids: List<EventId>, revision: Pair<RecordId, Revision>): EngineResult =
        if (boundary.commit(CommitOperation(before, updated))) EngineResult.Applied(ids, mapOf(revision.first.value to revision.second))
        else EngineResult.StorageFailure
    private fun invalid(reason: ResultReason) = EngineResult.Invalid(reason)
    private fun conflict(reason: ResultReason) = EngineResult.Conflict(reason)
}
