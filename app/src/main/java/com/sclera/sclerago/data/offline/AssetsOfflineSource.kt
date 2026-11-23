package com.sclera.sclerago.data.offline

import com.sclera.sclerago.data.mapper.DbJson
import com.sclera.sclerago.data.mapper.DbRow
import com.sclera.sclerago.data.mapper.toAsset
import com.sclera.sclerago.data.mapper.toAssetActivity
import com.sclera.sclerago.data.mapper.toAssetDetails
import com.sclera.sclerago.data.mapper.searchText
import com.sclera.sclerago.data.mapper.toMedia
import com.sclera.sclerago.data.sync.AdcDatabase
import com.sclera.sclerago.domain.model.Asset
import com.sclera.sclerago.domain.model.AssetActivity
import com.sclera.sclerago.domain.model.AssetDetails
import com.sclera.sclerago.domain.model.AssetQueryFilter
import com.sclera.sclerago.domain.model.AssetFull
import com.sclera.sclerago.domain.model.AssetSummary
import com.sclera.sclerago.domain.model.Media
import com.sclera.sclerago.domain.model.Status
import com.sclera.sclerago.domain.validation.mark
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * One read of the asset list: [open] says whether the database was open for it (when not, [assets] is empty). [managed]
 * are the assets in Managed Assets, read alongside and kept apart: [assets] never holds them.
 */
data class AssetsRead(val open: Boolean, val assets: List<Asset>, val managed: List<Asset> = emptyList())

/** Spec values shown after the make and model in an asset's one-line detail: database key to unit. */
private val SPEC_UNITS = listOf("rated_power_kw" to "kW", "voltage" to "V", "speed" to "RPM")

/** The app's field keys a scanned device is looked up in. */

/** The server's VdmsState RETURNING (3) and RETURNED (4): Managed Assets sent the asset back to be collected again. */
private val VDMS_SENT_BACK = setOf(3, 4)

