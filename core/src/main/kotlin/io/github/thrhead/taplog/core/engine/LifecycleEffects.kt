package io.github.thrhead.taplog.core.engine

import io.github.thrhead.taplog.core.domain.*

data class LifecycleEffect(
    val durationEvents: List<Event> = emptyList(),
    val resetScopes: List<StateScope> = emptyList(),
    val orphanedBindings: List<BindingLifecycle> = emptyList(),
    val invalidatedUndo: List<UndoInvalidation> = emptyList(),
)
