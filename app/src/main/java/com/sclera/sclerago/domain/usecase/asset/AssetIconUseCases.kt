package com.sclera.sclerago.domain.usecase.asset

import com.sclera.sclerago.domain.model.AssetIcons
import com.sclera.sclerago.domain.repository.AssetIconRepository
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/** The asset icons kept on the device, as they change. */
class ObserveAssetIconsUseCase @Inject constructor(private val repo: AssetIconRepository) {
    operator fun invoke(): StateFlow<AssetIcons> = repo.icons
}

/** Brings the asset icons up to date when the app opens (any property); offline it keeps what it has. */
class RefreshAssetIconsUseCase @Inject constructor(private val repo: AssetIconRepository) {
    suspend operator fun invoke() = repo.refreshIcons()
}

/** Opens the property's icons: those kept first, then (online) the server's latest; offline it keeps what it has. */
class OpenAssetIconsUseCase @Inject constructor(private val repo: AssetIconRepository) {
    suspend operator fun invoke(propertyId: String) {
        if (propertyId.isNotBlank()) repo.open(propertyId)
    }
}
