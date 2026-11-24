package com.sclera.sclerago.domain.model

/**
 * Whether the phone is on premise of the current property, for a property with geofencing on. Nothing here holds a position.
 */
enum class PremiseStatus {
    /** The current property has geofencing off (or there is no current property): nobody is restricted. */
    NOT_APPLICABLE,

    /** Geofencing is on and the phone is inside one of the property's fences. */
    ON_PREMISE,

    /** Geofencing is on and the phone is outside every fence. */
    OFF_PREMISE,

    /** Geofencing is on, but the location permission is not granted, so the location is unknown. */
    LOCATION_PERMISSION_NEEDED,

    /** Geofencing is on, but there is no location fix or the property has no fence drawn. */
    UNKNOWN,
    ;

    /** A collector is held to view only: anything but "not applicable" and "on premise" (unknown counts as off premise). */
    val restrictsCollectors: Boolean get() = this != NOT_APPLICABLE && this != ON_PREMISE
}

/** Why a collector is view only, to say so on screen; [NONE] when they are not held to it. */
enum class PremiseNotice { NONE, OFF_PREMISE, LOCATION_NEEDED, LOCATION_UNKNOWN }
