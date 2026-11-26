package com.sclera.sclerago.domain.repository

import com.sclera.sclerago.domain.model.ClassificationResult
import com.sclera.sclerago.domain.model.ClassifyDetails
import com.sclera.sclerago.domain.model.Media
import com.sclera.sclerago.domain.model.TypeField

/** Classifying the current property's assets at the server, and the fields each of its types brings (kept for working offline). */
interface ClassificationRepository {
    /**
     * What the asset is, as the server's two ways of asking: for an asset already on the server ([assetId]), from its
     * saved answers and stored photos, with the [photos] on this device first; for one still only on the phone, from
     * up to 3 [photos] and the [details] typed so far (also used when [assetId] isn't on the server yet). Nothing is
     * saved at the server (the suggested types' fields are kept on the device). With [masterFormatId] (the type the
     * person picked) the answer is just that type, with its fields and JACS codes. Throws
     * [com.sclera.sclerago.domain.model.OnlineException] (Offline, Invalid when there is nothing to go on).
     */
    suspend fun suggest(
        details: ClassifyDetails,
        photos: List<Media>,
        masterFormatId: String? = null,
        assetId: String? = null,
    ): ClassificationResult

    /**
     * The fields an asset of the type [masterFormatId] gets, as the property's database keeps them (they come with the
     * sync, so offline too). Empty for a type with none, or one the database doesn't have.
     */
    suspend fun typeFields(masterFormatId: String): List<TypeField>
}
