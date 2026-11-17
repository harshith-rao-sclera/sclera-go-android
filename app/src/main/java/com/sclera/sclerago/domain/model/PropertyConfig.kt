package com.sclera.sclerago.domain.model

// Configuration → Settings, photo rules and geofence: property-wide settings the server keeps (`/properties/{id}/settings`
// and `/properties/{id}/geofence`).

/** All kinds of photo together allow at most this many photos per asset (the server's limit). */
const val MAX_PHOTOS_PER_ASSET = 15

/** A property's settings at the server: the onboarding switches, AI in validation, and the photo rules (one per [PhotoKind]). */
data class PropertySettings(
    val bluetoothOnboarding: Boolean,
    val wifiOnboarding: Boolean,
    /** Off: validation runs skip the AI checks for this property. */
    val aiValidation: Boolean,
    val photoRules: List<PhotoRule>,
)

/** A point on the map. */
data class GeoPoint(val lat: Double, val lng: Double)

/** One fence: its outer ring, then any holes (each ring a list of points; the closing point is not repeated). */
data class Fence(val rings: List<List<GeoPoint>>)

/** The fences drawn round a property and whether the phone must be inside one to collect asset data there. */
data class Geofence(val enforced: Boolean, val fences: List<Fence>)

/** The server's limits on fences: at most [MAX_FENCES] fences, each ring 3 to [MAX_FENCE_POINTS] distinct points. */
const val MAX_FENCES = 50
const val MAX_FENCE_POINTS = 2000
const val MIN_FENCE_POINTS = 3

/** What changes on a building, floor or location at the server (null = left as it is). */
data class PlaceChange(
    val name: String? = null,
    val code: String? = null,
    val type: String? = null,
    val active: Boolean? = null,
    val position: Int? = null,
)

/** The server's limits on a place's code and type. */
const val MAX_PLACE_CODE = 40
const val MAX_PLACE_TYPE = 80
const val MAX_PLACE_NAME = 200
