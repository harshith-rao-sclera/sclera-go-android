package com.example.sclerago.auth

import android.content.Context
import android.net.Uri
import android.util.Base64
import androidx.annotation.StringRes
import com.example.sclerago.R
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

data class AuthSession(
    val email: String,
    val sessionId: String,
    val accessToken: String?,
    /** Epoch millis; 0 when unknown. */
    val expiresAt: Long,
    /** The `data` object of `/sclera/api/userInfo` as JSON text; null until it has been fetched. */
    val userInfo: String? = null,
) {
    fun tokenValid(now: Long = System.currentTimeMillis()): Boolean =
        accessToken != null && (expiresAt == 0L || expiresAt - now > AuthConfig.TOKEN_REFRESH_MARGIN_MS)

    private val info: JSONObject? get() = userInfo?.let { runCatching { JSONObject(it) }.getOrNull() }

    /**
     * The user's role for display (e.g. "Admin"): from the user details (`/sclera/api/userInfo`),
     * else from the token's claims; null when neither has one.
     */
    val role: String?
        get() = info?.let(::roleFromUserInfo) ?: jwtPayload(accessToken)?.let(::roleFromClaims)

    /** Name from the user details, else the JWT, falling back to the email (as getUser() in the 2.0 frontend). */
    val displayName: String
        get() = info?.let(::nameFromUserInfo)
            ?: jwtPayload(accessToken)?.let { p -> p.optString("name").ifBlank { p.optString("given_name") } }?.ifBlank { null }
            ?: email
}

/** "admin" / "SUPER_ADMIN" / "field-engineer" → "Admin" / "Super admin" / "Field engineer". */
internal fun prettyRole(raw: String): String =
    raw.trim().replace('_', ' ').replace('-', ' ').lowercase().replaceFirstChar { it.uppercase() }

/** Role from the userInfo `data` object (the web app keeps it as `role`). */
internal fun roleFromUserInfo(data: JSONObject): String? =
    sequenceOf("role", "roleName", "role_name", "userRole", "user_role")
        .map { data.optString(it).trim() }.firstOrNull { it.isNotEmpty() && it != "null" }?.let(::prettyRole)

/** Full name from the userInfo `data` object, if it has one. */
internal fun nameFromUserInfo(data: JSONObject): String? {
    data.optString("name").trim().takeIf { it.isNotEmpty() && it != "null" }?.let { return it }
    val first = sequenceOf("first_name", "firstName", "given_name").map { data.optString(it).trim() }.firstOrNull { it.isNotEmpty() && it != "null" }
    val last = sequenceOf("last_name", "lastName", "family_name").map { data.optString(it).trim() }.firstOrNull { it.isNotEmpty() && it != "null" }
    return listOfNotNull(first, last).joinToString(" ").ifEmpty { null }
}

/**
 * Every way auth can fail. [endsSession] errors send the user back to the login page.
 * [messageRes] / [messageArgs] are the localized, user-facing text; the exception message is
 * English and meant for logs only.
 */
sealed class AuthError(
    message: String,
    val endsSession: Boolean,
    @param:StringRes @get:StringRes val messageRes: Int,
    val messageArgs: List<Any> = emptyList(),
    cause: Throwable? = null,
) : Exception(message, cause) {
    class SessionExpired : AuthError("session expired", true, R.string.auth_error_session_expired)
    class MultipleSession : AuthError("signed in elsewhere", true, R.string.auth_error_multiple_session)
    class UserBlocked : AuthError("user blocked", true, R.string.auth_error_user_blocked)
    class UserNotFound : AuthError("user not found", true, R.string.auth_error_user_not_found)
    class Network(cause: Throwable? = null) : AuthError("network error", false, R.string.auth_error_network, cause = cause)
    class Server(val detail: String) : AuthError("server error: $detail", false, R.string.auth_error_server, listOf(detail))

    companion object {
        // Body status codes used by the Sclera backend (see fn() in the web bundle).
        const val STATUS_USER_NOT_FOUND = 701
        const val STATUS_USER_BLOCKED = 706
        const val STATUS_MULTIPLE_SESSION = 716
        const val STATUS_SESSION_EXPIRED = 821

        fun fromStatus(status: Int): AuthError? = when (status) {
            STATUS_SESSION_EXPIRED -> SessionExpired()
            STATUS_MULTIPLE_SESSION -> MultipleSession()
            STATUS_USER_BLOCKED -> UserBlocked()
            STATUS_USER_NOT_FOUND -> UserNotFound()
            else -> null
        }
    }
}

class AuthStore(context: Context) {
    private val prefs = context.getSharedPreferences("sclera_auth", Context.MODE_PRIVATE)

