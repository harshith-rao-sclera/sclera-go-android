package com.sclera.sclerago.domain.repository

import com.sclera.sclerago.domain.model.AssignResult

/** Assigning assets to a person at the server (online only, as on the web). Every call can throw OnlineException. */
interface AssetAssignmentRepository {
    /** Gives [assetIds] of [propertyId] to [email]; no email takes them off whoever has them. */
    suspend fun assign(propertyId: String, assetIds: Collection<String>, email: String?): AssignResult
}
