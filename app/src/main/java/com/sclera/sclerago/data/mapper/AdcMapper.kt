package com.sclera.sclerago.data.mapper

import com.sclera.sclerago.data.remote.adc.AssignResultDto
import com.sclera.sclerago.data.remote.adc.DirectoryPersonDto
import com.sclera.sclerago.data.remote.adc.GroupDto
import com.sclera.sclerago.data.remote.adc.MeDto
import com.sclera.sclerago.data.remote.adc.PermissionDto
import com.sclera.sclerago.data.remote.adc.PropertyDto
import com.sclera.sclerago.data.remote.adc.SettingsDto
import com.sclera.sclerago.data.remote.adc.UserDto
import com.sclera.sclerago.domain.model.DirectoryPerson
import com.sclera.sclerago.domain.model.PermissionInfo
import com.sclera.sclerago.domain.model.Address
import com.sclera.sclerago.domain.model.AssignResult
import com.sclera.sclerago.domain.model.Availability
import com.sclera.sclerago.domain.model.Contact
import com.sclera.sclerago.domain.model.CurrentAccess
import com.sclera.sclerago.domain.model.Group
import com.sclera.sclerago.domain.model.OnboardingChannel
import com.sclera.sclerago.domain.model.Permission
import com.sclera.sclerago.domain.model.Person
import com.sclera.sclerago.domain.model.Property
import com.sclera.sclerago.domain.model.permissionOf
import com.sclera.sclerago.domain.model.profilePhoto
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.OffsetDateTime

// The ADC API's JSON ↔ the app's models. Text the user cleared is sent as null (the API refuses an empty email).

/** A property with the ways of adding assets its settings switch off (the API's switches default to off). */
data class PropertyRecord(val property: Property, val disabled: Set<OnboardingChannel>)

private fun String?.shortTime(default: String): String = this?.takeIf { it.length >= 5 }?.take(5) ?: default

fun PropertyDto.toRecord(): PropertyRecord {
    val defaults = Availability()
    val property = Property(
        id = id,
        name = name,
        gps = false, // geofencing has no API; it stays on the device
        type = type?.trim().orEmpty(),
        address = Address(
            line1 = addressLine1.orEmpty(), line2 = addressLine2.orEmpty(), country = country.orEmpty(),
            state = state.orEmpty(), region = region.orEmpty(), city = city.orEmpty(), postcode = postalCode.orEmpty(),
        ),
        contact = Contact(contactName.orEmpty(), contactEmail.orEmpty(), contactPhone.orEmpty()),
        notes = notes.orEmpty(),
        availability = Availability(availableFrom.shortTime(defaults.start), availableUntil.shortTime(defaults.end), accessCode.orEmpty()),
    )
    return PropertyRecord(property, settings.channelsOff())
}

/** The ways of adding assets these settings switch off (Bluetooth and Wi-Fi onboarding). */
fun SettingsDto.channelsOff(): Set<OnboardingChannel> = buildSet {
    if (!bluetoothOnboarding) add(OnboardingChannel.BLUETOOTH)
    if (!wifiOnboarding) add(OnboardingChannel.NETWORK)
}

private fun textOrNull(value: String?): JsonElement = value?.trim()?.takeIf { it.isNotEmpty() }?.let(::JsonPrimitive) ?: JsonNull

/** The body of `POST /organisations/{org}/properties` and `PATCH /properties/{id}` for [property]. */
fun Property.toBody(): JsonObject = buildJsonObject {
    put("name", name.trim())
    put("type", textOrNull(type))
    put("addressLine1", textOrNull(address.line1))
    put("addressLine2", textOrNull(address.line2))
    put("city", textOrNull(address.city))
    put("state", textOrNull(address.state))
    put("country", textOrNull(address.country))
    put("region", textOrNull(address.region))
    put("contactName", textOrNull(contact.name))
    put("contactEmail", textOrNull(contact.email))
    put("contactPhone", textOrNull(contact.phone))
    put("notes", textOrNull(notes))
    put("availableFrom", textOrNull(availability.start))
    put("availableUntil", textOrNull(availability.end))
    put("accessCode", textOrNull(availability.accessCode))
}

