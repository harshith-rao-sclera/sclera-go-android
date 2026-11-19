package com.sclera.sclerago.domain.repository

import com.sclera.sclerago.domain.model.FieldKind

/** Fields that belong to one asset alone (the server's additional fields), kept with the asset. */
interface AssetOwnFieldsRepository {
    /** Adds a field named [name] of [kind] to asset [assetId]; its key, or null when the asset's form already has that name. */
    suspend fun add(assetId: String, name: String, kind: FieldKind): String?

    /** Removes asset [assetId]'s own field [key] with its answer; false when it isn't one of the asset's own fields. */
    suspend fun remove(assetId: String, key: String): Boolean
}
