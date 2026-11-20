package com.sclera.sclerago.data.repository

import com.sclera.sclerago.data.offline.FieldsOfflineSource
import com.sclera.sclerago.data.offline.MasterFormatsOfflineSource
import com.sclera.sclerago.domain.model.FieldCfg
import com.sclera.sclerago.domain.model.FormEntry
import com.sclera.sclerago.domain.model.MasterFormat
import com.sclera.sclerago.domain.repository.AssetFormRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/** An asset's fields (`asset_fields`) and the asset type catalogue (`master_formats`), read from the property's offline database. */
class AssetFormRepositoryImpl @Inject constructor(
    private val fields: FieldsOfflineSource,
    private val formats: MasterFormatsOfflineSource,
) : AssetFormRepository {
    override fun fields(assetId: String): Flow<List<FieldCfg>> = fields.effectiveFlow(assetId)

    override fun entries(assetId: String): Flow<List<FormEntry>> = fields.entriesFlow(assetId)

    override fun formats(): Flow<List<MasterFormat>> = formats.activeFlow()

    override fun formatsById(): Flow<Map<String, MasterFormat>> = formats.byIdFlow()
}
