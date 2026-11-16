package com.sclera.sclerago.domain.repository

import com.sclera.sclerago.domain.model.FieldCfg
import com.sclera.sclerago.domain.model.FormEntry
import com.sclera.sclerago.domain.model.MasterFormat
import kotlinx.coroutines.flow.Flow

/** What an asset's details form offers, as kept with the property's data: its fields and the asset type catalogue. */
interface AssetFormRepository {
    /** The fields of the asset [assetId]: the standard ones, then the asset's own, in their order. */
    fun fields(assetId: String): Flow<List<FieldCfg>>

    /** The rows of the asset's fields in their order, including its photo, video and voice memo fields. */
    fun entries(assetId: String): Flow<List<FormEntry>>

    /** The active rows of the asset type catalogue (system, asset and sub type), in their order. */
    fun formats(): Flow<List<MasterFormat>>
}
