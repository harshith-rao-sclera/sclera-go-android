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
    val id: String,
    val name: String,
    val detail: String,
    val loc: String?,
    val where: String,
    val src: Source,
    val st: Status,
    val type: AssetType,
    val propertyId: String,
    /** Photos and videos attached to this asset (from the camera or the gallery). */
    val media: List<Media> = emptyList(),
    /** When the asset was last added or edited (epoch millis); null for sample assets nobody has touched. */
    val updatedAt: Long? = null,
    val buildingId: String? = null,
    val floorId: String? = null,
    val locationId: String? = null,
    val masterFormatId: String? = null,
    val needsReview: Boolean = false,
    val aiSuggestion: Boolean = false,
    val validated: Boolean = false,
    /** Server row version, bumped on each synced edit. */
    val version: Int = 1,
    /** Sent back to be collected again (by a reviewer), and still drafted: the web's "Recollect" badge and filter. */
    val recollect: Boolean = false,
    /** Someone asked an admin to reopen it (it is submitted); null when nobody has. */
    val changeRequest: ChangeRequest? = null,
    /** The user it is assigned to (a user id; whoever added it, unless an admin chose someone else); null when nobody. */
    val assignedTo: String? = null,
    /** Its manufacturer and model ("Daikin · VRV IV"); empty when neither is filled in. [detail] adds its specs to these. */
    val makeModel: String = "",
    /**
     * How its validation stands, all its runs together as the server combines them: a step failed, or one needs a
     * look (the web card's validation icon). Null when every step passed or it was never validated.
     */
    val validationMark: ValidationMark? = null,
    /** How many of its steps failed or need a look. */
    val validationProblems: Int = 0,
    /** The kinds of tag the synced data says it carries (the assets table's is_qr_tagged, is_barcode_tagged, is_nfc_tagged). */
    val tagged: Set<TagKind> = emptySet(),
)

/** A validation result worth showing on an asset's row: the web's "Failed validation" and "Needs a look". */
enum class ValidationMark { FAILED, NEEDS_LOOK }

/** A request to reopen a submitted asset: who asked ([requestedBy], a user id), when, and what needs changing. */
data class ChangeRequest(val requestedBy: String?, val requestedAt: Long?, val note: String?)

/**
 * Whether a status chip or filter for [status] takes this asset. "AI suggestion" goes by the AI flag (AI could fill
 * some of its empty fields), whatever the asset's own status, the same as the server's `aiFillable` count; every
 * other status is the asset's own.
 */
fun Asset.isIn(status: Status): Boolean = if (status == Status.AI) aiSuggestion || st == Status.AI else st == status

/**
 * An asset to add. [asset] carries its media and, in [Asset.locationId], the id of the place it goes (a building,
 * floor or location); [values] are the details form answers to keep with it. [copyOf]: it is a copy of that asset (Clone), so it takes the original's own fields by name.
 */
data class NewAsset(val asset: Asset, val values: Map<String, String> = emptyMap(), val copyOf: String? = null)

/**
 * One device found by a Bluetooth, Wi-Fi network or access point scan, as a scan page row.
 * Built from the real scan results in scan/ScanRows.kt; [lvl] is 1–4 signal bars.
 * [type] is the asset type the device would become (from what the scan identified); it picks the
 * row's icon, the same icon the Assets list shows for that type. [facts] is what the scan found out about what it
 * is (no identifiers): an asset made from it starts with them, and the AI is asked about them.
 */
data class Device(
    val id: String,
    val name: String,
    val sub: String,
    val lvl: Int,
    val known: Boolean,
    val type: AssetType = AssetType.GENERIC,
    val facts: ScanFacts? = null,
)

/** Extra fields shown on the asset detail screen, keyed by asset id. */
data class AssetDetails(
    val code: String,
    val fields: Map<String, String>,
    val lowConfidence: String? = null,
    /** Per field key: how sure a reader was, 0..1. */
    val confidence: Map<String, Double> = emptyMap(),
    /** Per field key: the web page a looked-up answer came from. */
    val sources: Map<String, String> = emptyMap(),
)
