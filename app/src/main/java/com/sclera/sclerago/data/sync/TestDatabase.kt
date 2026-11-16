package com.sclera.sclerago.data.sync

import io.requery.android.database.sqlite.SQLiteDatabase

/**
 * A ready-made offline database to read instead of the downloaded one. Only debug builds provide an
 * implementation (see the debug source set); release builds have none, so the real sync always runs.
 */
interface TestDatabase {
    /** The open database, or null when there is no test database on this device (the real sync is used then). */
    fun connection(): SQLiteDatabase?
}
