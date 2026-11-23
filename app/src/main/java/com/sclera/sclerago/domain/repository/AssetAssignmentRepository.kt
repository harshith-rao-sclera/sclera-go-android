package com.sclera.sclerago.domain.repository

import com.sclera.sclerago.domain.model.PropertyAssignee
import com.sclera.sclerago.domain.model.AssignResult

/** Assigning assets to a person at the server (online only, as on the web). Every call can throw OnlineException. */
interface AssetAssignmentRepository {
    /**
     * Gives [assetIds] of [propertyId] to [email]; no email takes them off whoever has them. The phone's copy of who has
     * them follows when the server had them all, and the assignees are fetched again after.
     */
    suspend fun assign(propertyId: String, assetIds: Collection<String>, email: String?): AssignResult

    /**
     * Takes [assetIds] of [propertyId] (each assigned to nobody) for [userId], the signed-in user: one change per asset,
     * as the web's. The ones taken before a failure stay taken (the phone's copy follows them), then the error is thrown.
     */
    suspend fun take(propertyId: String, assetIds: Collection<String>, userId: String)

    /** The people [propertyId]'s assets are assigned to: user id → their name, else their email (kept on the device too). */
    suspend fun assigneeNames(propertyId: String): Map<String, String>

    /**
     * The assignees kept on the device for the downloaded properties, by user id (a user id is the same person in every
     * property): read when the app starts, and fetched again for a property when it is downloaded or opened online.
     */
    fun keptAssignees(): Map<String, PropertyAssignee> = emptyMap()

    /** The assignees kept on the device for [propertyId], by user id. */
    fun keptAssigneesOf(propertyId: String): Map<String, PropertyAssignee> = emptyMap()

    /** Reads [propertyId]'s kept assignees, then fetches them again when online (a failure keeps what is kept). */
    suspend fun openAssignees(propertyId: String) {}
}
