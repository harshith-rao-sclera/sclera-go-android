package com.sclera.sclerago.data.offline

import com.sclera.sclerago.domain.model.Media

/** A media file kept in the app's private storage under [key] ("<id>.<ext>"). */
data class StoredFile(
    val key: String,
    val filename: String,
    val contentType: String?,
    val sizeBytes: Long,
    val width: Int?,
    val height: Int?,
)

/** The photo, video and document files of assets, kept in app-private storage until the sync has uploaded them. */
interface AssetFileStore {
    /** Copies the file behind [media] into private storage as [id]; null when it can't be read, is not ours to read or is too large. */
    fun store(media: Media, id: String): StoredFile?

    /** The content of a stored file, or null when it is missing or too large to upload with the data sync. */
    fun bytes(key: String): ByteArray?

    /** Deletes the stored files [keys]. */
    fun delete(keys: Collection<String>)

    /** Deletes the app's own camera files behind [media] once they have been copied in. */
    fun discardOriginals(media: List<Media>)

    /** Where the file of [objectPath] can be opened: the stored file for a `local://` path that is on this device, else the path itself. */
    fun uriFor(objectPath: String): String
}

/** The object path stored in `asset_media` for a file kept under [key]. */
internal fun localObjectPath(key: String) = LOCAL_SCHEME + key

/** The key of a `local://` object path, or null for any other path. */
internal fun localKey(objectPath: String): String? = objectPath.removePrefix(LOCAL_SCHEME).takeIf { objectPath.startsWith(LOCAL_SCHEME) }

private const val LOCAL_SCHEME = "local://"
