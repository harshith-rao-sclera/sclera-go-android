package com.sclera.sclerago.security

import com.sclera.sclerago.domain.model.AuthState
import com.sclera.sclerago.domain.usecase.auth.CheckSessionOnForegroundUseCase
import com.sclera.sclerago.domain.usecase.auth.ClearUserDataUseCase
import com.sclera.sclerago.domain.usecase.auth.LogoutUseCase
import com.sclera.sclerago.domain.usecase.auth.ObserveAuthStateUseCase
import com.sclera.sclerago.presentation.session.SessionViewModel
import com.sclera.sclerago.testutil.CountingCleaner
import com.sclera.sclerago.testutil.FakeAuthRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/** MASVS-STORAGE: when the session ends nothing of the user stays on the device. */
@OptIn(ExperimentalCoroutinesApi::class)
class UserDataWipeTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun clearUserData_clearsEveryCleaner() {
        val a = CountingCleaner()
        val b = CountingCleaner()
        ClearUserDataUseCase(setOf(a, b))()
        assertEquals(1, a.cleared)
        assertEquals(1, b.cleared)
    }

    @Test
    fun clearUserData_keepsGoingWhenOneCleanerFails() {
        val failing = CountingCleaner(fails = true)
        val ok = CountingCleaner()
        ClearUserDataUseCase(setOf(failing, ok))()
        assertEquals(1, ok.cleared)
    }

    private fun viewModel(repo: FakeAuthRepository, cleaner: CountingCleaner) = SessionViewModel(
        ObserveAuthStateUseCase(repo), CheckSessionOnForegroundUseCase(repo), LogoutUseCase(repo), ClearUserDataUseCase(setOf(cleaner)),
    )

    @Test
    fun signedIn_doesNotWipe() = runTest(dispatcher) {
        val cleaner = CountingCleaner()
        viewModel(FakeAuthRepository(), cleaner)
        advanceUntilIdle()
        assertEquals(0, cleaner.cleared)
    }

    @Test
    fun logout_wipes() = runTest(dispatcher) {
        val repo = FakeAuthRepository()
        val cleaner = CountingCleaner()
        val vm = viewModel(repo, cleaner)
        advanceUntilIdle()
        vm.logout()
        advanceUntilIdle()
        assertEquals(1, cleaner.cleared)
        assertEquals(1, repo.logouts)
    }

    @Test
    fun sessionEndedByTheServer_wipes() = runTest(dispatcher) {
        val repo = FakeAuthRepository()
        val cleaner = CountingCleaner()
        viewModel(repo, cleaner)
        advanceUntilIdle()
        repo.set(AuthState.SignedOut())
        advanceUntilIdle()
        assertEquals(1, cleaner.cleared)
    }

    @Test
    fun onForeground_checksTheSession() = runTest(dispatcher) {
        val repo = FakeAuthRepository()
        viewModel(repo, CountingCleaner()).onForeground()
        assertEquals(1, repo.foregroundChecks)
    }
}
