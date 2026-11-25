package com.sclera.sclerago.data.remote.adc

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody
import okio.BufferedSink
import java.io.IOException
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The collect-from-files calls and an asset's Documents tab, as typed results. Every call can throw
 * [com.sclera.sclerago.domain.model.OnlineException]; see [OnlineCall].
 */
@Singleton
class AdcDocumentsApi @Inject constructor(private val service: AdcDocumentsService, private val call: OnlineCall) {

    /**
     * Uploads a file as the `file` part, streamed from [open] (a file can be up to the server's limit, so it is never
     * held in memory whole); [onProgress] gets 0..100 when [size] is known. [source] (`camera`) says where the assets
     * read from it come from; null for a file.
     */
    suspend fun upload(
        property: String,
        filename: String,
        contentType: String?,
        size: Long,
        open: () -> InputStream,
        onProgress: (Int) -> Unit,
        source: String? = null,
    ): DocumentDto {
        val body = StreamingFileBody((contentType ?: OCTET_STREAM).toMediaTypeOrNull(), size, open, onProgress)
        val part = MultipartBody.Part.createFormData("file", filename, body)
        val from = source?.toRequestBody(TEXT)
        return call.json({ service.upload(property, part, from) }) { documentOf(it) }
    }

    suspend fun document(property: String, document: String): DocumentDto =
        call.json({ service.document(property, document) }) { documentOf(it) }

    /** The drafts of [document] still waiting for review. */
    suspend fun pendingDrafts(property: String, document: String): List<DraftDto> =
        call.json({ service.drafts(property, document, PENDING) }) { AdcJson.decodeFromString(DraftPageDto.serializer(), it).data }

    /** Sends [values] (field id → text, null to clear) into draft [draft]; returns the draft. */
    suspend fun updateDraft(property: String, draft: String, values: Map<String, String?>): DraftDto {
        val body = JsonObject(mapOf("values" to JsonObject(values.mapValues { (_, v) -> v?.let(::JsonPrimitive) ?: JsonNull })))
        return call.json({ service.updateDraft(property, draft, body.asBody()) }) { AdcJson.decodeFromString(DraftDto.serializer(), it) }
    }

    suspend fun accept(property: String, ids: List<String>, createFields: Boolean): AcceptResultDto {
        val body = JsonObject(mapOf("ids" to ids.asJson(), "createFields" to JsonPrimitive(createFields)))
        return call.json({ service.accept(property, body.asBody()) }) { AdcJson.decodeFromString(AcceptResultDto.serializer(), it) }
    }

    suspend fun discard(property: String, ids: List<String>): DiscardResultDto {
        val body = JsonObject(mapOf("ids" to ids.asJson()))
        return call.json({ service.discard(property, body.asBody()) }) { AdcJson.decodeFromString(DiscardResultDto.serializer(), it) }
    }

    /** The file [asset] was read from, if any. */
    suspend fun assetFiles(property: String, asset: String): List<AssetFileDto> =
        call.json({ service.assetFiles(property, asset) }) { AdcJson.decodeFromString(ListSerializer(AssetFileDto.serializer()), it) }

    /** The web pages [asset]'s applied AI suggestions came from. */
    suspend fun webDocuments(property: String, asset: String): List<WebDocumentDto> =
        call.json({ service.webDocuments(property, asset) }) { AdcJson.decodeFromString(ListSerializer(WebDocumentDto.serializer()), it) }

    suspend fun removeWebDocument(property: String, asset: String, document: String) =
        call.unit { service.removeWebDocument(property, asset, document) }

    /** The uploaded file [document], handed to [read] as it downloads. */
    suspend fun <T> documentContent(property: String, document: String, read: (ResponseBody) -> T): T =
        call.stream({ service.documentContent(property, document) }, read)

    /** Web page [document]'s kept PDF, handed to [read] as it downloads. */
    suspend fun <T> webDocumentContent(property: String, asset: String, document: String, read: (ResponseBody) -> T): T =
        call.stream({ service.webDocumentContent(property, asset, document) }, read)

    /** Manual [manual]'s PDF, handed to [read] as it downloads. */
    suspend fun <T> manualContent(property: String, manual: String, read: (ResponseBody) -> T): T =
        call.stream({ service.manualContent(property, manual) }, read)

    /** [asset]'s media, in order. */
    suspend fun assetMedia(property: String, asset: String): List<MediaDto> =
        call.json({ service.assetMedia(property, asset) }) { AdcJson.decodeFromString(ListSerializer(MediaDto.serializer()), it) }

    /** Adds a file to [asset]'s media, streamed from [open] as [upload] streams one. */
    suspend fun uploadMedia(property: String, asset: String, filename: String, contentType: String?, size: Long, open: () -> InputStream): MediaDto {
        val body = StreamingFileBody((contentType ?: OCTET_STREAM).toMediaTypeOrNull(), size, open) {}
        val part = MultipartBody.Part.createFormData("file", filename, body)
        return call.json({ service.uploadMedia(property, asset, part) }) { AdcJson.decodeFromString(MediaDto.serializer(), it) }
    }

    suspend fun removeMedia(property: String, asset: String, media: String) = call.unit { service.removeMedia(property, asset, media) }

    /** Media file [media] of [asset], handed to [read] as it downloads; [preview]: a photo's smaller version (1280 px). */
    suspend fun <T> mediaContent(property: String, asset: String, media: String, read: (ResponseBody) -> T, preview: Boolean = false): T =
        call.stream({ service.mediaContent(property, asset, media, preview.takeIf { it }) }, read)

    private fun documentOf(text: String) = AdcJson.decodeFromString(DocumentDto.serializer(), text)

    private fun List<String>.asJson() = JsonArray(map(::JsonPrimitive))

    private fun JsonObject.asBody(): RequestBody = toString().toRequestBody(JSON)

    private companion object {
        val JSON = "application/json".toMediaType()
        val TEXT = "text/plain".toMediaType()
        const val OCTET_STREAM = "application/octet-stream"
        const val PENDING = "pending"
    }
}

/**
 * A request body read from [open] as it is sent, telling [onProgress] the percentage written (only when [size] is
 * known, and only when it changes). OkHttp may write it again on a retry, so each write opens the stream anew.
 */
private class StreamingFileBody(
    private val type: MediaType?,
    private val size: Long,
    private val open: () -> InputStream,
    private val onProgress: (Int) -> Unit,
) : RequestBody() {
    override fun contentType(): MediaType? = type

    override fun contentLength(): Long = if (size > 0) size else -1

    override fun writeTo(sink: BufferedSink) {
        val input = runCatching(open).getOrElse { throw IOException("file can't be opened", it) }
        input.use { stream ->
            val buffer = ByteArray(BUFFER_BYTES)
            var sent = 0L
            var shown = -1
            while (true) {
                val read = stream.read(buffer)
                if (read < 0) break
                sink.write(buffer, 0, read)
                sent += read
                if (size > 0) {
                    val percent = (sent * 100 / size).toInt().coerceIn(0, 100)
                    if (percent != shown) {
                        shown = percent
                        onProgress(percent)
                    }
                }
            }
        }
    }

    private companion object {
        const val BUFFER_BYTES = 64 * 1024
    }
}
