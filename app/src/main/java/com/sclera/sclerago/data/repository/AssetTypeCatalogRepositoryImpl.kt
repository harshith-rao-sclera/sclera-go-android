package com.sclera.sclerago.data.repository

import com.sclera.sclerago.data.offline.MasterFormatsOfflineSource
import com.sclera.sclerago.domain.model.ClassCount
import com.sclera.sclerago.domain.repository.AssetTypeCatalogRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/** The asset type catalogue (`master_formats`) counted against the property's assets, read from its offline database. */
class AssetTypeCatalogRepositoryImpl @Inject constructor(
    private val formats: MasterFormatsOfflineSource,
) : AssetTypeCatalogRepository {
    override fun tree(): Flow<List<ClassCount>> = formats.classificationFlow()
}
