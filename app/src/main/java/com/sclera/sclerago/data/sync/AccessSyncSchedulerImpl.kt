package com.sclera.sclerago.data.sync

import com.sclera.sclerago.di.ApplicationScope
import com.sclera.sclerago.domain.model.AuthState
import com.sclera.sclerago.domain.repository.AccessSyncScheduler
import com.sclera.sclerago.domain.repository.AuthRepository
import com.sclera.sclerago.domain.usecase.access.RecheckAccessUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads `GET /me` and the property list ([RecheckAccessUseCase]) as soon as the app is on screen and every
 * [INTERVAL_MS] after, while signed in. A new property list re-checks the geofence (`PremiseRepositoryImpl` follows
 * every list fresh from the server), so a switched-on geofence or new fence coordinates apply within one interval.
 * Nothing runs while the app is off screen (no background work, no location in the background).
 */
@Singleton
class AccessSyncSchedulerImpl @Inject constructor(
    private val recheck: RecheckAccessUseCase,
    private val auth: AuthRepository,
    @ApplicationScope private val scope: CoroutineScope,
) : AccessSyncScheduler {
    private var job: Job? = null

    @Synchronized
    override fun start() {
        job?.cancel()
        job = scope.launch {
            while (isActive) {
                if (auth.state.value is AuthState.SignedIn) recheck()
                delay(INTERVAL_MS)
            }
        }
    }

    @Synchronized
    override fun stop() {
        job?.cancel()
        job = null
    }

    private companion object {
        /** How often the access and the property list are read again while the app is on screen. */
        const val INTERVAL_MS = 30_000L
    }
}
