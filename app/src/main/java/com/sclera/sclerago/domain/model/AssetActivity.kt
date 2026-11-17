package com.sclera.sclerago.domain.model

enum class ActivityKind { COMMENT, HISTORY }

/** One field an edit changed: its name, and the value before and after (null: empty). */
data class FieldChange(val field: String, val from: String?, val to: String?)

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
    /** The fields an edit changed, in the order the server lists them. */
    val fieldChanges: List<FieldChange> = emptyList(),
    /** Written by the signed-in user. */
    val byMe: Boolean = false,
    /** A comment changed since it was written. */
    val edited: Boolean = false,
    /** Who wrote it, as far as this device knows (null: someone it doesn't know). */
    val authorName: String? = null,
    /** The signed-in user may change this comment's text (its author) / remove it (its author, or a validator). */
    val canEdit: Boolean = false,
    val canDelete: Boolean = false,
)
