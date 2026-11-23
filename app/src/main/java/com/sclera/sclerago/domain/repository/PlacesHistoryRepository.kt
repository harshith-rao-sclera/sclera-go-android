package com.sclera.sclerago.domain.repository

import com.sclera.sclerago.domain.model.PlacesDirection
import com.sclera.sclerago.domain.model.PlacesHistoryPage
import com.sclera.sclerago.domain.model.PlacesTransferDetail

/**
 * The history of buildings, floors and locations sent to the linked VDMS and imported from it, at the server
 * (`/v1/properties/{p}/integration/places/history`). Every call can throw OnlineException.
 */
interface PlacesHistoryRepository {
    /** [limit] transfers from [offset], newest first, only those going [direction] when given. */
    suspend fun page(propertyId: String, direction: PlacesDirection?, limit: Int, offset: Int): PlacesHistoryPage

    /** One transfer with its places (NotFound when there is no such transfer). */
    suspend fun transfer(propertyId: String, transferId: String): PlacesTransferDetail
}
