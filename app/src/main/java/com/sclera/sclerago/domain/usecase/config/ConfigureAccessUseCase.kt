package com.sclera.sclerago.domain.usecase.config

import com.sclera.sclerago.domain.model.AccessBlock
import com.sclera.sclerago.domain.model.Permission
import com.sclera.sclerago.domain.usecase.access.PermissionGuard
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Why the signed-in user can't change the property's set-up now, or null when they can (the User Access Guide's Configure
 * permission): everything on the Configuration and Integration pages, and Move to Managed assets. Kept up to date as the
 * access changes, so those buttons stay greyed out with the reason instead of being hidden. The use cases that make the
 * changes still check it, and the server decides.
 */
class ObserveConfigureBlockUseCase @Inject constructor(private val guard: PermissionGuard) {
    operator fun invoke(): Flow<AccessBlock?> = guard.observeBlock(Permission.CONFIGURE)
}
