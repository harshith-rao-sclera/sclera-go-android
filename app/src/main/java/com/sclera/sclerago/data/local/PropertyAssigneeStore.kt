package com.sclera.sclerago.data.local

import android.content.ContentValues
import android.content.Context
import com.sclera.sclerago.domain.model.PropertyAssignee
import com.sclera.sclerago.services.syncManager.GlobalConfigDatabaseHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import io.requery.android.database.sqlite.SQLiteDatabase
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The people each property's assets are assigned to, as the server lists them to anyone who can open the property
 * (`GET /properties/{id}/assets/assignees`), in `global_config.db`'s `property_assignees` table: so "Assigned to" shows
 * their email offline for every user, as the web shows it, and the people filter their profile photo (its link only).
 * Replaced per property on each refresh; wiped on sign-out. The table is plain SQLite, as the rest of that database (a
 * known gap in CLAUDE.md). Blocking: call off the main thread.
 */
@Singleton
class PropertyAssigneeStore @Inject constructor(@ApplicationContext private val context: Context) : PropertyLocalData {
    private val db: SQLiteDatabase by lazy {
        GlobalConfigDatabaseHelper.initializeInstance(context).globalDB.also {
            it.execSQL(CREATE)
            addPhotoColumn(it)
            // The organisation's users an earlier build kept here (admins' phones) are not kept any more.
            it.execSQL("DROP TABLE IF EXISTS org_users")
        }
    }

    /** The profile photo link column, added to a table an earlier build made without it. */
    private fun addPhotoColumn(db: SQLiteDatabase) {
        val has = db.rawQuery("PRAGMA table_info($TABLE)", emptyArray<String>()).use { c ->
            val name = c.getColumnIndex("name")
            generateSequence { if (c.moveToNext()) c.getString(name) else null }.any { it == PHOTO }
        }
        if (!has) db.execSQL("ALTER TABLE $TABLE ADD COLUMN $PHOTO TEXT")
    }

    /** The assignees kept for [propertyId]. */
    fun of(propertyId: String): List<PropertyAssignee> =
        db.rawQuery("SELECT user_id, name, email, $PHOTO FROM $TABLE WHERE property_id = ?", arrayOf(propertyId)).use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(
                        PropertyAssignee(
                            c.getString(0),
                            if (c.isNull(1)) null else c.getString(1),
                            if (c.isNull(2)) "" else c.getString(2),
                            if (c.isNull(3)) null else c.getString(3),
                        ),
                    )
                }
            }
        }

    /** Replaces [propertyId]'s assignees with [assignees], as fetched at [now] (epoch millis). */
    fun replace(propertyId: String, assignees: List<PropertyAssignee>, now: Long) {
        db.beginTransaction()
        try {
            db.delete(TABLE, "property_id = ?", arrayOf(propertyId))
            assignees.forEach { a ->
                db.insertWithOnConflict(
                    TABLE, null,
                    ContentValues().apply {
                        put("property_id", propertyId)
                        put("user_id", a.userId)
                        put("name", a.name)
                        put("email", a.email)
                        put(PHOTO, a.imageUrl)
                        put("updated_at", now)
                    },
                    SQLiteDatabase.CONFLICT_REPLACE,
                )
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    /** Forgets [propertyId]'s assignees (the user lost access to it). */
    override fun forget(propertyId: String) {
        db.delete(TABLE, "property_id = ?", arrayOf(propertyId))
    }

    /** Forgets every property's assignees (sign-out). */
    fun clear() {
        db.delete(TABLE, null, null)
    }

    private companion object {
        const val TABLE = "property_assignees"
        const val PHOTO = "image_url"
        const val CREATE =
            "CREATE TABLE IF NOT EXISTS $TABLE (property_id TEXT NOT NULL, user_id TEXT NOT NULL, name TEXT, email TEXT, " +
                "$PHOTO TEXT, updated_at INTEGER NOT NULL, PRIMARY KEY (property_id, user_id))"
    }
}
