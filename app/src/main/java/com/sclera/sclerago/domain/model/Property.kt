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
    /** Its data is downloaded on this device for working offline (several properties can be). */
    val downloaded: Boolean = false,
    /**
     * The fences round it, as the server lists them with the property and kept on the device so the geofence check
     * ([gps]) works offline; empty when it has none or the server didn't send them.
     */
    val fences: List<Fence> = emptyList(),
)

/** A choice in the Add property form: what is shown ([label]) and what is sent to the server ([value]). */
data class PropertyChoice(val label: String, val value: String)

/** The property types the web's Add New Property offers, in its order (Data Center is sent as "data_center"). */
val PROPERTY_TYPES: List<PropertyChoice> = listOf(
    PropertyChoice("Data Center", "data_center"),
) + listOf(
    "Consumer Goods", "Research and Development", "Retail", "Healthcare", "Government", "Education", "Hospitality",
    "Finance", "Energy", "Pharmaceutical", "Automotive", "Food and Beverage", "Construction and Infrastructure",
    "Telecommunications", "Agriculture", "Logistics", "Real Estate", "Defense", "Utilities",
    "Mining and Natural Resources", "Chemical Industry", "Aerospace", "Technology",
).map { PropertyChoice(it, it) }

/** The regions the web offers. */
val PROPERTY_REGIONS: List<PropertyChoice> = listOf(
    PropertyChoice("Americas", "americas"),
    PropertyChoice("EMEA", "emea"),
    PropertyChoice("APAC", "apac"),
)

/** How a type or region value reads: its label from [choices], or the value with a capital first letter (as the web shows it). */
fun labelOf(value: String, choices: List<PropertyChoice>): String =
    choices.firstOrNull { it.value == value }?.label ?: value.trim().replaceFirstChar { it.uppercaseChar() }

private val AMERICAS = (
    "AG AI AR AW BB BL BM BO BQ BR BS BZ CA CL CO CR CU CW DM DO EC FK GD GF GL GP GS GT GY HN HT JM " +
        "KN KY LC MF MQ MS MX NI PA PE PM PR PY SR SV SX TC TT UM US UY VC VE VG VI"
    ).split(' ').toSet()
private val APAC = (
    "AS AU BD BN BT CC CK CN CX FJ FM GU HK HM ID IN IO JP KH KI KP KR LA LK MH MM MN MO MP MV MY NC " +
        "NF NP NR NU NZ PF PG PH PK PN PW SB SG TH TK TL TO TV TW VN VU WF WS"
    ).split(' ').toSet()

/**
 * The region value ("americas", "emea" or "apac") of a country by its ISO [code], as the web works it out; null when
 * the code is unknown (or Antarctica).
 */
fun regionOfCountry(code: String?): String? {
    val c = code?.trim()?.uppercase()?.takeIf { it.length == 2 && it.all(Char::isLetter) && it != "AQ" } ?: return null
    return when (c) {
        in AMERICAS -> "americas"
        in APAC -> "apac"
        else -> "emea" // Europe, the Middle East, Africa and Central Asia
    }
}

/** Ways of adding assets a property can switch off in Property Settings (camera, files and manual always stay). */
enum class OnboardingChannel { BLUETOOTH, NETWORK }
