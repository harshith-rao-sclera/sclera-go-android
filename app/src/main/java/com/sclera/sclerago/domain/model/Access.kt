package com.sclera.sclerago.domain.model

// People and access: groups, their permissions and the people in them.

/**
 * What a group's people can do. [needsAllProperties] ones only make sense with access to every
 * property. [color] / [iconPath] (24×24 SVG path) are the permission's chip colour and icon.
 * Their user-facing names and descriptions live in string resources (presentation).
 */
enum class Permission(
    val color: Long,
    val iconPath: String,
    val needsAllProperties: Boolean = false,
) {
    COLLECT(
        0xFF6FA8E6, "M12 20h9M16.5 3.5a2.1 2.1 0 013 3L7 19l-4 1 1-4z",
    ),
    VALIDATE(
        0xFF5DBE8C, "M12 3l7 3v5c0 5-3 8-7 10-4-2-7-5-7-10V6l7-3zM9 12l2 2 4-4",
    ),
    CONFIGURE(
        0xFFEE9A4D, "M4 7h10M18 7h2M4 17h2M10 17h10M14 5v4M8 15v4",
    ),
    MANAGE_PROPERTIES(
        0xFFAE9CE6, "M5 21V4a1 1 0 011-1h12a1 1 0 011 1v17M3 21h18M9 7h2M13 7h2M9 11h2M13 11h2M9 15h2M13 15h2",
        needsAllProperties = true,
    ),
    MANAGE_ACCESS(
        0xFFF5B800, "M21 2l-2 2m-7.6 7.6a5.5 5.5 0 11-7.8 7.8 5.5 5.5 0 017.8-7.8zm0 0L15.5 7.5m0 0l3 3L22 7l-3-3m-3.5 3.5L19 4",
        needsAllProperties = true,
    ),
}

/** Chip for a group with no permissions. */
const val VIEW_ONLY_COLOR = 0xFF8B8F97
const val VIEW_ONLY_ICON = "M2 12s4-7 10-7 10 7 10 7-4 7-10 7S2 12 2 12zM12 15a3 3 0 100-6 3 3 0 000 6z"

/** A group says what its people can do. [locked] groups (Admins) always have every permission and can't be deleted or renamed. */
data class Group(
    val id: String,
    val name: String,
    val description: String,
    val permissions: Set<Permission>,
    val locked: Boolean = false,
) {
    val needsAllProperties: Boolean get() = locked || permissions.any { it.needsAllProperties }

    /** Whether this group's people can do [p] (a locked group can do everything). */
    fun can(p: Permission): Boolean = locked || p in permissions

    /** Everything the group's people can do, in [Permission] order. */
    val effectivePermissions: List<Permission> get() = Permission.entries.filter(::can)

    /** The strongest permission, which sets the group's icon and colour (null = view only). */
    val topPermission: Permission?
        get() = listOf(Permission.MANAGE_ACCESS, Permission.MANAGE_PROPERTIES, Permission.CONFIGURE, Permission.VALIDATE, Permission.COLLECT)
            .firstOrNull(::can)
}

/** Each person is in one group, for all properties or only [propertyIds]. [lastSeenAt] is epoch millis (null = never). */
data class Person(
    val id: String,
    val email: String,
    val groupId: String,
    val allProperties: Boolean,
    val propertyIds: Set<String> = emptySet(),
    val lastSeenAt: Long? = null,
) {
    val name: String get() = email.substringBefore('@')
}

const val ADMINS_ID = "g-admins"
