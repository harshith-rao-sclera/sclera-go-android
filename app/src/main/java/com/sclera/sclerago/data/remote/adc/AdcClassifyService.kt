package com.sclera.sclerago.data.remote.adc

import okhttp3.MultipartBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Retrofit description of the ADC `/v1` calls that classify an asset and list the fields a type brings. Same client
 * and error handling as [AdcApiService]; nothing here saves anything at the server.
 */
interface AdcClassifyService {
    /** Multipart: `details` (JSON text), up to 3 `photos`, and optionally `masterFormatId` (the type picked). */
    @Multipart
    @POST("properties/{property}/classification/suggest")
    suspend fun suggest(@Path("property") property: String, @Part parts: List<MultipartBody.Part>): Response<ResponseBody>

    /** The fields an asset of one type gets (a retired type still answers). */
    @GET("properties/{property}/master-formats/{format}/fields")
    suspend fun typeFields(@Path("property") property: String, @Path("format") format: String): Response<ResponseBody>

    /** Every active type's fields, `{ data: [{ masterFormatId, fields }] }`; [ids] (comma-separated) narrows it. */
    @GET("properties/{property}/master-formats/fields")
    suspend fun allTypeFields(@Path("property") property: String, @Query("ids") ids: String?): Response<ResponseBody>
}
