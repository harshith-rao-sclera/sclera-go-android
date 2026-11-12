package com.example.sclerago.presentation.common

import com.example.sclerago.domain.model.AuthError

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.sclerago.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AuthErrorTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun text(e: AuthError): String {
        val t = e.toUiText() as UiText.Res
        return context.getString(t.id, *t.args.toTypedArray())
    }

    @Test
    fun fromStatus_mapsBackendStatusCodes() {
        assertTrue(AuthError.fromStatus(821) is AuthError.SessionExpired)
        assertTrue(AuthError.fromStatus(716) is AuthError.MultipleSession)
        assertTrue(AuthError.fromStatus(706) is AuthError.UserBlocked)
        assertTrue(AuthError.fromStatus(701) is AuthError.UserNotFound)
    }

    @Test
    fun fromStatus_returnsNullForOtherCodes() {
        listOf(-1, 0, 200, 400, 401, 500, 700, 822).forEach { assertNull("status $it", AuthError.fromStatus(it)) }
    }

    @Test
    fun sessionEndingErrors_areFlagged() {
        assertTrue(AuthError.SessionExpired().endsSession)
        assertTrue(AuthError.MultipleSession().endsSession)
        assertTrue(AuthError.UserBlocked().endsSession)
        assertTrue(AuthError.UserNotFound().endsSession)
        assertFalse(AuthError.Network().endsSession)
        assertFalse(AuthError.Server("x").endsSession)
    }

    @Test
    fun messages_resolveToLocalizedStrings() {
        assertEquals(context.getString(R.string.auth_error_session_expired), text(AuthError.SessionExpired()))
        assertEquals(context.getString(R.string.auth_error_multiple_session), text(AuthError.MultipleSession()))
        assertEquals(context.getString(R.string.auth_error_user_blocked), text(AuthError.UserBlocked()))
        assertEquals(context.getString(R.string.auth_error_user_not_found), text(AuthError.UserNotFound()))
        assertEquals(context.getString(R.string.auth_error_network), text(AuthError.Network()))
    }

    @Test
    fun serverError_includesDetailInMessage() {
        val message = text(AuthError.Server("HTTP 502"))
        assertEquals("Something went wrong signing in (HTTP 502). Please try again.", message)
    }

    @Test
    fun network_keepsCause() {
        val cause = java.io.IOException("boom")
        assertEquals(cause, AuthError.Network(cause).cause)
    }
}
