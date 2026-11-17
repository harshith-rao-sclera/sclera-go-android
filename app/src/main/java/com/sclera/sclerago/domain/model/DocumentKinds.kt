package com.sclera.sclerago.domain.model

/** The `documents.kind` of a photo. */
const val IMAGE_KIND = "image"

/** The server's document kinds by file extension (its `readers._BY_EXTENSION`). */
private val KIND_BY_EXTENSION = mapOf(
    "xlsx" to "spreadsheet", "xlsm" to "spreadsheet", "xls" to "spreadsheet", "csv" to "spreadsheet", "tsv" to "spreadsheet",
    "pdf" to "pdf",
    "jpg" to "image", "jpeg" to "image", "png" to "image", "webp" to "image", "gif" to "image", "bmp" to "image",
    "tif" to "image", "tiff" to "image", "heic" to "image",
    "mp4" to "video", "mov" to "video", "avi" to "video", "mkv" to "video", "webm" to "video", "m4v" to "video",
    "docx" to "word",
    "txt" to "text", "md" to "text", "json" to "text", "xml" to "text",
)

/**
 * The `documents.kind` of a file named [filename] of [contentType], worked out as the server's `readers.kind_of` does:
 * by extension, then by type. Null when the server doesn't take it as a document.
 */
fun documentKind(filename: String, contentType: String?): String? {
    KIND_BY_EXTENSION[filename.substringAfterLast('.', "").lowercase()]?.let { return it }
    val type = contentType.orEmpty().lowercase()
    return when {
        type.startsWith("image/") -> "image"
        type.startsWith("video/") -> "video"
        type.startsWith("text/") -> "text"
        type.startsWith("application/pdf") -> "pdf"
        else -> null
    }
}
