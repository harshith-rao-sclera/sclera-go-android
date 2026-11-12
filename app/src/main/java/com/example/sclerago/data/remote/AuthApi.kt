package com.example.sclerago.data.remote

import android.net.Uri
import com.example.sclerago.domain.model.AuthError
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Login contract shared with the Sclera web bundle / 2.0 frontend:
 *  1. `getRedirectUrlByEmail` forwards the user to their org's Keycloak login page (email as `login_hint`).
 *  2. After sign-in, accounts redirects back to [APP_URL] with a `sessionId` cookie on [ACCOUNTS_URL].
 *  3. `GET /sclera/api/session` (with that cookie) returns `data.sessionId`.
 *  4. `POST /sclera/api/token` (with that cookie) returns `data.accessToken` / `data.expiresAt`.
 *     The same call regenerates the token whenever it expires or an API answers 401/403.
 */
object AuthConfig {
    // QA environment (matches REACT_APP_ACCOUNTS_URL in the web app). Prod: accounts.sclera.com / app.sclera.com
    const val ACCOUNTS_URL = "https://qa-accounts.sclera.com"
    const val APP_URL = "https://qa-app.sclera.com"
    const val SCLERA_API = "$ACCOUNTS_URL/sclera/api"
    const val SESSION_URL = "$SCLERA_API/session"
    const val TOKEN_URL = "$SCLERA_API/token"

    /** Refresh the access token this long before it expires. */
    const val TOKEN_REFRESH_MARGIN_MS = 60_000L

    val accountsHost: String get() = Uri.parse(ACCOUNTS_URL).host.orEmpty()

    /**
     * Same call as the web app (`${authlink}/api/user/${email}/getRedirectUrlByEmail?domain=…`):
     * redirects to the Keycloak login page for the user's org with the email as `login_hint`.
     */
    fun loginUrl(email: String): String =
        "$SCLERA_API/user/${Uri.encode(email)}/getRedirectUrlByEmail?domain=${Uri.encode(APP_URL)}"

    /** User details (role, organisation…), as the Sclera app's `ApiEndpoints.USER_INFO_URL`. */
    fun userInfoUrl(email: String): String = "$SCLERA_API/userInfo?loggedInUser=${Uri.encode(email)}"

    fun logoutUrl(email: String): String =
        "$SCLERA_API/user/${Uri.encode(email)}/logoutUserByEmail?loggedInUser=${Uri.encode(email)}"

    /** True once SSO has sent the browser back to the app domain, i.e. login finished. */
    fun isReturnUrl(url: String?): Boolean =
        url != null && Uri.parse(url).host.equals(Uri.parse(APP_URL).host, ignoreCase = true)

    /** An accounts JSON API response rendered in the WebView (e.g. getRedirectUrlByEmail rejecting the email). */
    fun isAccountsApiUrl(url: String?): Boolean {
        val uri = url?.let(Uri::parse) ?: return false
        return uri.host.equals(accountsHost, ignoreCase = true) && uri.path.orEmpty().startsWith("/sclera/api/")
    }
}

internal class HttpResult(val code: Int, val json: JSONObject?) {
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

/** Blocking HTTP call; run it off the main thread. Connection problems surface as [AuthError.Network]. */
internal fun http(method: String, url: String, headers: Map<String, String>, body: String? = null): HttpResult {
    val conn = try {
        URL(url).openConnection() as HttpURLConnection
    } catch (e: IOException) {
        throw AuthError.Network(e)
    }
    try {
        conn.requestMethod = method
        conn.connectTimeout = 30_000
        conn.readTimeout = 30_000
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty("Accept", "application/json")
        headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
        if (method == "POST" || method == "PUT") {
            conn.doOutput = true
            conn.outputStream.use { it.write((body ?: "").toByteArray()) }
        }
        val code = conn.responseCode
        val stream = if (code < 400) conn.inputStream else conn.errorStream
        val text = stream?.bufferedReader()?.use { it.readText() }
        return HttpResult(code, text?.let { runCatching { JSONObject(it) }.getOrNull() })
    } catch (e: IOException) {
        throw AuthError.Network(e)
    } finally {
        conn.disconnect()
    }
}
