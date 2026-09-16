package io.github.thrhead.taplog.core.engine

import io.github.thrhead.taplog.core.domain.*
import org.junit.Assert.*
import org.junit.Test

class EngineResultTest {
    @Test fun allResultCategoriesHaveStableTypesAndReasons() {
        assertTrue(EngineResult.Applied() is EngineResult)
        assertEquals(ResultReason.CONFIRMATION_REQUIRED, EngineResult.NeedsConfirmation(ResultReason.CONFIRMATION_REQUIRED).reason)
        assertEquals(ResultReason.STALE_REVISION, EngineResult.Conflict(ResultReason.STALE_REVISION).reason)
        assertEquals(ResultReason.INVALID_QUANTITY, EngineResult.Invalid(ResultReason.INVALID_QUANTITY).reason)
        assertTrue(EngineResult.StorageFailure is EngineResult)
    }
}
