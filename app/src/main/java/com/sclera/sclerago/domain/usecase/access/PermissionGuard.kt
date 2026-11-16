package com.sclera.sclerago.domain.usecase.access

import com.sclera.sclerago.domain.model.AccessState
import com.sclera.sclerago.domain.model.DataError
import com.sclera.sclerago.domain.model.DataException
import com.sclera.sclerago.domain.model.OnlineError
import com.sclera.sclerago.domain.model.OnlineException
import com.sclera.sclerago.domain.model.Permission
import com.sclera.sclerago.domain.repository.CurrentAccessRepository
import javax.inject.Inject

/**
 * A convenience check of the signed-in user's last known permissions, so a change they cannot make is refused up front
 * instead of being made offline and rejected later. It is not access control: the server decides every call, so when the
 * access has not been fetched yet (or never could be) the change is allowed.
 */
class PermissionGuard @Inject constructor(private val access: CurrentAccessRepository) {
    fun allows(permission: Permission): Boolean = when (val s = access.state.value) {
        is AccessState.Loaded -> permission in s.access.permissions
        is AccessState.NoGroup -> false
        AccessState.Loading, is AccessState.Failed -> true
    }

    /** For changes to the offline data. Throws [DataException] ([DataError.FORBIDDEN]). */
    fun requireOffline(permission: Permission) {
        if (!allows(permission)) throw DataException(DataError.FORBIDDEN)
    }

    /** For changes that go to the server. Throws [OnlineException] ([OnlineError.Forbidden]). */
    fun requireOnline(permission: Permission) {
        if (!allows(permission)) throw OnlineException(OnlineError.Forbidden)
    }
}
