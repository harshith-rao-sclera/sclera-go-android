package com.sclera.sclerago.testutil

import com.sclera.sclerago.data.local.AesGcmSecretCipher
import com.sclera.sclerago.data.local.SecretCipher
import com.sclera.sclerago.domain.model.AuthState
import com.sclera.sclerago.domain.model.UserSession
import com.sclera.sclerago.domain.repository.AuthRepository
import com.sclera.sclerago.domain.repository.UserDataCleaner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/** A real AES-GCM cipher with an in-memory key (the Android Keystore isn't available on the JVM). */
fun testCipher(key: SecretKey = newTestKey()): SecretCipher = AesGcmSecretCipher { key }

fun newTestKey(): SecretKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()

val TEST_USER = UserSession(email = "a@b.com", displayName = "Ada", role = "Admin")

/** An [AuthRepository] whose state the test sets directly. */
class FakeAuthRepository(initial: AuthState = AuthState.SignedIn(TEST_USER)) : AuthRepository {
    private val flow = MutableStateFlow(initial)
    override val state: StateFlow<AuthState> = flow

    var foregroundChecks = 0
    var logouts = 0

    fun set(state: AuthState) {
        flow.value = state
    }

    override suspend fun completeLogin(enteredEmail: String) = Unit
    override fun clearLoginCookies() = Unit
    override fun onForeground() {
        foregroundChecks++
    }

    override fun logout() {
        logouts++
        flow.value = AuthState.SignedOut()
    }
}

/** A [UserDataCleaner] that counts how often it is cleared. */
class CountingCleaner(private val fails: Boolean = false) : UserDataCleaner {
    var cleared = 0
    override fun clear() {
        cleared++
        if (fails) error("boom")
    }
}

/** Ids "prefix-1", "prefix-2", ... so tests can predict them. */
class SequentialIdGenerator : com.sclera.sclerago.domain.system.IdGenerator {
    private var n = 0
    override fun next(prefix: String): String = if (prefix.isEmpty()) "${++n}" else "$prefix-${++n}"
}

/** A clock the test moves by hand. */
class FakeTimeProvider(var now: Long = 1_700_000_000_000L) : com.sclera.sclerago.domain.system.TimeProvider {
    override fun nowMillis(): Long = now
}
