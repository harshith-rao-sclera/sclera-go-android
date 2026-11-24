package com.sclera.sclerago.domain.usecase.access

import com.sclera.sclerago.domain.model.Permission
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Whether a property's assets (the web's Data Collection) are offered, as the web's seesDataCollection: to anyone who may
 * look inside a property (View, which the server lists for anyone who collects, checks, validates or configures). While
 * the access isn't known yet the assets are offered (to look at only: [PermissionGuard] allows nothing then).
 */
class SeesAssetsUseCase @Inject constructor(private val guard: PermissionGuard) {
    // What the group grants, not what the phone's location allows: a collector held to view only still has the assets.
    operator fun invoke(): Boolean = !guard.known() || LOOK.any(guard::holds)

    /** [invoke], kept up to date as the access is fetched or changes. */
    fun observe(): Flow<Boolean> = guard.changes().map { invoke() }.distinctUntilChanged()

    private companion object {
        val LOOK = listOf(Permission.VIEW, Permission.COLLECT, Permission.CHECK, Permission.VALIDATE)
    }
}

/**
 * Whether the signed-in user opens properties at all, as the web's opensProperties: they see the assets ([SeesAssetsUseCase])
 * or set properties up (Configure). A group without View (one with nothing ticked) lists its properties but can't look
 * inside one: the server refuses it. True while the access isn't known yet.
 */
class OpensPropertiesUseCase @Inject constructor(private val guard: PermissionGuard, private val seesAssets: SeesAssetsUseCase) {
    operator fun invoke(): Boolean = seesAssets() || guard.holds(Permission.CONFIGURE)

    /** [invoke], kept up to date as the access is fetched or changes. */
    fun observe(): Flow<Boolean> = guard.changes().map { invoke() }.distinctUntilChanged()
}
