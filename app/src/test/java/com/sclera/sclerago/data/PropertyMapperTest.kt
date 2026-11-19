package com.sclera.sclerago.data

import com.sclera.sclerago.data.mapper.hoursMinutes
import com.sclera.sclerago.data.mapper.settingsBody
import com.sclera.sclerago.data.mapper.toBody
import com.sclera.sclerago.data.mapper.toCreateBody
import com.sclera.sclerago.data.mapper.toGroup
import com.sclera.sclerago.data.mapper.toInviteBody
import com.sclera.sclerago.data.mapper.toPerson
import com.sclera.sclerago.data.mapper.toRecord
import com.sclera.sclerago.data.mapper.toUpdateBody
import com.sclera.sclerago.data.remote.adc.AdcJson
import com.sclera.sclerago.data.remote.adc.GroupDto
import com.sclera.sclerago.data.remote.adc.PropertyDto
import com.sclera.sclerago.data.remote.adc.UserDto
import com.sclera.sclerago.domain.model.Address
import com.sclera.sclerago.domain.model.Availability
import com.sclera.sclerago.domain.model.Contact
import com.sclera.sclerago.domain.model.Group
import com.sclera.sclerago.domain.model.OnboardingChannel
import com.sclera.sclerago.domain.model.Permission
import com.sclera.sclerago.domain.model.Person
import com.sclera.sclerago.domain.model.Property
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The ADC API's JSON → the app's properties, groups and people, and the app's models → the request bodies. */
class PropertyMapperTest {
    private val full = """
        {"id":"p-1","organisationId":"85161001","name":"Test tower","type":"data_center",
         "addressLine1":"8 Marine Drive","addressLine2":null,"city":"Mumbai","state":"Maharashtra","country":"India",
         "region":"emea","postalCode":"400020","contactName":"Ada","contactEmail":"ada@example.com","contactPhone":"+91 1",
         "notes":"n","availableFrom":"00:45:00","availableUntil":"23:30:00","accessCode":"123","status":"ready",
         "statusDetail":null,"settings":{"bluetoothOnboarding":true,"wifiOnboarding":false,
         "photoRules":{"nameplate":{"min":0,"max":3}}},"geofenceEnforced":true,
         "createdAt":"2026-11-12T10:08:17.496039Z","updatedAt":"2026-11-13T09:39:15.250067Z"}
    """.trimIndent()

    @Test
    fun property_readsEveryField() {
        val r = AdcJson.decodeFromString(PropertyDto.serializer(), full).toRecord()
        val p = r.property
        assertEquals("p-1", p.id)
        assertEquals("Test tower", p.name)
        assertEquals("Data center", p.type)
        assertFalse(p.gps) // geofencing has no API: it stays on the device
        assertEquals("8 Marine Drive", p.address.line1)
        assertEquals("", p.address.line2)
        assertEquals("Mumbai", p.address.city)
        assertEquals("400020", p.address.postcode)
        assertEquals(Contact("Ada", "ada@example.com", "+91 1"), p.contact)
        assertEquals("n", p.notes)
        assertEquals(Availability("00:45", "23:30", "123"), p.availability)
        // Bluetooth on, Wi-Fi off: only Wi-Fi onboarding is switched off.
        assertEquals(setOf(OnboardingChannel.NETWORK), r.disabled)
    }

    @Test
    fun property_withOnlyTheRequiredFields_usesTheDefaults() {
        val r = AdcJson.decodeFromString(PropertyDto.serializer(), """{"id":"x","name":"X"}""").toRecord()
        assertEquals(Availability(), r.property.availability)
        assertEquals("", r.property.type)
        assertEquals(Address(), r.property.address)
        // The API's switches default to off.
        assertEquals(setOf(OnboardingChannel.BLUETOOTH, OnboardingChannel.NETWORK), r.disabled)
    }

    @Test
    fun property_toBody_sendsClearedTextAsNull() {
        val p = Property(
            "p9", " Depot ", gps = false, type = "Warehouse", address = Address(line1 = "1 Road", city = "Pune"),
            contact = Contact(email = " "), availability = Availability("08:00", "18:00", "42"),
        )
        val body = p.toBody()
        assertEquals("Depot", body.getValue("name").jsonPrimitive.content)
        assertEquals("Warehouse", body.getValue("type").jsonPrimitive.content)
        assertEquals("1 Road", body.getValue("addressLine1").jsonPrimitive.content)
        assertEquals(JsonNull, body.getValue("addressLine2"))
        assertEquals(JsonNull, body.getValue("contactEmail")) // the API refuses an empty email
        assertEquals("08:00", body.getValue("availableFrom").jsonPrimitive.content)
        assertEquals("42", body.getValue("accessCode").jsonPrimitive.content)
    }

    @Test
    fun settingsBody_namesTheSwitch() {
        assertTrue(settingsBody(OnboardingChannel.BLUETOOTH, true).getValue("bluetoothOnboarding").jsonPrimitive.boolean)
        assertFalse(settingsBody(OnboardingChannel.NETWORK, false).getValue("wifiOnboarding").jsonPrimitive.boolean)
    }

    @Test
    fun group_readsPermissions_ignoringUnknownOnes_andLocksBuiltInGroups() {
        val g = GroupDto("g1", "Admins", null, listOf("collect", "validate", "teleport"), isBuiltIn = true).toGroup()
        assertEquals(setOf(Permission.COLLECT, Permission.VALIDATE), g.permissions)
        assertEquals("", g.description)
        assertTrue(g.locked)
    }

    @Test
    fun group_toCreateBody_sendsPermissionKeys() {
        val body = Group("", " Auditors ", "", setOf(Permission.VALIDATE)).toCreateBody()
        assertEquals("Auditors", body.getValue("name").jsonPrimitive.content)
        assertEquals(JsonNull, body.getValue("description"))
        assertEquals(JsonArray(listOf(JsonPrimitive("validate"))), body.getValue("permissions"))
    }

    @Test
    fun person_readsLastSeen_andToleratesABadDate() {
        val seen = UserDto("u1", "a@b.com", groupId = "g1", allProperties = false, propertyIds = listOf("p1"), lastSeenAt = "2026-11-13T09:39:15Z").toPerson()
        assertEquals(setOf("p1"), seen.propertyIds)
        assertEquals(1_794_562_755_000L, seen.lastSeenAt)
        val bad = UserDto("u2", "c@d.com", lastSeenAt = "yesterday", signedIn = false).toPerson()
        assertNull(bad.lastSeenAt)
        assertEquals("", bad.groupId)
        assertFalse(bad.signedIn)
    }

    @Test
    fun person_bodies_dropChosenPropertiesWhenTheyHaveEveryProperty() {
        val p = Person("", " a@b.com ", "g1", allProperties = true, propertyIds = setOf("p1"), inviteName = " Ada ")
        val invite = p.toInviteBody()
        assertEquals("a@b.com", invite.getValue("email").jsonPrimitive.content)
        assertEquals("Ada", invite.getValue("name").jsonPrimitive.content)
        assertTrue(invite.getValue("propertyIds").jsonArray.isEmpty())
        val update = p.copy(allProperties = false).toUpdateBody()
        assertEquals(JsonArray(listOf(JsonPrimitive("p1"))), update.getValue("propertyIds"))
        assertFalse("email" in update)
    }

    @Test
    fun helpers() {
        assertEquals("00:45", hoursMinutes("00:45:00"))
        assertEquals("08:00", hoursMinutes("08:00"))
    }
}
