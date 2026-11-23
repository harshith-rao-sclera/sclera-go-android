package com.sclera.sclerago.domain.repository

import com.sclera.sclerago.domain.model.AccessState
import kotlinx.coroutines.flow.StateFlow

/** What the signed-in user may do (the server's `GET /me`). */
interface CurrentAccessRepository {
    /** [AccessState.Loading] until fetched after sign-in; the last copy kept on the device shows first when there is one. */
    val state: StateFlow<AccessState>

    /** Fetches the access again after it [failed][AccessState.Failed]; does nothing while signed out. */
    fun retry() {}

    /**
     * Fetches the access again (the app came back to the foreground): a group or permission changed by an admin applies
     * at once, as the web re-reads it whenever its tab comes back. What is known stays while it is fetched and when the
     * fetch fails; does nothing while signed out or while a fetch is already running.
     */
    fun refresh() {}
}
