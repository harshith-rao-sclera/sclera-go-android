package com.sclera.sclerago.domain.model

/**
 * Who the signed-in user is and what they can do in the organisation, as the server reports it (`GET /me`).
 * [organisationId] is the organisation every call uses. The server decides every call; this is for showing and a convenience check only.
 */
data class CurrentAccess(
    val userId: String,
    val email: String,
    val name: String?,
    val role: String?,
    val organisationId: String?,
    val groupId: String?,
    val groupName: String?,
    val permissions: Set<Permission>,
    val allProperties: Boolean,
    val canManageProperties: Boolean,
) {
    /** A person with no group has no access yet: the server refuses everything for them. */
    val hasGroup: Boolean get() = groupId != null

    override fun toString() = "CurrentAccess(redacted)"
}

/** What is known about the signed-in user's access: loaded, loaded but without a group, or not known (yet). */
sealed interface AccessState {
    /** Being fetched after sign-in; the server decides in the meantime. */
    data object Loading : AccessState

    /** The user is in a group; [access] says what it allows. */
    data class Loaded(val access: CurrentAccess) : AccessState

    /** The user is signed in but no admin has put them in a group yet ([access] has no permissions). */
    data class NoGroup(val access: CurrentAccess) : AccessState

    /** It could not be fetched and was never fetched before, for [error]. The server decides. */
    data class Failed(val error: OnlineError) : AccessState
}
