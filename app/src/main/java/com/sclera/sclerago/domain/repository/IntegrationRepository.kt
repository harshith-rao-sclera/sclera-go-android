package com.sclera.sclerago.domain.repository

import com.sclera.sclerago.domain.model.VdmsChoice
import com.sclera.sclerago.domain.model.VdmsLink

/** The Integration page at the server (`/v1/properties/{p}/integration`). Every call can throw OnlineException. */
interface IntegrationRepository {
    /** The property's VDMS link, or null when it has none. */
    suspend fun link(propertyId: String): VdmsLink?

    /** The organisation's VDMS not linked anywhere yet, matching [key] (the VDMS id) when given. */
    suspend fun available(propertyId: String, key: String?): List<VdmsChoice>

    /** Links the property to [vdmsId] (Conflict when it or the VDMS is already linked). */
    suspend fun linkVdms(propertyId: String, vdmsId: String): VdmsLink
}
