package com.sclera.sclerago.domain.repository

import com.sclera.sclerago.domain.model.CurrentAccess
import kotlinx.coroutines.flow.StateFlow

/** What the signed-in user may do (the server's `GET /me`). */
interface CurrentAccessRepository {
    /** Null until it has been fetched after sign-in (or when it could not be). */
    val access: StateFlow<CurrentAccess?>
}
