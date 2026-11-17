package com.sclera.sclerago.data.offline

import com.sclera.sclerago.domain.model.Media

/** A picked, taken or recorded file, read for the offline database: its name, type, size, image size and content. */
class PickedContent(
    val filename: String,
    val contentType: String?,
    val sizeBytes: Long,
    val width: Int?,
    val height: Int?,
    val bytes: ByteArray,
)

/**
 * Reads the files added to assets. Their only copy is then the offline database's ([MediaBlobs]); the app keeps none
 * of its own.
 */
interface AssetFileStore {
    /**
     * The file behind [media] (a picked file's content URI, or one of the app's own captures), read whole for the
     * database; null when it can't be read, is not ours to read, is larger than the import cap or doesn't fit in memory.
     */
    fun read(media: Media): PickedContent?

    /**
     * [media] made ready for the database: each video re-encoded smaller (an app-private copy the caller discards once
     * it is kept), everything else as it is. A video that can't be made smaller stays as it was.
     */
    suspend fun prepare(media: List<Media>): List<Media> = media

    /** Deletes the app's own camera and voice memo files behind [media] once they are in the database. */
    fun discardOriginals(media: List<Media>)

    /**
     * Deletes the copies older versions of the app kept on the device of the media rows the database also holds
     * ([held]), so each file is kept once.
     */
    fun dropLegacyCopies(held: (rowId: String) -> Boolean) {}

    /** Deletes any copy an older version of the app kept of the media rows [rowIds] (their asset was deleted). */
    fun forget(rowIds: Collection<String>) {}

    /**
     * The photo behind [media] to send to the server, or null when it is not an image, can't be read or is larger
     * than [maxBytes].
     */
    fun photo(media: Media, maxBytes: Long): PhotoFile? = null
}

/** A photo's content to send in a request. */
class PhotoFile(val filename: String, val contentType: String, val bytes: ByteArray)
