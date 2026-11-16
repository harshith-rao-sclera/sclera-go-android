package com.sclera.sclerago.domain.model

enum class ActivityKind { COMMENT, HISTORY }

/** One entry in an asset's activity feed: a comment, or a recorded change ([action] such as created or submitted). */
data class AssetActivity(
    val id: String,
    val assetId: String,
    val kind: ActivityKind,
    val action: String?,
    val text: String,
    val changes: String?,
    val createdBy: String?,
    val createdAt: Long?,
)
