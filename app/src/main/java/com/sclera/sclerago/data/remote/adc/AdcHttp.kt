package com.sclera.sclerago.data.remote.adc

import com.sclera.sclerago.domain.model.AuthError
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import java.io.IOException
import java.util.concurrent.TimeUnit

/** The Sclera access token (implemented by the auth repository, which renews it and ends the session when it can't). */
interface AccessTokenProvider {
    /** A valid token; pass the one that was just [rejected] to force a new one. Throws [AuthError]. */
    suspend fun accessToken(rejected: String? = null): String
}

private const val BEARER = "Bearer "

/** The token the call was sent with, if any. */
private fun Request.sentToken(): String? = header("Authorization")?.takeIf { it.startsWith(BEARER) }?.removePrefix(BEARER)

/** [AuthError] → what OkHttp understands: a session-ending error is an [AdcSessionException]; anything else (no connection, server trouble) an [IOException]. */
private fun AuthError.asIoException(): IOException = if (endsSession) AdcSessionException(this) else IOException("token unavailable", this)

/**
 * Adds `Authorization: Bearer <token>` to every call (the only place the token is sent; never in a URL).
 *
 * OkHttp calls interceptors on its own worker thread, so the token (which may need a network refresh) is
 * fetched with [runBlocking]: that blocks only that worker, never the main thread or a coroutine dispatcher.
 */
class AdcAuthInterceptor(private val tokens: AccessTokenProvider) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val token = try {
            runBlocking { tokens.accessToken() }
        } catch (e: AuthError) {
            throw e.asIoException()
        }
        return chain.proceed(chain.request().newBuilder().header("Authorization", BEARER + token).build())
    }
}

/**
 * On a 401 asks for a new token (the rejected one is passed so it is really replaced) and retries the call once.
 * It gives up (returns null, so the caller sees the 401) when the call was already a retry or the token did not
 * change. A 403 is never retried: it means "not allowed", not "token expired".
 *
 * Runs on an OkHttp worker thread, so it blocks with [runBlocking] as [AdcAuthInterceptor] does.
 */
class AdcAuthenticator(private val tokens: AccessTokenProvider) : Authenticator {
    override fun authenticate(route: Route?, response: Response): Request? {
        if (response.priorResponse != null) return null
        val rejected = response.request.sentToken()
        val fresh = try {
            runBlocking { tokens.accessToken(rejected = rejected) }
        } catch (e: AuthError) {
            if (!e.endsSession) throw e.asIoException()
            return null
        }
        if (fresh == rejected) return null
        return response.request.newBuilder().header("Authorization", BEARER + fresh).build()
    }
}

/** The client for the ADC API: the shared client plus the token handling above and 30 s timeouts. No logging interceptor. */
fun OkHttpClient.forAdc(tokens: AccessTokenProvider): OkHttpClient = newBuilder()
    .connectTimeout(AdcConfig.TIMEOUT_SECONDS, TimeUnit.SECONDS)
    .readTimeout(AdcConfig.TIMEOUT_SECONDS, TimeUnit.SECONDS)
    .writeTimeout(AdcConfig.TIMEOUT_SECONDS, TimeUnit.SECONDS)
    .addInterceptor(AdcAuthInterceptor(tokens))
    .authenticator(AdcAuthenticator(tokens))
    .build()
