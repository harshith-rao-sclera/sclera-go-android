package com.sclera.sclerago.domain.model

/** Where an asset is in its review. Colours and icons: presentation/model/ModelUi.kt. */
enum class Status(val label: String) {
    AI("AI suggestion"),
    VALIDATED("Validated"),
    SUBMITTED("Submitted"),
    DRAFT("Draft"),
    INPUT("Input needed"),
    EXCEPTION("Exception"),
}

/** How an asset was onboarded. */
enum class Source(val label: String, val via: String) {
    CAMERA("Onboarded with camera", "via Camera"),
    BT("Onboarded by Bluetooth scan", "via Bluetooth"),
    WIFI("Onboarded by Wi-Fi network scan", "via Wi-Fi scan"),
    AP("Onboarded by access point scan", "via Access point scan"),
    FILE("Imported from file", "via File import"),
    MANUAL("Added manually", "Added manually"),
}

enum class AssetType(val label: String) {
    MOTOR("Motor"), PUMP("Pump"), CHILLER("Chiller"),
    FAN("Fan / AHU"), CONTROLLER("Controller"), CCTV("CCTV camera"),
    PANEL("Distribution board"), AP("Access point"), COMPUTER("Computer"),
    PHONE("Phone"), SPEAKER("Speakerphone"), SENSOR("Sensor"),
    GENERIC("Generic"),
}

fun typeOf(name: String?): AssetType {
    val n = (name ?: "").lowercase()
    return when {
        Regex("motor").containsMatchIn(n) -> AssetType.MOTOR
        Regex("pump").containsMatchIn(n) -> AssetType.PUMP
        Regex("chiller").containsMatchIn(n) -> AssetType.CHILLER
        Regex("fan|ahu|compressor").containsMatchIn(n) -> AssetType.FAN
        Regex("panel|board|db-").containsMatchIn(n) -> AssetType.PANEL
        Regex("camera").containsMatchIn(n) -> AssetType.CCTV
        Regex("speaker").containsMatchIn(n) -> AssetType.SPEAKER
        Regex("phone").containsMatchIn(n) -> AssetType.PHONE
        Regex("pc|computer|laptop").containsMatchIn(n) -> AssetType.COMPUTER
        Regex("controller|gateway").containsMatchIn(n) -> AssetType.CONTROLLER
        Regex("access point|router").containsMatchIn(n) -> AssetType.AP
        Regex("sensor").containsMatchIn(n) -> AssetType.SENSOR
        else -> AssetType.GENERIC
    }
}

data class Asset(
    val id: Int,
    val name: String,
    val detail: String,
    val loc: String?,
    val where: String,
    val src: Source,
    val st: Status,
    val type: AssetType,
    val prop: String = "",
    /** Photos and videos attached to this asset (from the camera or the gallery). */
    val media: List<Media> = emptyList(),
    /** When the asset was last added or edited (epoch millis); null for sample assets nobody has touched. */
    val updatedAt: Long? = null,
)

/**
 * One device found by a Bluetooth, Wi-Fi network or access point scan, as a scan page row.
 * Built from the real scan results in scan/ScanRows.kt; [lvl] is 1–4 signal bars.
 * [type] is the asset type the device would become (from what the scan identified); it picks the
 * row's icon, the same icon the Assets list shows for that type.
 */
data class Device(
    val id: String,
    val name: String,
    val sub: String,
    val lvl: Int,
    val known: Boolean,
    val type: AssetType = AssetType.GENERIC,
)

/** Extra fields shown on the asset detail screen, keyed by asset id. */
data class AssetDetails(
    val code: String,
    val fields: Map<String, String>,
    val lowConfidence: String? = null,
)

val SYSTEM_TYPES = listOf("Generic", "Mechanical", "Electrical", "HVAC", "Fire safety", "IT & network", "Security", "Audio-visual")
val ASSET_TYPES = listOf("Generic") + AssetType.entries.filter { it != AssetType.GENERIC }.map { it.label }
val SUB_TYPES = listOf(
    "Generic", "Induction", "Centrifugal", "Axial", "Plug fan", "Air-cooled screw", "TP&N final distribution",
    "BACnet/IP controller", "Outdoor bullet", "Wi-Fi 6 indoor AP", "Desktop", "VoIP desk phone", "Bluetooth speakerphone",
)
val JACS_CODES = listOf("" to "Select JACS code", "[JACS code 1]" to "[JACS code 1]", "[JACS code 2]" to "[JACS code 2]")
