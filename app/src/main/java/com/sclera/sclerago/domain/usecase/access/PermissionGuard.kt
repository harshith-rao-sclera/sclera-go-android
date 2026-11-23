package com.sclera.sclerago.domain.usecase.access

import com.sclera.sclerago.domain.model.AccessState
import com.sclera.sclerago.domain.model.DataError
import com.sclera.sclerago.domain.model.DataException
import com.sclera.sclerago.domain.model.OnlineError
import com.sclera.sclerago.domain.model.OnlineException
import com.sclera.sclerago.domain.model.Permission
import com.sclera.sclerago.domain.model.PremiseNotice
import com.sclera.sclerago.domain.model.PremiseStatus
import com.sclera.sclerago.domain.repository.CurrentAccessRepository
import com.sclera.sclerago.domain.repository.NoPremiseRestriction
import com.sclera.sclerago.domain.repository.PremiseRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * A convenience check of the signed-in user's last known permissions, so a change they cannot make is refused up front
 * instead of being made offline and rejected later. It is not access control: the server decides every call, so when the
 * access has not been fetched yet (or never could be) the change is allowed.
 */
class PermissionGuard @Inject constructor(
    private val access: CurrentAccessRepository,
    private val premise: PremiseRepository = NoPremiseRestriction,
) {
    /**
     * What the user may do now: their permissions, except that a collector (not an admin, with Collect) of a property
     * with geofencing on has no Collect while the phone is not on premise (or its location is unknown): view only.
     */
    fun allows(permission: Permission): Boolean = allows(access.state.value, premise.status.value, permission)

    /** [allows], kept up to date as the access or the premise status changes (for showing or hiding a button). */
    fun observe(permission: Permission): Flow<Boolean> =
        combine(access.state, premise.status) { state, status -> allows(state, status, permission) }.distinctUntilChanged()

    /** What the group grants, whatever the phone's location (for choosing which pages a group is offered). */
    fun holds(permission: Permission): Boolean = holds(access.state.value, permission)

    /** [holds], kept up to date as the access changes. */
    fun observeHolds(permission: Permission): Flow<Boolean> = access.state.map { holds(it, permission) }.distinctUntilChanged()

    /** The user is a collector held to view only by the geofence right now (see [allows]). */
    fun viewOnlyByLocation(): Boolean = heldByLocation(access.state.value, premise.status.value)

    /** Why the user is a collector held to view only, as [PremiseNotice.NONE] when they are not; kept up to date. */
    fun observePremiseNotice(): Flow<PremiseNotice> =
        combine(access.state, premise.status) { state, status ->
            when {
                !heldByLocation(state, status) -> PremiseNotice.NONE
                status == PremiseStatus.OFF_PREMISE -> PremiseNotice.OFF_PREMISE
                status == PremiseStatus.LOCATION_PERMISSION_NEEDED -> PremiseNotice.LOCATION_NEEDED
                else -> PremiseNotice.LOCATION_UNKNOWN
            }
        }.distinctUntilChanged()

    private fun heldByLocation(state: AccessState, status: PremiseStatus): Boolean =
        status.restrictsCollectors && state is AccessState.Loaded && !state.access.isAdmin && Permission.COLLECT in state.access.permissions

    private fun allows(state: AccessState, status: PremiseStatus, permission: Permission): Boolean =
        holds(state, permission) && !(permission == Permission.COLLECT && heldByLocation(state, status))

    /**
     * Only what the access says, as the web (which won't sign someone in until `/me` answers): while it isn't known
     * (being fetched, or failed with no copy kept on the device) nothing is allowed, so no page or change the server
     * may refuse is offered, and no change is made offline that the sync would send unchecked. A kept copy shows at
     * once, so this only holds back a first sign-in.
     */
    private fun holds(state: AccessState, permission: Permission): Boolean = when (state) {
        is AccessState.Loaded -> permission in state.access.permissions
        is AccessState.NoGroup -> false
        AccessState.Loading, is AccessState.Failed -> false
    }

    /** The access is known (fetched, or kept from before): false while it is being fetched or failed. */
    fun known(): Boolean = access.state.value.let { it is AccessState.Loaded || it is AccessState.NoGroup }

    /** Every change to the access (fetched, failed, changed) or to the premise status, for re-reading permission checks shown on screen. */
    fun changes(): Flow<AccessState> = combine(access.state, premise.status) { state, _ -> state }

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
