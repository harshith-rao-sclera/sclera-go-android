package com.sclera.sclerago.domain.usecase.asset

import com.sclera.sclerago.domain.model.Asset
import com.sclera.sclerago.domain.model.Permission
import com.sclera.sclerago.domain.repository.AutofillRepository
import com.sclera.sclerago.domain.usecase.access.PermissionGuard
import javax.inject.Inject

/** Asks for suggestions for the empty fields of an asset's details form (the server's look-up needs the Collect permission). */
class AutofillAssetUseCase @Inject constructor(private val repo: AutofillRepository, private val guard: PermissionGuard) {
    suspend operator fun invoke(asset: Asset, values: Map<String, String>): Map<String, String> {
        guard.requireOnline(Permission.COLLECT)
        return repo.suggest(asset, values)
    }
}
