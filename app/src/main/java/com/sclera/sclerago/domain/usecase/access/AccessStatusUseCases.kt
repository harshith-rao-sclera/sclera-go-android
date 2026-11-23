package com.sclera.sclerago.domain.usecase.access

import com.sclera.sclerago.domain.model.AccessState
import com.sclera.sclerago.domain.repository.CurrentAccessRepository
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
