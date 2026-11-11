package com.example.sclerago.auth

import android.content.Context
import android.net.Uri
import android.util.Log
import android.webkit.CookieManager
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
import kotlinx.coroutines.withContext
import org.json.JSONObject

sealed interface AuthState {
    /** [reason] explains why the user landed on the login page (expired session, blocked, …). */
    data class SignedOut(val reason: AuthError? = null) : AuthState
    data class SignedIn(val session: AuthSession) : AuthState
}

/**
 * Owns the Sclera session: login completion, access-token generation/regeneration,
 * session validation and sign-out. Any error with [AuthError.endsSession] moves the app
 * to [AuthState.SignedOut], which MainActivity renders as the login page.
 */
object AuthManager {
    private const val TAG = "ScleraAuth"
    private const val FOREGROUND_CHECK_INTERVAL_MS = 60_000L
    private const val REFRESH_RETRY_MS = 30_000L

    private lateinit var store: AuthStore
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val tokenMutex = Mutex()
    private var refreshJob: Job? = null
    private var lastSessionCheck = 0L

    private val _state = MutableStateFlow<AuthState>(AuthState.SignedOut())
    val state: StateFlow<AuthState> = _state.asStateFlow()

    private val session: AuthSession? get() = (_state.value as? AuthState.SignedIn)?.session

    fun init(context: Context) {
        if (::store.isInitialized) return
        store = AuthStore(context.applicationContext)
        val saved = store.load() ?: return
        Log.i(TAG, "Restored saved session")
        // Open straight into the app (works offline); the session is validated and the user
        // details refreshed in the background.
        _state.value = AuthState.SignedIn(saved)
        scheduleRefresh(saved)
        scope.launch {
            validateSession(force = true)
            refreshUserInfo()
        }
    }

    // ---- Login -------------------------------------------------------------------------------

    /**
     * Called (on the main thread) once the SSO WebView has returned to the app domain.
     * Reads the session id, generates the first access token and persists both.
     * Call [enter] with the result to switch the app to [AuthState.SignedIn].
     */
    suspend fun completeLogin(enteredEmail: String): AuthSession {
        val cookies = CookieManager.getInstance().run { flush(); getCookie(AuthConfig.ACCOUNTS_URL) }
            ?: throw AuthError.Server("no session cookie from ${AuthConfig.accountsHost}")

        val sessionData = withContext(Dispatchers.IO) {
            val res = http("GET", AuthConfig.SESSION_URL, mapOf("Cookie" to cookies))
            res.error(authEndpoint = true)?.let { throw it }
            res.json?.optJSONObject("data") ?: JSONObject()
        }
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

    // ---- User details ------------------------------------------------------------------------

    /**
     * [s] with the user details from `GET /sclera/api/userInfo?loggedInUser=<email>` (bearer token
     * + session cookie, as the Sclera app calls it); [s] unchanged when the call fails.
     */
    private suspend fun withUserInfo(s: AuthSession): AuthSession {
        val token = s.accessToken ?: return s
        return try {
            val res = withContext(Dispatchers.IO) {
                http("GET", AuthConfig.userInfoUrl(s.email), mapOf("Authorization" to "Bearer $token", "Cookie" to sessionCookie(s)))
            }
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
            _state.value = AuthState.SignedIn(updated)
        }
    }

    fun enter(session: AuthSession) {
        _state.value = AuthState.SignedIn(session)
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

        suspend fun call(token: String) = withContext(Dispatchers.IO) {
            http(method, fullUrl, mapOf("Authorization" to "Bearer $token"), body?.toString())
        }

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
                _state.value = AuthState.SignedIn(fresh)
                scheduleRefresh(fresh)
            }
        }
    } catch (e: AuthError) {
        if (e.endsSession) signOut(e)
        throw e
    }

    /** POST /sclera/api/token with the session cookie (same call the web app and native interceptor use). */
    private suspend fun requestToken(current: AuthSession): AuthSession = withContext(Dispatchers.IO) {
        val res = http("POST", AuthConfig.TOKEN_URL, mapOf("Cookie" to sessionCookie(current)))
        res.error(authEndpoint = true)?.let { throw it }
        val data = res.json?.optJSONObject("data") ?: throw AuthError.Server("token response had no data")
        val token = data.optString("accessToken").ifEmpty { throw AuthError.Server("token response had no accessToken") }
        val expiresAt = tokenExpiry(data, token)
        Log.i(TAG, "Access token generated, expires ${if (expiresAt > 0) java.util.Date(expiresAt) else "unknown"}")
        current.copy(accessToken = token, expiresAt = expiresAt)
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
    fun onForeground() {
        scope.launch { validateSession(force = false) }
    }

    private suspend fun validateSession(force: Boolean) {
        val current = session ?: return
        val now = System.currentTimeMillis()
        if (!force && now - lastSessionCheck < FOREGROUND_CHECK_INTERVAL_MS) return
        lastSessionCheck = now
        try {
            val res = withContext(Dispatchers.IO) {
                http("GET", AuthConfig.SESSION_URL, mapOf("Cookie" to sessionCookie(current)))
            }
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
    fun logout() {
        val current = session ?: return
        signOut(null)
        scope.launch(Dispatchers.IO) {
            runCatching {
                val headers = buildMap {
                    put("Cookie", sessionCookie(current))
                    current.accessToken?.let { put("Authorization", "Bearer $it") }
                }
                http("POST", AuthConfig.logoutUrl(current.email), headers)
            }.onFailure { Log.w(TAG, "Server logout failed: ${it.message}") }
        }
    }

    /** Clears the local session and sends the user to the login page with [reason]. */
    fun signOut(reason: AuthError?) {
        if (_state.value is AuthState.SignedOut) return
        Log.i(TAG, "Signed out${reason?.let { ": ${it.message}" } ?: ""}")
        refreshJob?.cancel()
        store.clear()
        CookieManager.getInstance().apply { removeAllCookies(null); flush() }
        _state.value = AuthState.SignedOut(reason)
    }

    // ---- Helpers -----------------------------------------------------------------------------

    /** The accounts cookies from the SSO WebView, or a bare `sessionId` cookie like the legacy native app. */
    private fun sessionCookie(s: AuthSession): String {
        val jar = CookieManager.getInstance().getCookie(AuthConfig.ACCOUNTS_URL)
        return if (jar != null && cookieValue(jar, "sessionId") == s.sessionId) jar else "sessionId=${s.sessionId}"
    }
}
