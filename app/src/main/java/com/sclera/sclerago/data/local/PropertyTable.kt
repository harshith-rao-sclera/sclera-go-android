package com.sclera.sclerago.data.local

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import com.sclera.sclerago.data.remote.adc.AdcJson
import com.sclera.sclerago.data.remote.adc.PropertyDto
import com.sclera.sclerago.data.remote.adc.SettingsDto
import com.sclera.sclerago.services.syncManager.GlobalConfigDatabaseHelper
import com.sclera.sclerago.services.syncManager.GlobalConfigDatabaseHelper.PROPERTIES_COLUMN_ACCESS_CODE
import com.sclera.sclerago.services.syncManager.GlobalConfigDatabaseHelper.PROPERTIES_COLUMN_CONTACT_EMAIL
import com.sclera.sclerago.services.syncManager.GlobalConfigDatabaseHelper.PROPERTIES_COLUMN_CONTACT_NAME
import com.sclera.sclerago.services.syncManager.GlobalConfigDatabaseHelper.PROPERTIES_COLUMN_CONTACT_PHONE
import com.sclera.sclerago.services.syncManager.GlobalConfigDatabaseHelper.PROPERTIES_COLUMN_IS_SYNCED
import com.sclera.sclerago.services.syncManager.GlobalConfigDatabaseHelper.TABLE_NAME_PROPERTIES
import dagger.hilt.android.qualifiers.ApplicationContext
import io.requery.android.database.sqlite.SQLiteDatabase
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The organisation's property list in `global_config.db`'s `properties` table (the sync engine makes the table), as
 * the server lists it, with `is_synced` = 1 for a property downloaded to this device (its offline data synced here)
 * and 0 for one that isn't. The access code and the contact's name, email and phone are stored encrypted with the
 * Keystore [SecretCipher] (AES-GCM, the key never leaves the device); the rows are deleted on sign-out. Blocking:
 * call off the main thread.
 */
@Singleton
class PropertyTable @Inject constructor(
    @ApplicationContext private val context: Context,
    private val cipher: SecretCipher,
) {
    private val db: SQLiteDatabase get() = GlobalConfigDatabaseHelper.initializeInstance(context).globalDB

    /** The properties kept, in the server's order. */
    fun read(): List<PropertyDto> = db.rawQuery("SELECT * FROM $TABLE_NAME_PROPERTIES ORDER BY rowid", null).use { c ->
        buildList { while (c.moveToNext()) add(c.toDto()) }
    }

    /** Replaces the properties kept with [list], for [organisationId]; those in [synced] (downloaded here) are marked synced. */
    fun replace(list: List<PropertyDto>, organisationId: String?, synced: Set<String>) {
        val db = db
        db.beginTransaction()
        try {
            db.delete(TABLE_NAME_PROPERTIES, null, null)
            list.forEach { dto ->
                db.insertWithOnConflict(TABLE_NAME_PROPERTIES, null, dto.toRow(organisationId, dto.id in synced), SQLiteDatabase.CONFLICT_REPLACE)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    /** Marks the properties in [synced] as synced (downloaded to this device) and every other one as not. */
    fun markSynced(synced: Set<String>) {
        val db = db
        db.beginTransaction()
        try {
            db.execSQL("UPDATE $TABLE_NAME_PROPERTIES SET $PROPERTIES_COLUMN_IS_SYNCED = 0")
            synced.forEach { id ->
                db.execSQL("UPDATE $TABLE_NAME_PROPERTIES SET $PROPERTIES_COLUMN_IS_SYNCED = 1 WHERE id = ?", arrayOf<Any>(id))
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    /** Forgets every property (sign-out). */
    fun clear() {
        db.delete(TABLE_NAME_PROPERTIES, null, null)
    }

    private fun PropertyDto.toRow(organisationId: String?, synced: Boolean) = ContentValues().apply {
        put("id", id)
        put("organisation_id", organisationId)
        put("name", name)
        put("type", type)
        put("address_line1", addressLine1)
        put("address_line2", addressLine2)
        put("city", city)
        put("state", state)
        put("country", country)
        put("region", region)
        put("postal_code", postalCode)
        put(PROPERTIES_COLUMN_CONTACT_NAME, contactName?.let(cipher::encrypt))
        put(PROPERTIES_COLUMN_CONTACT_EMAIL, contactEmail?.let(cipher::encrypt))
        put(PROPERTIES_COLUMN_CONTACT_PHONE, contactPhone?.let(cipher::encrypt))
        put("notes", notes)
        put("available_from", availableFrom)
        put("available_until", availableUntil)
        put(PROPERTIES_COLUMN_ACCESS_CODE, accessCode?.let(cipher::encrypt))
        put("status", status)
        put("settings", AdcJson.encodeToString(SettingsDto.serializer(), settings))
        put(PROPERTIES_COLUMN_IS_SYNCED, if (synced) 1 else 0)
    }

    private fun Cursor.toDto(): PropertyDto {
        fun text(column: String): String? = getColumnIndex(column).takeIf { it >= 0 && !isNull(it) }?.let(::getString)
        fun secret(column: String): String? = text(column)?.let(cipher::decrypt)
        return PropertyDto(
            id = text("id").orEmpty(),
            name = text("name").orEmpty(),
            type = text("type"),
            addressLine1 = text("address_line1"),
            addressLine2 = text("address_line2"),
            city = text("city"),
            state = text("state"),
            country = text("country"),
            region = text("region"),
            postalCode = text("postal_code"),
            contactName = secret(PROPERTIES_COLUMN_CONTACT_NAME),
            contactEmail = secret(PROPERTIES_COLUMN_CONTACT_EMAIL),
            contactPhone = secret(PROPERTIES_COLUMN_CONTACT_PHONE),
            notes = text("notes"),
            availableFrom = text("available_from"),
            availableUntil = text("available_until"),
            accessCode = secret(PROPERTIES_COLUMN_ACCESS_CODE),
            status = text("status") ?: "ready",
            settings = text("settings")?.let { runCatching { AdcJson.decodeFromString(SettingsDto.serializer(), it) }.getOrNull() } ?: SettingsDto(),
        )
    }
}
