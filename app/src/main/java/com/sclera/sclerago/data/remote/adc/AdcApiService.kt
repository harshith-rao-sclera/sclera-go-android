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
 * Retrofit description of the ADC `/v1` API ([AdcConfig.API_URL]) for the signed-in user, property, settings,
 * member and access calls. Bodies stay raw: [OnlineCall] turns errors into [com.sclera.sclerago.domain.model.OnlineError]
 * and [AdcApi] parses the JSON. The Authorization header is added by the client ([AdcHttp]), never here.
 */
interface AdcApiService {
    @GET("me")
    suspend fun me(): Response<ResponseBody>

    // ---- Properties

    @GET("organisations/{org}/properties")
    suspend fun properties(
        @Path("org") org: String,
        @Query("limit") limit: Int,
        @Query("cursor") cursor: String?,
    ): Response<ResponseBody>

    @POST("organisations/{org}/properties")
    suspend fun createProperty(@Path("org") org: String, @Body body: RequestBody): Response<ResponseBody>

    @GET("properties/{id}")
    suspend fun property(@Path("id") id: String): Response<ResponseBody>

    @PATCH("properties/{id}")
    suspend fun updateProperty(@Path("id") id: String, @Body body: RequestBody): Response<ResponseBody>

    @GET("properties/{id}/settings")
    suspend fun settings(@Path("id") id: String): Response<ResponseBody>

    @PATCH("properties/{id}/settings")
    suspend fun updateSettings(@Path("id") id: String, @Body body: RequestBody): Response<ResponseBody>

    @GET("properties/{id}/members")
    suspend fun members(@Path("id") id: String): Response<ResponseBody>

    @POST("properties/{id}/members")
    suspend fun addMember(@Path("id") id: String, @Body body: RequestBody): Response<ResponseBody>

    @DELETE("properties/{id}/members/{user}")
    suspend fun removeMember(@Path("id") id: String, @Path("user") userId: String): Response<ResponseBody>

    // ---- Access

    @GET("organisations/{org}/access/permissions")
    suspend fun permissions(@Path("org") org: String): Response<ResponseBody>

    @GET("organisations/{org}/groups")
    suspend fun groups(@Path("org") org: String): Response<ResponseBody>

    @POST("organisations/{org}/groups")
    suspend fun createGroup(@Path("org") org: String, @Body body: RequestBody): Response<ResponseBody>

    @PATCH("organisations/{org}/groups/{id}")
    suspend fun updateGroup(@Path("org") org: String, @Path("id") id: String, @Body body: RequestBody): Response<ResponseBody>

    @DELETE("organisations/{org}/groups/{id}")
    suspend fun deleteGroup(@Path("org") org: String, @Path("id") id: String): Response<ResponseBody>

    @GET("organisations/{org}/users")
    suspend fun users(
        @Path("org") org: String,
        @Query("limit") limit: Int,
        @Query("cursor") cursor: String?,
    ): Response<ResponseBody>

    @POST("organisations/{org}/users")
    suspend fun inviteUser(@Path("org") org: String, @Body body: RequestBody): Response<ResponseBody>

    @PATCH("organisations/{org}/users/{id}")
    suspend fun updateUser(@Path("org") org: String, @Path("id") id: String, @Body body: RequestBody): Response<ResponseBody>

    @GET("organisations/{org}/directory")
    suspend fun directory(@Path("org") org: String, @Query("q") q: String?, @Query("limit") limit: Int): Response<ResponseBody>
}
