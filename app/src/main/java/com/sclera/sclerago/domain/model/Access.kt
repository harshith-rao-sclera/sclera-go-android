package com.sclera.sclerago.domain.model

// People and access: groups, their permissions and the people in them.

/**
 * What a group's people can do ([key] is its name in the API). [needsAllProperties] ones only make sense with access to every
 * property. [color] / [iconPath] (24×24 SVG path) are the permission's chip colour and icon.
 * Their user-facing names and descriptions live in string resources (presentation).
 */
enum class Permission(
    val key: String,
    val color: Long,
    val iconPath: String,
    val needsAllProperties: Boolean = false,
) {
    COLLECT(
        "collect",
        0xFF6FA8E6, "M12 20h9M16.5 3.5a2.1 2.1 0 013 3L7 19l-4 1 1-4z",
    ),

    /** Run validations and accept or dismiss fixes on assets not yet submitted. The server gives it to everyone with [VALIDATE] too. */
    CHECK(
        "check",
        0xFF4DB6C4, "M9 12l2 2 4-4M12 3a9 9 0 100 18 9 9 0 000-18z",
    ),
    VALIDATE(
        "validate",
        0xFF5DBE8C, "M12 3l7 3v5c0 5-3 8-7 10-4-2-7-5-7-10V6l7-3zM9 12l2 2 4-4",
    ),
    CONFIGURE(
        "configure",
        0xFFEE9A4D, "M4 7h10M18 7h2M4 17h2M10 17h10M14 5v4M8 15v4",
    ),
    MANAGE_PROPERTIES(
        "manage_properties",
        0xFFAE9CE6, "M5 21V4a1 1 0 011-1h12a1 1 0 011 1v17M3 21h18M9 7h2M13 7h2M9 11h2M13 11h2M9 15h2M13 15h2",
        needsAllProperties = true,
    ),
    MANAGE_ACCESS(
        "manage_access",
        0xFFF5B800, "M21 2l-2 2m-7.6 7.6a5.5 5.5 0 11-7.8 7.8 5.5 5.5 0 017.8-7.8zm0 0L15.5 7.5m0 0l3 3L22 7l-3-3m-3.5 3.5L19 4",
        needsAllProperties = true,
    ),
}

/** Colour of the "View only" tag of a group with no permissions. */
const val VIEW_ONLY_COLOR = 0xFF8B8F97

/** A group says what its people can do. [locked] groups (Admins) always have every permission and can't be deleted. */
data class Group(
    val id: String,
    val name: String,
    val description: String,
    val permissions: Set<Permission>,
    val locked: Boolean = false,
) {
    /** Whether this group's people can do [p] (a locked group can do everything). */
    fun can(p: Permission): Boolean = locked || p in permissions

    /** Everything the group's people can do, in [Permission] order. */
    val effectivePermissions: List<Permission> get() = Permission.entries.filter(::can)

    /** The strongest permission, which sets the group's icon and colour (null = view only). */
    val topPermission: Permission?
        get() = listOf(Permission.MANAGE_ACCESS, Permission.MANAGE_PROPERTIES, Permission.CONFIGURE, Permission.VALIDATE, Permission.CHECK, Permission.COLLECT)
            .firstOrNull(::can)
}

/**
 * A permission as the server describes it (`GET organisations/{org}/access/permissions`): its [key], the [name] and
 * [description] to show, and whether it only works with access to every property.
 */
data class PermissionInfo(val key: String, val name: String, val description: String, val needsAllProperties: Boolean)

/**
 * Someone the organisation's directory lists, to pick when adding a person; [userId] is set when they are already here.
 * [imageUrl] is their profile photo's link as Sclera's list has it (see [profilePhoto]).
 */
data class DirectoryPerson(val email: String, val name: String?, val userId: String?, val imageUrl: String? = null)

/**
 * A page of the organisation's directory. When it isn't [available] (the server can't ask Sclera), [reason] says why
 * and there is nobody in it; [hasMore] says another page can be asked for.
 */
data class DirectoryPage(val people: List<DirectoryPerson>, val available: Boolean, val reason: String?, val hasMore: Boolean)

/**
 * Each person is in one group ([groupId] empty = no access), for all properties or only [propertyIds].
 * [lastSeenAt] is epoch millis (null = never); [fullName] is the name the server has for them, if any.
 */
data class Person(
    val id: String,
    val email: String,
    val groupId: String,
    val allProperties: Boolean,
    val propertyIds: Set<String> = emptySet(),
    val lastSeenAt: Long? = null,
    /** False for someone invited who has not signed in yet. */
    val signedIn: Boolean = true,
    /** The name picked from the organisation directory when inviting; sent with the invitation, not kept. */
    val inviteName: String? = null,
    val fullName: String? = null,
    /**
     * Their profile photo's link (see [profilePhoto]): as the server has it, or, for someone being invited, as the
     * directory gave it (sent with the invitation, as the web does).
     */
    val imageUrl: String? = null,
) {
    /** Their name, or their email when the server has no name for them (as the web shows it). */
    val name: String get() = fullName?.trim()?.takeIf { it.isNotEmpty() } ?: email

    /** Whether they are in a group; without one they keep their sign-in but can open nothing. */
    val hasAccess: Boolean get() = groupId.isNotBlank()
}

/** The permission the API calls [key], or null for one this app does not know (those are ignored). */
fun permissionOf(key: String): Permission? = Permission.entries.firstOrNull { it.key == key }

/** Someone a property's assets are assigned to, as the server names them to anyone who can open it, with their profile photo's link. */
data class PropertyAssignee(val userId: String, val name: String?, val email: String, val imageUrl: String? = null) {
    /** Their name, else their email. */
    val label: String get() = name?.trim()?.takeIf { it.isNotEmpty() } ?: email
}
