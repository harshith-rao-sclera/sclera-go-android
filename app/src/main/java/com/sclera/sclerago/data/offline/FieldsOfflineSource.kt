package com.sclera.sclerago.data.offline

import com.sclera.sclerago.data.mapper.DbJson
import com.sclera.sclerago.data.mapper.DbRow
import com.sclera.sclerago.data.mapper.toFieldCfg
import com.sclera.sclerago.data.sync.AdcDatabase
import com.sclera.sclerago.domain.model.FieldCfg
import com.sclera.sclerago.domain.model.FieldDef
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

private const val STANDARD_FIELDS = "SELECT * FROM asset_fields WHERE (asset_id IS NULL OR asset_id = '') AND %LIVE% ORDER BY position, id"

private const val EFFECTIVE_FIELDS =
    "SELECT * FROM asset_fields WHERE (asset_id IS NULL OR asset_id = '' OR asset_id = ?) AND %LIVE% ORDER BY (asset_id IS NOT NULL AND asset_id <> ''), position, id"

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

    private fun loadStandard(): List<DbRow> = db.whenTable(TABLE, emptyList()) {
        db.query(STANDARD_FIELDS.replace("%LIVE%", db.notRemoved(TABLE)))
    }

    private fun loadEffective(assetId: String): List<FieldCfg> = db.whenTable(TABLE, emptyList()) {
        db.query(EFFECTIVE_FIELDS.replace("%LIVE%", db.notRemoved(TABLE)), listOf(assetId)).mapNotNull { it.toFieldCfg() }
    }

    private fun DbRow.toDef() = toFieldCfg()?.let { FieldDef(it, DbJson.stringList(string("aliases"))) }

    private companion object {
        const val TABLE = "asset_fields"
    }
}
