package io.github.thrhead.taplog.data.persistence

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.test.platform.app.InstrumentationRegistry

/** Schema-independent builders; each test owns closing its databases and deleting named files. */
internal class RoomTestFixtures<T : RoomDatabase>(
    private val databaseClass: Class<T>,
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext,
) {
    fun inMemory(): T = Room.inMemoryDatabaseBuilder(context, databaseClass)
        .allowMainThreadQueries()
        .build()

    /** Open the same test-owned name again to exercise close/reopen recovery. */
    fun open(name: String): T = Room.databaseBuilder(context, databaseClass, name)
        .allowMainThreadQueries()
        .build()
}
