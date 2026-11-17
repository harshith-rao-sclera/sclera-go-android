package com.sclera.sclerago.data.remote.adc

import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Retrofit description of the ADC `/v1` calls of Configuration → Asset fields and Building info (a property's standard
 * fields, buildings, floors and locations). Bodies stay raw: [OnlineCall] maps errors and [AdcConfigApi] parses the JSON.
 */
interface AdcConfigService {
    // ---- Asset fields

    @GET("properties/{p}/fields")
    suspend fun fields(
        @Path("p") property: String,
        @Query("standard") standard: Boolean,
        @Query("limit") limit: Int,
        @Query("cursor") cursor: String?,
    ): Response<ResponseBody>

    @POST("properties/{p}/fields/bulk")
    suspend fun createFields(@Path("p") property: String, @Body body: RequestBody): Response<ResponseBody>

    @PATCH("properties/{p}/fields/{id}")
    suspend fun updateField(@Path("p") property: String, @Path("id") id: String, @Body body: RequestBody): Response<ResponseBody>

    @DELETE("properties/{p}/fields/{id}")
    suspend fun deleteField(@Path("p") property: String, @Path("id") id: String): Response<ResponseBody>

    // ---- Buildings, floors, locations

    @GET("properties/{p}/buildings")
    suspend fun buildings(@Path("p") property: String, @Query("limit") limit: Int, @Query("cursor") cursor: String?): Response<ResponseBody>

    @GET("properties/{p}/floors")
    suspend fun floors(@Path("p") property: String, @Query("limit") limit: Int, @Query("cursor") cursor: String?): Response<ResponseBody>

    @GET("properties/{p}/locations")
    suspend fun locations(@Path("p") property: String, @Query("limit") limit: Int, @Query("cursor") cursor: String?): Response<ResponseBody>

    @POST("properties/{p}/buildings")
    suspend fun createBuilding(@Path("p") property: String, @Body body: RequestBody): Response<ResponseBody>

    @POST("properties/{p}/buildings/{b}/floors")
    suspend fun createFloor(@Path("p") property: String, @Path("b") building: String, @Body body: RequestBody): Response<ResponseBody>

    @POST("properties/{p}/floors/{f}/locations")
    suspend fun createLocation(@Path("p") property: String, @Path("f") floor: String, @Body body: RequestBody): Response<ResponseBody>

    @PATCH("properties/{p}/{kind}/{id}")
    suspend fun updatePlace(
        @Path("p") property: String,
        @Path("kind") kind: String,
        @Path("id") id: String,
        @Body body: RequestBody,
    ): Response<ResponseBody>

    @DELETE("properties/{p}/{kind}/{id}")
    suspend fun deletePlace(@Path("p") property: String, @Path("kind") kind: String, @Path("id") id: String): Response<ResponseBody>
}
