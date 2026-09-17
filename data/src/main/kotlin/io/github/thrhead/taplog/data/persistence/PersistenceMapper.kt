package io.github.thrhead.taplog.data.persistence

import io.github.thrhead.taplog.core.domain.*
import io.github.thrhead.taplog.core.domain.Record
import java.math.BigDecimal
import io.github.thrhead.taplog.core.engine.*

/** Complete scalar row input/output; Room/Android APIs stay outside the mapping boundary. */
internal data class PersistenceRows(
    val records: List<RecordEntity> = emptyList(),
    val targets: List<TargetEntity> = emptyList(),
    val recordTargets: List<RecordTargetEntity> = emptyList(),
    val stateGroups: List<StateGroupEntity> = emptyList(),
    val events: List<EventEntity> = emptyList(),
    val stateScopes: List<StateScopeEntity> = emptyList(),
    val bindings: List<BindingEntity> = emptyList(),
    val bindingUndoInvalidations: List<BindingUndoInvalidationEntity> = emptyList(),
    val undoReceipts: List<UndoReceiptEntity> = emptyList(),
    val metadata: List<DatasetMetadataEntity> = emptyList(),
)

internal sealed class PersistenceFailure(message: String, cause: Throwable? = null) : RuntimeException(message, cause) {
    val retryable: Boolean get() = this is DatabaseOpenFailure || this is DatabaseReadFailure
}
internal class DatabaseOpenFailure(message: String, cause: Throwable? = null) : PersistenceFailure(message, cause)
internal class DatabaseReadFailure(message: String, cause: Throwable? = null) : PersistenceFailure(message, cause)
internal class MigrationFailure(message: String, cause: Throwable? = null) : PersistenceFailure(message, cause)
internal class CorruptRowFailure(message: String, cause: Throwable? = null) : PersistenceFailure(message, cause)
internal class MappingFailure(message: String, cause: Throwable? = null) : PersistenceFailure(message, cause)

internal object PersistenceMapper {
    fun toRows(state: DomainState): PersistenceRows {
        requireInvariant(state.records.all { (key, value) -> key == value.id }, "Record map key disagrees with ID")
        requireInvariant(state.targets.all { (key, value) -> key == value.id }, "Target map key disagrees with ID")
        requireInvariant(state.relationships.all { (key, value) -> key == (value.recordId to value.targetId) }, "Relationship map key disagrees with IDs")
        requireInvariant(state.stateGroups.all { (key, value) -> key == value.id }, "State Group map key disagrees with ID")
        requireInvariant(state.bindings.all { (key, value) -> key == value.bindingId }, "Binding map key disagrees with ID")
        requireInvariant(state.undoReceipts.all { (key, value) -> key == value.receiptId }, "Undo map key disagrees with ID")
        val rows = PersistenceRows(
            records = state.records.values.map { RecordEntity(it.id.value, it.name, it.icon, encodeEnum(it.behavior),
                encodeEnum(it.lifecycle), it.unit?.value, it.defaultQuantity?.let(::encodeQuantity), it.stateGroupId?.value,
                it.revision.value, it.hasEvents) },
            targets = state.targets.values.map { TargetEntity(it.id.value, it.name, it.icon, encodeEnum(it.lifecycle), it.revision.value) },
            recordTargets = state.relationships.values.map { RecordTargetEntity(it.recordId.value, it.targetId.value, it.linked, it.revision.value) },
            stateGroups = state.stateGroups.values.map { StateGroupEntity(it.id.value, it.name, it.revision.value) },
            events = state.events.map(::eventToRow),
            stateScopes = (state.stateGenerations.keys + state.currentStates.keys).map { scope ->
                StateScopeEntity(scope.groupId.value, scopeKey(scope.targetId), state.stateGenerations[scope]?.value ?: 0,
                    state.currentStates[scope]?.value)
            },
            bindings = state.bindings.values.map { binding ->
                val display = binding.lastKnownDisplay
                BindingEntity(binding.bindingId.value, binding.recordId.value, binding.targetId?.value, encodeEnum(binding.status),
                    binding.revision.value, display.recordName, display.recordIcon, display.targetName, display.targetIcon)
            },
            bindingUndoInvalidations = state.bindings.values.flatMap { binding -> binding.undoInvalidations.map {
                BindingUndoInvalidationEntity(binding.bindingId.value, it.receiptId.value, encodeEnum(it.reason))
            } },
            undoReceipts = state.undoReceipts.values.map { receipt ->
                UndoReceiptEntity(receipt.receiptId.value, receipt.eventId.value, receipt.eventRevision.value, receipt.datasetGeneration.value,
                    receipt.scope?.scope?.groupId?.value, receipt.scope?.scope?.let { scopeKey(it.targetId) }, receipt.scope?.generation?.value)
            },
            metadata = listOf(DatasetMetadataEntity(1, state.generation.value, state.nextSequence.value, 1)),
        )
        // The same row checks apply to both directions; invalid domain state is never serialized.
        fromRows(rows)
        return rows
    }

