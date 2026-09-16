package io.github.thrhead.taplog.core.engine

import io.github.thrhead.taplog.core.domain.BindingId
import io.github.thrhead.taplog.core.domain.DatasetGeneration
import io.github.thrhead.taplog.core.domain.EpochMillis
import io.github.thrhead.taplog.core.domain.Event
import io.github.thrhead.taplog.core.domain.Record
import io.github.thrhead.taplog.core.domain.RecordId
import io.github.thrhead.taplog.core.domain.RecordTarget
import io.github.thrhead.taplog.core.domain.StateGroup
import io.github.thrhead.taplog.core.domain.StateGroupId
import io.github.thrhead.taplog.core.domain.Sequence
import io.github.thrhead.taplog.core.domain.Target
import io.github.thrhead.taplog.core.domain.TargetId
import io.github.thrhead.taplog.core.domain.ReceiptId
import io.github.thrhead.taplog.core.domain.BindingLifecycle

interface AcceptanceClock { fun now(): EpochMillis }
data class DomainState(val records: Map<RecordId, Record> = emptyMap(), val targets: Map<TargetId, Target> = emptyMap(),
    val relationships: Map<Pair<RecordId, TargetId>, RecordTarget> = emptyMap(), val events: List<Event> = emptyList(),
    val stateGroups: Map<StateGroupId, StateGroup> = emptyMap(), val generation: DatasetGeneration = DatasetGeneration(0),
    val nextSequence: Sequence = Sequence(1),
    val currentStates: Map<StateScope, RecordId> = emptyMap(),
    val stateGenerations: Map<StateScope, DatasetGeneration> = emptyMap(),
    val bindings: Map<BindingId, BindingLifecycle> = emptyMap(),
    val undoReceipts: Map<ReceiptId, UndoReceipt> = emptyMap())
data class StateScope(val groupId: StateGroupId, val targetId: TargetId?)
data class CommitOperation(
    val expected: DomainState,
    val state: DomainState,
    val lifecycleEffect: LifecycleEffect = LifecycleEffect(),
)
interface AtomicCommitBoundary { fun read(): DomainState; fun commit(operation: CommitOperation): Boolean }
