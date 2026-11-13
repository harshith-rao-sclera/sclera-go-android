package com.sclera.sclerago.data.mapper

import com.sclera.sclerago.domain.model.ActivityKind
import com.sclera.sclerago.domain.model.AssetActivity
import com.sclera.sclerago.domain.model.Media

/**
 * An `asset_media` row as [Media]. [uri] is where the file can be opened (a local file or a signed link);
 * it defaults to the stored object path. Documents and audio keep their file name.
 */
fun DbRow.toMedia(uri: String? = null): Media {
    val kind = string("kind")
    val isFile = kind == "document" || kind == "audio"
    return Media(
        id = string("id").orEmpty(),
        uri = uri ?: string("object_path").orEmpty(),
        video = kind == "video",
        durationMs = ((double("duration_seconds") ?: 0.0) * 1000).toLong(),
        createdAt = parseDbTime(string("created_at")) ?: 0L,
        fileName = if (isFile) string("filename") else null,
    )
}

/** An `asset_activity` row as an [AssetActivity]. */
fun DbRow.toAssetActivity() = AssetActivity(
    id = string("id").orEmpty(),
    assetId = string("asset_id").orEmpty(),
    kind = if (string("kind") == "comment") ActivityKind.COMMENT else ActivityKind.HISTORY,
    action = string("action"),
    text = string("text").orEmpty(),
    changes = string("changes"),
    createdBy = string("created_by"),
    createdAt = parseDbTime(string("created_at")),
)