    fun fromRows(rows: PersistenceRows): DomainState {
        requireInvariant(rows.metadata.size == 1, "Exactly one Dataset metadata row is required")
        val metadata = rows.metadata.single()
        requireInvariant(metadata.singletonKey == 1 && metadata.schemaVersion == 1, "Unsupported Dataset metadata key or schema version")
        val records = unique(rows.records.map { row -> Record(RecordId(row.recordId), row.name, row.icon, enum(row.behavior),
            enum(row.lifecycle), row.unit?.let(::unit), row.defaultQuantity?.let(::decodeQuantity), row.stateGroupId?.let(::StateGroupId),
            Revision(row.revision), row.hasEvents) }) { it.id }
        val targets = unique(rows.targets.map { Target(TargetId(it.targetId), it.name, it.icon, enum(it.lifecycle), Revision(it.revision)) }) { it.id }
        val relationships = unique(rows.recordTargets.map { RecordTarget(RecordId(it.recordId), TargetId(it.targetId), it.linked, Revision(it.revision)) }) {
            it.recordId to it.targetId
        }
        val groups = unique(rows.stateGroups.map { StateGroup(StateGroupId(it.stateGroupId), it.name, Revision(it.revision)) }) { it.id }
        val scopeRows = unique(rows.stateScopes) { StateScope(StateGroupId(it.stateGroupId), targetId(it.targetScopeKey)) }
        scopeRows.values.forEach { requireInvariant(it.resetSequence == null && it.resetAt == null, "State reset metadata is not represented by DomainState") }
        val invalidations = unique(rows.bindingUndoInvalidations) { it.bindingId to it.receiptId }.values.groupBy { it.bindingId }
        val bindings = unique(rows.bindings.map { row -> BindingLifecycle(BindingId(row.bindingId), RecordId(row.recordId), row.targetId?.let(::TargetId),
            enum(row.status), Revision(row.revision), DisplaySnapshot(row.snapshotRecordName, row.snapshotRecordIcon, row.snapshotTargetName, row.snapshotTargetIcon),
            invalidations[row.bindingId].orEmpty().map { UndoInvalidation(ReceiptId(it.receiptId), enum(it.reason)) }) }) { it.bindingId }
        invalidations.keys.forEach { requireInvariant(BindingId(it) in bindings, "Undo invalidation references a missing Binding") }
        val receipts = unique(rows.undoReceipts.map { row ->
            requireInvariant(row.operation == null && row.beforeImageJson == null && !row.consumed && row.invalidationReason == null,
                "Undo adapter metadata is not represented by UndoReceipt")
            val scopePresent = listOf(row.stateGroupId != null, row.targetScopeKey != null, row.expectedScopeGeneration != null)
            requireInvariant(scopePresent.all { it } || scopePresent.none { it }, "Undo State scope is partially populated")
            UndoReceipt(ReceiptId(row.receiptId), EventId(row.eventId), Revision(row.expectedEventRevision), DatasetGeneration(row.expectedDatasetGeneration),
                row.stateGroupId?.let { ScopeContext(StateScope(StateGroupId(it), targetId(row.targetScopeKey!!)), DatasetGeneration(row.expectedScopeGeneration!!)) })
        }) { it.receiptId }
        val events = unique(rows.events.map(::eventFromRow)) { it.id }.values.toList()
        val state = DomainState(records, targets, relationships, events, groups, DatasetGeneration(metadata.datasetGeneration), Sequence(metadata.nextSequence),
            scopeRows.mapNotNull { (scope, row) -> row.currentRecordId?.let { scope to RecordId(it) } }.toMap(),
            scopeRows.mapValues { DatasetGeneration(it.value.generation) }, bindings, receipts)
        validate(state)
        return state
    }

