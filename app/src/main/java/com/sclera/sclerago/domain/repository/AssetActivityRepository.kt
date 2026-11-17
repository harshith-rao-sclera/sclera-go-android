package com.sclera.sclerago.domain.repository

import com.sclera.sclerago.domain.model.AssetActivity
import kotlinx.coroutines.flow.Flow

/**
 * An asset's comments and history, as kept with the property's data. Writes throw
 * [com.sclera.sclerago.domain.model.DataException] when they cannot be made.
 */
interface AssetActivityRepository {
    /** The comments and history of the asset [assetId], newest first, again whenever they change. */
    fun observe(assetId: String): Flow<List<AssetActivity>>

    /** Adds the comment [text] (already checked, see `checkComment`) to the asset [assetId], by the signed-in user. */
    suspend fun addComment(assetId: String, text: String)

    /** Changes the text of comment [commentId] of asset [assetId] to [text] (already checked). */
    suspend fun updateComment(assetId: String, commentId: String, text: String)

    /** Removes comment [commentId] of asset [assetId]. */
    suspend fun removeComment(assetId: String, commentId: String)
}
