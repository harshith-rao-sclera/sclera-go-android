package com.sclera.sclerago.services.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.sclera.sclerago.domain.model.GeoPoint
import com.sclera.sclerago.domain.repository.DeviceLocationSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * The phone's position from the platform [LocationManager] (no extra library), to tell whether it is inside a property's
 * fences. It reads only when the location permission is already granted (it never asks), asks the system for one fresh
 * fix per provider, and falls back to a recent last known one. The position goes straight back to the caller: it is not
 * kept, logged or sent.
 */
@Singleton
class AndroidDeviceLocationSource @Inject constructor(
    @ApplicationContext private val context: Context,
) : DeviceLocationSource {
    override fun permissionGranted(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    override suspend fun current(): GeoPoint? {
        if (!permissionGranted()) return null
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        val providers = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
            .filter { runCatching { manager.isProviderEnabled(it) }.getOrDefault(false) }
        for (provider in providers) {
            fresh(manager, provider)?.let { return it.toPoint() }
        }
        return recent(manager)?.toPoint()
    }

    @SuppressLint("MissingPermission")
    private suspend fun fresh(manager: LocationManager, provider: String): Location? = withTimeoutOrNull(FIX_TIMEOUT_MS) {
        suspendCancellableCoroutine { cont ->
            val signal = CancellationSignal()
            cont.invokeOnCancellation { signal.cancel() }
            try {
                LocationManagerCompat.getCurrentLocation(manager, provider, signal, ContextCompat.getMainExecutor(context)) { cont.resume(it) }
            } catch (_: SecurityException) {
                cont.resume(null)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun recent(manager: LocationManager): Location? {
        val now = System.currentTimeMillis()
        return listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER, LocationManager.PASSIVE_PROVIDER)
            .mapNotNull { runCatching { if (it in manager.allProviders) manager.getLastKnownLocation(it) else null }.getOrNull() }
            .filter { now - it.time <= MAX_AGE_MS }
            .maxByOrNull { it.time }
    }

    private fun Location.toPoint() = GeoPoint(lat = latitude, lng = longitude)

    private companion object {
        const val FIX_TIMEOUT_MS = 8_000L
        const val MAX_AGE_MS = 10 * 60_000L
    }
}
