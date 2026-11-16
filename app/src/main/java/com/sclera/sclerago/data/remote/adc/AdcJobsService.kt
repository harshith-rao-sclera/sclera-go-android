package com.sclera.sclerago.data.remote.adc

import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Retrofit description of the ADC `/v1` calls that start work on the server and are polled: spec look-ups
 * (AI autofill) and validation runs. Same client and error handling as [AdcApiService].
 */
interface AdcJobsService {
    @POST("properties/{property}/assets/{asset}/lookup-jobs")
    suspend fun startLookup(
        @Path("property") property: String,
        @Path("asset") asset: String,
        @Body body: RequestBody,
    ): Response<ResponseBody>

    @GET("properties/{property}/lookup-jobs/{job}")
    suspend fun lookup(@Path("property") property: String, @Path("job") job: String): Response<ResponseBody>

    @POST("properties/{property}/lookup-jobs/{job}/seen")
    suspend fun lookupSeen(@Path("property") property: String, @Path("job") job: String): Response<ResponseBody>

    @POST("properties/{property}/validation-jobs")
    suspend fun startValidation(@Path("property") property: String, @Body body: RequestBody): Response<ResponseBody>

    @GET("properties/{property}/validation-jobs/{job}")
    suspend fun validation(@Path("property") property: String, @Path("job") job: String): Response<ResponseBody>

    @GET("properties/{property}/validation-jobs/{job}/results")
    suspend fun validationResults(
        @Path("property") property: String,
        @Path("job") job: String,
        @Query("limit") limit: Int,
        @Query("cursor") cursor: String?,
    ): Response<ResponseBody>

    @GET("properties/{property}/validation-jobs/{job}/issues")
    suspend fun validationIssues(
        @Path("property") property: String,
        @Path("job") job: String,
        @Query("status") status: String,
        @Query("limit") limit: Int,
    ): Response<ResponseBody>
}
