package com.sclera.sclerago.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sclera.sclerago.data.local.PropertyRecord
import com.sclera.sclerago.data.mapper.hoursMinutes
import com.sclera.sclerago.data.mapper.prettyPropertyType
import com.sclera.sclerago.data.mapper.propertyPage
import com.sclera.sclerago.data.mapper.toDomain
import com.sclera.sclerago.data.mapper.toLocalRecord
import com.sclera.sclerago.domain.model.Address
import com.sclera.sclerago.domain.model.Availability
import com.sclera.sclerago.domain.model.Contact
import com.sclera.sclerago.domain.model.Property
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** The ADC properties page → `properties` rows → the app's [Property]. */
@RunWith(AndroidJUnit4::class)
class PropertyMapperTest {
    private val full = """
        {"id":"p-1","organisationId":"85161001","name":" Test tower ","type":"data_center",
         "addressLine1":"8 Marine Drive","addressLine2":null,"city":"Mumbai","state":"Maharashtra","country":"India",
         "region":"emea","postalCode":"400020","contactName":"Ada","contactEmail":"ada@example.com","contactPhone":"+91 1",
         "notes":"n","availableFrom":"00:45:00","availableUntil":"23:30:00","accessCode":"123","status":"ready",
         "statusDetail":null,"settings":{"bluetoothOnboarding":true,"wifiOnboarding":false,
         "photoRules":{"nameplate":{"min":0,"max":3}}},"geofenceEnforced":true,
         "createdAt":"2026-11-12T10:08:17.496039Z","updatedAt":"2026-11-13T09:39:15.250067Z"}
    """.trimIndent()

    @Test
    fun page_readsEveryFieldAndThePaging() {
        val page = propertyPage(JSONObject("""{"data":[$full],"nextCursor":"c-2","hasMore":true}"""))
        assertEquals("c-2", page.nextCursor)
        assertTrue(page.hasMore)
        val r = page.records.single()
        assertEquals("p-1", r.id)
        assertEquals("85161001", r.organisationId)
        assertEquals("Test tower", r.name)
        assertEquals("data_center", r.type)
        assertNull(r.addressLine2)
        assertEquals("400020", r.postalCode)
        assertEquals("ada@example.com", r.contactEmail)
        assertEquals("00:45:00", r.availableFrom)
        assertEquals("123", r.accessCode)
        assertEquals("ready", r.status)
        assertNull(r.statusDetail)
        assertTrue(r.geofenceEnforced)
        assertEquals("2026-11-13T09:39:15.250067Z", r.updatedAt)
        assertTrue(r.isSynced)
        assertEquals(true, JSONObject(r.settings!!).getBoolean("bluetoothOnboarding"))
    }

    @Test
    fun page_lastPageAndOddEntries() {
        val page = propertyPage(JSONObject("""{"data":[{"name":"no id"},{"id":"p-2"},5],"nextCursor":null,"hasMore":false}"""))
        assertNull(page.nextCursor)
        assertFalse(page.hasMore)
        val r = page.records.single()
        assertEquals("p-2", r.id)
        assertEquals("", r.name)
        assertNull(r.settings)
        assertFalse(r.geofenceEnforced)
    }

    @Test
    fun page_withoutData_isEmpty() {
        val page = propertyPage(JSONObject("{}"))
        assertTrue(page.records.isEmpty())
        assertFalse(page.hasMore)
    }

    @Test
    fun record_toDomain() {
        val p = propertyPage(JSONObject("""{"data":[$full]}""")).records.single().toDomain()
        assertEquals("p-1", p.id)
        assertEquals("Data center", p.type)
        assertTrue(p.gps)
        assertEquals("8 Marine Drive", p.address.line1)
        assertEquals("", p.address.line2)
        assertEquals("400020", p.address.postcode)
        assertEquals(Contact("Ada", "ada@example.com", "+91 1"), p.contact)
        assertEquals(Availability("00:45", "23:30", "123"), p.availability)
    }

    @Test
    fun record_withoutTimes_usesTheDefaults() {
        val p = PropertyRecord(id = "x", name = "X").toDomain()
        assertEquals(Availability(), p.availability)
        assertEquals("", p.type)
    }

    @Test
    fun localProperty_isAnUnsyncedRow() {
        val p = Property(
            "p9", "Depot", gps = false, type = "Warehouse", address = Address(line1 = "1 Road", city = "Pune"),
            contact = Contact(email = "a@b.com"), availability = Availability("08:00", "18:00", "42"),
        )
        val r = p.toLocalRecord("org-1")
        assertFalse(r.isSynced)
        assertEquals("org-1", r.organisationId)
        assertEquals("1 Road", r.addressLine1)
        assertNull(r.addressLine2)
        assertNull(r.contactName)
        assertEquals("08:00:00", r.availableFrom)
        assertEquals("42", r.accessCode)
        assertEquals(p.copy(type = "Warehouse"), r.toDomain())
    }

    @Test
    fun helpers() {
        assertEquals("Data center", prettyPropertyType("data_center"))
        assertEquals("Commercial office", prettyPropertyType("Commercial office"))
        assertEquals("00:45", hoursMinutes("00:45:00"))
        assertEquals("08:00", hoursMinutes("08:00"))
    }
}
