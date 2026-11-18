package com.sclera.sclerago.data.offline

import com.sclera.sclerago.data.mapper.DbJson
import com.sclera.sclerago.data.mapper.DbRow
import com.sclera.sclerago.data.mapper.FieldKeys
import com.sclera.sclerago.data.mapper.toFieldCfg
import com.sclera.sclerago.data.sync.AdcDatabase
import com.sclera.sclerago.domain.model.DraftField
import com.sclera.sclerago.domain.model.FieldCfg
import com.sclera.sclerago.domain.model.FormEntry
import com.sclera.sclerago.domain.model.FieldDef
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * As the server decides it (`services/fields.py`): a standard field (`is_standard`) is on every asset, any other field
 * only on the asset its `asset_id` names. A row that is neither (no asset linked) is on no asset.
 */
private const val STANDARD_FIELDS = "SELECT * FROM asset_fields WHERE is_standard = 1 AND %LIVE% ORDER BY position, key"

private const val EFFECTIVE_FIELDS =
    "SELECT * FROM asset_fields WHERE (is_standard = 1 OR asset_id = ?) AND %LIVE% ORDER BY (COALESCE(is_standard, 0) = 0), position, key"

/** The asset fields: the standard ones every asset has, and the extra ones a single asset carries. Reads only. */
class FieldsOfflineSource(private val db: AdcDatabase, private val io: CoroutineDispatcher) {

    /** Standard fields in their configured order (those with an input the app has; inactive ones have `visible` false). */
    suspend fun standard(): List<FieldCfg> = withContext(io) { loadStandard().mapNotNull { it.toFieldCfg() } }

    fun standardFlow(): Flow<List<FieldCfg>> = db.watch(io, TABLE) { loadStandard().mapNotNull { it.toFieldCfg() } }

    /** The standard fields with the other names each answers to in imported sheets. */
    suspend fun standardDefs(): List<FieldDef> = withContext(io) { loadStandard().mapNotNull { it.toDef() } }

    /** The fields of [assetId]: the standard ones, then the asset's own. */
    suspend fun effective(assetId: String): List<FieldCfg> = withContext(io) { loadEffective(assetId) }

    fun effectiveFlow(assetId: String): Flow<List<FieldCfg>> = db.watch(io, TABLE) { loadEffective(assetId) }

    /**
     * The rows of [assetId]'s fields in the same order, active ones only: inputs by key, and its photo, video and voice
     * memo fields (answered in the Media tab). Key-value fields have no row, as on the web.
     */
    fun entriesFlow(assetId: String): Flow<List<FormEntry>> = db.watch(io, TABLE) {
        db.whenTable(TABLE, emptyList()) {
            db.query(EFFECTIVE_FIELDS.replace("%LIVE%", db.notRemoved(TABLE)), listOf(assetId)).mapNotNull { row ->
                when {
                    !row.bool("is_active") -> null
                    row.string("type") in MEDIA_TYPES ->
                        FormEntry.MediaField(row.string("id").orEmpty(), row.string("name").orEmpty(), row.bool("is_required"))
                    else -> row.toFieldCfg()?.let { FormEntry.Input(it.key) }
                }
            }
        }
    }

    /**
     * The active standard fields in their configured order, by the server's id and key, without the photo, video and voice
     * memo fields (what the web's "Add assets from files" shows a file's values in).
     */
    suspend fun activeStandard(): List<DraftField> = withContext(io) {
        loadStandard().filter { it.bool("is_active") && it.string("type") !in MEDIA_TYPES }.mapNotNull { row ->
            val id = row.string("id")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            DraftField(id, row.string("key").orEmpty(), row.string("name").orEmpty())
        }
    }

    /** Field id by form key (the app's key, so manufacturer is "make") for the fields of [assetId]: the standard ones and its own. */
    suspend fun idsByKey(assetId: String): Map<String, String> = withContext(io) {
        db.whenTable(TABLE, emptyMap()) {
            db.query(EFFECTIVE_FIELDS.replace("%LIVE%", db.notRemoved(TABLE)), listOf(assetId))
                .mapNotNull { row -> row.string("id")?.let { FieldKeys.toApp(row.string("key").orEmpty()) to it } }
                .toMap()
        }
    }

    private fun loadStandard(): List<DbRow> = db.whenTable(TABLE, emptyList()) {
        db.query(STANDARD_FIELDS.replace("%LIVE%", db.notRemoved(TABLE)))
    }

    private fun loadEffective(assetId: String): List<FieldCfg> = db.whenTable(TABLE, emptyList()) {
        db.query(EFFECTIVE_FIELDS.replace("%LIVE%", db.notRemoved(TABLE)), listOf(assetId)).mapNotNull { it.toFieldCfg() }
    }

    private fun DbRow.toDef() = toFieldCfg()?.let { FieldDef(it, DbJson.stringList(string("aliases"))) }

    private companion object {
        const val TABLE = "asset_fields"
        val MEDIA_TYPES = setOf("image", "video", "voice_memo")
    }
}
