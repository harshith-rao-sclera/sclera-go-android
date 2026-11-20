package com.sclera.sclerago.data.remote.adc

import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path

/**
 * Retrofit description of the ADC `/v1` calls that start work on the server and are polled: spec look-ups
 * (AI autofill) and validation runs. Same client and error handling as [AdcApiService].
 */
interface AdcJobsService {
    /** The manuals of the asset's model and the latest search for them. */
    @GET("properties/{property}/assets/{asset}/manuals")
    suspend fun assetManuals(@Path("property") property: String, @Path("asset") asset: String): Response<ResponseBody>

    @POST("properties/{property}/assets/{asset}/lookup-jobs")
    suspend fun startLookup(
        @Path("property") property: String,
        @Path("asset") asset: String,
        @Body body: RequestBody,
    ): Response<ResponseBody>

    /** The asset's newest look-up, or `null`. */
    @GET("properties/{property}/assets/{asset}/lookup-jobs/latest")
    suspend fun latestLookup(@Path("property") property: String, @Path("asset") asset: String): Response<ResponseBody>

    @GET("properties/{property}/lookup-jobs/{job}")
    suspend fun lookup(@Path("property") property: String, @Path("job") job: String): Response<ResponseBody>

    @POST("properties/{property}/lookup-jobs/{job}/seen")
    suspend fun lookupSeen(@Path("property") property: String, @Path("job") job: String): Response<ResponseBody>

    /**
     * The phone's look-up (multipart): up to 3 `photos` (nameplate, label, equipment), `values` (JSON: field id -> what
     * is typed) and optionally `masterFormatId`. The AI reads the photos, the fields still empty are looked up online
     * by the model, and without a type it suggests one (`suggestedType`). Nothing is saved; it waits for the AI and the
     * web search, so it gets the longer read timeout.
     */
    @Multipart
    @Headers(AdcConfig.SLOW_CALL)
    @POST("properties/{property}/lookup")
    suspend fun lookUpOnPhone(@Path("property") property: String, @Part parts: List<MultipartBody.Part>): Response<ResponseBody>

    /**
     * Voice fill: what was said (already text) and the asset's Info form; the AI answers with values for the fields that
     * were spoken about. Nothing is saved. `422` when nothing matched a field; the server gives the AI 15 seconds.
     */
    @Headers(AdcConfig.SLOW_CALL)
    @POST("properties/{property}/voice-fill")
    suspend fun voiceFill(@Path("property") property: String, @Body body: RequestBody): Response<ResponseBody>

    /** Validates one asset again: from its first failed or warning step, or every step with `{"fromStart": true}`. */
    @POST("properties/{property}/assets/{asset}/revalidate")
    suspend fun revalidate(@Path("property") property: String, @Path("asset") asset: String, @Body body: RequestBody): Response<ResponseBody>

    @GET("properties/{property}/validation-jobs/{job}")
    suspend fun validation(@Path("property") property: String, @Path("job") job: String): Response<ResponseBody>
}