    private fun eventToRow(event: Event): EventEntity {
        val counter = event.payload as? EventPayload.Counter
        val duration = event.payload as? EventPayload.Duration
        val state = event.payload as? EventPayload.State
        val snapshot = event.snapshot
        return EventEntity(event.id.value, event.recordId.value, event.targetId?.value, scopeKey(event.targetId), encodeEnum(event.behavior),
            event.occurredAt.value, event.createdAt.value, event.updatedAt.value, event.sequence.value, encodeEnum(event.source), event.revision.value,
            snapshot.recordName, snapshot.recordIcon, snapshot.targetName, snapshot.targetIcon, encodeEnum(snapshot.behavior), snapshot.unit?.value,
            counter?.quantity?.let(::encodeQuantity), counter?.unit?.value, duration?.startAt?.value, duration?.endAt?.value,
            duration?.status?.let(::encodeEnum), duration?.incompleteReason, state?.stateGroupId?.value, state?.generation?.value)
    }

    private fun eventFromRow(row: EventEntity): Event {
        val behavior: Behavior = enum(row.behavior)
        val counterColumns = listOf(row.counterQuantity, row.counterUnit)
        val durationColumns = listOf(row.durationStartAt, row.durationEndAt, row.durationStatus, row.durationIncompleteReason)
        val stateColumns = listOf(row.stateGroupId, row.stateGeneration)
        requireInvariant(behavior == Behavior.COUNTER || counterColumns.all { it == null }, "Extraneous Counter payload columns")
        requireInvariant(behavior == Behavior.DURATION || durationColumns.all { it == null }, "Extraneous Duration payload columns")
        requireInvariant(behavior == Behavior.STATE || stateColumns.all { it == null }, "Extraneous State payload columns")
        val payload = when (behavior) {
            Behavior.MOMENT -> EventPayload.Moment
            Behavior.COUNTER -> EventPayload.Counter(decodeQuantity(required(row.counterQuantity, "Counter quantity")), row.counterUnit?.let(::unit))
            Behavior.DURATION -> EventPayload.Duration(EpochMillis(required(row.durationStartAt, "Duration start")), row.durationEndAt?.let(::EpochMillis),
                enum(required(row.durationStatus, "Duration status")), row.durationIncompleteReason)
            Behavior.STATE -> EventPayload.State(StateGroupId(required(row.stateGroupId, "State group")), DatasetGeneration(required(row.stateGeneration, "State generation")))
        }
        val target = row.targetId?.let(::TargetId)
        requireInvariant(targetId(row.targetScopeKey) == target, "Event Target scope disagrees with nullable Target")
        val snapshot = EventSnapshot(row.snapshotRecordName, row.snapshotRecordIcon, row.snapshotTargetName, row.snapshotTargetIcon,
            enum(row.snapshotBehavior), row.snapshotUnit?.let(::unit))
        requireInvariant(payload.isValidFor(behavior), "Invalid terminal or typed Event payload")
        requireInvariant(snapshot.behavior == behavior, "Snapshot behavior disagrees with Event")
        requireInvariant(payload !is EventPayload.Counter || snapshot.unit == payload.unit, "Counter unit disagrees with snapshot")
        return Event(EventId(row.eventId), RecordId(row.recordId), target, behavior, EpochMillis(row.occurredAt), EpochMillis(row.createdAt),
            EpochMillis(row.updatedAt), Sequence(row.sequence), enum(row.source), Revision(row.revision), snapshot, payload)
    }

