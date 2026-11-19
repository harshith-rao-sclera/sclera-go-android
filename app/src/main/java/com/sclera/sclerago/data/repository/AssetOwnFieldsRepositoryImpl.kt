package com.sclera.sclerago.data.repository

import com.sclera.sclerago.data.mapper.FieldKeys
import com.sclera.sclerago.data.offline.AssetsWriter
import com.sclera.sclerago.domain.model.FieldKind
import com.sclera.sclerago.domain.model.serverType
import com.sclera.sclerago.domain.repository.AssetOwnFieldsRepository
import javax.inject.Inject
import javax.inject.Singleton

/** An asset's own fields, written to the offline database; the sync sends them to the server. */
@Singleton
class AssetOwnFieldsRepositoryImpl @Inject constructor(private val writer: AssetsWriter) : AssetOwnFieldsRepository {
    override suspend fun add(assetId: String, name: String, kind: FieldKind): String? = writer.addOwnField(assetId, name, kind.serverType)

    override suspend fun remove(assetId: String, key: String): Boolean = writer.removeOwnField(assetId, FieldKeys.toDb(key))
}
