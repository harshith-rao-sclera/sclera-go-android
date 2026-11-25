package com.sclera.sclerago.domain.repository

/**
 * Keeps what the server decides about the signed-in user up to date while the app is on screen: their access
 * (`GET /me`: group and permissions) and their property list (properties added or removed for them, and each property's
 * geofence switch and fences, which the geofence check follows). Offline or refused, what is known stays.
 */
interface AccessSyncScheduler {
    /** The app is on screen: reads both now, then again every 30 seconds until [stop]. Calling it again restarts it. */
    fun start()

    /** The app left the screen: no more reads until [start]. */
    fun stop()
}
