package com.sclera.sclerago.data.local

import android.content.ContentValues
import android.content.Context
import com.sclera.sclerago.services.syncManager.GlobalConfigDatabaseHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import io.requery.android.database.sqlite.SQLiteDatabase
import javax.inject.Inject
import javax.inject.Singleton

/** An icon kept on the device: its SVG and when it was downloaded (epoch millis). */
class StoredIcon(val svg: String, val updatedAt: Long)

/**
 * The asset icons kept in `global_config.db`, in tables of their own: `asset_icons` (each icon's SVG, as the server
 * serves it) and `asset_type_icons` (which icon each master format of a property uses). Icons say nothing about
 * anyone's data; they are kept so the lists show them offline. Blocking: call off the main thread.
 */
@Singleton
class AssetIconStore @Inject constructor(@ApplicationContext private val context: Context) {
    private val db: SQLiteDatabase by lazy {
        GlobalConfigDatabaseHelper.initializeInstance(context).globalDB.also {
            it.execSQL(CREATE_ICONS)
            it.execSQL(CREATE_TYPE_ICONS)
        }
    }

    /** Every icon kept, by name. */
    fun icons(): Map<String, StoredIcon> = db.rawQuery("SELECT name, svg, updated_at FROM $ICONS", null).use { c ->
        buildMap { while (c.moveToNext()) put(c.getString(0), StoredIcon(c.getString(1), c.getLong(2))) }
    }

    /** Keeps (or replaces) icons, SVG by name, as downloaded at [now]. */
    fun putIcons(svgs: Map<String, Pair<String, String>>, now: Long) {
        if (svgs.isEmpty()) return
        db.beginTransaction()
        try {
            svgs.forEach { (name, labelAndSvg) ->
                db.insertWithOnConflict(
                    ICONS, null,
                    ContentValues().apply {
                        put("name", name)
                        put("label", labelAndSvg.first)
                        put("svg", labelAndSvg.second)
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

    /** Which icon each master format of [propertyId] uses. */
    fun typeIcons(propertyId: String): Map<String, String> =
        db.rawQuery("SELECT master_format_id, icon FROM $TYPE_ICONS WHERE property_id = ?", arrayOf(propertyId)).use { c ->
            buildMap { while (c.moveToNext()) put(c.getString(0), c.getString(1)) }
        }

    /** Replaces [propertyId]'s type icons with [byFormat] (master format id → icon name). */
    fun replaceTypeIcons(propertyId: String, byFormat: Map<String, String>) {
        db.beginTransaction()
        try {
            db.delete(TYPE_ICONS, "property_id = ?", arrayOf(propertyId))
            byFormat.forEach { (format, icon) ->
                db.insert(
                    TYPE_ICONS, null,
                    ContentValues().apply {
                        put("property_id", propertyId)
                        put("master_format_id", format)
                        put("icon", icon)
                    },
                )
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private companion object {
        const val ICONS = "asset_icons"
        const val TYPE_ICONS = "asset_type_icons"
        const val CREATE_ICONS =
            "CREATE TABLE IF NOT EXISTS $ICONS (name TEXT PRIMARY KEY, label TEXT, svg TEXT NOT NULL, updated_at INTEGER NOT NULL)"
        const val CREATE_TYPE_ICONS =
            "CREATE TABLE IF NOT EXISTS $TYPE_ICONS (property_id TEXT NOT NULL, master_format_id TEXT NOT NULL, icon TEXT NOT NULL, " +
                "PRIMARY KEY (property_id, master_format_id))"
    }
}
