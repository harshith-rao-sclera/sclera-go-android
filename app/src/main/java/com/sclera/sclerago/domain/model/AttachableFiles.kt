package com.sclera.sclerago.domain.model

// Asset details → Media → Add file: office documents and text only, kept on the phone and synced like the asset's photos.

/** The largest file Add file takes: 10 MB. */
const val MAX_ATTACHED_FILE_BYTES = 10L * 1024 * 1024

/** The file types Add file offers in the system picker: Excel, CSV, PDF, Word and text (by MIME type). */
val ATTACHABLE_FILE_TYPES = listOf(
    "application/pdf",
    "application/msword",
    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    "application/vnd.ms-excel",
    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
    "text/csv",
    "text/comma-separated-values",
    "text/plain",
)

/** The same types by file name, checked again after picking (a picker may not honour the types it was given). */
private val ATTACHABLE_EXTENSIONS = setOf("pdf", "doc", "docx", "xls", "xlsx", "csv", "txt")

/** Why a picked file isn't added: not one of the allowed types (a photo or video included), or over 10 MB. */
enum class AttachRefusal { TYPE, TOO_BIG }

/** Why Add file refuses [file], or null when it takes it. A file whose size isn't known is taken. */
fun attachRefusal(file: Media): AttachRefusal? = when {
    file.video || file.audio || !file.isDoc -> AttachRefusal.TYPE
    file.fileName.orEmpty().substringAfterLast('.', "").lowercase() !in ATTACHABLE_EXTENSIONS -> AttachRefusal.TYPE
    (file.sizeBytes ?: 0L) > MAX_ATTACHED_FILE_BYTES -> AttachRefusal.TOO_BIG
    else -> null
}
