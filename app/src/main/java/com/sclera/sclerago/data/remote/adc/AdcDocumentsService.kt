package com.sclera.sclerago.data.remote.adc

import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Retrofit description of the ADC `/v1` calls that collect assets from files (the server's `api/v1/documents.py`).
 * Same client and error handling as [AdcApiService].
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
    suspend fun upload(@Path("property") property: String, @Part file: MultipartBody.Part): Response<ResponseBody>

    @GET("properties/{property}/documents/{document}")
    suspend fun document(@Path("property") property: String, @Path("document") document: String): Response<ResponseBody>

    /** The assets read from the file; `status` pending (the default) lists those still waiting for review. */
    @GET("properties/{property}/documents/{document}/drafts")
    suspend fun drafts(
        @Path("property") property: String,
        @Path("document") document: String,
        @Query("status") status: String,
    ): Response<ResponseBody>

    /** A blank spreadsheet (xlsx) with a column for each of the property's active fields, to fill in and upload. */
    @GET("properties/{property}/documents/sample")
    suspend fun sample(@Path("property") property: String): Response<ResponseBody>

    /** Corrects a draft: `{"values": {fieldId: value or null}}`; answers with the draft. */
    @PATCH("properties/{property}/asset-drafts/{draft}")
    suspend fun updateDraft(@Path("property") property: String, @Path("draft") draft: String, @Body body: RequestBody): Response<ResponseBody>

    /** Adds drafts as assets: `{"ids": [...], "createFields": bool}`. */
    @POST("properties/{property}/asset-drafts/bulk/accept")
    suspend fun accept(@Path("property") property: String, @Body body: RequestBody): Response<ResponseBody>

    /** Throws drafts away: `{"ids": [...]}`. */
    @POST("properties/{property}/asset-drafts/bulk/discard")
    suspend fun discard(@Path("property") property: String, @Body body: RequestBody): Response<ResponseBody>
}
