package com.sclera.sclerago.data.repository

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import com.sclera.sclerago.data.local.DocumentDownloads
import com.sclera.sclerago.data.mapper.parseDbTime
import com.sclera.sclerago.data.offline.FieldsOfflineSource
import com.sclera.sclerago.data.remote.adc.AdcDocumentsApi
import com.sclera.sclerago.data.remote.adc.AdcJobsApi
import com.sclera.sclerago.data.remote.adc.AssetFileDto
import com.sclera.sclerago.data.remote.adc.ManualDto
import com.sclera.sclerago.data.remote.adc.MediaDto
import com.sclera.sclerago.data.remote.adc.WebDocumentDto
import com.sclera.sclerago.domain.model.AssetDocument
import com.sclera.sclerago.domain.model.DocumentFileStatus
import com.sclera.sclerago.domain.model.DocumentSource
import com.sclera.sclerago.domain.model.IMAGE_KIND
import com.sclera.sclerago.domain.model.ManualKind
import com.sclera.sclerago.domain.model.Media
import com.sclera.sclerago.domain.model.OnlineError
import com.sclera.sclerago.domain.model.OnlineException
import com.sclera.sclerago.domain.model.OpenableFile
import com.sclera.sclerago.domain.model.documentKind
import com.sclera.sclerago.domain.repository.AssetDocumentsRepository
import com.sclera.sclerago.domain.repository.PropertyDownloadRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import okhttp3.ResponseBody
import java.io.File
import java.io.FileNotFoundException
import java.io.InputStream
import javax.inject.Inject

/**
 * An asset's documents, as the web's Documents tab shows them, read from the server every time (documents are not
 * synced): the ones added to it (`/assets/{id}/media`, kind document), the file it was read from (`/assets/{id}/files`),
 * the web pages its values came from (`/assets/{id}/web-documents`) and its model's manuals (`/assets/{id}/manuals`). A
 * file is fetched only to be opened ([DocumentDownloads]); a picked file is streamed from its URI into the upload.
 */
class AssetDocumentsRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val api: AdcDocumentsApi,
    private val jobs: AdcJobsApi,
    private val fields: FieldsOfflineSource,
    private val downloads: DocumentDownloads,
    private val properties: PropertyDownloadRepository,
) : AssetDocumentsRepository {

    private fun property(): String = properties.downloads.value.currentId ?: throw OnlineException(OnlineError.NotFound)

    override suspend fun list(assetId: String): List<AssetDocument> = coroutineScope {
        val property = property()
        // Offline each list comes from the response cache: one never fetched online is left out, not the whole tab.
        val media = async { lastKnown { api.assetMedia(property, assetId) } }
        val files = async { lastKnown { api.assetFiles(property, assetId) } }
        val pages = async { lastKnown { api.webDocuments(property, assetId) } }
        val manuals = async { lastKnown { jobs.assetManuals(property, assetId).manuals } }
        val names = fieldNames(assetId)
        val parts = listOf(files, media, pages, manuals).map { it.await() }
        // Offline with none of them kept: offline, as before.
        if (parts.all { it == null }) throw OnlineException(OnlineError.Offline)
        // As the web's Documents tab: the file the asset was read from first, then the documents added to it (one answering
        // a field is that field's, not one added to the asset), then web pages and manuals.
        files.await().orEmpty().map { it.toDocument() } +
            media.await().orEmpty().filter { it.kind == DOCUMENT_KIND && it.fieldId == null }.map { it.toDocument() } +
            pages.await().orEmpty().map { it.toDocument(names) } +
            manuals.await().orEmpty().map { it.toDocument() }
    }

    /** [read]'s answer, or null when it fails only for want of a connection (offline with nothing kept for it). */
    private suspend fun <T> lastKnown(read: suspend () -> List<T>): List<T>? = try {
        read()
    } catch (e: OnlineException) {
        if (e.error == OnlineError.Offline) null else throw e
    }

    override suspend fun download(assetId: String, document: AssetDocument): OpenableFile {
        val property = property()
        val name = fileName(document)
        val keep = { body: ResponseBody ->
            val type = body.contentType()?.let { "${it.type}/${it.subtype}" }
            OpenableFile(body.byteStream().use { downloads.keep(document.id, name, it) }, type)
        }
        return when (document.source) {
            DocumentSource.ATTACHMENT -> api.mediaContent(property, assetId, document.id, keep)
            DocumentSource.SOURCE_FILE -> api.documentContent(property, document.id, keep)
            DocumentSource.WEB_PAGE -> api.webDocumentContent(property, assetId, document.id, keep)
            DocumentSource.MANUAL -> api.manualContent(property, document.id, keep)
        }
    }

    override suspend fun removeAttachment(assetId: String, documentId: String) = api.removeMedia(property(), assetId, documentId)

    override suspend fun removeWebPage(assetId: String, documentId: String) = api.removeWebDocument(property(), assetId, documentId)


    /** Field id → name, for what a web page was used for (the asset's fields as the last sync brought them). */
    private suspend fun fieldNames(assetId: String): Map<String, String> {
        val labels = fields.effective(assetId).associate { it.key to it.label }
        return fields.idsByKey(assetId).entries.mapNotNull { (key, id) -> labels[key]?.let { id to it } }.toMap()
    }

    /** The name the fetched file is kept under: a web page's or manual's PDF is named after its title. */
    private fun fileName(d: AssetDocument): String = when (d.source) {
        DocumentSource.WEB_PAGE, DocumentSource.MANUAL -> d.title.removeSuffix(".pdf").ifBlank { DEFAULT_NAME } + ".pdf"
        DocumentSource.ATTACHMENT, DocumentSource.SOURCE_FILE -> d.title.ifBlank { DEFAULT_NAME }
    }

    /** A document added to the asset. */
    private fun MediaDto.toDocument(): AssetDocument {
        val name = filename.ifBlank { DEFAULT_NAME }
        return AssetDocument(
            id = id, source = DocumentSource.ATTACHMENT, title = name, url = null, publisher = null,
            hasFile = true, fileKind = documentKind(name, contentType), uploadedBy = createdBy,
            uploadedAt = parseDbTime(createdAt), sizeBytes = sizeBytes, contentType = contentType?.takeIf { it.isNotBlank() },
        )
    }

    private fun AssetFileDto.toDocument() = AssetDocument(
        id = id, source = DocumentSource.SOURCE_FILE, title = name, url = null, publisher = null,
        hasFile = true, fileKind = kind, uploadedAt = parseDbTime(createdAt), sizeBytes = sizeBytes,
        contentType = contentType?.takeIf { it.isNotBlank() }, preview = preview?.takeIf { it.isNotBlank() },
    )

    private fun WebDocumentDto.toDocument(names: Map<String, String>) = AssetDocument(
        id = id, source = DocumentSource.WEB_PAGE, title = title, url = url, publisher = publisher?.takeIf { it.isNotBlank() },
        official = official, usedFor = fieldIds.mapNotNull(names::get), fileStatus = DocumentFileStatus.of(fileStatus),
        hasFile = hasFile, fileKind = if (hasFile || isPdfLink(url)) PDF else null, sizeBytes = sizeBytes,
    )

    private fun ManualDto.toDocument() = AssetDocument(
        id = id, source = DocumentSource.MANUAL, title = title, url = url, publisher = publisher?.takeIf { it.isNotBlank() },
        official = true, manualKind = ManualKind.of(kind),
        model = listOf(manufacturer, model).filter { it.isNotBlank() }.joinToString(" ").ifEmpty { null },
        // A manual's file is downloaded as it is found: kept, or only its link.
        fileStatus = if (hasFile) DocumentFileStatus.SAVED else DocumentFileStatus.LINK, hasFile = hasFile, fileKind = PDF,
    )

    private fun isPdfLink(url: String?): Boolean = url.orEmpty().contains(PDF_LINK)

    private companion object {
        /** The media kind of a document added to an asset (media.py). */
        const val DOCUMENT_KIND = "document"
        const val FILE_SCHEME = "file"
        const val PDF = "pdf"
        const val DEFAULT_NAME = "document"
        val PDF_LINK = Regex("\\.pdf($|[?#])", RegexOption.IGNORE_CASE)
    }
}
