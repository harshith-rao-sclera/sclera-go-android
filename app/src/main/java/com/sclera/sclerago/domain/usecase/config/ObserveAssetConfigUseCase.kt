package com.sclera.sclerago.domain.usecase.config

import com.sclera.sclerago.domain.model.AssetConfig
import com.sclera.sclerago.domain.repository.AssetConfigRepository
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/** The downloaded property's asset fields as the asset screens use them (from the offline data). */
class ObserveAssetConfigUseCase @Inject constructor(private val repo: AssetConfigRepository) {
    operator fun invoke(): StateFlow<AssetConfig> = repo.config
}
