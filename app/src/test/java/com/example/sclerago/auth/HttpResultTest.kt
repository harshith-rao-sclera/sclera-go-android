package com.example.sclerago.auth

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HttpResultTest {
    private fun result(code: Int, body: String?) = HttpResult(code, body?.let(::JSONObject))

    @Test
    fun success_withoutStatus_isNotAnError() {
        assertNull(result(200, """{"data":{}}""").error(authEndpoint = true))
        assertNull(result(204, null).error(authEndpoint = false))
    }

    @Test
    fun success_withOkBodyStatus_isNotAnError() {
        assertNull(result(200, """{"success":true,"status":200}""").error(authEndpoint = true))
    }

    @Test
    fun bodyStatus_sessionCodes_winOverHttpCode() {
        assertTrue(result(200, """{"status":821}""").error(authEndpoint = false) is AuthError.SessionExpired)
        assertTrue(result(500, """{"status":716}""").error(authEndpoint = false) is AuthError.MultipleSession)
        assertTrue(result(200, """{"status":706}""").error(authEndpoint = true) is AuthError.UserBlocked)
        assertTrue(result(404, """{"status":701}""").error(authEndpoint = true) is AuthError.UserNotFound)
    }

    @Test
    fun http200_withFailedBody_isServerErrorWithMessage() {
        val err = result(200, """{"success":false,"status":500,"message":"Token service down"}""").error(true)
        assertTrue(err is AuthError.Server)
        assertEquals("Token service down", (err as AuthError.Server).detail)
    }

    @Test
    fun http200_withFailedBodyAndNoMessage_reportsStatus() {
        val err = result(200, """{"success":false,"status":503}""").error(true) as AuthError.Server
        assertEquals("status 503", err.detail)
    }

    @Test
    fun authEndpoint_clientErrors_meanSessionExpired() {
        listOf(400, 401, 403).forEach { code ->
            assertTrue("HTTP $code", result(code, null).error(authEndpoint = true) is AuthError.SessionExpired)
        }
    }

    @Test
    fun otherEndpoint_clientErrors_areServerErrors() {
        val err = result(401, null).error(authEndpoint = false) as AuthError.Server
        assertEquals("HTTP 401", err.detail)
    }

    @Test
    fun serverError_prefersBodyMessage() {
        val err = result(500, """{"message":"Internal error"}""").error(authEndpoint = true) as AuthError.Server
        assertEquals("Internal error", err.detail)
    }

    @Test
    fun ok_and_bodyStatus() {
        assertTrue(result(299, null).ok)
        assertFalse(result(300, null).ok)
        assertEquals(-1, result(200, null).bodyStatus)
        assertEquals(716, result(200, """{"status":716}""").bodyStatus)
    }
}
