package com.sclera.sclerago.data.remote.tagging

import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.Query
import retrofit2.http.Url

/**
 * Retrofit description of the Sclera server's tag calls (`/vdms/api/...`, not the `/v1` API). The path differs by tag kind, so each
 * call takes its relative [path] (built by [TagPaths], ids encoded as one segment, no leading slash).
 */
interface TagService {
    @GET
    suspend fun check(
        @Url path: String,
        @Query("collectionId") asset: String,
        @Query("propertyId") property: String,
    ): Response<ResponseBody>

    @POST
    suspend fun register(@Url path: String, @Body body: RequestBody): Response<ResponseBody>

    /** A QR code's write: a `body` part holding the JSON, and the photo as the `image` part (as the old Sclera app sends it). */
    @Multipart
    @PUT
    suspend fun writeWithPhoto(
        @Url path: String,
        @Part parts: List<MultipartBody.Part>,
        /** Writing sends a photo, so it asks for a longer read timeout (see [com.sclera.sclerago.data.remote.adc.SlowCallInterceptor]). */
        @Header(com.sclera.sclerago.data.remote.adc.AdcConfig.SLOW_CALL_HEADER) timeoutSeconds: String,
    ): Response<ResponseBody>

    /** A barcode or NFC write has no photo: a plain JSON body. */
    @PUT
    suspend fun write(@Url path: String, @Body body: RequestBody): Response<ResponseBody>
}
