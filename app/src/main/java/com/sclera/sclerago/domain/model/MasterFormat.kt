package com.sclera.sclerago.domain.model

/** One row of the asset type catalogue: system type, then optionally asset type and sub type. */
data class MasterFormat(
    val id: String,
    val systemType: String,
    val assetType: String? = null,
    val subtype: String? = null,
) {
    /** The most specific name the app's [AssetType] is worked out from. */
    val label: String get() = assetType ?: systemType
}
