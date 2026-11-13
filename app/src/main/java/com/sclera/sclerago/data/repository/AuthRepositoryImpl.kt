package com.sclera.sclerago.data.repository

import android.net.Uri
import android.util.Log
import android.webkit.CookieManager
import com.sclera.sclerago.data.local.AuthSession
import com.sclera.sclerago.data.local.AuthStore
import com.sclera.sclerago.data.mapper.cookieValue
import com.sclera.sclerago.data.mapper.roleFromUserInfo
import com.sclera.sclerago.data.mapper.signedInEmail
import com.sclera.sclerago.data.mapper.toDomain
import com.sclera.sclerago.data.mapper.tokenExpiry
import com.sclera.sclerago.data.remote.AuthConfig
import com.sclera.sclerago.data.remote.AuthApi
import com.sclera.sclerago.domain.model.AuthError
import com.sclera.sclerago.domain.model.AuthState
import com.sclera.sclerago.domain.repository.AuthRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject

/**
 * Owns the Sclera session (formerly `AuthManager`): login completion, access-token
 * generation/regeneration, session validation and sign-out. Any error with
 * [AuthError.endsSession] moves the app to [AuthState.SignedOut], which MainActivity renders as the
 * login page. One instance per process (provided by Hilt, see di/AppModule).
 */
