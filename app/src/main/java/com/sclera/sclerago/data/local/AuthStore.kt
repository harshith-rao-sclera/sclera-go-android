package com.sclera.sclerago.data.local

import android.content.Context
import com.sclera.sclerago.data.mapper.jwtPayload
import com.sclera.sclerago.data.mapper.nameFromUserInfo
import com.sclera.sclerago.data.mapper.roleFromClaims
import com.sclera.sclerago.data.mapper.roleFromUserInfo
import com.sclera.sclerago.data.remote.AuthConfig
import org.json.JSONObject

/** The persisted Sclera session (tokens and raw user details); the domain sees it as a UserSession. */
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

/** SharedPreferences "sclera_auth" (keys unchanged). */
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
