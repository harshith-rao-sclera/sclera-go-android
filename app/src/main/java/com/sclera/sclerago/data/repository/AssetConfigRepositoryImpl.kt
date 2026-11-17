package com.sclera.sclerago.data.repository

import com.sclera.sclerago.data.offline.FieldsOfflineSource
import com.sclera.sclerago.di.ApplicationScope
import com.sclera.sclerago.domain.model.AssetConfig
import com.sclera.sclerago.domain.repository.AssetConfigRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

/** The asset fields of the downloaded property, from the offline database (Configuration changes them at the server). */
@Singleton
class AssetConfigRepositoryImpl @Inject constructor(
    source: FieldsOfflineSource,
    @ApplicationScope scope: CoroutineScope,
) : AssetConfigRepository {
    override val config: StateFlow<AssetConfig> = source.standardFlow().map { AssetConfig(it) }
        .stateIn(scope, SharingStarted.Eagerly, AssetConfig(emptyList()))
}
