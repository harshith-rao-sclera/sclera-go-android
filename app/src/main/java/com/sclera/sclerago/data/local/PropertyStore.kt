package com.sclera.sclerago.data.local

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import com.sclera.sclerago.services.syncManager.GlobalConfigDatabaseHelper as Db
import dagger.hilt.android.qualifiers.ApplicationContext
import io.requery.android.database.sqlite.SQLiteDatabase
import javax.inject.Inject

/** One row of the `properties` table: a property as the ADC API describes it, plus whether it is on the server. */
data class PropertyRecord(
    val id: String,
    val organisationId: String? = null,
    val name: String,
    val type: String? = null,
    val addressLine1: String? = null,
    val addressLine2: String? = null,
    val city: String? = null,
    val state: String? = null,
    val country: String? = null,
    val region: String? = null,
    val postalCode: String? = null,
    val contactName: String? = null,
    val contactEmail: String? = null,
    val contactPhone: String? = null,
    val notes: String? = null,
    /** "HH:mm:ss", as the API sends it. */
    val availableFrom: String? = null,
    val availableUntil: String? = null,
    val accessCode: String? = null,
    val status: String? = null,
    val statusDetail: String? = null,
    /** The API's `settings` object (onboarding switches, photo rules) as JSON text. */
    val settings: String? = null,
    val geofenceEnforced: Boolean = false,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    /** True for rows that came from (or have reached) the server; false for properties only on this device. */
    val isSynced: Boolean = false,
)

/** The saved properties. Blocking: call it off the main thread. */
interface PropertyStore {
    /** Every saved property, by name. */
    fun all(): List<PropertyRecord>

    /** Saves [records], replacing rows with the same id. */
    fun upsert(records: List<PropertyRecord>)

    /** Makes the synced rows exactly [records] (the server's full list), keeping properties not yet synced. */
    fun replaceSynced(records: List<PropertyRecord>)

    /** Deletes every property (sign-out). */
    fun clear()
}

/**
 * [PropertyStore] on the `properties` table of the sync manager's `global_config.db`. Access codes and
 * contact details are sensitive (MASVS-STORAGE), so they are stored encrypted with [cipher]
 * (`enc1:` + ciphertext, as in [AuthStore]); the other columns are plain.
 */
