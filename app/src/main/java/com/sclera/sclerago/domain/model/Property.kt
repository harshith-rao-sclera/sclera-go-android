package com.sclera.sclerago.domain.model

data class Address(
    val line1: String = "",
    val line2: String = "",
    val country: String = "",
    val state: String = "",
    val region: String = "",
    val city: String = "",
    val postcode: String = "",
)

data class Contact(val name: String = "", val email: String = "", val phone: String = "")

/** When the site can be visited ("HH:mm") and the code to get in. */
data class Availability(val start: String = "00:01", val end: String = "23:59", val accessCode: String = "")

data class Property(
    val id: String,
    val name: String,
    val gps: Boolean,
    val type: String = "Office",
    val address: Address = Address(),
    val contact: Contact = Contact(),
    val notes: String = "",
    val availability: Availability = Availability(),
)

val PROPERTY_TYPES = listOf("Office", "Warehouse", "Hospital", "Retail", "Residential", "Industrial", "Campus", "Other")

/** Countries offered in Add New Property, with their states (none for Singapore). */
val PROPERTY_COUNTRIES: Map<String, List<String>> = linkedMapOf(
    "Australia" to listOf("New South Wales", "Queensland", "Victoria"),
    "Germany" to listOf("Bavaria", "Berlin", "Hesse"),
    "India" to listOf("Delhi", "Karnataka", "Maharashtra", "Tamil Nadu"),
    "Singapore" to emptyList(),
    "United Arab Emirates" to listOf("Abu Dhabi", "Dubai", "Sharjah"),
    "United Kingdom" to listOf("England", "Northern Ireland", "Scotland", "Wales"),
    "United States" to listOf("California", "New York", "Texas", "Washington"),
)

val PROPERTY_REGIONS = listOf("North", "South", "East", "West", "Central")

/** Ways of adding assets a property can switch off in Property Settings (camera, files and manual always stay). */
enum class OnboardingChannel { BLUETOOTH, NETWORK }
