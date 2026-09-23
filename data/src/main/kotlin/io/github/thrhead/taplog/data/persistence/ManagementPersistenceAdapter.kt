package io.github.thrhead.taplog.data.persistence

import android.content.Context
import io.github.thrhead.taplog.core.engine.AtomicCommitBoundary
import io.github.thrhead.taplog.core.engine.CommitOperation
import io.github.thrhead.taplog.core.engine.DomainState

private const val DATABASE_NAME = "taplog.db"

/** Creates the management persistence boundary without exposing Room types. */
fun createManagementPersistencePort(context: Context): ManagementPersistencePort =
    ManagementPersistenceHolder.get(context)

private object ManagementPersistenceHolder {
    private var instance: ManagementPersistencePort? = null

    @Synchronized
    fun get(context: Context): ManagementPersistencePort =
        instance ?: create(context).also { instance = it }

    private fun create(context: Context): ManagementPersistencePort {
        val database = TapLogDatabase.builder(context, DATABASE_NAME).build()
        return ManagementPersistenceAdapter(RoomAtomicCommitBoundary(database))
    }
}

internal class ManagementPersistenceAdapter(
    private val boundary: AtomicCommitBoundary,
) : ManagementPersistencePort {
    override fun read(): DomainState = boundary.read()

    override fun commit(operation: CommitOperation): Boolean = boundary.commit(operation)
}
