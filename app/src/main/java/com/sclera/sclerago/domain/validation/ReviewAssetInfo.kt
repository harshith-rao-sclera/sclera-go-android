package com.sclera.sclerago.domain.validation

/** One line of an asset's info in the review: a field's name and its value as a person reads it (null when empty). */
data class AssetInfoRow(val label: String, val value: String?)

/** What the review's asset sheet shows about the asset as it is on this phone: its fields, and how many are filled. */
data class ReviewAssetInfo(val rows: List<AssetInfoRow>) {
    val filled: Int get() = rows.count { it.value != null }
    val total: Int get() = rows.size
}
