package com.sclera.sclerago.domain.repository

import com.sclera.sclerago.domain.model.ClassificationResult
import com.sclera.sclerago.domain.model.ClassifyDetails
import com.sclera.sclerago.domain.model.Media
import com.sclera.sclerago.domain.model.TypeField

/** Classifying the current property's assets at the server, and the fields each of its types brings (kept for working offline). */
interface ClassificationRepository {
    /**
     * What the asset is, from up to 3 [photos] and the [details] typed so far; nothing is saved at the server and the
     * asset need not be synced (the suggested types' fields are kept on the device). With [masterFormatId] (the type the
     * person picked) the answer is just that type, with its fields and JACS codes. Throws [com.sclera.sclerago.domain.model.OnlineException] (Offline, Invalid when there is nothing to go on).
     */
    suspend fun suggest(details: ClassifyDetails, photos: List<Media>, masterFormatId: String? = null): ClassificationResult

    /**
     * The fields an asset of the type [masterFormatId] gets: the ones kept on the device, else asked from the server (and
     * kept). Empty for a type the library doesn't know, or when neither is possible.
     */
    suspend fun typeFields(masterFormatId: String): List<TypeField>

    /** Downloads every active type's fields of the current property to keep for working offline; does nothing without a connection. */
    suspend fun refresh()
}
