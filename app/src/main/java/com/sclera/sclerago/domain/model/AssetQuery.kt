package com.sclera.sclerago.domain.model

import com.sclera.sclerago.domain.validation.AssetResult

/**
 * What an asset query keeps. [placeIds] are building, floor or location ids (an asset matches when it is in
 * any of them); [unmappedOnly] keeps assets with no location; [sources] null = every way of onboarding.
 */
data class AssetFilter(
    val status: Status? = null,
    val sources: Set<Source>? = null,
    val placeIds: List<String> = emptyList(),
    val unmappedOnly: Boolean = false,
    val text: String = "",
)

/** How many assets there are, per app [Status], and how many carry the AI or validated flag. */
data class AssetSummary(
    val total: Int,
    val byStatus: Map<Status, Int>,
    val aiSuggestions: Int,
    val validated: Int,
)

/** One asset with everything its detail screen shows. [validation] is its result in the latest validation job, if any. */
data class AssetFull(
    val asset: Asset,
    val details: AssetDetails,
    val activity: List<AssetActivity>,
    val validation: AssetResult?,
)

/** An asset field with the other names its column goes by in imported sheets. */
data class FieldDef(val cfg: FieldCfg, val aliases: List<String>)

/**
 * The asset type catalogue as a tree of system type, asset type and sub type, each with how many assets are
 * filed under it (children included). [formatId] is set where a master format ends.
 */
data class ClassCount(val name: String, val assets: Int, val formatId: String? = null, val kids: List<ClassCount> = emptyList())
