package com.sclera.sclerago.data.local

import android.content.Context
import com.sclera.sclerago.data.mapper.avatarFromUserInfo
import com.sclera.sclerago.data.mapper.jwtPayload
import com.sclera.sclerago.data.mapper.nameFromUserInfo
import com.sclera.sclerago.data.mapper.organisationFromUserInfo
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

    /** The user's organisation from the user details; null until they have been fetched. */
    val organisation: String?
        get() = info?.let(::organisationFromUserInfo)

    /** Profile photo URL from the user details; null when there is none. */
    val avatarUrl: String?
        get() = info?.let(::avatarFromUserInfo)

    /** Name from the user details, else the JWT, falling back to the email (as getUser() in the 2.0 frontend). */
    val displayName: String
        get() = info?.let(::nameFromUserInfo)
            ?: jwtPayload(accessToken)?.let { p -> p.optString("name").ifBlank { p.optString("given_name") } }?.ifBlank { null }
            ?: email
}

/**
 * The saved session, in SharedPreferences "sclera_auth" (keys unchanged). Every value is encrypted with [cipher]
 * (`enc1:` + ciphertext). Values saved in plain text by older versions are still read, and are
 * re-saved encrypted the first time they are loaded; a value that can no longer be decrypted counts as no session.
 */
class AuthStore(context: Context, private val cipher: SecretCipher) {
    private val prefs = context.getSharedPreferences("sclera_auth", Context.MODE_PRIVATE)

    fun load(): AuthSession? {
        val email = readText(KEY_EMAIL) ?: return null
        val sessionId = readText(KEY_SESSION) ?: return null
        val session = AuthSession(
            email, sessionId, readText(KEY_TOKEN), prefs.getLong(KEY_EXPIRES, 0L), readText(KEY_USER_INFO),
        )
        // Values from older versions were plain text: encrypt them now.
        if (isPlain(KEY_EMAIL) || isPlain(KEY_SESSION) || isPlain(KEY_TOKEN) || isPlain(KEY_USER_INFO)) save(session)
        return session
    }

    fun save(session: AuthSession) {
        prefs.edit()
            .putString(KEY_EMAIL, seal(session.email))
            .putString(KEY_SESSION, seal(session.sessionId))
            .putString(KEY_TOKEN, session.accessToken?.let(::seal))
            .putLong(KEY_EXPIRES, session.expiresAt)
            .putString(KEY_USER_INFO, session.userInfo?.let(::seal))
            .apply()
    }

    fun clear() = prefs.edit().clear().apply()

    private fun seal(plain: String) = PREFIX + cipher.encrypt(plain)

    private fun isPlain(key: String): Boolean = prefs.getString(key, null)?.startsWith(PREFIX) == false

    /** The stored text for [key]: decrypted, or as-is when an older version saved it in plain text; null if absent or unreadable. */
    private fun readText(key: String): String? {
        val raw = prefs.getString(key, null) ?: return null
        return if (raw.startsWith(PREFIX)) cipher.decrypt(raw.removePrefix(PREFIX)) else raw
    }

    private companion object {
        const val PREFIX = "enc1:"
        const val KEY_EMAIL = "email"
        const val KEY_SESSION = "session_id"
        const val KEY_TOKEN = "access_token"
        const val KEY_EXPIRES = "expires_at"
        const val KEY_USER_INFO = "user_info"
    }
}