class GlobalConfigPropertyStore(
    private val db: () -> SQLiteDatabase,
    private val cipher: SecretCipher,
) : PropertyStore {

    @Inject constructor(@ApplicationContext context: Context, cipher: SecretCipher) :
        this({ Db.initializeInstance(context).globalDB }, cipher)

    override fun all(): List<PropertyRecord> =
        db().query(Db.TABLE_NAME_PROPERTIES, null, null, null, null, null, "${Db.PROPERTIES_COLUMN_NAME} COLLATE NOCASE").use { c ->
            buildList { while (c.moveToNext()) add(c.toRecord()) }
        }

    override fun upsert(records: List<PropertyRecord>) = inTransaction { records.forEach(::insert) }

    override fun replaceSynced(records: List<PropertyRecord>) = inTransaction {
        db().delete(Db.TABLE_NAME_PROPERTIES, "${Db.PROPERTIES_COLUMN_IS_SYNCED} = 1", null)
        records.forEach { insert(it.copy(isSynced = true)) }
    }

    override fun clear() {
        db().delete(Db.TABLE_NAME_PROPERTIES, null, null)
    }

    private fun inTransaction(block: () -> Unit) {
        val d = db()
        d.beginTransaction()
        try {
            block()
            d.setTransactionSuccessful()
        } finally {
            d.endTransaction()
        }
    }

    private fun insert(r: PropertyRecord) {
        val v = ContentValues().apply {
            put(Db.PROPERTIES_COLUMN_ID, r.id)
            put(Db.PROPERTIES_COLUMN_ORGANISATION_ID, r.organisationId)
            put(Db.PROPERTIES_COLUMN_NAME, r.name)
            put(Db.PROPERTIES_COLUMN_TYPE, r.type)
            put(Db.PROPERTIES_COLUMN_ADDRESS_LINE1, r.addressLine1)
            put(Db.PROPERTIES_COLUMN_ADDRESS_LINE2, r.addressLine2)
            put(Db.PROPERTIES_COLUMN_CITY, r.city)
            put(Db.PROPERTIES_COLUMN_STATE, r.state)
            put(Db.PROPERTIES_COLUMN_COUNTRY, r.country)
            put(Db.PROPERTIES_COLUMN_REGION, r.region)
            put(Db.PROPERTIES_COLUMN_POSTAL_CODE, r.postalCode)
            put(Db.PROPERTIES_COLUMN_CONTACT_NAME, seal(r.contactName))
            put(Db.PROPERTIES_COLUMN_CONTACT_EMAIL, seal(r.contactEmail))
            put(Db.PROPERTIES_COLUMN_CONTACT_PHONE, seal(r.contactPhone))
            put(Db.PROPERTIES_COLUMN_NOTES, r.notes)
            put(Db.PROPERTIES_COLUMN_AVAILABLE_FROM, r.availableFrom)
            put(Db.PROPERTIES_COLUMN_AVAILABLE_UNTIL, r.availableUntil)
            put(Db.PROPERTIES_COLUMN_ACCESS_CODE, seal(r.accessCode))
            put(Db.PROPERTIES_COLUMN_STATUS, r.status)
            put(Db.PROPERTIES_COLUMN_STATUS_DETAIL, r.statusDetail)
            put(Db.PROPERTIES_COLUMN_SETTINGS, r.settings)
            put(Db.PROPERTIES_COLUMN_GEOFENCE_ENFORCED, if (r.geofenceEnforced) 1 else 0)
            put(Db.PROPERTIES_COLUMN_CREATED_AT, r.createdAt)
            put(Db.PROPERTIES_COLUMN_UPDATED_AT, r.updatedAt)
            put(Db.PROPERTIES_COLUMN_IS_SYNCED, if (r.isSynced) 1 else 0)
        }
        db().insertWithOnConflict(Db.TABLE_NAME_PROPERTIES, null, v, SQLiteDatabase.CONFLICT_REPLACE)
    }

    private fun Cursor.text(column: String): String? = getColumnIndex(column).takeIf { it >= 0 && !isNull(it) }?.let { getString(it) }

    private fun Cursor.flag(column: String): Boolean = getColumnIndex(column).takeIf { it >= 0 && !isNull(it) }?.let { getInt(it) } == 1

    private fun Cursor.toRecord() = PropertyRecord(
        id = text(Db.PROPERTIES_COLUMN_ID).orEmpty(),
        organisationId = text(Db.PROPERTIES_COLUMN_ORGANISATION_ID),
        name = text(Db.PROPERTIES_COLUMN_NAME).orEmpty(),
        type = text(Db.PROPERTIES_COLUMN_TYPE),
        addressLine1 = text(Db.PROPERTIES_COLUMN_ADDRESS_LINE1),
        addressLine2 = text(Db.PROPERTIES_COLUMN_ADDRESS_LINE2),
        city = text(Db.PROPERTIES_COLUMN_CITY),
        state = text(Db.PROPERTIES_COLUMN_STATE),
        country = text(Db.PROPERTIES_COLUMN_COUNTRY),
        region = text(Db.PROPERTIES_COLUMN_REGION),
        postalCode = text(Db.PROPERTIES_COLUMN_POSTAL_CODE),
        contactName = open(text(Db.PROPERTIES_COLUMN_CONTACT_NAME)),
        contactEmail = open(text(Db.PROPERTIES_COLUMN_CONTACT_EMAIL)),
        contactPhone = open(text(Db.PROPERTIES_COLUMN_CONTACT_PHONE)),
        notes = text(Db.PROPERTIES_COLUMN_NOTES),
        availableFrom = text(Db.PROPERTIES_COLUMN_AVAILABLE_FROM),
        availableUntil = text(Db.PROPERTIES_COLUMN_AVAILABLE_UNTIL),
        accessCode = open(text(Db.PROPERTIES_COLUMN_ACCESS_CODE)),
        status = text(Db.PROPERTIES_COLUMN_STATUS),
        statusDetail = text(Db.PROPERTIES_COLUMN_STATUS_DETAIL),
        settings = text(Db.PROPERTIES_COLUMN_SETTINGS),
        geofenceEnforced = flag(Db.PROPERTIES_COLUMN_GEOFENCE_ENFORCED),
        createdAt = text(Db.PROPERTIES_COLUMN_CREATED_AT),
        updatedAt = text(Db.PROPERTIES_COLUMN_UPDATED_AT),
        isSynced = flag(Db.PROPERTIES_COLUMN_IS_SYNCED),
    )

    private fun seal(plain: String?): String? = plain?.let { SEALED + cipher.encrypt(it) }

    /** The stored text decrypted; null when it can no longer be read (e.g. the key was lost). */
    private fun open(stored: String?): String? =
        stored?.let { if (it.startsWith(SEALED)) cipher.decrypt(it.removePrefix(SEALED)) else it }

    private companion object {
        const val SEALED = "enc1:"
    }
}
