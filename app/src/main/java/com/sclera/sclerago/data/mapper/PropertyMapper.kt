package com.sclera.sclerago.data.mapper

import com.sclera.sclerago.domain.model.Address
import com.sclera.sclerago.domain.model.Availability
import com.sclera.sclerago.domain.model.Contact
import com.sclera.sclerago.domain.model.Property
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull

/**
 * A `properties` row as the app's [Property]. The row mirrors the API's property: address, contact and
 * availability columns plus a `settings` JSON text whose `gps` flag says whether geofencing is on.
 */
fun DbRow.toProperty(): Property {
    val defaults = Availability()
    return Property(
        id = string("id").orEmpty(),
        name = string("name").orEmpty(),
        gps = (DbJson.obj(string("settings"))["gps"] as? JsonPrimitive)?.booleanOrNull ?: false,
        type = string("type").orEmpty(),
        address = Address(
            line1 = string("address_line1").orEmpty(),
            line2 = string("address_line2").orEmpty(),
            country = string("country").orEmpty(),
            state = string("state").orEmpty(),
            region = string("region").orEmpty(),
            city = string("city").orEmpty(),
            postcode = string("postcode").orEmpty(),
        ),
        contact = Contact(string("contact_name").orEmpty(), string("contact_email").orEmpty(), string("contact_phone").orEmpty()),
        notes = string("notes").orEmpty(),
        availability = Availability(
            start = string("available_from")?.takeIf { it.isNotBlank() } ?: defaults.start,
            end = string("available_until")?.takeIf { it.isNotBlank() } ?: defaults.end,
            accessCode = string("access_code").orEmpty(),
        ),
    )
}
