package com.example.sclerago.presentation.login

import com.example.sclerago.presentation.common.UiText
import com.example.sclerago.presentation.common.toUiText

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.sclerago.R
import com.example.sclerago.domain.model.AuthError
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LoginLogicTest {

    // ---- validateEmail ----

    @Test
    fun validateEmail_blank() {
        assertEquals(UiText.Res(R.string.login_error_email_blank), validateEmail(""))
        assertEquals(UiText.Res(R.string.login_error_email_blank), validateEmail("   "))
    }

    @Test
    fun validateEmail_invalid() {
        listOf("rashmi", "a@", "@b.com", "a b@c.com", "a@b").forEach {
            assertEquals(it, UiText.Res(R.string.login_error_email_invalid), validateEmail(it))
        }
    }

    @Test
    fun validateEmail_valid_isTrimmed() {
        assertNull(validateEmail("masteruser.brijesh@sclera.com"))
        assertNull(validateEmail("  first.last+qa@sub.example.co.uk  "))
    }

    // ---- accountsRejection: JSON that getRedirectUrlByEmail renders instead of redirecting ----

    private fun rejection(json: String) = accountsRejection(JSONObject(json))

    @Test
    fun successIsNotAnError() {
        assertNull(rejection("""{"status":200,"data":{}}"""))
        assertNull(rejection("""{"data":{}}"""))
    }

    @Test
    fun knownStatusUsesAuthErrorText() {
        assertEquals(AuthError.UserNotFound().toUiText(), rejection("""{"status":701,"message":"User not found"}"""))
        assertEquals(AuthError.UserBlocked().toUiText(), rejection("""{"status":706}"""))
    }

    @Test
    fun unknownStatusShowsServerMessage() {
        assertEquals(UiText.Raw("Domain not configured"), rejection("""{"status":702,"message":"Domain not configured"}"""))
    }

    @Test
    fun unknownStatusWithoutMessage() {
        assertEquals(UiText.Res(R.string.sso_error_status, listOf(702)), rejection("""{"status":702}"""))
    }
}
