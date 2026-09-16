package io.github.thrhead.taplog.core.engine

import io.github.thrhead.taplog.core.domain.*
data class LifecycleEffect(val incompleteDurationIds: List<EventId> = emptyList(), val stateResets: List<StateReset> = emptyList(),
    val orphanedBindings: List<BindingLifecycle> = emptyList(), val invalidatedReceiptIds: List<ReceiptId> = emptyList())