    fun load(): AuthSession? {
        val email = prefs.getString(KEY_EMAIL, null) ?: return null
        val sessionId = prefs.getString(KEY_SESSION, null) ?: return null
        return AuthSession(
            email, sessionId, prefs.getString(KEY_TOKEN, null), prefs.getLong(KEY_EXPIRES, 0L), prefs.getString(KEY_USER_INFO, null),
        )
    }

    fun save(session: AuthSession) {
        prefs.edit()
            .putString(KEY_EMAIL, session.email)
            .putString(KEY_SESSION, session.sessionId)
            .putString(KEY_TOKEN, session.accessToken)
            .putLong(KEY_EXPIRES, session.expiresAt)
            .putString(KEY_USER_INFO, session.userInfo)
            .apply()
    }

    fun clear() = prefs.edit().clear().apply()

    private companion object {
        const val KEY_EMAIL = "email"
        const val KEY_SESSION = "session_id"
        const val KEY_TOKEN = "access_token"
        const val KEY_EXPIRES = "expires_at"
        const val KEY_USER_INFO = "user_info"
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

internal fun jwtPayload(token: String?): JSONObject? = runCatching {
    val part = token!!.split('.')[1]
    JSONObject(String(Base64.decode(part, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)))
}.getOrNull()

/** Roles every Keycloak user has; they say nothing about what the user is. */
private val BUILT_IN_ROLES = setOf(
    "offline_access", "uma_authorization", "manage-account", "manage-account-links", "view-profile", "delete-account",
)

/** Client id of the Sclera app in Keycloak (`client_id=sclera` in the login URL); its roles win. */
private const val KEYCLOAK_CLIENT = "sclera"

/**
 * The user's role from the token, looked up where Keycloak and the Sclera backend may put it, in
 * this order: `role` / `userRole` / `user_role`, `roles`, the Sclera client's roles
 * (`resource_access.sclera.roles`), other clients' roles, realm roles (`realm_access.roles`), then
 * group membership (`groups`, e.g. "/Admins"). Built-in Keycloak roles are skipped.
 */
internal fun roleFromClaims(claims: JSONObject): String? {
    fun JSONObject.strings(key: String): List<String> {
        optJSONArray(key)?.let { a -> return (0 until a.length()).map { a.optString(it) } }
        return optString(key).takeIf { it.isNotBlank() }?.let { listOf(it) } ?: emptyList()
    }
    val clients = claims.optJSONObject("resource_access")
    val candidates = buildList {
        listOf("role", "userRole", "user_role", "roles").forEach { addAll(claims.strings(it)) }
        clients?.optJSONObject(KEYCLOAK_CLIENT)?.let { addAll(it.strings("roles")) }
        clients?.keys()?.asSequence()?.filter { it != KEYCLOAK_CLIENT }?.forEach { c -> clients.optJSONObject(c)?.let { addAll(it.strings("roles")) } }
        claims.optJSONObject("realm_access")?.let { addAll(it.strings("roles")) }
        addAll(claims.strings("groups").map { it.substringAfterLast('/') })
    }
    return candidates.map { it.trim() }
        .firstOrNull { it.isNotEmpty() && it !in BUILT_IN_ROLES && !it.startsWith("default-roles") }
        ?.let(::prettyRole)
}

/** Debug aid: the claim names in [token] and any role-like claims, for logcat (never the token). */
internal fun describeClaims(token: String?): String {
    val p = jwtPayload(token) ?: return "token is not a readable JWT"
    val roleish = listOf("role", "userRole", "user_role", "roles", "realm_access", "resource_access", "groups")
        .filter { p.has(it) }.joinToString { "$it=${p.opt(it)}" }
    return "claims=${p.keys().asSequence().toList()} role-like: ${roleish.ifEmpty { "none" }}"
}

/** Value of cookie [name] in a `Cookie` header string, or null. */
internal fun cookieValue(cookies: String, name: String): String? =
    cookies.split(';').map { it.trim() }.firstOrNull { it.startsWith("$name=") }?.substringAfter('=')

/**
 * The signed-in user's email: from the `/session` data, else the token's `email`,
 * `preferred_username` (Keycloak) or `upn` claim — whichever first looks like an email.
 */
internal fun signedInEmail(sessionData: JSONObject, token: String?): String? {
    val claims = jwtPayload(token)
    return sequenceOf(
        sessionData.optString("email"),
        sessionData.optString("emailId"),
        claims?.optString("email"),
        claims?.optString("preferred_username"),
        claims?.optString("upn"),
    ).firstOrNull { !it.isNullOrBlank() && '@' in it }
}

/** Token expiry in epoch millis from `expiresAt` (seconds or millis) or the JWT `exp` claim; 0 if unknown. */
internal fun tokenExpiry(data: JSONObject, token: String): Long {
    val raw = data.optLong("expiresAt", 0L).takeIf { it > 0 } ?: jwtPayload(token)?.optLong("exp", 0L) ?: 0L
    return if (raw in 1 until 100_000_000_000L) raw * 1000 else raw
}
