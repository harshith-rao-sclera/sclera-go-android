package com.sclera.sclerago.data.remote

import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path
import retrofit2.http.Query
import javax.inject.Inject
import javax.inject.Singleton

/** Retrofit description of the ADC API ([ApiEndpoint]). Bodies stay raw JSON, read by [httpCall]. */
interface AdcApiService {
    @GET(ApiEndpoint.ME)
    suspend fun me(@Header("Authorization") authorization: String): Response<ResponseBody>

    @GET(ApiEndpoint.PROPERTIES)
    suspend fun properties(
        @Header("Authorization") authorization: String,
        @Path("orgId") organisationId: String,
        @Query(ApiEndpoint.LIMIT) limit: Int,
        @Query(ApiEndpoint.CURSOR) cursor: String?,
    ): Response<ResponseBody>
}

/** Calls to the ADC API with a given access token. Main-safe; see [httpCall] for errors. */
@Singleton
class AdcApi @Inject constructor(private val service: AdcApiService) {

    suspend fun me(accessToken: String): HttpResult = httpCall { service.me(bearer(accessToken)) }

    /** One page of [organisationId]'s properties, starting after [cursor] (null for the first page). */
    suspend fun properties(accessToken: String, organisationId: String, cursor: String?, limit: Int = ApiEndpoint.PROPERTIES_PAGE_SIZE): HttpResult =
        httpCall { service.properties(bearer(accessToken), organisationId, limit, cursor) }

    companion object {
        /** [AdcApiService] rooted at [baseUrl] (must end in `/`), over the shared [client]. */
        fun service(baseUrl: String = ApiEndpoint.ADC_BASE_URL, client: OkHttpClient = AuthApi.okHttpClient()): AdcApiService =
            Retrofit.Builder().baseUrl(baseUrl).client(client).build().create(AdcApiService::class.java)
    }
}
