package com.sclera.sclerago.domain.validation

import com.sclera.sclerago.domain.model.FieldCfg
import com.sclera.sclerago.domain.model.Status

/** One line of an asset's info in the review: a field's name and its value as a person reads it (null when empty). */
data class AssetInfoRow(val label: String, val value: String?)

/**
 * What the review's asset sheet shows about the asset as the server has it: its fields, how many are filled, where it
 * is (building › floor › location, blank when it has no place) and its status.
 */
data class ReviewAssetInfo(val rows: List<AssetInfoRow>, val place: String = "", val status: Status? = null) {
    val filled: Int get() = rows.count { it.value != null }
    val total: Int get() = rows.size
}

/** An asset as the server has it, for the review's Asset info (the web's AssetDetails). */
data class ServerReviewAsset(
    /** The active fields it has, in the form's order: the standard fields, then its own. */
    val fields: List<FieldCfg>,
    /** Field id → the answer as text; empty answers are left out. */
    val values: Map<String, String>,
    /** Building › floor › location; blank when it has no place. */
    val place: String,
    val status: Status?,
)
