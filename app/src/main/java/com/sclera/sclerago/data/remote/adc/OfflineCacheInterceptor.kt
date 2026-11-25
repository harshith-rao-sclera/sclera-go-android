package com.sclera.sclerago.data.remote.adc

import com.sclera.sclerago.data.local.ResponseCache
import com.sclera.sclerago.domain.repository.NetworkMonitor
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** Whose answers the response cache keeps right now (the signed-in user and organisation); null when nobody's yet. */
fun interface ResponseCacheScope {
    fun current(): String?
}

/** Lets [OnlineCall] send calls without a connection, because something in front of the network can answer some of them. */
interface OfflineReads {
    /** True when calls should still reach the client offline (it answers or refuses them itself). */
    val enabled: Boolean

    companion object {
        /** No offline answers: [OnlineCall] refuses every call without a connection, before it is sent. */
        val None = object : OfflineReads {
            override val enabled = false
        }
    }
}

/**
 * Network first, then the last answer: every ADC read the cache may keep ([ResponseCachePolicy]) goes to the server when
 * there is a connection, and a good JSON answer is saved ([ResponseCache], encrypted) for the signed-in user. Without a
 * connection, or when the connection drops on the way, the last saved answer is given back as if the server had sent it,
 * so screens show the freshest data the device has seen. With nothing saved (or a call the cache doesn't keep) it fails as
 * before, an [IOException] that [OnlineCall] reports as offline. A call never goes out (nor asks for a token) offline.
 *
 * Only a lost connection falls back. Any answer from the server (401, 403, 404, 5xx) is given as it is, so a change of
 * access shows; and a session that ended ([AdcSessionException]) is never covered up. Nothing about a call is logged.
 *
 * It is the client's first interceptor, so it sits in front of the token handling.
 */
@Singleton
class OfflineCacheInterceptor @Inject constructor(
    private val network: NetworkMonitor,
    private val cache: ResponseCache,
    private val scope: ResponseCacheScope,
) : Interceptor, OfflineReads {

    override val enabled = true

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val key = if (ResponseCachePolicy.keeps(request)) scope.current()?.let { ResponseCachePolicy.key(it, request) } else null
        if (!network.isOnline()) return key?.let { cached(request, it) } ?: throw IOException(OFFLINE)
        val response = try {
            chain.proceed(request)
        } catch (e: AdcSessionException) {
            throw e
        } catch (e: IOException) {
            return key?.let { cached(request, it) } ?: throw e
        }
        if (key != null) save(key, response)
        return response
    }

    /** Keeps a successful JSON answer that isn't too big; saving never fails the call. */
    private fun save(key: String, response: Response) {
        if (response.code != 200) return
        val type = response.body?.contentType() ?: return
        if (!type.isJson()) return
        runCatching {
            val peeked = response.peekBody(MAX_BODY_BYTES + 1)
            if (peeked.contentLength() <= MAX_BODY_BYTES) cache.write(key, peeked.string())
        }
    }

    /** The last answer saved for [key], as a `200` the caller reads like the server's; null when there is none. */
    private fun cached(request: Request, key: String): Response? {
        val text = cache.read(key) ?: return null
        return Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(text.toResponseBody(JSON))
            .build()
    }

    private fun okhttp3.MediaType.isJson() = subtype == "json" || subtype.endsWith("+json")

    private companion object {
        const val OFFLINE = "offline"

        /** The largest answer kept (a property list or a user directory page is far smaller). */
        const val MAX_BODY_BYTES = 4L * 1024 * 1024

        val JSON = "application/json; charset=utf-8".toMediaType()
    }
}

/**
 * Which ADC calls the response cache keeps, and the key each is kept under. Only reads (`GET`) of the ADC API, never
 * writes, and not these reads:
 * - file content and file downloads (`…/content`, import templates, `…/export`, `asset-icons/….svg`): streamed or binary,
 *   and asset media already has its own cache;
 * - the status of a server job (`lookup-jobs/…`, `validation-jobs/{job}`): it is polled until it finishes, and an old
 *   "still running" answer offline would keep it polling instead of saying there is no connection.
 * The server's answer must also be JSON (checked when it arrives).
 */
internal object ResponseCachePolicy {
    fun keeps(request: Request): Boolean {
        if (request.method != "GET") return false
        if (!request.url.toString().startsWith(AdcConfig.API_URL)) return false
        val segments = request.url.pathSegments.filter { it.isNotEmpty() }
        val last = segments.lastOrNull() ?: return false
        if (last in FILES || last.endsWith(".svg")) return false
        if ("lookup-jobs" in segments) return false
        val jobs = segments.indexOf("validation-jobs")
        if (jobs >= 0 && segments.size == jobs + 2) return false
        return true
    }

    /** One key per user, organisation, method, host, path and query. */
    fun key(scope: String, request: Request): String =
        listOf(scope, request.method, request.url.host, request.url.encodedPath, request.url.encodedQuery.orEmpty()).joinToString("\n")

    private val FILES = setOf("content", "template", "export")
}
