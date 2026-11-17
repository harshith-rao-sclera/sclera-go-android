package com.sclera.sclerago.domain.repository

/** Whether a property is linked to a VDMS (Managed Assets), which has to confirm the deletes made here. */
interface VdmsLinkRepository {
    /** From the server when the device is online, else the last answer it gave (false when it never said). */
    suspend fun isLinked(propertyId: String): Boolean
}
