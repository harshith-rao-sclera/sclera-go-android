package com.sclera.sclerago.data.sync

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import io.requery.android.database.sqlite.SQLiteDatabase
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Debug only. Opens `databases/master-test.db` when it exists (pushed with tools/push_test_db.sh), read and write.
 * Sign-out leaves this file alone so it need not be pushed again; delete it to go back to the real sync.
 */
@Singleton
class LocalTestDatabase @Inject constructor(@ApplicationContext private val context: Context) : TestDatabase {
    private var open: SQLiteDatabase? = null

    @Synchronized
    override fun connection(): SQLiteDatabase? {
        open?.takeIf { it.isOpen }?.let { return it }
        val file = context.getDatabasePath(FILE_NAME)
        if (!file.exists()) return null
        return runCatching { SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READWRITE) }
            .getOrNull().also { open = it }
    }

    private companion object {
        const val FILE_NAME = "master-test.db"
    }
}
