package com.sclera.sclerago.domain.usecase

import com.sclera.sclerago.domain.model.AuthError
import com.sclera.sclerago.domain.model.AuthState
import com.sclera.sclerago.domain.usecase.property.SyncPropertiesUseCase
import com.sclera.sclerago.testutil.FakeAuthRepository
import com.sclera.sclerago.testutil.FakePropertyRepository
import com.sclera.sclerago.testutil.TEST_USER
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** Properties load whenever the signed-in user's organisation becomes known or changes. */
@OptIn(ExperimentalCoroutinesApi::class)
class SyncPropertiesUseCaseTest {
    private val auth = FakeAuthRepository(AuthState.SignedIn(TEST_USER))
    private val repo = FakePropertyRepository()

    private fun signedIn(org: String?) = AuthState.SignedIn(TEST_USER.copy(organisationId = org))

    @Test
    fun loadsOncePerOrganisation() = runTest {
        val job = launch { SyncPropertiesUseCase(auth, repo)() }
        advanceUntilIdle()
        assertEquals(emptyList<String>(), repo.refreshed) // no organisation id yet

        auth.set(signedIn("org-1"))
        advanceUntilIdle()
        auth.set(signedIn("org-1").copy(session = TEST_USER.copy(organisationId = "org-1", displayName = "New name")))
        advanceUntilIdle()
        auth.set(signedIn("org-2"))
        advanceUntilIdle()
        assertEquals(listOf("org-1", "org-2"), repo.refreshed)

        auth.set(AuthState.SignedOut())
        auth.set(signedIn("org-1"))
        advanceUntilIdle()
        assertEquals(listOf("org-1", "org-2", "org-1"), repo.refreshed)
        job.cancel()
    }

    @Test
    fun aFailedLoad_isNotFatal() = runTest {
        repo.failure = AuthError.Network()
        val job = launch { SyncPropertiesUseCase(auth, repo)() }
        auth.set(signedIn("org-1"))
        advanceUntilIdle()
        auth.set(signedIn("org-2"))
        advanceUntilIdle()
        assertEquals(listOf("org-1", "org-2"), repo.refreshed)
        job.cancel()
    }
}
