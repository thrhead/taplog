package io.github.thrhead.taplog.data.persistence

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.driver.SupportSQLiteConnection
import androidx.sqlite.execSQL

@Database(
    entities = [
        RecordEntity::class,
        TargetEntity::class,
        RecordTargetEntity::class,
        StateGroupEntity::class,
        EventEntity::class,
        StateScopeEntity::class,
        BindingEntity::class,
        BindingUndoInvalidationEntity::class,
        UndoReceiptEntity::class,
        DatasetMetadataEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
internal abstract class TapLogDatabase : RoomDatabase() {
    abstract fun persistenceDao(): PersistenceDao

    companion object {
        /** V1 has no production upgrade or downgrade edges. Unknown versions fail closed. */
        val productionMigrations: List<Migration> = emptyList()

        // Room accepts migrations only as varargs; the owned registry is deliberately copied
        // at this API boundary and remains the single source for supported upgrade edges.
        @Suppress("SpreadOperator")
        fun builder(context: Context, name: String): Builder<TapLogDatabase> =
            Room.databaseBuilder(context.applicationContext, TapLogDatabase::class.java, name)
                .addMigrations(*productionMigrations.toTypedArray())
                .addCallback(PersistenceDatabaseCallback)
    }
}

internal object PersistenceDatabaseCallback : RoomDatabase.Callback() {
    override fun onCreate(db: SupportSQLiteDatabase) = onCreate(SupportSQLiteConnection(db))

    override fun onCreate(connection: SQLiteConnection) {
        connection.execSQL(
            "INSERT INTO dataset_metadata(singletonKey,datasetGeneration,nextSequence,schemaVersion) " +
                "VALUES (1,0,1,1)",
        )
    }

    override fun onOpen(db: SupportSQLiteDatabase) = onOpen(SupportSQLiteConnection(db))

    override fun onOpen(connection: SQLiteConnection) {
        connection.execSQL("PRAGMA foreign_keys = ON")
        connection.prepare("PRAGMA foreign_keys").use { statement ->
            if (!statement.step() || statement.getLong(0) != 1L) {
                throw DatabaseOpenFailure("Foreign-key enforcement is unavailable")
            }
        }

        // Room cannot express WHERE indexes. Install after its fresh-schema/identity checks;
        // explicit generated schema validation of this installed extra index is unsupported.
        connection.execSQL(CREATE_OPEN_DURATION_INDEX_SQL)
        connection.prepare("SELECT sql FROM sqlite_master WHERE type='index' AND name=?").use { statement ->
            statement.bindText(1, OPEN_DURATION_INDEX_NAME)
            val expectedSql = CREATE_OPEN_DURATION_INDEX_SQL.replace(" IF NOT EXISTS", "")
            if (!statement.step() || statement.getText(0) != expectedSql) {
                throw DatabaseOpenFailure("The OPEN Duration scope index has an incompatible definition")
            }
        }
    }
}
