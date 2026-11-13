package com.sclera.sclerago

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sclera.sclerago.data.local.AesGcmSecretCipher
import com.sclera.sclerago.data.local.GlobalConfigPropertyStore
import com.sclera.sclerago.data.local.PropertyRecord
import com.sclera.sclerago.services.syncManager.GlobalConfigDatabaseHelper
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import javax.crypto.KeyGenerator

/**
 * The `properties` table in global_config.db on a device (the requery SQLite build is native, so this
 * can't run on the JVM). Uses the app's own database: it empties the properties table before and after.
 */
@RunWith(AndroidJUnit4::class)
class GlobalConfigPropertyStoreTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    private val store = GlobalConfigPropertyStore(context, AesGcmSecretCipher { key })
    private val db get() = GlobalConfigDatabaseHelper.initializeInstance(context).globalDB

    private val full = PropertyRecord(
        id = "p-1", organisationId = "85161001", name = "Harbour Point", type = "Commercial office",
        addressLine1 = "8 Marine Drive", city = "Mumbai", postalCode = "400020",
        contactName = "Rohan", contactEmail = "rohan@example.com", contactPhone = "+91 22 6600 1400",
        availableFrom = "00:45:00", availableUntil = "23:30:00", accessCode = "4321", status = "ready",
        settings = """{"bluetoothOnboarding":true}""", geofenceEnforced = true,
        createdAt = "2026-11-12T10:08:17Z", updatedAt = "2026-11-13T09:39:15Z", isSynced = true,
    )

    @Before
    fun clean() = store.clear()

    @After
    fun tearDown() = store.clear()

    @Test
    fun upsert_thenAll_roundTripsEveryColumn() {
        store.upsert(listOf(full))
        assertEquals(listOf(full), store.all())
    }

    @Test
    fun all_isSortedByName() {
        store.upsert(listOf(PropertyRecord(id = "b", name = "beta"), PropertyRecord(id = "a", name = "Alpha")))
        assertEquals(listOf("Alpha", "beta"), store.all().map { it.name })
    }

    @Test
    fun replaceSynced_keepsDeviceOnlyProperties() {
        store.upsert(listOf(full, PropertyRecord(id = "local", name = "Mine", isSynced = false)))
        store.replaceSynced(listOf(PropertyRecord(id = "p-2", name = "Hub")))
        val rows = store.all().associateBy { it.id }
        assertEquals(setOf("local", "p-2"), rows.keys)
        assertFalse(rows.getValue("local").isSynced)
        assertTrue(rows.getValue("p-2").isSynced)
    }

    @Test
    fun accessCodesAndContacts_areEncryptedAtRest() {
        store.upsert(listOf(full))
        db.rawQuery("SELECT access_code, contact_name, contact_email, contact_phone, is_synced FROM properties", null).use { c ->
            assertTrue(c.moveToFirst())
            (0..3).forEach { assertTrue(c.getString(it).startsWith("enc1:")) }
            assertEquals(1, c.getInt(4))
        }
    }

    @Test
    fun unreadableSecrets_comeBackEmpty() {
        store.upsert(listOf(full))
        val otherKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        val row = GlobalConfigPropertyStore(context, AesGcmSecretCipher { otherKey }).all().single()
        assertNull(row.accessCode)
        assertEquals("Harbour Point", row.name)
    }

    @Test
    fun isSynced_onlyAllowsZeroOrOne() {
        val bad = runCatching { db.execSQL("INSERT INTO properties (id, name, is_synced) VALUES ('x', 'X', 2)") }
        assertTrue(bad.isFailure)
    }
}
