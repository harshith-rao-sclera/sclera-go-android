package com.sclera.sclerago.data.remote.adc

import com.sclera.sclerago.domain.model.GeoPoint
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Where the phone was last found, for the server's geo restriction: someone whose group is geo restricted sends it with
 * every call inside a property (`X-ADC-Position: latitude,longitude`), and the server lets them change things there only
 * from inside its geofence (from anywhere else, or without it, they may only look). Set by the premise check, only for
 * such a person and only while the open property has a fence; null otherwise, so nobody else's position ever leaves the
 * phone. Held in memory only (never stored or logged) and cleared on sign-out.
 */
@Singleton
class PositionForCalls @Inject constructor() {
    @Volatile
    private var point: GeoPoint? = null

    fun set(position: GeoPoint?) {
        point = position
    }

    /** The header's value ("42.361000,-71.103000"), or null when there is nothing to send. */
    fun header(): String? = point?.let { String.format(Locale.ROOT, "%.6f,%.6f", it.lat, it.lng) }
}

/**
 * Adds [PositionForCalls]'s position to calls inside a property (`…/properties/{id}/…`), the only ones the server reads it
 * on. Nothing is added when there is no position to send. The header is never logged.
 */
internal class PositionHeaderInterceptor(private val position: PositionForCalls) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val value = position.header() ?: return chain.proceed(request)
        val segments = request.url.pathSegments
        val at = segments.indexOf(PROPERTIES)
        if (at < 0 || segments.size <= at + 2) return chain.proceed(request)
        return chain.proceed(request.newBuilder().header(POSITION_HEADER, value).build())
    }

    private companion object {
        const val PROPERTIES = "properties"
        const val POSITION_HEADER = "X-ADC-Position"
    }
}
