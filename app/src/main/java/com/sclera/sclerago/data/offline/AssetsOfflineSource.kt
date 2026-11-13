package com.sclera.sclerago.data.offline

import com.sclera.sclerago.data.mapper.DbJson
import com.sclera.sclerago.data.mapper.DbRow
import com.sclera.sclerago.data.mapper.FieldKeys
import com.sclera.sclerago.data.mapper.toAsset
import com.sclera.sclerago.data.mapper.toAssetActivity
import com.sclera.sclerago.data.mapper.toAssetDetails
import com.sclera.sclerago.data.mapper.toMedia
import com.sclera.sclerago.data.sync.AdcDatabase
import com.sclera.sclerago.domain.model.Asset
import com.sclera.sclerago.domain.model.AssetActivity
import com.sclera.sclerago.domain.model.AssetDetails
import com.sclera.sclerago.domain.model.AssetFilter
import com.sclera.sclerago.domain.model.AssetFull
import com.sclera.sclerago.domain.model.AssetSummary
import com.sclera.sclerago.domain.model.Media
import com.sclera.sclerago.domain.model.Status
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/** Spec values shown after the make and model in an asset's one-line detail: database key to unit. */
private val SPEC_UNITS = listOf("rated_power_kw" to "kW", "voltage" to "V", "speed" to "RPM")

/** The app's field keys a scanned device is looked up in. */
private val SCAN_KEYS = listOf("ble", "ip", "label", "note")

private val ASSET_TABLES = arrayOf("assets", "asset_values", "asset_media", "buildings", "floors", "locations", "master_formats")

/**
 * The assets of the offline database: lists with filters and paging, the status counts, one asset in full, and
 * its media and activity. Reads only. `propertyId` stamps the assets returned, as the database holds one property.
 */