class AuthRepositoryImpl(
    private val store: AuthStore,
    private val api: AuthApi,
    mainDispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
) : AuthRepository {
    private companion object {
        const val TAG = "ScleraAuth"
        const val FOREGROUND_CHECK_INTERVAL_MS = 60_000L
        const val REFRESH_RETRY_MS = 30_000L
    }

    private val scope = CoroutineScope(SupervisorJob() + mainDispatcher)
    private val tokenMutex = Mutex()
    private var refreshJob: Job? = null
    private var lastSessionCheck = 0L

    private val _state = MutableStateFlow<AuthState>(AuthState.SignedOut())
    override val state: StateFlow<AuthState> = _state.asStateFlow()

    /** The persisted session behind [AuthState.SignedIn]; null when signed out. */
    private var current: AuthSession? = null

    private val session: AuthSession? get() = current

    private fun publishSignedIn(s: AuthSession) {
        current = s
        _state.value = AuthState.SignedIn(s.toDomain())
    }

    private fun publishSignedOut(reason: AuthError?) {
        current = null
        _state.value = AuthState.SignedOut(reason)
    }

    /** Restores the saved session (once, at app start), like the former `AuthManager.init`. */
    fun restore() {
        val saved = store.load() ?: return
        Log.i(TAG, "Restored saved session")
        // Open straight into the app (works offline); the session is validated and the user
        // details refreshed in the background.
        publishSignedIn(saved)
        scheduleRefresh(saved)
        scope.launch {
            validateSession(force = true)
            refreshUserInfo()
        }
    }

    // ---- Login -------------------------------------------------------------------------------

    override suspend fun completeLogin(enteredEmail: String) {
        enter(completeLoginSession(enteredEmail))
    }

    /**
     * Called (on the main thread) once the SSO WebView has returned to the app domain.
     * Reads the session id, generates the first access token and persists both.
     */
    private suspend fun completeLoginSession(enteredEmail: String): AuthSession {
        val cookies = CookieManager.getInstance().run { flush(); getCookie(AuthConfig.ACCOUNTS_URL) }
            ?: throw AuthError.Server("no session cookie from ${AuthConfig.accountsHost}")

        val sessionRes = api.session(cookies)
        sessionRes.error(authEndpoint = true)?.let { throw it }
        val sessionData = sessionRes.json?.optJSONObject("data") ?: JSONObject()
        val sessionId = sessionData.optString("sessionId")
            .ifEmpty { cookieValue(cookies, "sessionId").orEmpty() }
            .ifEmpty { throw AuthError.Server("session response had no sessionId") }
        Log.i(TAG, "Session established")

        val withToken = requestToken(AuthSession(enteredEmail, sessionId, null, 0L))
        // Prefer the account Keycloak actually signed in (the user can change it on that page).
        val signedIn = withToken.copy(email = signedInEmail(sessionData, withToken.accessToken) ?: enteredEmail)
        // Role, name… come from userInfo; signing in still works without them.
        val session = withUserInfo(signedIn)
        store.save(session)
        return session
    }

    override fun clearLoginCookies() {
        CookieManager.getInstance().removeAllCookies(null)
    }

    // ---- User details ------------------------------------------------------------------------

    /**
     * [s] with the user details from `GET /sclera/api/userInfo?loggedInUser=<email>` (bearer token
     * + session cookie, as the Sclera app calls it); [s] unchanged when the call fails.
     */
    private suspend fun withUserInfo(s: AuthSession): AuthSession {
        val token = s.accessToken ?: return s
        return try {
            val res = api.userInfo(s.email, token, sessionCookie(s))
            val data = res.json?.optJSONObject("data")
            if (res.error(authEndpoint = false) != null || data == null) {
                Log.w(TAG, "userInfo: HTTP ${res.code}, status ${res.bodyStatus}, data=${data != null}")
                s
            } else {
                // Debug builds only (Log.d is stripped from release): which fields userInfo has.
                Log.d(TAG, "userInfo fields=${data.keys().asSequence().toList()} → role=${roleFromUserInfo(data)}")
                s.copy(userInfo = data.toString())
            }
        } catch (e: AuthError) {
            Log.w(TAG, "userInfo failed: ${e.message}")
            s
        }
    }

    /** Re-reads the user details for the signed-in user and keeps them if the session is unchanged. */
    private suspend fun refreshUserInfo() {
        val current = session ?: return
        val fresh = withUserInfo(current)
        if (fresh.userInfo != current.userInfo && session?.sessionId == current.sessionId) {
            val updated = session!!.copy(userInfo = fresh.userInfo)
            store.save(updated)
            publishSignedIn(updated)
        }
    }

    private fun enter(session: AuthSession) {
        publishSignedIn(session)
        lastSessionCheck = System.currentTimeMillis()
        scheduleRefresh(session)
    }

    // ---- Tokens ------------------------------------------------------------------------------

    /**
     * A valid access token, regenerating it when missing/expired. Pass the token that was just
     * rejected (401/403) as [rejected] to force a new one; concurrent callers share one refresh.
     */
    suspend fun accessToken(rejected: String? = null): String = tokenMutex.withLock {
        val current = session ?: throw AuthError.SessionExpired()
        val token = current.accessToken
        if (token != null && token != rejected && current.tokenValid()) return token
        refreshLocked(current).accessToken!!
    }

    /**
     * Authenticated call to a Sclera API: adds the Bearer token and `loggedInUser`, regenerates
     * the token and retries once on 401/403, and signs out on session-ending errors.
     */
    suspend fun request(method: String, url: String, body: JSONObject? = null): JSONObject {
        val email = session?.email ?: throw AuthError.SessionExpired()
        val fullUrl = Uri.parse(url).buildUpon().appendQueryParameter("loggedInUser", email).build().toString()

        suspend fun call(token: String) = api.authenticated(method, fullUrl, token, body?.toString())

        var token = accessToken()
        var res = call(token)
        if (res.code == 401 || res.code == 403) {
            Log.i(TAG, "$method ${Uri.parse(url).path} → ${res.code}; regenerating token")
            token = accessToken(rejected = token)
            res = call(token)
        }
        res.error(authEndpoint = false)?.let { err -> if (err.endsSession) signOut(err); throw err }
        return res.json ?: JSONObject()
    }

    private suspend fun refreshLocked(current: AuthSession): AuthSession = try {
        requestToken(current).also { fresh ->
            if (session?.sessionId == fresh.sessionId) {
                store.save(fresh)
                publishSignedIn(fresh)
                scheduleRefresh(fresh)
            }
        }
    } catch (e: AuthError) {
        if (e.endsSession) signOut(e)
        throw e
    }

    /** POST /sclera/api/token with the session cookie (same call the web app and native interceptor use). */
    private suspend fun requestToken(current: AuthSession): AuthSession {
        val res = api.token(sessionCookie(current))
        res.error(authEndpoint = true)?.let { throw it }
        val data = res.json?.optJSONObject("data") ?: throw AuthError.Server("token response had no data")
        val token = data.optString("accessToken").ifEmpty { throw AuthError.Server("token response had no accessToken") }
        val expiresAt = tokenExpiry(data, token)
        Log.i(TAG, "Access token generated, expires ${if (expiresAt > 0) java.util.Date(expiresAt) else "unknown"}")
        return current.copy(accessToken = token, expiresAt = expiresAt)
    }

    /** Regenerate the token shortly before it expires, so API calls rarely hit a 401. */
    private fun scheduleRefresh(s: AuthSession) {
        refreshJob?.cancel()
        if (s.expiresAt == 0L) return
        refreshJob = scope.launch {
            var wait = s.expiresAt - AuthConfig.TOKEN_REFRESH_MARGIN_MS - System.currentTimeMillis()
            while (true) {
                if (wait > 0) delay(wait)
                try {
                    tokenMutex.withLock { session?.let { if (!it.tokenValid()) refreshLocked(it) } }
                    return@launch // a successful refresh schedules the next one
                } catch (e: AuthError) {
                    if (e.endsSession) return@launch
                    Log.w(TAG, "Token refresh failed, retrying: ${e.message}")
                    wait = REFRESH_RETRY_MS
                }
            }
        }
    }

    // ---- Session validation ------------------------------------------------------------------

    /** Call when the app comes to the foreground; mirrors the web app's visibilitychange session check. */
    override fun onForeground() {
        scope.launch { validateSession(force = false) }
    }

    private suspend fun validateSession(force: Boolean) {
        val current = session ?: return
        val now = System.currentTimeMillis()
        if (!force && now - lastSessionCheck < FOREGROUND_CHECK_INTERVAL_MS) return
        lastSessionCheck = now
        try {
            val res = api.session(sessionCookie(current))
            res.error(authEndpoint = true)?.let { throw it }
            if (!current.tokenValid()) accessToken()
        } catch (e: AuthError) {
            if (e.endsSession) {
                Log.w(TAG, "Session no longer valid: ${e.message}")
                signOut(e)
            } else {
                Log.w(TAG, "Session check skipped: ${e.message}") // offline: keep working
            }
        }
    }

    // ---- Sign out ----------------------------------------------------------------------------

    /** User-initiated logout: tells accounts to end the session (best effort), then clears it locally. */
    override fun logout() {
        val current = session ?: return
        signOut(null)
        scope.launch {
            runCatching {
                val headers = buildMap {
                    put("Cookie", sessionCookie(current))
                    current.accessToken?.let { put("Authorization", "Bearer $it") }
                }
                api.logout(current.email, headers)
            }.onFailure { Log.w(TAG, "Server logout failed: ${it.message}") }
        }
    }

    /** Clears the local session and sends the user to the login page with [reason]. */
    private fun signOut(reason: AuthError?) {
        if (_state.value is AuthState.SignedOut) return
        Log.i(TAG, "Signed out${reason?.let { ": ${it.message}" } ?: ""}")
        refreshJob?.cancel()
        store.clear()
        CookieManager.getInstance().apply { removeAllCookies(null); flush() }
        publishSignedOut(reason)
    }

    // ---- Helpers -----------------------------------------------------------------------------

    /** The accounts cookies from the SSO WebView, or a bare `sessionId` cookie like the legacy native app. */
    private fun sessionCookie(s: AuthSession): String {
        val jar = CookieManager.getInstance().getCookie(AuthConfig.ACCOUNTS_URL)
        return if (jar != null && cookieValue(jar, "sessionId") == s.sessionId) jar else "sessionId=${s.sessionId}"
    }
}
