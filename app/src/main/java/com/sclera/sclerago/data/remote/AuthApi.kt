package com.sclera.sclerago.data.remote

import android.net.Uri
import com.sclera.sclerago.BuildConfig
import com.sclera.sclerago.domain.model.AuthError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody
import org.json.JSONObject
import retrofit2.Response
import retrofit2.Retrofit
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Login contract shared with the Sclera web bundle / 2.0 frontend:
 *  1. `getRedirectUrlByEmail` forwards the user to their org's Keycloak login page (email as `login_hint`).
 *  2. After sign-in, accounts redirects back to [APP_URL] with a `sessionId` cookie on [ACCOUNTS_URL].
 *  3. `GET /sclera/api/session` (with that cookie) returns `data.sessionId`.
 *  4. `POST /sclera/api/token` (with that cookie) returns `data.accessToken` / `data.expiresAt`.
 *     The same call regenerates the token whenever it expires or an API answers 401/403.
 */
object AuthConfig {
    // Set per build type in app/build.gradle.kts: QA for debug builds, production for release builds.
    const val ACCOUNTS_URL = BuildConfig.ACCOUNTS_URL
    const val APP_URL = BuildConfig.APP_URL
    const val SCLERA_API = "$ACCOUNTS_URL/sclera/api"

    /** Refresh the access token this long before it expires. */
    const val TOKEN_REFRESH_MARGIN_MS = 60_000L

    val accountsHost: String get() = Uri.parse(ACCOUNTS_URL).host.orEmpty()

    /** The Sclera domain (e.g. `sclera.com`): the sign-in web view may only load https pages from it and its sub-domains. */
    val trustedDomain: String get() = accountsHost.substringAfter('.', accountsHost)

    fun isTrustedHost(host: String?): Boolean {
        val h = host?.lowercase() ?: return false
        return h == trustedDomain || h.endsWith(".$trustedDomain")
    }

    /**
     * Same call as the web app (`${authlink}/api/user/${email}/getRedirectUrlByEmail?domain=…`):
     * redirects to the Keycloak login page for the user's org with the email as `login_hint`.
     */
    fun loginUrl(email: String): String =
        "$SCLERA_API/user/${Uri.encode(email)}/getRedirectUrlByEmail?domain=${Uri.encode(APP_URL)}"

    /** True once SSO has sent the browser back to the app domain, i.e. login finished. */
    fun isReturnUrl(url: String?): Boolean =
        url != null && Uri.parse(url).host.equals(Uri.parse(APP_URL).host, ignoreCase = true)

    /** An accounts JSON API response rendered in the WebView (e.g. getRedirectUrlByEmail rejecting the email). */
    fun isAccountsApiUrl(url: String?): Boolean {
        val uri = url?.let(Uri::parse) ?: return false
        return uri.host.equals(accountsHost, ignoreCase = true) && uri.path.orEmpty().startsWith("/sclera/api/")
    }
}

class HttpResult(val code: Int, val json: JSONObject?) {
    val ok get() = code in 200..299
    /** The backend's own status code in the body, if any. */
    val bodyStatus get() = json?.optInt("status", -1) ?: -1

    /**
     * Maps a response to an [AuthError], or null when it succeeded.
     * [authEndpoint]: for session/token calls a bare 400/401/403 means the session is no longer valid
     * (the native AuthInterceptor treats 400 from the session URL the same way).
     */
    fun error(authEndpoint: Boolean): AuthError? {
        AuthError.fromStatus(bodyStatus)?.let { return it }
        if (ok) {
            val failed = json != null && json.has("success") && bodyStatus != -1 && bodyStatus !in 200..299
            return if (failed) AuthError.Server(message() ?: "status $bodyStatus") else null
        }
        if (authEndpoint && code in setOf(400, 401, 403)) return AuthError.SessionExpired()
        return AuthError.Server(message() ?: "HTTP $code")
    }

    private fun message(): String? = json?.optString("message")?.takeIf { it.isNotBlank() }
}

internal fun bearer(token: String) = "Bearer $token"

/**
 * Runs one Retrofit call off the main thread and reads its JSON body (error bodies too). Connection
 * problems surface as [AuthError.Network]; every HTTP status comes back as an [HttpResult].
 */
internal suspend fun httpCall(request: suspend () -> Response<ResponseBody>): HttpResult = withContext(Dispatchers.IO) {
    val response = try {
        request()
    } catch (e: IOException) {
        throw AuthError.Network(e)
    }
    val text = try {
        (if (response.isSuccessful) response.body() else response.errorBody())?.use { it.string() }
    } catch (e: IOException) {
        throw AuthError.Network(e)
    }
    HttpResult(response.code(), text?.let { runCatching { JSONObject(it) }.getOrNull() })
}

/**
 * Calls to the Sclera accounts API over [ScleraApiService]. Main-safe; connection problems surface
 * as [AuthError.Network], everything else as an [HttpResult] for the caller to interpret.
 */
@Singleton
class AuthApi @Inject constructor(private val service: ScleraApiService) {

    suspend fun session(cookie: String): HttpResult = call { service.session(cookie) }

    suspend fun token(cookie: String): HttpResult = call { service.token(cookie) }

    suspend fun userInfo(email: String, accessToken: String, cookie: String): HttpResult =
        call { service.userInfo(Uri.encode(email), bearer(accessToken), cookie) }

    suspend fun logout(email: String, headers: Map<String, String>): HttpResult =
        call { service.logout(Uri.encode(email), Uri.encode(email), headers) }

    /** Authenticated [method] call to an absolute Sclera API [url] with an optional JSON [body]. */
    suspend fun authenticated(method: String, url: String, accessToken: String, body: String? = null): HttpResult {
        val auth = bearer(accessToken)
        val json = (body ?: "").toByteArray().toRequestBody(JSON)
        return call {
            when (method.uppercase()) {
                "GET" -> service.get(url, auth)
                "POST" -> service.post(url, auth, json)
                "PUT" -> service.put(url, auth, json)
                "DELETE" -> service.delete(url, auth)
                else -> throw IllegalArgumentException("Unsupported HTTP method $method")
            }
        }
    }

    private suspend fun call(request: suspend () -> Response<ResponseBody>): HttpResult = httpCall(request)

    companion object {
        private val JSON = "application/json".toMediaType()
        private const val TIMEOUT_SECONDS = 30L

        /** The client every Sclera call uses: JSON headers and 30 s timeouts, as before Retrofit. */
        fun okHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                chain.proceed(
                    chain.request().newBuilder()
                        .header("Content-Type", "application/json")
                        .header("Accept", "application/json")
                        .build(),
                )
            }
            .build()

        /** [ScleraApiService] rooted at [baseUrl] (must end in `/`). */
        fun service(baseUrl: String = AuthConfig.SCLERA_API + "/", client: OkHttpClient = okHttpClient()): ScleraApiService =
            Retrofit.Builder().baseUrl(baseUrl).client(client).build().create(ScleraApiService::class.java)
    }
}
