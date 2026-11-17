package com.sclera.sclerago.domain.repository

import com.sclera.sclerago.domain.model.ManagedCounts
import com.sclera.sclerago.domain.model.PlaceScope
import com.sclera.sclerago.domain.model.VdmsAssignee

/** Moving validated assets to Managed Assets (the property's linked VDMS) at the server. Every call can throw OnlineException. */
interface ManagedAssetsRepository {
    /** The linked VDMS's network names, which a moved asset goes on. */
    suspend fun networks(propertyId: String): List<String>

    /** People the VDMS can assign moved assets to, matching [key] when given. */
    suspend fun assignees(propertyId: String, key: String?): List<VdmsAssignee>

    /** How many of the property's assets in [scope] were moved to Managed Assets, and sent back from it. */
    suspend fun counts(propertyId: String, scope: PlaceScope): ManagedCounts

    /** Moves [assetIds] (the server takes only validated ones still in ADC); how many will move, or null when it doesn't say. */
    suspend fun move(propertyId: String, assetIds: Collection<String>, networkName: String, assignee: String?): Int?
}
