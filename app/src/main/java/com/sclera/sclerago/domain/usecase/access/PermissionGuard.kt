package com.sclera.sclerago.domain.usecase.access

import com.sclera.sclerago.domain.model.AccessState
import com.sclera.sclerago.domain.model.DataError
import com.sclera.sclerago.domain.model.DataException
import com.sclera.sclerago.domain.model.OnlineError
import com.sclera.sclerago.domain.model.OnlineException
import com.sclera.sclerago.domain.model.Permission
import com.sclera.sclerago.domain.repository.CurrentAccessRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * A convenience check of the signed-in user's last known permissions, so a change they cannot make is refused up front
 * instead of being made offline and rejected later. It is not access control: the server decides every call, so when the
 * access has not been fetched yet (or never could be) the change is allowed.
 */
class PermissionGuard @Inject constructor(private val access: CurrentAccessRepository) {
    fun allows(permission: Permission): Boolean = allows(access.state.value, permission)

    /** [allows], kept up to date as the access is fetched or changes (for showing or hiding a button). */
    fun observe(permission: Permission): Flow<Boolean> = access.state.map { allows(it, permission) }.distinctUntilChanged()

    private fun allows(state: AccessState, permission: Permission): Boolean = when (state) {
        is AccessState.Loaded -> permission in state.access.permissions
        is AccessState.NoGroup -> false
        AccessState.Loading, is AccessState.Failed -> true
    }

    /** The signed-in user's id, as the server knows them; null until the access has been fetched. */
    fun userId(): String? = (access.state.value as? AccessState.Loaded)?.access?.userId

    /** The user is known to be in the Admins group (false until the access has been fetched). */
    fun isAdmin(): Boolean = (access.state.value as? AccessState.Loaded)?.access?.isAdmin == true

    /**
     * An asset assigned to [assignedTo] (a user id) is someone else's, as the server's assigned_elsewhere: it has an
     * assignee who isn't the signed-in user. One assigned to nobody is anyone's to work on. False while the access isn't
     * known yet, so nothing is locked on a guess (the server still decides). Admins are checked separately.
     */
    fun isAssignedElsewhere(assignedTo: String?): Boolean {
        val me = (access.state.value as? AccessState.Loaded)?.access?.userId ?: return false
        return !assignedTo.isNullOrBlank() && assignedTo != me
    }

    /**
     * The user is a plain "user", as the web works it out from the permissions (`legacyRole`): someone who can only
     * collect and check (or only look); anyone who can validate, configure or manage is not. The `role` text from
     * `/me` isn't used, as on the web. False until the access has been fetched.
     */
    fun isPlainUser(): Boolean = when (val s = access.state.value) {
        is AccessState.Loaded -> s.access.permissions.all { it == Permission.COLLECT || it == Permission.CHECK }
        is AccessState.NoGroup -> true
        AccessState.Loading, is AccessState.Failed -> false
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
