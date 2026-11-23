package com.sclera.sclerago.domain.repository

import com.sclera.sclerago.domain.model.ClassCount
import kotlinx.coroutines.flow.Flow

/** The current property's asset type catalogue as kept on the device (Configuration → Asset types). Reads only. */
interface AssetTypeCatalogRepository {
    /** System types, each with its asset types and their sub types, counting the assets filed under every level. */
    fun tree(): Flow<List<ClassCount>>
}
