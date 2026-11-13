package com.sclera.sclerago.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sclera.sclerago.data.local.AuthSession
import com.sclera.sclerago.data.local.AuthStore
import com.sclera.sclerago.data.mapper.toDomain
import com.sclera.sclerago.testutil.newTestKey
import com.sclera.sclerago.testutil.testCipher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AuthStoreTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val key = newTestKey()
    private val store get() = AuthStore(context, testCipher(key))
    private val prefs get() = context.getSharedPreferences("sclera_auth", Context.MODE_PRIVATE)

    @Before
    fun clean() {
        prefs.edit().clear().commit()
    }

    @Test
    fun load_isNullWhenNothingSaved() = assertNull(store.load())

    @Test
    fun save_thenLoad_roundTrips() {
        val session = AuthSession("a@b.com", "sid-1", "token-1", 1_800_000_000_000L)
        store.save(session)
        assertEquals(session, store.load())
    }

    @Test
    fun save_withUserInfo_roundTrips() {
        val session = AuthSession("a@b.com", "sid-1", "token-1", 0L, userInfo = """{"role":"admin"}""")
        store.save(session)
        assertEquals(session, store.load())
    }

    @Test
    fun save_withMe_roundTripsAndGivesTheOrganisationId() {
        val me = """{"userId":"u-1","organisationId":"85161001","groupName":"Admins","permissions":["collect"]}"""
        val session = AuthSession("a@b.com", "sid-1", "token-1", 0L, userInfo = """{"role":"admin"}""", me = me)
        store.save(session)
        val loaded = store.load()!!
        assertEquals(session, loaded)
        assertEquals("85161001", loaded.organisationId)
        assertEquals("85161001", loaded.toDomain().organisationId)
    }

    @Test
    fun organisationId_isNullWithoutOrWithUnreadableMe() {
        assertNull(AuthSession("a@b.com", "s", null, 0L).organisationId)
        assertNull(AuthSession("a@b.com", "s", null, 0L, me = "not json").organisationId)
        assertNull(AuthSession("a@b.com", "s", null, 0L, me = """{"organisationId":null}""").organisationId)
    }

    @Test
    fun save_withoutToken_roundTrips() {
        val session = AuthSession("a@b.com", "sid-1", null, 0L)
        store.save(session)
        assertEquals(session, store.load())
    }

    @Test
    fun clear_removesSession() {
        store.save(AuthSession("a@b.com", "sid-1", "token-1", 0L))
        store.clear()
        assertNull(store.load())
    }

    @Test
    fun load_isNullWithoutSessionId() {
        prefs.edit().putString("email", "a@b.com").commit()
        assertNull(store.load())
    }

    // ---- MASVS-STORAGE

    @Test
    fun values_areEncryptedAtRest() {
        store.save(AuthSession("a@b.com", "sid-secret", "token-secret", 5L, userInfo = """{"name":"Ada"}""", me = """{"organisationId":"org-secret"}"""))
        val all = prefs.all.values.filterIsInstance<String>().joinToString("|")
        listOf("a@b.com", "sid-secret", "token-secret", "Ada", "org-secret").forEach { assertFalse("$it is stored in plain text", all.contains(it)) }
        assertTrue(prefs.getString("access_token", "")!!.startsWith("enc1:"))
    }

    @Test
    fun plainTextFromOlderVersions_isReadAndThenEncrypted() {
        prefs.edit().putString("email", "a@b.com").putString("session_id", "sid-1").putString("access_token", "old-token")
            .putLong("expires_at", 7L).commit()
        val loaded = store.load()
        assertEquals(AuthSession("a@b.com", "sid-1", "old-token", 7L), loaded)
        assertFalse(prefs.getString("access_token", "")!!.contains("old-token"))
        assertTrue(prefs.getString("email", "")!!.startsWith("enc1:"))
        assertEquals(loaded, store.load())
    }

    @Test
    fun unreadableValues_countAsNoSession() {
        store.save(AuthSession("a@b.com", "sid-1", "token-1", 0L))
        // The key is gone (e.g. the Keystore was reset): the old values can't be read any more.
        assertNull(AuthStore(context, testCipher(newTestKey())).load())
    }
}
