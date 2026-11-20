package com.sclera.sclerago.domain.repository

import com.sclera.sclerago.domain.model.PlaceKey
import com.sclera.sclerago.domain.model.PlacesImportResult
import com.sclera.sclerago.domain.model.PlacesPush
import com.sclera.sclerago.domain.model.VdmsPlaces

/**
 * The places import between a property and its linked VDMS at the server (`/v1/properties/{p}/integration/places`).
 * Every call can throw OnlineException.
 */
interface VdmsPlacesRepository {
    /** Both sides' buildings, floors and locations, and the latest send to VDMS. */
    suspend fun places(propertyId: String): VdmsPlaces

    /** Asks VDMS to send its list again (it arrives later); returns when it was asked, by the server's clock. */
    suspend fun refresh(propertyId: String): Long?

    /** Sends the [chosen] places of the property to VDMS, which adds them in the background (Conflict while an earlier send runs). */
    suspend fun push(propertyId: String, chosen: Set<PlaceKey>): PlacesPush

    /** Imports the [chosen] VDMS places (VDMS ids) into the property (Conflict when VDMS hasn't sent its list yet). */
    suspend fun pull(propertyId: String, chosen: Set<PlaceKey>): PlacesImportResult
}
