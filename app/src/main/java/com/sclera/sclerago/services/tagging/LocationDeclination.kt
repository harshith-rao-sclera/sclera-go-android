package com.sclera.sclerago.services.tagging

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.GeomagneticField
import android.location.Location
import android.location.LocationManager
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.sclera.sclerago.domain.direction.DeclinationSource

/**
 * The magnetic declination at the last known location of the device, for turning a magnetic compass bearing into a true one. It only
 * reads a location the system already has and only when location permission is already granted: it never asks for it and never
 * starts a location request. The location is used once for the declination and is neither kept, logged nor sent; only the
 * declination (degrees) is kept, for the scan. Without a fix it tries again at most every two seconds.
 */
class LocationDeclination(private val context: Context) : DeclinationSource {
    private var known: Double? = null
    private var lastTryMs = -RETRY_MS

    @Synchronized
    override fun degrees(): Double? {
        known?.let { return it }
        val now = SystemClock.elapsedRealtime()
        if (now - lastTryMs < RETRY_MS) return null
        lastTryMs = now
        val location = lastKnownLocation() ?: return null
        known = GeomagneticField(
            location.latitude.toFloat(), location.longitude.toFloat(), location.altitude.toFloat(), System.currentTimeMillis(),
        ).declination.toDouble()
        return known
    }

    @SuppressLint("MissingPermission")
    private fun lastKnownLocation(): Location? {
        val granted = listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            .any { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
        if (!granted) return null
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        return listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
            .mapNotNull { provider ->
                runCatching { if (provider in manager.allProviders) manager.getLastKnownLocation(provider) else null }.getOrNull()
            }
            .maxByOrNull { it.time }
    }

    private companion object {
        const val RETRY_MS = 2000L
    }
}