private val ASSET_TABLES = arrayOf(
    "assets", "asset_values", "asset_media", "buildings", "floors", "locations", "master_formats", "asset_fields", "validation_results",
    "validation_jobs",
)

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
    private val blobs: MediaBlobs,
) {
    /** Whether the copies older versions of the app kept on the device have been checked against the database. */
    @Volatile
    private var legacyChecked = false

    /** Every asset [filter] keeps, newest first. */
    suspend fun list(filter: AssetQueryFilter = AssetQueryFilter(), propertyId: String = ""): List<Asset> =
        withContext(io) { loadPage(filter, null, 0, propertyId) }

    /**
     * Every asset [filter] keeps, read again when the asset tables change, with whether the database was open for the
     * read (decided in the same read, so the two always match): not open yet means nothing is known, rather than no assets.
     */
    fun listReadFlow(filter: AssetQueryFilter = AssetQueryFilter()): Flow<AssetsRead> =
        db.watch(io, *ASSET_TABLES) {
            if (db.isOpen && db.hasTable("assets")) AssetsRead(true, loadPage(filter, null, 0, ""), loadManaged()) else AssetsRead(false, emptyList())
        }

    /** One page of [list]: [limit] assets from [offset]. */
    suspend fun page(filter: AssetQueryFilter, limit: Int, offset: Int, propertyId: String = ""): List<Asset> =
        withContext(io) { loadPage(filter, limit, offset, propertyId) }

    fun pageFlow(filter: AssetQueryFilter, limit: Int, offset: Int, propertyId: String = ""): Flow<List<Asset>> =
        db.watch(io, *ASSET_TABLES) { loadPage(filter, limit, offset, propertyId) }

    /** How many assets [filter] keeps. */
    suspend fun count(filter: AssetQueryFilter = AssetQueryFilter()): Int = withContext(io) { loadCount(filter) }

    fun countFlow(filter: AssetQueryFilter = AssetQueryFilter()): Flow<Int> = db.watch(io, "assets", "asset_values", "asset_fields") { loadCount(filter) }

    /** Counts per status for the status chips: [filter] without its status. */
    suspend fun summary(filter: AssetQueryFilter = AssetQueryFilter()): AssetSummary = withContext(io) { loadSummary(filter) }

    fun summaryFlow(filter: AssetQueryFilter = AssetQueryFilter()): Flow<AssetSummary> =
        db.watch(io, "assets", "asset_values", "asset_fields") { loadSummary(filter) }

    /** [assetId] with its field values, activity and latest validation result; null when it is not there. */
    suspend fun asset(assetId: String, propertyId: String = ""): AssetFull? = withContext(io) { loadFull(assetId, propertyId) }

    fun assetFlow(assetId: String, propertyId: String = ""): Flow<AssetFull?> =
        db.watch(io, *ASSET_TABLES, "asset_activity", "validation_results", "validation_issues") { loadFull(assetId, propertyId) }

    /** The details of each of [assetIds], by asset id. */
    suspend fun detailsFor(assetIds: Collection<String>): Map<String, AssetDetails> = withContext(io) { loadDetails(assetIds) }

    /** An asset's photos, videos and documents in order. [Media.uri] is the stored object path (`local://` or `s3://`). */
    suspend fun media(assetId: String): List<Media> = withContext(io) { mediaFor(listOf(assetId))[assetId].orEmpty() }

    /** An asset's comments and recorded changes, newest first. */
    suspend fun activity(assetId: String): List<AssetActivity> = withContext(io) { loadActivity(assetId) }

    fun activityFlow(assetId: String): Flow<List<AssetActivity>> = db.watch(io, "asset_activity") { loadActivity(assetId) }

    /** One page of the assets [filter] keeps. */
    private fun loadPage(filter: AssetQueryFilter, limit: Int?, offset: Int, propertyId: String): List<Asset> =
        db.whenTable("assets", emptyList()) {
            val where = db.assetWhere(filter)
            val paging = if (limit != null) " LIMIT ${limit.coerceAtLeast(0)} OFFSET ${offset.coerceAtLeast(0)}" else ""
            val rows = db.query("SELECT a.* FROM assets a WHERE ${where.sql} $ASSET_ORDER$paging", where.args)
            toAssets(rows, propertyId)
        }

    /** The assets in Managed Assets, newest first: what the Managed assets chip lists (read-only). */
    private fun loadManaged(): List<Asset> = db.whenTable("assets", emptyList()) {
        toAssets(db.query("SELECT a.* FROM assets a WHERE ${db.managedAssets("a")} $ASSET_ORDER", emptyList()), "")
    }

    private fun loadCount(filter: AssetQueryFilter): Int = db.whenTable("assets", 0) {
        val where = db.assetWhere(filter)
        db.queryOne("SELECT COUNT(*) AS n FROM assets a WHERE ${where.sql}", where.args)?.int("n") ?: 0
    }

    private fun loadSummary(filter: AssetQueryFilter): AssetSummary = db.whenTable("assets", AssetSummary(0, emptyMap(), 0, 0)) {
        val where = db.assetWhere(filter, withStatus = false)
        val rows = db.query(ASSET_SUMMARY.replace("%WHERE%", where.sql), where.args)
        val counts = rows.mapNotNull { r -> Status.entries.firstOrNull { it.name == r.string("st") }?.let { it to (r.int("n") ?: 0) } }.toMap()
        AssetSummary(
            total = rows.sumOf { it.int("n") ?: 0 },
            byStatus = counts.filterValues { it > 0 },
            aiSuggestions = rows.sumOf { it.int("ai") ?: 0 },
            validated = rows.sumOf { it.int("ok") ?: 0 },
        )
    }

    private fun loadFull(assetId: String, propertyId: String): AssetFull? = db.whenTable("assets", null) {
        // One in Managed Assets opens too (read-only), as the server's GET answers for it.
        val row = db.queryOne(ASSET_BY_ID.replace("%LIVE%", db.readableAssets("a")), listOf(assetId))
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
        val sentBack = sentBack(ids)
        val checked = validation.validationsFor(ids)
        val formatById = formats.byId()
        val index = places.index()
        return rows.map { row ->
            val id = row.string("id").orEmpty()
            val place = index.location(row.string("location_id"))
            val asset = row.toAsset(propertyId, place, formatById[row.string("master_format_id")], media[id].orEmpty())
            asset.copy(
                detail = detailLine(asset.detail, values[id].orEmpty()),
                searchText = row.searchText(values[id].orEmpty().values),
                where = place?.where ?: index.floorOrBuilding(asset.floorId, asset.buildingId),
                // As the server's recollect_condition: still drafted, and either a reviewer's latest decision sent it back
                // or Managed Assets (VDMS) returned it.
                recollect = row.string("status") == "drafted" && (id in sentBack || row.int("vdms_state") in VDMS_SENT_BACK),
                validationMark = checked[id]?.mark(),
                validationProblems = checked[id]?.problems?.size ?: 0,
            )
        }
    }

    private fun loadDetails(assetIds: Collection<String>): Map<String, AssetDetails> = db.whenTable("assets", emptyMap()) {
        if (assetIds.isEmpty()) return@whenTable emptyMap()
        val rows = db.queryIn(ASSETS_BY_IDS.replace("%LIVE%", db.assetsInAdc("a")), assetIds)
        val values = db.whenTable("asset_values", emptyList()) { db.queryIn(ASSET_VALUES_ROWS, assetIds) }.associateBy { it.string("asset_id").orEmpty() }
        val formatById = formats.byId()
        rows.associate { row ->
            val id = row.string("id").orEmpty()
            id to row.toAssetDetails(values[id], formatById[row.string("master_format_id")])
        }
    }

    private fun detailLine(makeModel: String, values: Map<String, String>): String =
        (listOf(makeModel) + SPEC_UNITS.mapNotNull { (key, unit) -> values[key]?.takeIf { it.isNotBlank() }?.let { spec(it, unit) } })
            .filter { it.isNotBlank() }.joinToString(" · ")

    private fun spec(value: String, unit: String) = if (value.any(Char::isLetter)) value else "$value $unit"

    /**
     * The media of [ids]. Each opens from the database (its `multimedia` blob, through the media provider), the only copy
     * of the file, whether it was added here or downloaded from the server.
     */
    private fun mediaFor(ids: List<String>): Map<String, List<Media>> = db.whenTable("asset_media", emptyMap()) {
        if (!legacyChecked) {
            legacyChecked = true
            // Files an older version kept here as well as in the database: the database's copy is the one now.
            files.dropLegacyCopies(blobs::has)
        }
        db.queryIn(MEDIA_FOR.replace("%LIVE%", db.notRemoved("asset_media")), ids)
            .groupBy({ it.string("asset_id").orEmpty() }, { row -> row.toMedia(blobs.uri("asset_media", row.string("id").orEmpty())) })
    }

    /** Of [ids], the assets whose latest validation decision sent them back to be collected again (decision "recollect"). */
    private fun sentBack(ids: List<String>): Set<String> = db.whenTable("validation_results", emptySet()) {
        val decided = if (db.hasColumn("validation_results", "decided_at")) "r.decided_at" else "r.created_at"
        val sql = LATEST_DECISIONS.replace("%LIVE%", db.notRemoved("validation_results", "r")).replace("%DECIDED%", decided)
        db.queryIn(sql, ids)
            .groupBy { it.string("asset_id").orEmpty() }
            .filterValues { rows -> rows.first().string("decision") == "recollect" }
            .keys
    }

    private fun valuesFor(ids: List<String>): Map<String, Map<String, String>> = db.whenTable("asset_values", emptyMap()) {
        db.queryIn(db.withAnswers(ASSET_VALUES_FOR), ids).associate { it.string("asset_id").orEmpty() to DbJson.stringMap(it.string("answers")) }
    }
}
