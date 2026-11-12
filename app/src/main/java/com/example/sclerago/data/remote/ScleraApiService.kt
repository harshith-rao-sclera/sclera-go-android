package com.example.sclerago.data.remote

import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.HeaderMap
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Url

/**
 * Retrofit description of the Sclera accounts API ([AuthConfig.SCLERA_API]).
 *
 * Bodies stay raw ([ResponseBody]): the backend reports errors through a `status` field in any
 * response shape, which [HttpResult] interprets. Emails are passed already URL-encoded
 * (`encoded = true`) so requests match the web app's byte for byte.
 */
interface ScleraApiService {
    @GET("session")
    suspend fun session(@Header("Cookie") cookie: String): Response<ResponseBody>

    @POST("token")
    suspend fun token(@Header("Cookie") cookie: String): Response<ResponseBody>

    @GET("userInfo")
    suspend fun userInfo(
        @Query("loggedInUser", encoded = true) encodedEmail: String,
        @Header("Authorization") authorization: String,
        @Header("Cookie") cookie: String,
    ): Response<ResponseBody>

    @POST("user/{email}/logoutUserByEmail")
    suspend fun logout(
        @Path("email", encoded = true) encodedEmail: String,
        @Query("loggedInUser", encoded = true) encodedLoggedInUser: String,
        @HeaderMap headers: Map<String, String>,
    ): Response<ResponseBody>

    // ---- Authenticated calls to any Sclera API (see AuthRepositoryImpl.request)

    @GET
    suspend fun get(@Url url: String, @Header("Authorization") authorization: String): Response<ResponseBody>

    @POST
    suspend fun post(@Url url: String, @Header("Authorization") authorization: String, @Body body: RequestBody): Response<ResponseBody>

    @PUT
    suspend fun put(@Url url: String, @Header("Authorization") authorization: String, @Body body: RequestBody): Response<ResponseBody>

    @DELETE
    suspend fun delete(@Url url: String, @Header("Authorization") authorization: String): Response<ResponseBody>
}
