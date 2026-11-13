package com.sclera.sclerago.data.mapper

import com.sclera.sclerago.data.local.PropertyRecord
import com.sclera.sclerago.domain.model.Address
import com.sclera.sclerago.domain.model.Availability
import com.sclera.sclerago.domain.model.Contact
import com.sclera.sclerago.domain.model.Property
import org.json.JSONObject

/** One page of `GET /api/v1/organisations/{orgId}/properties`. */
data class PropertyPage(val records: List<PropertyRecord>, val nextCursor: String?, val hasMore: Boolean)

/** [json] as a page of properties; entries without an id are skipped. */
fun propertyPage(json: JSONObject): PropertyPage {
    val data = json.optJSONArray("data")
    val records = (0 until (data?.length() ?: 0)).mapNotNull { data?.optJSONObject(it)?.toPropertyRecord() }
    return PropertyPage(records, json.text("nextCursor"), json.optBoolean("hasMore", false))
}

/** A property object from the API, as a synced row; null when it has no id. */
fun JSONObject.toPropertyRecord(): PropertyRecord? {
    val id = text("id") ?: return null
    return PropertyRecord(
        id = id,
        organisationId = text("organisationId"),
        name = text("name").orEmpty(),
        type = text("type"),
        addressLine1 = text("addressLine1"),
        addressLine2 = text("addressLine2"),
        city = text("city"),
        state = text("state"),
        country = text("country"),
        region = text("region"),
        postalCode = text("postalCode"),
        contactName = text("contactName"),
        contactEmail = text("contactEmail"),
        contactPhone = text("contactPhone"),
        notes = text("notes"),
        availableFrom = text("availableFrom"),
        availableUntil = text("availableUntil"),
        accessCode = text("accessCode"),
        status = text("status"),
        statusDetail = text("statusDetail"),
        settings = optJSONObject("settings")?.toString(),
        geofenceEnforced = optBoolean("geofenceEnforced", false),
        createdAt = text("createdAt"),
        updatedAt = text("updatedAt"),
        isSynced = true,
    )
}

/** The property as the app shows it. */
fun PropertyRecord.toDomain(): Property {
    val defaults = Availability()
    return Property(
        id = id,
        name = name,
        gps = geofenceEnforced,
        type = type?.let(::prettyPropertyType).orEmpty(),
        address = Address(
            line1 = addressLine1.orEmpty(), line2 = addressLine2.orEmpty(), country = country.orEmpty(),
            state = state.orEmpty(), region = region.orEmpty(), city = city.orEmpty(), postcode = postalCode.orEmpty(),
        ),
        contact = Contact(contactName.orEmpty(), contactEmail.orEmpty(), contactPhone.orEmpty()),
        notes = notes.orEmpty(),
        availability = Availability(
            start = availableFrom?.let(::hoursMinutes) ?: defaults.start,
            end = availableUntil?.let(::hoursMinutes) ?: defaults.end,
            accessCode = accessCode.orEmpty(),
        ),
    )
}

/** A property added on this device: a row the server doesn't have yet. */
fun Property.toLocalRecord(organisationId: String?) = PropertyRecord(
    id = id,
    organisationId = organisationId,
    name = name,
    type = type.ifBlank { null },
    addressLine1 = address.line1.ifBlank { null },
    addressLine2 = address.line2.ifBlank { null },
    city = address.city.ifBlank { null },
    state = address.state.ifBlank { null },
    country = address.country.ifBlank { null },
    region = address.region.ifBlank { null },
    postalCode = address.postcode.ifBlank { null },
    contactName = contact.name.ifBlank { null },
    contactEmail = contact.email.ifBlank { null },
    contactPhone = contact.phone.ifBlank { null },
    notes = notes.ifBlank { null },
    availableFrom = "${availability.start}:00",
    availableUntil = "${availability.end}:00",
    accessCode = availability.accessCode.ifBlank { null },
    geofenceEnforced = gps,
    isSynced = false,
)

/** "data_center" → "Data center"; types that are already words ("Commercial office") stay as they are. */
internal fun prettyPropertyType(raw: String): String =
    if ('_' in raw) raw.trim().replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() } else raw.trim()

/** "00:45:00" → "00:45". */
internal fun hoursMinutes(time: String): String = time.trim().split(':').take(2).joinToString(":")

/** The trimmed text at [key]; null when it is missing, JSON null or blank. */
private fun JSONObject.text(key: String): String? = if (isNull(key)) null else firstText(key)
