package com.sclera.sclerago.domain.repository

/**
 * Change requests on submitted assets: someone who can't change a submitted asset asks an admin to reopen it (the
 * server's `POST` / `DELETE …/assets/{id}/change-request`). Reopening is the admin moving it back to draft.
 */
interface ChangeRequestRepository {
    /** Asks an admin to reopen asset [assetId], saying what needs changing in [note]; asking again replaces the note. */
    suspend fun request(assetId: String, note: String?)

    /** Closes asset [assetId]'s request without reopening it: [declined] by an admin, else withdrawn by whoever asked. */
    suspend fun close(assetId: String, declined: Boolean)
}
