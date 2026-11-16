package com.sclera.sclerago.data.offline

import com.sclera.sclerago.data.mapper.DbJson
import com.sclera.sclerago.data.mapper.DbRow
import com.sclera.sclerago.data.sync.AdcDatabase
import com.sclera.sclerago.domain.model.AssetDocument
import com.sclera.sclerago.domain.model.DocumentFileStatus
import com.sclera.sclerago.domain.model.DocumentSource
import com.sclera.sclerago.domain.model.ManualKind
import com.sclera.sclerago.domain.model.modelKey
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow

private const val ASSET_ROW = "SELECT manufacturer, model, source_document_id FROM assets WHERE id = ? AND %LIVE%"

private const val SOURCE_FILE = "SELECT * FROM documents WHERE id = ? AND %LIVE%"

/** The pages an asset's values came from, in the order they were kept (ids are time-ordered), as the server lists them. */
private const val SOURCE_PAGES = "SELECT * FROM web_documents WHERE asset_id = ? AND %LIVE% ORDER BY id"

/** A model's manuals, most relevant first, as the server lists them. */
private const val MODEL_MANUALS = """
    SELECT * FROM web_documents WHERE (asset_id IS NULL OR asset_id = '') AND maker_key = ? AND model_key = ? AND %LIVE%
    ORDER BY confidence IS NULL, confidence DESC, id"""

private const val DOCUMENT_FIELD_NAMES = "SELECT id, name FROM asset_fields WHERE id IN (%IN%)"

private val DOCUMENT_TABLES = arrayOf("documents", "web_documents", "assets", "asset_fields")

/**
 * An asset's documents as the server's Documents tab lists them, from the property's offline database: the file it was
 * read from (`documents`, by `assets.source_document_id`), the web pages its values came from and its model's manuals
 * (`web_documents`). Reads only.
 */
class DocumentsOfflineSource(private val db: AdcDatabase, private val io: CoroutineDispatcher) {

    fun documentsFlow(assetId: String): Flow<List<AssetDocument>> = db.watch(io, *DOCUMENT_TABLES) { load(assetId) }

    private fun load(assetId: String): List<AssetDocument> = db.whenTable("assets", emptyList()) {
        val asset = db.queryOne(ASSET_ROW.replace("%LIVE%", db.notRemoved("assets")), listOf(assetId)) ?: return@whenTable emptyList()
        sourceFile(asset) + sourcePages(assetId) + manuals(asset)
    }

    /** The file the asset was read from; a photo shows in the Media tab instead, as on the web. */
    private fun sourceFile(asset: DbRow): List<AssetDocument> {
        val id = asset.string("source_document_id")?.takeIf { it.isNotBlank() } ?: return emptyList()
        if (!db.hasTable("documents")) return emptyList()
        val doc = db.queryOne(SOURCE_FILE.replace("%LIVE%", db.notRemoved("documents")), listOf(id)) ?: return emptyList()
        val kind = doc.string("kind")
        if (kind == "image") return emptyList()
        return listOf(
            AssetDocument(
                id = id, source = DocumentSource.SOURCE_FILE, title = doc.string("filename").orEmpty(), url = null, publisher = null,
                hasFile = true, fileKind = kind,
            ),
        )
    }

    private fun sourcePages(assetId: String): List<AssetDocument> = db.whenTable("web_documents", emptyList()) {
        val rows = db.query(SOURCE_PAGES.replace("%LIVE%", db.notRemoved("web_documents")), listOf(assetId))
        val names = fieldNames(rows.flatMap { DbJson.stringList(it.string("field_ids")) }.distinct())
        rows.map { r ->
            r.toDocument(DocumentSource.WEB_PAGE).copy(usedFor = DbJson.stringList(r.string("field_ids")).mapNotNull(names::get))
        }
    }

    /** No model, no manuals (the server's rule). */
    private fun manuals(asset: DbRow): List<AssetDocument> = db.whenTable("web_documents", emptyList()) {
        val model = modelKey(asset.string("model"))
        if (model.isEmpty()) return@whenTable emptyList()
        db.query(MODEL_MANUALS.replace("%LIVE%", db.notRemoved("web_documents")), listOf(modelKey(asset.string("manufacturer")), model))
            .map { r ->
                val label = listOfNotNull(r.string("manufacturer"), r.string("model")).filter { it.isNotBlank() }.joinToString(" ")
                r.toDocument(DocumentSource.MANUAL).copy(manualKind = ManualKind.of(r.string("kind")), model = label.ifEmpty { null })
            }
    }

    private fun DbRow.toDocument(source: DocumentSource): AssetDocument {
        val hasFile = !string("object_path").isNullOrBlank() || string("file_status") == "saved"
        return AssetDocument(
            id = string("id").orEmpty(),
            source = source,
            title = string("title").orEmpty(),
            url = string("url"),
            publisher = string("publisher")?.takeIf { it.isNotBlank() },
            official = bool("official"),
            // A manual's file is downloaded as it is found: kept, or only its link.
            fileStatus = if (source == DocumentSource.MANUAL) (if (hasFile) DocumentFileStatus.SAVED else DocumentFileStatus.LINK)
            else DocumentFileStatus.of(string("file_status")),
            hasFile = hasFile,
            fileKind = if (hasFile || string("url").orEmpty().contains(Regex("\\.pdf($|[?#])", RegexOption.IGNORE_CASE))) "pdf" else null,
        )
    }

    /** Field id → name, for the "Used for" of a page. */
    private fun fieldNames(ids: List<String>): Map<String, String> =
        if (ids.isEmpty() || !db.hasTable("asset_fields")) emptyMap()
        else db.queryIn(DOCUMENT_FIELD_NAMES, ids).associate { it.string("id").orEmpty() to it.string("name").orEmpty() }
}
