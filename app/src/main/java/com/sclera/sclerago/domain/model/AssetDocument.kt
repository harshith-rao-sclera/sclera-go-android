package com.sclera.sclerago.domain.model

/** What a document of an asset is: a file added to it, the file it was read from, or a web document. */
enum class DocumentSource {
    /** The file the asset was read from (an import). */
    SOURCE_FILE,

    /** A web page some of the asset's values came from (kept when AI suggestions were applied). */
    WEB_PAGE,

    /** A manual of the asset's model, from the manufacturer's own site. */
    MANUAL,

    /** A document added to the asset (the Documents tab's Add, or a file from Media): one of its media. */
    ATTACHMENT,
}

/** Whether the PDF behind a web page or manual is kept, still downloading, or only the link was kept. */
enum class DocumentFileStatus {
    PENDING, SAVED, LINK;

    companion object {
        fun of(text: String?): DocumentFileStatus = when (text) {
            "pending" -> PENDING
            "saved" -> SAVED
            else -> LINK
        }
    }
}

/** A kind of manual (the server's `web_documents.kind`): manual, installation, datasheet, parts or other. */
enum class ManualKind {
    MANUAL, INSTALLATION, DATASHEET, PARTS, OTHER;

    companion object {
        fun of(text: String?): ManualKind = entries.firstOrNull { it.name.equals(text, ignoreCase = true) } ?: OTHER
    }
}

/**
 * One document of an asset, as the server lists it (documents are not synced): one added to it, the file it was read
 * from, a page its values came from or a manual of its model.
 */
data class AssetDocument(
    val id: String,
    val source: DocumentSource,
    /** The page's title, the manual's title, or the file's name. */
    val title: String,
    val url: String?,
    /** The site it is on (its host name). */
    val publisher: String?,
    /** On the manufacturer's own site. */
    val official: Boolean = false,
    val manualKind: ManualKind? = null,
    /** Web pages: the names of the fields whose values came from it. */
    val usedFor: List<String> = emptyList(),
    /** Manuals: the manufacturer and model it is for. */
    val model: String? = null,
    val fileStatus: DocumentFileStatus = DocumentFileStatus.LINK,
    /** The file itself is kept (else only the link). */
    val hasFile: Boolean = false,
    /** The file's kind as the server names it (pdf, spreadsheet, word, text…), for its icon. */
    val fileKind: String? = null,
    /** The uploaded file: who uploaded it (a user id), their name as far as this device knows, and when. */
    val uploadedBy: String? = null,
    val uploaderName: String? = null,
    val uploadedAt: Long? = null,
    /** The uploaded file's size and type ("application/pdf"). */
    val sizeBytes: Long? = null,
    val contentType: String? = null,
    /**
     * It can be taken off: a web page by anyone who can collect; the uploaded file by whoever uploaded it, or anyone who
     * can configure the property (the server's rule). A model's manual belongs to every asset of that model.
     */
    val removable: Boolean = false,
    /** A photo the asset was read from: a resized copy of it (base64 JPEG or PNG) to show as a thumbnail. */
    val preview: String? = null,
) {
    /** A photo the asset was read from: Media shows it under "From the source file" (not a document to list). */
    val isSourcePhoto: Boolean get() = source == DocumentSource.SOURCE_FILE && fileKind == IMAGE_KIND

    /** A photo or video the asset was read from: it is previewed in the app's viewer rather than opened in another app. */
    val previewable: Boolean get() = source == DocumentSource.SOURCE_FILE && (fileKind == IMAGE_KIND || fileKind == VIDEO_KIND)
}

/** How the server matches a manufacturer or model to its manuals: lowercase letters and digits only. */
fun modelKey(text: String?): String = (text ?: "").lowercase().filter { it in 'a'..'z' || it in '0'..'9' }

/** The manuals of an asset's model as the server has them now, and whether a search for more is still running. */
data class ModelManuals(val manuals: List<AssetDocument>, val searching: Boolean)

/** A document's file fetched to the device: where it is opened ([uri]) and its type, when the server said. */
data class OpenableFile(val uri: String, val contentType: String?)
