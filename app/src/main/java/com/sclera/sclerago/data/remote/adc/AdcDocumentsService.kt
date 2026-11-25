package com.sclera.sclerago.data.remote.adc

import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Streaming

/**
 * Retrofit description of the ADC `/v1` calls about files: collecting assets from files (the server's
 * `api/v1/documents.py`) and an asset's Documents tab, read from the server every time (`assets.py` files,
 * `web_documents.py`, `manuals.py`, `media.py`). Same client and error handling as [AdcApiService].
 */
interface AdcDocumentsService {
    /**
     * Uploads one `file` (multipart). `201` with the document, `processing` while the server reads it in the
     * background; `422` for a file it can't read, an empty one or one over its size limit. Storing a large file takes
     * a while, so it gets the longer read timeout.
     */
    @Multipart
    @Headers(AdcConfig.SLOW_CALL)
    @POST("properties/{property}/documents")
    suspend fun upload(
        @Path("property") property: String,
        @Part file: MultipartBody.Part,
        /** Where the assets read from it come from (`camera`); left out for a file, which the server calls `document`. */
        @Part("source") source: RequestBody? = null,
    ): Response<ResponseBody>

    @GET("properties/{property}/documents/{document}")
    suspend fun document(@Path("property") property: String, @Path("document") document: String): Response<ResponseBody>

    /** The assets read from the file; `status` pending (the default) lists those still waiting for review. */
    @GET("properties/{property}/documents/{document}/drafts")
    suspend fun drafts(
        @Path("property") property: String,
        @Path("document") document: String,
        @Query("status") status: String,
    ): Response<ResponseBody>

    /** Corrects a draft: `{"values": {fieldId: value or null}}`; answers with the draft. */
    @PATCH("properties/{property}/asset-drafts/{draft}")
    suspend fun updateDraft(@Path("property") property: String, @Path("draft") draft: String, @Body body: RequestBody): Response<ResponseBody>

    /** Adds drafts as assets: `{"ids": [...], "createFields": bool}`. */
    @POST("properties/{property}/asset-drafts/bulk/accept")
    suspend fun accept(@Path("property") property: String, @Body body: RequestBody): Response<ResponseBody>

    /** Throws drafts away: `{"ids": [...]}`. */
    @POST("properties/{property}/asset-drafts/bulk/discard")
    suspend fun discard(@Path("property") property: String, @Body body: RequestBody): Response<ResponseBody>

    /** The file the asset was read from, if any (a list of one). */
    @GET("properties/{property}/assets/{asset}/files")
    suspend fun assetFiles(@Path("property") property: String, @Path("asset") asset: String): Response<ResponseBody>

    /** The web pages the asset's applied AI suggestions came from. */
    @GET("properties/{property}/assets/{asset}/web-documents")
    suspend fun webDocuments(@Path("property") property: String, @Path("asset") asset: String): Response<ResponseBody>

    /** Takes a web page off the asset (`204`); refused on an asset the user may not change. */
    @DELETE("properties/{property}/assets/{asset}/web-documents/{document}")
    suspend fun removeWebDocument(
        @Path("property") property: String,
        @Path("asset") asset: String,
        @Path("document") document: String,
    ): Response<ResponseBody>

    /** The uploaded file the asset was read from, as it was uploaded. */
    @Streaming
    @Headers(AdcConfig.SLOW_CALL)
    @GET("properties/{property}/documents/{document}/content")
    suspend fun documentContent(@Path("property") property: String, @Path("document") document: String): Response<ResponseBody>

    /** A web page's kept PDF; `404` when only its link was kept. */
    @Streaming
    @Headers(AdcConfig.SLOW_CALL)
    @GET("properties/{property}/assets/{asset}/web-documents/{document}/content")
    suspend fun webDocumentContent(
        @Path("property") property: String,
        @Path("asset") asset: String,
        @Path("document") document: String,
    ): Response<ResponseBody>

    /** A manual's downloaded PDF. */
    @Streaming
    @Headers(AdcConfig.SLOW_CALL)
    @GET("properties/{property}/manuals/{manual}/content")
    suspend fun manualContent(@Path("property") property: String, @Path("manual") manual: String): Response<ResponseBody>

    /** The asset's media, in order (photos, videos, voice memos and the documents added to it). */
    @GET("properties/{property}/assets/{asset}/media")
    suspend fun assetMedia(@Path("property") property: String, @Path("asset") asset: String): Response<ResponseBody>

    /**
     * Adds one `file` (multipart) to the asset's media: a document becomes one added to it. `201` with the media; `409`
     * when the asset is locked, `422` for a file it can't take, an empty one or one over its size limit.
     */
    @Multipart
    @Headers(AdcConfig.SLOW_CALL)
    @POST("properties/{property}/assets/{asset}/media")
    suspend fun uploadMedia(
        @Path("property") property: String,
        @Path("asset") asset: String,
        @Part file: MultipartBody.Part,
    ): Response<ResponseBody>

    /** Takes one of the asset's media off it. `204`. */
    @DELETE("properties/{property}/assets/{asset}/media/{media}")
    suspend fun removeMedia(
        @Path("property") property: String,
        @Path("asset") asset: String,
        @Path("media") media: String,
    ): Response<ResponseBody>

    /**
     * One of the asset's media files (a photo, video, voice memo or document added to it). With `preview=true` a photo
     * comes back smaller (at most 1280 px on its longest side), as the web's thumbnails; left out (null) for the file itself.
     */
    @Streaming
    @Headers(AdcConfig.SLOW_CALL)
    @GET("properties/{property}/assets/{asset}/media/{media}/content")
    suspend fun mediaContent(
        @Path("property") property: String,
        @Path("asset") asset: String,
        @Path("media") media: String,
        @Query("preview") preview: Boolean? = null,
    ): Response<ResponseBody>
}
