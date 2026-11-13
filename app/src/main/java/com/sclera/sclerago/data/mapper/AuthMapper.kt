package com.sclera.sclerago.data.mapper

import android.util.Base64
import com.sclera.sclerago.data.local.AuthSession
import com.sclera.sclerago.domain.model.UserSession
import org.json.JSONObject

/** Persisted session → what the app shows. */
fun AuthSession.toDomain() = UserSession(
    email = email, displayName = displayName, role = role, organisation = organisation, avatarUrl = avatarUrl,
    organisationId = organisationId,
)

internal fun JSONObject.firstText(vararg keys: String): String? =
    keys.asSequence().map { optString(it).trim() }.firstOrNull { it.isNotEmpty() && it != "null" }

/** Organisation id from the ADC `/api/v1/me` response (e.g. "85161001"). */
internal fun organisationIdFromMe(me: JSONObject): String? = me.firstText("organisationId")

/** Organisation name from the userInfo `data` object (e.g. "Optima"). */
internal fun organisationFromUserInfo(data: JSONObject): String? =
    data.firstText("organisation_title", "company_name", "provider_name")

/** Profile photo from the userInfo `data` object; only absolute https URLs are used (cleartext is blocked). */
internal fun avatarFromUserInfo(data: JSONObject): String? =
    data.firstText("image_url")?.takeIf { it.startsWith("https://", ignoreCase = true) }

/** "admin" / "SUPER_ADMIN" / "field-engineer" → "Admin" / "Super admin" / "Field engineer". */
internal fun prettyRole(raw: String): String =
    raw.trim().replace('_', ' ').replace('-', ' ').lowercase().replaceFirstChar { it.uppercase() }

/** Role from the userInfo `data` object (the web app keeps it as `role`). */
internal fun roleFromUserInfo(data: JSONObject): String? =
    sequenceOf("role", "roleName", "role_name", "userRole", "user_role")
        .map { data.optString(it).trim() }.firstOrNull { it.isNotEmpty() && it != "null" }?.let(::prettyRole)

/** Full name from the userInfo `data` object, if it has one. */
internal fun nameFromUserInfo(data: JSONObject): String? {
    data.optString("name").trim().takeIf { it.isNotEmpty() && it != "null" }?.let { return it }
    val first = sequenceOf("first_name", "firstName", "given_name").map { data.optString(it).trim() }.firstOrNull { it.isNotEmpty() && it != "null" }
    val last = sequenceOf("last_name", "lastName", "family_name").map { data.optString(it).trim() }.firstOrNull { it.isNotEmpty() && it != "null" }
    return listOfNotNull(first, last).joinToString(" ").ifEmpty { null }
}

internal fun jwtPayload(token: String?): JSONObject? = runCatching {
    val part = token!!.split('.')[1]
    JSONObject(String(Base64.decode(part, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)))
}.getOrNull()

/** Roles every Keycloak user has; they say nothing about what the user is. */
private val BUILT_IN_ROLES = setOf(
    "offline_access", "uma_authorization", "manage-account", "manage-account-links", "view-profile", "delete-account",
)

/** Client id of the Sclera app in Keycloak (`client_id=sclera` in the login URL); its roles win. */
private const val KEYCLOAK_CLIENT = "sclera"

/**
 * The user's role from the token, looked up where Keycloak and the Sclera backend may put it, in
 * this order: `role` / `userRole` / `user_role`, `roles`, the Sclera client's roles
 * (`resource_access.sclera.roles`), other clients' roles, realm roles (`realm_access.roles`), then
 * group membership (`groups`, e.g. "/Admins"). Built-in Keycloak roles are skipped.
 */
internal fun roleFromClaims(claims: JSONObject): String? {
    fun JSONObject.strings(key: String): List<String> {
        optJSONArray(key)?.let { a -> return (0 until a.length()).map { a.optString(it) } }
        return optString(key).takeIf { it.isNotBlank() }?.let { listOf(it) } ?: emptyList()
    }
    val clients = claims.optJSONObject("resource_access")
    val candidates = buildList {
        listOf("role", "userRole", "user_role", "roles").forEach { addAll(claims.strings(it)) }
        clients?.optJSONObject(KEYCLOAK_CLIENT)?.let { addAll(it.strings("roles")) }
        clients?.keys()?.asSequence()?.filter { it != KEYCLOAK_CLIENT }?.forEach { c -> clients.optJSONObject(c)?.let { addAll(it.strings("roles")) } }
        claims.optJSONObject("realm_access")?.let { addAll(it.strings("roles")) }
        addAll(claims.strings("groups").map { it.substringAfterLast('/') })
    }
    return candidates.map { it.trim() }
        .firstOrNull { it.isNotEmpty() && it !in BUILT_IN_ROLES && !it.startsWith("default-roles") }
        ?.let(::prettyRole)
}

/** Debug aid: the claim names in [token] and any role-like claims, for logcat (never the token). */
internal fun describeClaims(token: String?): String {
    val p = jwtPayload(token) ?: return "token is not a readable JWT"
    val roleish = listOf("role", "userRole", "user_role", "roles", "realm_access", "resource_access", "groups")
        .filter { p.has(it) }.joinToString { "$it=${p.opt(it)}" }
    return "claims=${p.keys().asSequence().toList()} role-like: ${roleish.ifEmpty { "none" }}"
}

/** Value of cookie [name] in a `Cookie` header string, or null. */
internal fun cookieValue(cookies: String, name: String): String? =
    cookies.split(';').map { it.trim() }.firstOrNull { it.startsWith("$name=") }?.substringAfter('=')

/**
 * The signed-in user's email: from the `/session` data, else the token's `email`,
 * `preferred_username` (Keycloak) or `upn` claim — whichever first looks like an email.
 */
internal fun signedInEmail(sessionData: JSONObject, token: String?): String? {
    val claims = jwtPayload(token)
    return sequenceOf(
        sessionData.optString("email"),
        sessionData.optString("emailId"),
        claims?.optString("email"),
        claims?.optString("preferred_username"),
        claims?.optString("upn"),
    ).firstOrNull { !it.isNullOrBlank() && '@' in it }
}

/** Token expiry in epoch millis from `expiresAt` (seconds or millis) or the JWT `exp` claim; 0 if unknown. */
internal fun tokenExpiry(data: JSONObject, token: String): Long {
    val raw = data.optLong("expiresAt", 0L).takeIf { it > 0 } ?: jwtPayload(token)?.optLong("exp", 0L) ?: 0L
    return if (raw in 1 until 100_000_000_000L) raw * 1000 else raw
}