    private fun validate(state: DomainState) {
        nonnegative(state.generation.value, "Dataset generation")
        requireInvariant(state.nextSequence.value > 0, "Next sequence must be positive")
        state.records.values.forEach { record ->
            nonnegative(record.revision.value, "Record revision")
            record.stateGroupId?.let { requireInvariant(it in state.stateGroups, "Record references a missing State Group") }
            if (record.behavior == Behavior.STATE) requireInvariant(record.stateGroupId != null, "State Record requires a State Group")
            if (record.behavior == Behavior.COUNTER) requireInvariant(record.defaultQuantity?.isValid == true, "Counter default quantity must be positive")
        }
        state.targets.values.forEach { nonnegative(it.revision.value, "Target revision") }
        state.stateGroups.values.forEach { nonnegative(it.revision.value, "State Group revision") }
        state.relationships.values.forEach {
            references(state, it.recordId, it.targetId)
            nonnegative(it.revision.value, "Relationship revision")
        }
        val sequences = mutableSetOf<Long>()
        val openScopes = mutableSetOf<Pair<RecordId, TargetId?>>()
        state.events.forEach { event ->
            references(state, event.recordId, event.targetId)
            nonnegative(event.revision.value, "Event revision")
            requireInvariant(event.sequence.value > 0 && event.sequence.value < state.nextSequence.value, "Event sequence outside allocation boundary")
            requireInvariant(sequences.add(event.sequence.value), "Event sequence reused")
            val payload = event.payload
            if (payload is EventPayload.Duration && payload.status == DurationStatus.OPEN)
                requireInvariant(openScopes.add(event.recordId to event.targetId), "Multiple OPEN Durations in one scope")
            if (payload is EventPayload.State) {
                nonnegative(payload.generation.value, "Event State generation")
                requireInvariant(payload.stateGroupId in state.stateGroups, "Event references a missing State Group")
                requireInvariant(state.records.getValue(event.recordId).stateGroupId == payload.stateGroupId, "Event State Group disagrees with Record")
                val active = state.stateGenerations[StateScope(payload.stateGroupId, event.targetId)]?.value ?: 0
                requireInvariant(payload.generation.value <= active, "Event State generation exceeds scope generation")
            }
        }
        state.stateGenerations.forEach { (scope, generation) ->
            requireInvariant(scope.groupId in state.stateGroups, "State scope references a missing State Group")
            scope.targetId?.let { requireInvariant(it in state.targets, "State scope references a missing Target") }
            nonnegative(generation.value, "State scope generation")
        }
        state.currentStates.forEach { (scope, recordId) ->
            // Confirmed core deletion may retain this contextual ID after removing its Record.
            state.records[recordId]?.let { record -> requireInvariant(record.behavior == Behavior.STATE && record.stateGroupId == scope.groupId,
                "Current State Record disagrees with scope") }
        }
        state.bindings.values.forEach {
            references(state, it.recordId, it.targetId)
            nonnegative(it.revision.value, "Binding revision")
        }
        state.undoReceipts.values.forEach {
            // Undo/Delete may retain old references; these scalar contexts do not own entity lifetimes.
            nonnegative(it.eventRevision.value, "Undo Event revision")
            nonnegative(it.datasetGeneration.value, "Undo Dataset generation")
            it.scope?.let { context -> nonnegative(context.generation.value, "Undo State generation") }
        }
    }

    private fun references(state: DomainState, record: RecordId, target: TargetId?) {
        requireInvariant(record in state.records, "Missing referenced Record")
        target?.let { requireInvariant(it in state.targets, "Missing referenced Target") }
    }

