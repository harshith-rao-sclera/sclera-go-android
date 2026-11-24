package com.sclera.sclerago.domain.model

/**
 * Why the signed-in user can't use a button that needs a permission (the User Access Guide's rules). The button stays
 * where it is, greyed out, and a tap says why instead of running. Asset-level reasons (assigned to someone else,
 * submitted, in Managed Assets) are [AssetLock].
 */
enum class AccessBlock {
    /** The access is still being fetched (or the fetch failed with nothing kept): nothing is allowed until it's known. */
    CHECKING,

    /** The person isn't in a group yet: the server refuses everything for them. */
    NO_GROUP,

    /** The group lacks the permission the button needs. */
    NEEDS_VIEW,
    NEEDS_COLLECT,
    NEEDS_CHECK,
    NEEDS_VALIDATE,
    NEEDS_CONFIGURE,
    NEEDS_MANAGE_PROPERTIES,
    NEEDS_MANAGE_ACCESS,

    /** The group has Manage properties or Manage access, but the person has a chosen list of properties, not all of them. */
    NEEDS_ALL_PROPERTIES,

    /** A collector of a property with geofencing on who is away from the site (or whose location is unknown): view only. */
    OFF_PREMISE,
    ;

    companion object {
        /** The block for a group that lacks [permission]. */
        fun missing(permission: Permission): AccessBlock = when (permission) {
            Permission.VIEW -> NEEDS_VIEW
            Permission.COLLECT -> NEEDS_COLLECT
            Permission.CHECK -> NEEDS_CHECK
            Permission.VALIDATE -> NEEDS_VALIDATE
            Permission.CONFIGURE -> NEEDS_CONFIGURE
            Permission.MANAGE_PROPERTIES -> NEEDS_MANAGE_PROPERTIES
            Permission.MANAGE_ACCESS -> NEEDS_MANAGE_ACCESS
        }
    }
}
