package com.sclera.sclerago.data.repository

import com.sclera.sclerago.data.local.DEFAULT_PHOTO_REQS
import com.sclera.sclerago.data.offline.FieldsOfflineSource
import com.sclera.sclerago.data.offline.FieldsWriter
import com.sclera.sclerago.di.ApplicationScope
import com.sclera.sclerago.domain.model.AssetConfig
import com.sclera.sclerago.domain.model.PhotoReq
import com.sclera.sclerago.domain.repository.AssetConfigRepository
import com.sclera.sclerago.domain.repository.UserDataCleaner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The asset configuration: the fields come from (and go back to) the offline database. The photos each asset needs
 * and the geofencing switch are not in the database yet, so they are kept in memory, from their defaults.
 */
@Singleton
class AssetConfigRepositoryImpl @Inject constructor(
    source: FieldsOfflineSource,
    private val writer: FieldsWriter,
    @ApplicationScope scope: CoroutineScope,
) : AssetConfigRepository, UserDataCleaner {
    private data class Local(val photoReqs: List<PhotoReq>, val geoEnforce: Boolean)

    private val local = MutableStateFlow(Local(DEFAULT_PHOTO_REQS, false))

    override val config: StateFlow<AssetConfig> = combine(source.standardFlow(), local) { fields, l ->
        AssetConfig(fields, l.photoReqs, l.geoEnforce)
    }.stateIn(scope, SharingStarted.Eagerly, AssetConfig(emptyList(), DEFAULT_PHOTO_REQS))

    override suspend fun update(f: (AssetConfig) -> AssetConfig) {
        val before = config.value
        val after = f(before)
        writer.apply(before.fields, after.fields)
        local.value = Local(after.photoReqs, after.geoEnforce)
    }

    override fun clear() {
        local.value = Local(DEFAULT_PHOTO_REQS, false)
    }
}
