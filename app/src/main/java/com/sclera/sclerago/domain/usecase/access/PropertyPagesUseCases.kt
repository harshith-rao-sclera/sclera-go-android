package com.sclera.sclerago.domain.usecase.access

import com.sclera.sclerago.domain.model.Permission
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import javax.inject.Inject

/**
 * Whether a property's assets (the web's Data Collection) are offered, as the web's seesDataCollection: looking at
 * assets needs no permission, so a group with none of the property's permissions (Viewers) has them, and so does
 * anyone who collects, checks or validates. A group that only sets a property up (Configure, without those three)
 * isn't offered them: their pages are Configuration and Integration, and a property opens on Configuration for them.
 * While the access isn't known yet the assets are offered (to look at only: [PermissionGuard] allows nothing then).
 */
class SeesAssetsUseCase @Inject constructor(private val guard: PermissionGuard) {
    operator fun invoke(): Boolean = WORK.any(guard::allows) || !guard.allows(Permission.CONFIGURE)

    /** [invoke], kept up to date as the access is fetched or changes. */
    fun observe(): Flow<Boolean> =
        combine(WORK.map(guard::observe) + guard.observe(Permission.CONFIGURE)) { allowed ->
            allowed.dropLast(1).any { it } || !allowed.last()
        }.distinctUntilChanged()

    private companion object {
        val WORK = listOf(Permission.COLLECT, Permission.CHECK, Permission.VALIDATE)
    }
}