/** The body of `PATCH /properties/{id}/settings` for one ways-of-adding-assets switch. */
fun settingsBody(channel: OnboardingChannel, enabled: Boolean): JsonObject = buildJsonObject {
    put(if (channel == OnboardingChannel.BLUETOOTH) "bluetoothOnboarding" else "wifiOnboarding", enabled)
}

// ---- Access

fun GroupDto.toGroup() = Group(
    id = id,
    name = name,
    description = description.orEmpty(),
    permissions = permissions.mapNotNull(::permissionOf).toSet(), // a key this app doesn't know is ignored
    locked = isBuiltIn,
)

private fun permissionKeys(permissions: Set<Permission>) = JsonArray(permissions.map { JsonPrimitive(it.key) })

/** A group's name, description and permissions; a locked group (Admins) always has every permission, so none are sent (as the web does). */
fun Group.toCreateBody(): JsonObject = buildJsonObject {
    put("name", name.trim())
    put("description", textOrNull(description))
    if (!locked) put("permissions", permissionKeys(permissions))
}

fun Group.toUpdateBody(): JsonObject = toCreateBody()

fun UserDto.toPerson() = Person(
    id = id,
    email = email,
    groupId = groupId.orEmpty(),
    allProperties = allProperties,
    propertyIds = propertyIds.toSet(),
    lastSeenAt = lastSeenAt?.let { runCatching { OffsetDateTime.parse(it).toInstant().toEpochMilli() }.getOrNull() },
    signedIn = signedIn,
    fullName = name,
    imageUrl = imageUrl,
)

/** `POST /assets/bulk/assign`: the assets and who gets them (no email takes them off whoever has them), as the web sends it. */
fun assignBody(assetIds: Collection<String>, email: String?): JsonObject = buildJsonObject {
    put("ids", JsonArray(assetIds.map(::JsonPrimitive)))
    put("email", email?.trim()?.takeIf { it.isNotEmpty() }?.let(::JsonPrimitive) ?: JsonNull)
}

/** The body that takes an asset for [userId] (`PATCH /properties/{p}/assets/{id}`), as the web's takeAssets. */
fun takeBody(userId: String): JsonObject = buildJsonObject { put("assignedTo", JsonPrimitive(userId)) }

fun AssignResultDto.toAssignResult() = AssignResult(
    assigned.coerceAtLeast(0), email?.takeIf { it.isNotBlank() }, name?.takeIf { it.isNotBlank() }, added, givenAccess,
    unchanged = unchanged.coerceAtLeast(0), notFound = notFound.coerceAtLeast(0),
)

fun PermissionDto.toPermissionInfo() = PermissionInfo(key, name, description, needsAllProperties)

fun DirectoryPersonDto.toDirectoryPerson() = DirectoryPerson(email, name, userId, imageUrl)

private fun Person.propertyArray() = JsonArray((if (allProperties) emptySet() else propertyIds).map(::JsonPrimitive))

fun Person.toInviteBody(): JsonObject = buildJsonObject {
    put("email", email.trim())
    inviteName?.trim()?.takeIf { it.isNotEmpty() }?.let { put("name", it) }
    // Their photo as the directory gave it, when they were picked from it (the server keeps only Sclera's https links).
    profilePhoto(imageUrl)?.let { put("imageUrl", it) }
    put("groupId", groupId)
    put("allProperties", allProperties)
    put("propertyIds", propertyArray())
}

/** `PATCH /users/{id}`: their group (none takes away all access, as the web's "No access") and properties. */
fun Person.toUpdateBody(): JsonObject = buildJsonObject {
    put("groupId", groupId.takeIf { it.isNotBlank() }?.let(::JsonPrimitive) ?: JsonNull)
    put("allProperties", allProperties)
    put("propertyIds", propertyArray())
}

fun MeDto.toCurrentAccess(signedInEmail: String) = CurrentAccess(
    userId = userId,
    email = email.ifBlank { signedInEmail },
    name = name,
    role = role,
    organisationId = organisationId?.takeIf { it.isNotBlank() },
    groupId = groupId,
    groupName = groupName,
    permissions = permissions.mapNotNull(::permissionOf).toSet(),
    allProperties = allProperties,
    canManageProperties = canManageProperties,
    isAdmin = isAdmin,
    imageUrl = imageUrl,
)
