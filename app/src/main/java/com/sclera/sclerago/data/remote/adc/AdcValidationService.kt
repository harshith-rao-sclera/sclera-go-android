package com.sclera.sclerago.data.remote.adc

import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Retrofit description of the ADC `/v1` Validation page calls (catalogue, saved validations, runs, results, issues,
 * decisions, export, merge). Same client and error handling as [AdcApiService].
 */
interface AdcValidationService {
    @GET("properties/{p}/validation/rules")
    suspend fun rules(@Path("p") property: String): Response<ResponseBody>

    @GET("properties/{p}/validation/templates")
    suspend fun templates(@Path("p") property: String): Response<ResponseBody>

    @GET("properties/{p}/validation/order")
    suspend fun order(@Path("p") property: String): Response<ResponseBody>

    @PUT("properties/{p}/validation/order")
    suspend fun saveOrder(@Path("p") property: String, @Body body: RequestBody): Response<ResponseBody>

    @DELETE("properties/{p}/validation/order")
    suspend fun resetOrder(@Path("p") property: String): Response<ResponseBody>

    @GET("properties/{p}/fields")
    suspend fun fields(
        @Path("p") property: String,
        @Query("active") active: Boolean,
        @Query("limit") limit: Int,
        @Query("cursor") cursor: String?,
    ): Response<ResponseBody>

    @GET("properties/{p}/validations")
    suspend fun validations(@Path("p") property: String): Response<ResponseBody>

    @POST("properties/{p}/validations")
    suspend fun createValidation(@Path("p") property: String, @Body body: RequestBody): Response<ResponseBody>

    @PATCH("properties/{p}/validations/{id}")
    suspend fun updateValidation(@Path("p") property: String, @Path("id") id: String, @Body body: RequestBody): Response<ResponseBody>

    @DELETE("properties/{p}/validations/{id}")
    suspend fun deleteValidation(@Path("p") property: String, @Path("id") id: String): Response<ResponseBody>

    @GET("properties/{p}/validation-jobs")
    suspend fun jobs(@Path("p") property: String, @Query("limit") limit: Int): Response<ResponseBody>

    @POST("properties/{p}/validation-jobs")
    suspend fun startJob(@Path("p") property: String, @Body body: RequestBody): Response<ResponseBody>

    @GET("properties/{p}/validation-jobs/{job}")
    suspend fun job(@Path("p") property: String, @Path("job") job: String): Response<ResponseBody>

    @POST("properties/{p}/validation-jobs/{job}/run")
    suspend fun runReady(@Path("p") property: String, @Path("job") job: String): Response<ResponseBody>

    /** A finished run again: a new run with the same steps, settings and assets. */
    @POST("properties/{p}/validation-jobs/{job}/rerun")
    suspend fun rerun(@Path("p") property: String, @Path("job") job: String): Response<ResponseBody>

    @DELETE("properties/{p}/validation-jobs/{job}")
    suspend fun discard(@Path("p") property: String, @Path("job") job: String): Response<ResponseBody>

    @GET("properties/{p}/validation-jobs/{job}/results")
    suspend fun results(
        @Path("p") property: String,
        @Path("job") job: String,
        @Query("outcome") outcome: String?,
        @Query("step") step: String?,
        @Query("q") query: String?,
        @Query("limit") limit: Int,
        @Query("cursor") cursor: String?,
    ): Response<ResponseBody>

    @GET("properties/{p}/validation-jobs/{job}/issues")
    suspend fun issues(
        @Path("p") property: String,
        @Path("job") job: String,
        @Query("status") status: String,
        @Query("assetId") assetId: String?,
        @Query("limit") limit: Int,
    ): Response<ResponseBody>

    @GET("properties/{p}/validation-jobs/{job}/insights")
    suspend fun insights(@Path("p") property: String, @Path("job") job: String): Response<ResponseBody>

    @GET("properties/{p}/validation-jobs/{job}/export")
    suspend fun export(@Path("p") property: String, @Path("job") job: String): Response<ResponseBody>

    @POST("properties/{p}/validation-jobs/{job}/decisions")
    suspend fun decide(@Path("p") property: String, @Path("job") job: String, @Body body: RequestBody): Response<ResponseBody>

    @POST("properties/{p}/validation-issues/{id}/accept")
    suspend fun accept(@Path("p") property: String, @Path("id") id: String, @Body body: RequestBody): Response<ResponseBody>

    @POST("properties/{p}/validation-issues/{id}/dismiss")
    suspend fun dismiss(@Path("p") property: String, @Path("id") id: String): Response<ResponseBody>

    @POST("properties/{p}/validation-issues/{id}/revert")
    suspend fun revert(@Path("p") property: String, @Path("id") id: String): Response<ResponseBody>

    @POST("properties/{p}/validation-issues/bulk")
    suspend fun bulk(@Path("p") property: String, @Body body: RequestBody): Response<ResponseBody>

    @GET("properties/{p}/assets/{id}")
    suspend fun asset(@Path("p") property: String, @Path("id") id: String): Response<ResponseBody>

    @POST("properties/{p}/assets/merge")
    suspend fun merge(@Path("p") property: String, @Body body: RequestBody): Response<ResponseBody>
}
