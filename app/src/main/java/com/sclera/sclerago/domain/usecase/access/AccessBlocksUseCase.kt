package com.sclera.sclerago.domain.usecase.access

import com.sclera.sclerago.domain.model.AccessBlock
import com.sclera.sclerago.domain.model.Permission
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Why the signed-in user can't use each kind of button right now (the User Access Guide), null where they can. A button
 * that needs one of these stays on screen, greyed out, and a tap says why. Each is [PermissionGuard.block] of its
 * permission, so it is null exactly when [PermissionGuard.allows] is true.
 */
data class AccessBlocks(
    /** Add, edit, delete and submit assets, upload files, comment (Collect; view only away from a geofenced site). */
    val collect: AccessBlock? = AccessBlock.CHECKING,
    /** The Validation page and running validations (Check). */
    val check: AccessBlock? = AccessBlock.CHECKING,
    /** Sign assets off, move submitted ones back to Draft, answer change requests (Validate). */
    val validate: AccessBlock? = AccessBlock.CHECKING,
    /** The Configuration and Integration pages, Move to Managed assets (Configure). */
    val configure: AccessBlock? = AccessBlock.CHECKING,
    /** Create, import, edit and delete properties (Manage properties, with all properties). */
    val manageProperties: AccessBlock? = AccessBlock.CHECKING,
    /** The access page: groups and users (Manage access, with all properties). */
    val manageAccess: AccessBlock? = AccessBlock.CHECKING,
    /** Data Collection, the property's assets ([SeesAssetsUseCase]): looking inside a property (View). */
    val assets: AccessBlock? = null,
    /** Assign (a reviewer, to anyone) or Assign to me (anyone else who collects, once who they are is known). */
    val assign: AccessBlock? = AccessBlock.CHECKING,
)

/** [AccessBlocks] now, and again whenever the access or the premise status changes. */
class ObserveAccessBlocksUseCase @Inject constructor(
    private val guard: PermissionGuard,
    private val seesAssets: SeesAssetsUseCase,
) {
    operator fun invoke(): Flow<AccessBlocks> = guard.changes().map { now() }.distinctUntilChanged()

    fun now(): AccessBlocks {
        val collect = guard.block(Permission.COLLECT)
        return AccessBlocks(
            collect = collect,
            check = guard.block(Permission.CHECK),
            validate = guard.block(Permission.VALIDATE),
            configure = guard.block(Permission.CONFIGURE),
            manageProperties = guard.block(Permission.MANAGE_PROPERTIES),
            manageAccess = guard.block(Permission.MANAGE_ACCESS),
            // A group that holds no permission that looks (nothing ticked, or only configures without View).
            assets = if (seesAssets()) null else guard.block(Permission.VIEW) ?: AccessBlock.NEEDS_VIEW,
            assign = collect ?: AccessBlock.CHECKING.takeUnless { guard.isReviewer() || guard.userId() != null },
        )
    }
}
