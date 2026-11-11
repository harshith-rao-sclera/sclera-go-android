package com.example.sclerago.auth

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AuthStoreTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val store = AuthStore(context)

    @Test
    fun load_isNullWhenNothingSaved() = assertNull(store.load())

    @Test
    fun save_thenLoad_roundTrips() {
        val session = AuthSession("a@b.com", "sid-1", "token-1", 1_800_000_000_000L)
        store.save(session)
        assertEquals(session, AuthStore(context).load())
    }

    @Test
    fun save_withUserInfo_roundTrips() {
        val session = AuthSession("a@b.com", "sid-1", "token-1", 0L, userInfo = """{"role":"admin"}""")
        store.save(session)
        assertEquals(session, AuthStore(context).load())
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
        context.getSharedPreferences("sclera_auth", Context.MODE_PRIVATE).edit().putString("email", "a@b.com").commit()
        assertNull(store.load())
    }
}