class AssetsOfflineSource(
    private val db: AdcDatabase,
    private val io: CoroutineDispatcher,
    private val files: AssetFileStore,
    private val places: PlacesOfflineSource,
    private val formats: MasterFormatsOfflineSource,
    private val validation: ValidationOfflineSource,
) {
    /** Every asset [filter] keeps, newest first. */
    suspend fun list(filter: AssetFilter = AssetFilter(), propertyId: String = ""): List<Asset> =
        withContext(io) { loadPage(filter, null, 0, propertyId) }

    fun listFlow(filter: AssetFilter = AssetFilter(), propertyId: String = ""): Flow<List<Asset>> =
        db.watch(io, *ASSET_TABLES) { loadPage(filter, null, 0, propertyId) }

    /** One page of [list]: [limit] assets from [offset]. */
    suspend fun page(filter: AssetFilter, limit: Int, offset: Int, propertyId: String = ""): List<Asset> =
        withContext(io) { loadPage(filter, limit, offset, propertyId) }

    fun pageFlow(filter: AssetFilter, limit: Int, offset: Int, propertyId: String = ""): Flow<List<Asset>> =
        db.watch(io, *ASSET_TABLES) { loadPage(filter, limit, offset, propertyId) }

    /** How many assets [filter] keeps. */
    suspend fun count(filter: AssetFilter = AssetFilter()): Int = withContext(io) { loadCount(filter) }

    fun countFlow(filter: AssetFilter = AssetFilter()): Flow<Int> = db.watch(io, "assets", "asset_values") { loadCount(filter) }

    /** Counts per status for the status chips: [filter] without its status. */
    suspend fun summary(filter: AssetFilter = AssetFilter()): AssetSummary = withContext(io) { loadSummary(filter) }

    fun summaryFlow(filter: AssetFilter = AssetFilter()): Flow<AssetSummary> =
        db.watch(io, "assets", "asset_values") { loadSummary(filter) }

    /** [assetId] with its field values, activity and latest validation result; null when it is not there. */
    suspend fun asset(assetId: String, propertyId: String = ""): AssetFull? = withContext(io) { loadFull(assetId, propertyId) }

    fun assetFlow(assetId: String, propertyId: String = ""): Flow<AssetFull?> =
        db.watch(io, *ASSET_TABLES, "asset_activity", "validation_results", "validation_issues") { loadFull(assetId, propertyId) }

    /** The details of each of [assetIds], by asset id. */
    suspend fun detailsFor(assetIds: Collection<String>): Map<String, AssetDetails> = withContext(io) { loadDetails(assetIds) }

    /** Scanned device id → the asset made from it. */
    fun deviceLinksFlow(): Flow<Map<String, String>> = db.watch(io, "assets") { loadDeviceLinks() }

    /** Asset id → its BLE tag, IP address, label and notes, one per line, for matching scan results. */
    fun scanTextFlow(): Flow<Map<String, String>> = db.watch(io, "assets", "asset_values") { loadScanText() }

    /** An asset's photos, videos and documents in order. [Media.uri] is the stored object path (`local://` or `s3://`). */
    suspend fun media(assetId: String): List<Media> = withContext(io) { mediaFor(listOf(assetId))[assetId].orEmpty() }

    /** An asset's comments and recorded changes, newest first. */
    suspend fun activity(assetId: String): List<AssetActivity> = withContext(io) { loadActivity(assetId) }

    fun activityFlow(assetId: String): Flow<List<AssetActivity>> = db.watch(io, "asset_activity") { loadActivity(assetId) }

    private fun loadPage(filter: AssetFilter, limit: Int?, offset: Int, propertyId: String): List<Asset> =
        db.whenTable("assets", emptyList()) {
            val where = db.assetWhere(filter)
            val paging = if (limit != null) " LIMIT ${limit.coerceAtLeast(0)} OFFSET ${offset.coerceAtLeast(0)}" else ""
            val rows = db.query("SELECT a.* FROM assets a WHERE ${where.sql} $ASSET_ORDER$paging", where.args)
            toAssets(rows, propertyId)
        }

    private fun loadCount(filter: AssetFilter): Int = db.whenTable("assets", 0) {
        val where = db.assetWhere(filter)
        db.queryOne("SELECT COUNT(*) AS n FROM assets a WHERE ${where.sql}", where.args)?.int("n") ?: 0
    }

    private fun loadSummary(filter: AssetFilter): AssetSummary = db.whenTable("assets", AssetSummary(0, emptyMap(), 0, 0)) {
        val where = db.assetWhere(filter, withStatus = false)
        val rows = db.query(ASSET_SUMMARY.replace("%WHERE%", where.sql), where.args)
        AssetSummary(
            total = rows.sumOf { it.int("n") ?: 0 },
            byStatus = rows.mapNotNull { r -> Status.entries.firstOrNull { it.name == r.string("st") }?.let { it to (r.int("n") ?: 0) } }.toMap(),
            aiSuggestions = rows.sumOf { it.int("ai") ?: 0 },
            validated = rows.sumOf { it.int("ok") ?: 0 },
        )
    }

    private fun loadFull(assetId: String, propertyId: String): AssetFull? = db.whenTable("assets", null) {
        val row = db.queryOne(ASSET_BY_ID.replace("%LIVE%", db.notRemoved("assets", "a")), listOf(assetId))
        row?.let {
            val asset = toAssets(listOf(it), propertyId).first()
            val valuesRow = db.whenTable("asset_values", null) { db.queryOne(ASSET_VALUES_ONE, listOf(assetId)) }
            AssetFull(
                asset = asset,
                details = it.toAssetDetails(valuesRow, formats.byId()[asset.masterFormatId]),
                activity = loadActivity(assetId),
                validation = validation.latestResultNow(assetId),
            )
        }
    }

    private fun loadActivity(assetId: String): List<AssetActivity> = db.whenTable("asset_activity", emptyList()) {
        db.query(ACTIVITY_FOR.replace("%LIVE%", db.notRemoved("asset_activity")), listOf(assetId)).map { it.toAssetActivity() }
    }

    /** [rows] as assets: media, spec values, master formats and place names are fetched for all of them at once. */
    private fun toAssets(rows: List<DbRow>, propertyId: String): List<Asset> {
        if (rows.isEmpty()) return emptyList()
        val ids = rows.mapNotNull { it.string("id") }
        val media = mediaFor(ids)
        val values = valuesFor(ids)
        val formatById = formats.byId()
        val index = places.index()
        return rows.map { row ->
            val id = row.string("id").orEmpty()
            val place = index.location(row.string("location_id"))
            val asset = row.toAsset(propertyId, place, formatById[row.string("master_format_id")], media[id].orEmpty())
            asset.copy(
                detail = detailLine(asset.detail, values[id].orEmpty()),
                where = place?.where ?: index.floorOrBuilding(asset.floorId, asset.buildingId),
            )
        }
    }

    private fun loadDetails(assetIds: Collection<String>): Map<String, AssetDetails> = db.whenTable("assets", emptyMap()) {
        if (assetIds.isEmpty()) return@whenTable emptyMap()
        val rows = db.queryIn(ASSETS_BY_IDS.replace("%LIVE%", db.notRemoved("assets", "a")), assetIds)
        val values = db.whenTable("asset_values", emptyList()) { db.queryIn(ASSET_VALUES_ROWS, assetIds) }.associateBy { it.string("asset_id").orEmpty() }
        val formatById = formats.byId()
        rows.associate { row ->
            val id = row.string("id").orEmpty()
            id to row.toAssetDetails(values[id], formatById[row.string("master_format_id")])
        }
    }

    private fun loadDeviceLinks(): Map<String, String> = db.whenTable("assets", emptyMap()) {
        db.query(DEVICE_LINKS.replace("%LIVE%", db.notRemoved("assets", "a"))).associate { it.string("source_ref").orEmpty() to it.string("id").orEmpty() }
    }

    private fun loadScanText(): Map<String, String> = db.whenTable("asset_values", emptyMap()) {
        if (!db.hasTable("assets")) return@whenTable emptyMap()
        db.query(ALL_ASSET_VALUES.replace("%LIVE%", db.notRemoved("assets", "a"))).mapNotNull { row ->
            val values = DbJson.stringMap(row.string("values"))
            val text = SCAN_KEYS.mapNotNull { values[FieldKeys.toDb(it)] }.filter { it.isNotBlank() }.joinToString("\n")
            text.takeIf { it.isNotEmpty() }?.let { row.string("asset_id").orEmpty() to it }
        }.toMap()
    }

    private fun detailLine(makeModel: String, values: Map<String, String>): String =
        (listOf(makeModel) + SPEC_UNITS.mapNotNull { (key, unit) -> values[key]?.takeIf { it.isNotBlank() }?.let { spec(it, unit) } })
            .filter { it.isNotBlank() }.joinToString(" · ")

    private fun spec(value: String, unit: String) = if (value.any(Char::isLetter)) value else "$value $unit"

    private fun mediaFor(ids: List<String>): Map<String, List<Media>> = db.whenTable("asset_media", emptyMap()) {
        db.queryIn(MEDIA_FOR.replace("%LIVE%", db.notRemoved("asset_media")), ids)
            .groupBy({ it.string("asset_id").orEmpty() }, { it.toMedia(files.uriFor(it.string("object_path").orEmpty())) })
    }

    private fun valuesFor(ids: List<String>): Map<String, Map<String, String>> = db.whenTable("asset_values", emptyMap()) {
        db.queryIn(ASSET_VALUES_FOR, ids).associate { it.string("asset_id").orEmpty() to DbJson.stringMap(it.string("values")) }
    }
}
