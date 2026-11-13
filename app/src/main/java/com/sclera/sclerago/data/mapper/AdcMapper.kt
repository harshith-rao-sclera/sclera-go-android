package com.sclera.sclerago.data.mapper

import com.sclera.sclerago.data.remote.adc.GroupDto
import com.sclera.sclerago.data.remote.adc.MeDto
import com.sclera.sclerago.data.remote.adc.PropertyDto
import com.sclera.sclerago.data.remote.adc.UserDto
import com.sclera.sclerago.domain.model.Address
import com.sclera.sclerago.domain.model.Availability
import com.sclera.sclerago.domain.model.Contact
import com.sclera.sclerago.domain.model.CurrentAccess
import com.sclera.sclerago.domain.model.Group
import com.sclera.sclerago.domain.model.OnboardingChannel
import com.sclera.sclerago.domain.model.Permission
import com.sclera.sclerago.domain.model.Person
import com.sclera.sclerago.domain.model.Property
import com.sclera.sclerago.domain.model.permissionOf
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
        type = type.orEmpty(),
        address = Address(
            line1 = addressLine1.orEmpty(), line2 = addressLine2.orEmpty(), country = country.orEmpty(),
            state = state.orEmpty(), region = region.orEmpty(), city = city.orEmpty(), postcode = postalCode.orEmpty(),
        ),
        contact = Contact(contactName.orEmpty(), contactEmail.orEmpty(), contactPhone.orEmpty()),
        notes = notes.orEmpty(),
        availability = Availability(availableFrom.shortTime(defaults.start), availableUntil.shortTime(defaults.end), accessCode.orEmpty()),
    )
    val disabled = buildSet {
        if (!settings.bluetoothOnboarding) add(OnboardingChannel.BLUETOOTH)
        if (!settings.wifiOnboarding) add(OnboardingChannel.NETWORK)
    }
    return PropertyRecord(property, disabled)
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
    put("postalCode", textOrNull(address.postcode))
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

fun Group.toCreateBody(): JsonObject = buildJsonObject {
    put("name", name.trim())
    put("description", textOrNull(description))
    put("permissions", permissionKeys(permissions))
}

fun Group.toUpdateBody(): JsonObject = toCreateBody()

fun UserDto.toPerson() = Person(
    id = id,
    email = email,
    groupId = groupId.orEmpty(),
    allProperties = allProperties,
    propertyIds = propertyIds.toSet(),
    lastSeenAt = lastSeenAt?.let { runCatching { OffsetDateTime.parse(it).toInstant().toEpochMilli() }.getOrNull() },
)

private fun Person.propertyArray() = JsonArray((if (allProperties) emptySet() else propertyIds).map(::JsonPrimitive))

fun Person.toInviteBody(): JsonObject = buildJsonObject {
    put("email", email.trim())
    put("groupId", groupId)
    put("allProperties", allProperties)
    put("propertyIds", propertyArray())
}

fun Person.toUpdateBody(): JsonObject = buildJsonObject {
    put("groupId", groupId)
    put("allProperties", allProperties)
    put("propertyIds", propertyArray())
}

/** `PATCH /users/{id}` that takes away all of a person's access (the API has no delete). */
fun removeAccessBody(): JsonObject = buildJsonObject { put("groupId", JsonNull) }

fun MeDto.toCurrentAccess() = CurrentAccess(
    groupName = groupName,
    permissions = permissions.mapNotNull(::permissionOf).toSet(),
    allProperties = allProperties,
    canManageProperties = canManageProperties,
)
