package com.sclera.sclerago.domain.usecase.access

import com.sclera.sclerago.domain.model.AccessState
import com.sclera.sclerago.domain.model.OnlineException
import com.sclera.sclerago.domain.repository.AccessRefusals
import com.sclera.sclerago.domain.repository.CurrentAccessRepository
import com.sclera.sclerago.domain.repository.PropertyRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * The signed-in user's access couldn't be fetched and none was kept on the device: until it is, nothing that needs a
 * permission is offered (see [PermissionGuard]). The screens say so and offer [RetryAccessUseCase].
 */
class ObserveAccessFailedUseCase @Inject constructor(private val repo: CurrentAccessRepository) {
    operator fun invoke(): Flow<Boolean> = repo.state.map { it is AccessState.Failed }.distinctUntilChanged()
}

/**
 * The app came back to the foreground: the signed-in user's access is fetched again, so a group or permission an admin
 * changed meanwhile applies at once (pages and buttons follow it; see [ObserveAccessChangesUseCase]).
 */
class RefreshAccessOnForegroundUseCase @Inject constructor(private val repo: CurrentAccessRepository) {
    operator fun invoke() = repo.refresh()
}

/**
 * Reads the signed-in user's access and the property list again, so a group, permission or property an admin changed
 * applies while the app stays open: after a refused call, and every few minutes while it is on screen. Offline or
 * refused, what is known stays.
 */
class RecheckAccessUseCase @Inject constructor(
    private val access: CurrentAccessRepository,
    private val properties: PropertyRepository,
) {
    suspend operator fun invoke() {
        access.refresh()
        try {
            properties.refresh()
        } catch (_: OnlineException) {
            // The list shown stays.
        }
    }
}

/** Every call the server refused (403), so the access is read again ([RecheckAccessUseCase]). */
class ObserveAccessRefusalsUseCase @Inject constructor(private val refusals: AccessRefusals) {
    operator fun invoke(): Flow<Unit> = refusals.refused
}

/** Fetches the signed-in user's access again after it failed. */
class RetryAccessUseCase @Inject constructor(private val repo: CurrentAccessRepository) {
    operator fun invoke() = repo.retry()
}

/**
 * Every change to the signed-in user's access (fetched, failed, changed), so a screen re-reads the permission checks
 * it shows (who may see a page, a button, a change) as soon as they change, not only when it opens.
 */
class ObserveAccessChangesUseCase @Inject constructor(private val guard: PermissionGuard) {
    operator fun invoke(): Flow<AccessState> = guard.changes()
}
