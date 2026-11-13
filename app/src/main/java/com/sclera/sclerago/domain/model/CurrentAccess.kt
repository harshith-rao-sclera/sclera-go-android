package com.sclera.sclerago.domain.model

/** What the signed-in user can do in the organisation, as the server reports it. For showing and gating UI only; the server decides. */
data class CurrentAccess(
    val groupName: String?,
    val permissions: Set<Permission>,
    val allProperties: Boolean,
    val canManageProperties: Boolean,
)
