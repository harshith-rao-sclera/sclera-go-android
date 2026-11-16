package com.sclera.sclerago.data.remote.adc

import com.sclera.sclerago.di.IoDispatcher
import com.sclera.sclerago.domain.model.OnlineError
import com.sclera.sclerago.domain.model.OnlineException
import com.sclera.sclerago.domain.repository.NetworkMonitor
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.ResponseBody
import retrofit2.Response
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** The JSON settings every parse uses: the API may add keys, and a missing optional one is its default. */
internal val AdcJson = Json { ignoreUnknownKeys = true; coerceInputValues = true }

/** The client could not get an access token for a call: the session is over. Mapped to [OnlineError.SessionExpired]. */
class AdcSessionException(cause: Throwable? = null) : IOException("session ended", cause)

/**
 * Runs one call to the ADC API: checks the connection first, and turns every failure into an
 * [OnlineException] ([OnlineError]) so no server text, status line or body reaches a screen or a log.
 */
@Singleton
class OnlineCall @Inject constructor(
    private val network: NetworkMonitor,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    /** The parsed JSON body of [request]. */
    suspend fun <T> json(request: suspend () -> Response<ResponseBody>, parse: (String) -> T): T = withContext(io) {
        val text = send(request)
        try {
            parse(text)
        } catch (e: SerializationException) {
            throw OnlineException(OnlineError.Server, e)
        } catch (e: IllegalArgumentException) {
            throw OnlineException(OnlineError.Server, e)
        }
    }

    /** A call with nothing to read back (`204`). */
    suspend fun unit(request: suspend () -> Response<ResponseBody>) {
        withContext(io) { send(request) }
    }

    private suspend fun send(request: suspend () -> Response<ResponseBody>): String {
        if (!network.isOnline()) throw OnlineException(OnlineError.Offline)
        val response = try {
            request()
        } catch (e: AdcSessionException) {
            throw OnlineException(OnlineError.SessionExpired, e)
        } catch (e: IOException) {
            throw OnlineException(OnlineError.Offline, e)
        }
        return try {
            if (response.isSuccessful) response.body()?.use { it.string() }.orEmpty()
            else throw OnlineException(errorFor(response.code(), response.headers()["Retry-After"], response.errorBody()?.use { it.string() }))
        } catch (e: IOException) {
            throw OnlineException(OnlineError.Offline, e)
        }
    }

    internal companion object {
        /** The [OnlineError] for an HTTP failure; [body] is the RFC 9457 problem JSON, read only for its `type`. */
        fun errorFor(status: Int, retryAfter: String?, body: String?): OnlineError = when (status) {
            401 -> OnlineError.SessionExpired
            403 -> OnlineError.Forbidden
            404 -> OnlineError.NotFound
            409, 412 -> OnlineError.Conflict(problemCode(body) ?: "conflict")
            400, 422 -> OnlineError.Invalid
            503 -> OnlineError.Unavailable(retryAfter?.trim()?.toIntOrNull()?.coerceIn(0, 300) ?: 0)
            else -> OnlineError.Server
        }

        private fun problemCode(body: String?): String? = runCatching {
            (AdcJson.parseToJsonElement(body ?: return null) as? JsonObject)?.get("type")?.jsonPrimitive?.contentOrNull
        }.getOrNull()?.substringAfterLast(':')?.takeIf { it.isNotBlank() }
    }
}
