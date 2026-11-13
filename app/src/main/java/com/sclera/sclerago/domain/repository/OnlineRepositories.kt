package com.sclera.sclerago.domain.repository

import com.sclera.sclerago.domain.model.AccessState
import kotlinx.coroutines.flow.StateFlow

/** What the signed-in user may do (the server's `GET /me`). */
interface CurrentAccessRepository {
    /** [AccessState.Loading] until fetched after sign-in; the last copy kept on the device shows first when there is one. */
    val state: StateFlow<AccessState>
}
