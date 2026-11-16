package com.sclera.sclerago.domain.model

/** What a document of an asset is (the web's Documents tab shows all three). */
enum class DocumentSource {
    /** The file the asset was read from (an import). */
    SOURCE_FILE,

    /** A web page some of the asset's values came from (kept when AI suggestions were applied). */
    WEB_PAGE,

    /** A manual of the asset's model, from the manufacturer's own site. */
    MANUAL,
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
 * One document of an asset, from the property's data: the file it was read from (`documents`), a page its values came
 * from or a manual of its model (`web_documents`).
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
) {
    /** A web page can be removed from the asset; a model's manual belongs to every asset of that model. */
    val removable: Boolean get() = source == DocumentSource.WEB_PAGE
}

/** How the server matches a manufacturer or model to its manuals: lowercase letters and digits only. */
fun modelKey(text: String?): String = (text ?: "").lowercase().filter { it in 'a'..'z' || it in '0'..'9' }
