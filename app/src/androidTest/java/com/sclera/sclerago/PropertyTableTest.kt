package com.sclera.sclerago

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sclera.sclerago.data.local.AesGcmSecretCipher
import com.sclera.sclerago.data.local.PropertyTable
import com.sclera.sclerago.data.remote.adc.PropertyDto
import com.sclera.sclerago.services.syncManager.GlobalConfigDatabaseHelper
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import javax.crypto.KeyGenerator

/**
 * The `properties` table in global_config.db on a device (the requery SQLite build is native, so this can't run on the
 * JVM). Uses the app's own database: it empties the properties table before and after.
 */
@RunWith(AndroidJUnit4::class)
class PropertyTableTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    private val table = PropertyTable(context, AesGcmSecretCipher { key })
    private val db get() = GlobalConfigDatabaseHelper.initializeInstance(context).globalDB

    private val full = PropertyDto(
        id = "p-1", name = "Harbour Point", type = "Commercial office", addressLine1 = "8 Marine Drive", city = "Mumbai",
        postalCode = "400020", contactName = "Rohan", contactEmail = "rohan@example.com", contactPhone = "+91 22 6600 1400",
        availableFrom = "00:45:00", availableUntil = "23:30:00", accessCode = "4321", status = "ready",
    )

    @Before
    fun clean() = table.clear()

    @After
    fun tearDown() = table.clear()

    @Test
    fun replace_thenRead_roundTripsTheProperty() {
        table.replace(listOf(full), organisationId = "85161001", synced = emptySet())
        assertEquals(listOf(full), table.read())
    }

    @Test
    fun read_keepsTheServersOrder() {
        table.replace(listOf(PropertyDto(id = "b", name = "beta"), PropertyDto(id = "a", name = "Alpha")), "o", emptySet())
        assertEquals(listOf("beta", "Alpha"), table.read().map { it.name })
    }

    @Test
    fun replace_marksOnlyDownloadedPropertiesSynced_andMarkSyncedMovesTheMark() {
        table.replace(listOf(full, PropertyDto(id = "p-2", name = "Hub")), "o", synced = setOf("p-2"))
        assertEquals(mapOf("p-1" to 0, "p-2" to 1), syncedFlags())
        table.markSynced(setOf("p-1"))
        assertEquals(mapOf("p-1" to 1, "p-2" to 0), syncedFlags())
    }

    @Test
    fun accessCodesAndContacts_areEncryptedAtRest() {
        table.replace(listOf(full), "o", emptySet())
        val plain = listOf(full.accessCode, full.contactName, full.contactEmail, full.contactPhone)
        db.rawQuery("SELECT access_code, contact_name, contact_email, contact_phone FROM properties", null).use { c ->
            assertTrue(c.moveToFirst())
            // Stored as AES-GCM ciphertext, never as the plain text.
            (0..3).forEach { assertNotEquals(plain[it], c.getString(it)) }
        }
    }

    @Test
    fun unreadableSecrets_comeBackEmpty() {
        table.replace(listOf(full), "o", emptySet())
        val otherKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        val row = PropertyTable(context, AesGcmSecretCipher { otherKey }).read().single()
        assertNull(row.accessCode)
        assertEquals("Harbour Point", row.name)
    }

    private fun syncedFlags(): Map<String, Int> =
        db.rawQuery("SELECT id, is_synced FROM properties", null).use { c ->
            buildMap { while (c.moveToNext()) put(c.getString(0), c.getInt(1)) }
        }
}
