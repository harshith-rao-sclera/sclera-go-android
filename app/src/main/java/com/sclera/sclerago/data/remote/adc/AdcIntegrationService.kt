package com.sclera.sclerago.data.remote.adc

import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/** Retrofit description of the ADC `/v1` Integration (Managed Assets / VDMS) calls. Same client and error handling as [AdcApiService]. */
interface AdcIntegrationService {
    @GET("properties/{p}/integration")
    suspend fun integration(@Path("p") property: String): Response<ResponseBody>

    @GET("properties/{p}/integration/available-vdms")
    suspend fun available(@Path("p") property: String, @Query("key") key: String?): Response<ResponseBody>

    /** Retrofit encodes [vdmsId] as one path segment. */
    @POST("properties/{p}/integration/vdms/{vdms}")
    suspend fun link(@Path("p") property: String, @Path("vdms") vdmsId: String): Response<ResponseBody>

    /** The linked VDMS's network names, which a moved asset goes on. */
    @GET("properties/{p}/integration/networks")
    suspend fun networks(@Path("p") property: String): Response<ResponseBody>

    /** People the VDMS can assign moved assets to. */
    @GET("properties/{p}/integration/assignees")
    suspend fun assignees(@Path("p") property: String, @Query("key") key: String?): Response<ResponseBody>

    /** Assets moved to Managed Assets and sent back; place ids are comma-separated (none: every place). */
    @GET("properties/{p}/integration/counts")
    suspend fun counts(
        @Path("p") property: String,
        @Query("buildingId") buildingIds: String?,
        @Query("floorId") floorIds: String?,
        @Query("locationId") locationIds: String?,
    ): Response<ResponseBody>

    /** Moves validated assets to Managed Assets (the VDMS). */
    @POST("properties/{p}/integration/move")
    suspend fun move(@Path("p") property: String, @Body body: RequestBody): Response<ResponseBody>
}