    private fun scopeKey(targetId: TargetId?): String = encodeTargetScope(targetId?.let(TargetScope::ForTarget) ?: TargetScope.NoTarget)
    private fun targetId(key: String): TargetId? = when (val scope = decodeTargetScope(key)) {
        TargetScope.NoTarget -> null
        is TargetScope.ForTarget -> scope.targetId
    }
    private fun unit(text: String): UnitName = UnitName(text).also { requireInvariant(it.isValid, "Unit must not be blank") }
    private fun nonnegative(value: Long, field: String) = requireInvariant(value >= 0, "$field must be nonnegative")
    private fun <T : Any> required(value: T?, field: String): T = value ?: throw MappingFailure("Missing $field")
    private inline fun <reified E : Enum<E>> enum(text: String): E = decodeEnum(text, E::class.java)
    private fun <K, V> unique(values: List<V>, key: (V) -> K): Map<K, V> {
        val result = LinkedHashMap<K, V>()
        values.forEach { value -> requireInvariant(result.put(key(value), value) == null, "Duplicate entity key") }
        return result
    }

    private val canonicalQuantityText = Regex("(?:[1-9][0-9]*(?:\\.[0-9]*[1-9])?|0\\.[0-9]*[1-9])")

    /** Numeric value is exact; redundant decimal scale is not part of the stored representation. */
    fun encodeQuantity(quantity: Quantity): String {
        requireInvariant(quantity.isValid, "Quantity must be positive")
        return try {
            quantity.value.stripTrailingZeros().toPlainString()
        } catch (cause: ArithmeticException) {
            throw MappingFailure("Quantity cannot be encoded as plain decimal text", cause)
        }
    }

    /** Persisted text must already be canonical; invalid rows are never normalized on read. */
    fun decodeQuantity(text: String): Quantity {
        // Reject exponent/locale/scale variants before any potential decimal expansion.
        requireInvariant(canonicalQuantityText.matches(text), "Quantity text must be positive canonical decimal")
        val decimal = try {
            BigDecimal(text)
        } catch (cause: NumberFormatException) {
            throw MappingFailure("Invalid quantity text", cause)
        }
        val quantity = Quantity.exact(decimal) ?: throw MappingFailure("Quantity must be positive")
        return quantity
    }

    // Core EpochMillis is already a UTC epoch offset, not a local wall-clock time.
    fun encodeEpochMillis(epoch: EpochMillis): Long = epoch.value
    fun decodeEpochMillis(value: Long): EpochMillis = EpochMillis(value)

    fun encodeEnum(value: Enum<*>): String = value.name

    fun <E : Enum<E>> decodeEnum(text: String, type: Class<E>): E = try {
        java.lang.Enum.valueOf(type, text)
    } catch (cause: IllegalArgumentException) {
        throw MappingFailure("Unknown ${type.simpleName} value: $text", cause)
    }

    fun encodeTargetScope(scope: TargetScope): String = when (scope) {
        TargetScope.NoTarget -> NO_TARGET_SCOPE_KEY
        is TargetScope.ForTarget -> TARGET_SCOPE_KEY_PREFIX + scope.targetId.value
    }

    fun decodeTargetScope(key: String): TargetScope = when {
        key == NO_TARGET_SCOPE_KEY -> TargetScope.NoTarget
        key.startsWith(TARGET_SCOPE_KEY_PREFIX) ->
            TargetScope.ForTarget(TargetId(key.removePrefix(TARGET_SCOPE_KEY_PREFIX)))
        else -> throw MappingFailure("Invalid target scope key: $key")
    }

    fun payloadBehavior(payload: EventPayload): Behavior = when (payload) {
        EventPayload.Moment -> Behavior.MOMENT
        is EventPayload.Counter -> Behavior.COUNTER
        is EventPayload.Duration -> Behavior.DURATION
        is EventPayload.State -> Behavior.STATE
    }

    fun requirePayloadAgreement(behavior: Behavior, payload: EventPayload) {
        requireInvariant(behavior == payloadBehavior(payload), "Behavior and payload discriminator disagree")
    }

    fun requireInvariant(valid: Boolean, message: String) {
        if (!valid) throw MappingFailure(message)
    }
}
