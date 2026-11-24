package com.sclera.sclerago.data.sync

import android.content.Context
import com.sclera.sclerago.BuildConfig
import com.sclera.sclerago.data.remote.AuthConfig
import com.sclera.sclerago.services.syncManager.GlobalConfigDatabaseHelper
import com.sclera.sclerago.services.syncManager.SyncManager
import com.sclera.sclerago.services.syncManager.SyncManagerRepository
import com.sclera.sclerago.services.syncManager.SyncManagerService
import com.sclera.sclerago.services.syncManager.constants.SyncConfigTables
import com.sclera.sclerago.services.syncManager.utils.SyncUtils
import com.sclera.sclerago.utility.Utility
import dagger.hilt.android.qualifiers.ApplicationContext
import io.requery.android.database.sqlite.SQLiteDatabase
import org.json.JSONObject
import java.net.URI
import java.util.Optional
import javax.inject.Inject
import javax.inject.Singleton

/** What the Java sync engine reports about itself at one moment. */
data class EngineSnapshot(
    val hasInstance: Boolean = false,
    /** The property whose data the engine holds or is downloading. */
    val propertyId: String? = null,
    /** The engine's own status: 0 none, 1 downloading, 2 synced, 3 failed, 4 unzipping. */
    val status: Int = 0,
    val progressPercent: Int = 0,
    val totalMb: Double = 0.0,
    val downloadedMb: Double = 0.0,
    /** The database is open and the table configuration has been pulled, so it can be read. */
    val readable: Boolean = false,
)

/**
 * The only place that touches the Java sync engine's statics ([SyncUtils.dbInstance], [SyncManager] fields),
 * so the rest of the app talks to this class instead of to globals.
 */
@Singleton
class SyncManagerBridge @Inject constructor(
    @ApplicationContext private val context: Context,
    private val testDatabase: Optional<TestDatabase>,
) {
    private val lock = Any()

    private fun testConnection(): SQLiteDatabase? = testDatabase.orElse(null)?.connection()

    /** The app-wide config database (the synced QR tag tables live in it), or null before it exists. */
    fun globalDatabase(): SQLiteDatabase? =
        if (context.getDatabasePath(GLOBAL_DB).exists()) GlobalConfigDatabaseHelper.initializeInstance(context).globalDB else null

    /** True when a debug test database is present: it is read instead of the engine's, and no sync runs. */
    fun usesTestDatabase(): Boolean = testConnection() != null

    /** The property the app works on: [connection] reads its database. Every downloaded property keeps syncing. */
    @Volatile
    private var selected: String? = null

    /** The test database when present, else the selected property's open connection; null until it has been opened or after a wipe. */
    fun connection(): SQLiteDatabase? =
        testConnection() ?: selected?.let { SyncUtils.adcInstances[it] }?.dbConnection?.takeIf { it.isOpen }

    /** Makes [propertyId] (null: none) the property the app reads, like choosing the vdms for inspection. */
    fun select(propertyId: String?) {
        selected = propertyId
        pointEngineAtSelected()
    }

    fun isSelected(propertyId: String): Boolean = selected == propertyId

    /** Properties whose engine [reopenAll] started and [begin] hasn't seen yet. Guarded by [lock]. */
    private val reopened = mutableSetOf<String>()

    /** The engine's own "current instance" follows the selected property once its engine exists. */
    private fun pointEngineAtSelected() {
        val id = selected ?: return
        SyncUtils.adcInstances[id]?.let { SyncUtils.dbInstance = it }
    }

    fun snapshot(propertyId: String): EngineSnapshot {
        val engine = SyncUtils.adcInstances[propertyId] ?: return EngineSnapshot()
        val conn = engine.dbConnection?.takeIf { it.isOpen }
        return EngineSnapshot(
            hasInstance = true,
            propertyId = engine.propertyId,
            status = engine.isSynced,
            progressPercent = engine.progress,
            totalMb = engine.totalFileSizeMB,
            downloadedMb = engine.totalMBDownloaded,
            readable = engine.isSynced == 2 && conn != null && configurationPulled(conn),
        )
    }

    private fun configurationPulled(conn: SQLiteDatabase): Boolean = runCatching {
        conn.rawQuery(
            "SELECT ${SyncConfigTables.globalTimestampTables} FROM ${SyncConfigTables.timeStampTable} WHERE ${SyncConfigTables.globalTimestampId} = '1'",
            null,
        ).use { it.moveToFirst() && !it.isNull(0) }
    }.getOrDefault(false)

    /** True when the device is too full for the download [propertyId]'s engine was told about. */
    fun storageLow(propertyId: String): Boolean = runCatching {
        SyncUtils.adcInstances[propertyId]?.let { SyncUtils.checkStorage(it.totalFileSizeMB) } ?: false
    }.getOrDefault(false)

    /**
     * Hands the signed-in user and [propertyId] to the engine, which downloads that property's database (or reopens
     * it) and keeps it syncing on its own threads, alongside the other properties. Blocking but short.
     */
    fun begin(credentials: SyncCredentials, propertyId: String) = synchronized(lock) {
        Utility.context = context
        // A different user's data on this device is dropped before the new user's download starts.
        SyncManagerService.deleteDatabaseIfWrongUser(credentials.email)
        // [reopenAll] has already started this one: it only needs the fresh credentials, not a second start.
        if (reopened.remove(propertyId) && SyncUtils.adcInstances.containsKey(propertyId)) {
            updateCredentials(credentials)
            pointEngineAtSelected()
            return@synchronized
        }
        deleteLeftovers(propertyId)
        val config = JSONObject()
            .put("propertyId", propertyId)
            .put("id", credentials.userId)
            .put("url", ENGINE_URL)
            .put("email", credentials.email)
            .put("organisationId", credentials.organisationId)
            .put("role", credentials.role)
            .put("accessToken", credentials.accessToken)
            .put("sessionUrl", SESSION_URL)
            .put("sessionId", credentials.sessionId)
            .put("cloudUrl", AuthConfig.APP_URL)
        SyncManagerService.initAssetDataSync(config)
        pointEngineAtSelected()
    }

    /**
     * Restarts the engine of every property fully downloaded on this device at once, from the engine's saved config, for
     * the signed-in user ([credentials]; another user's data is dropped first). Half-finished downloads are left to
     * [begin], which shows their progress. The engines get [credentials] straight away, and the selected property's
     * engine becomes the engine's current one. Blocking but short; call only while someone is signed in.
     */
    fun reopenAll(credentials: SyncCredentials) = synchronized(lock) {
        Utility.context = context
        SyncManagerService.deleteDatabaseIfWrongUser(credentials.email)
        val ids = downloadedProperties() - SyncUtils.adcInstances.keys
        if (ids.isEmpty()) return@synchronized
        SyncManagerRepository().initializeAssetDBInstance(ids)
        reopened += ids.filter { SyncUtils.adcInstances.containsKey(it) }
        updateCredentials(credentials)
        pointEngineAtSelected()
    }

    /** Starts [propertyId] over with a fresh engine instance (a failed one cannot be reused cleanly); the others carry on. */
    fun restart(credentials: SyncCredentials, propertyId: String) = synchronized(lock) {
        reopened.remove(propertyId)
        SyncManagerService.closeAssetDataSync(propertyId)
        begin(credentials, propertyId)
    }

    /**
     * Stops [propertyId]'s engine and deletes its database, download and engine settings, and its rows in the engine's
     * `global_config.db` tables: its saved engine config (so it isn't reopened) and its QR, NFC and barcode tag records,
     * unsent offline tags included. The other properties carry on. Blocking but short.
     */
    fun remove(propertyId: String) = synchronized(lock) {
        reopened.remove(propertyId)
        if (selected == propertyId) selected = null
        SyncManagerService.closeAssetDataSync(propertyId)
        SyncUtils.deleteAssetDataCollectionData(context, downloadedProperties() + SyncUtils.adcInstances.keys - propertyId)
        val global = globalDatabase() ?: return@synchronized
        val id = arrayOf(propertyId)
        runCatching {
            global.delete(GlobalConfigDatabaseHelper.TABLE_NAME_ASSET_CONFIG, "${GlobalConfigDatabaseHelper.ASSET_CONFIG_TABLE_COLUMN_PROPERTY_ID} = ?", id)
        }
        // Each tag record names the property it belongs to as its vdms_id.
        TAG_TABLES.forEach { table -> runCatching { global.delete(table, "vdms_id = ?", id) } }
    }

    /**
     * Deletes what no engine is working on and can't be opened: unfinished downloads of properties that aren't
     * syncing, and the legacy single database. Downloaded properties and running engines are kept.
     */
    private fun deleteLeftovers(propertyId: String) {
        SyncUtils.deleteAssetDataCollectionData(context, downloadedProperties() + SyncUtils.adcInstances.keys + propertyId)
    }

    /** The properties whose data is fully downloaded on this device. */
    fun downloadedProperties(): Set<String> =
        runCatching { SyncUtils.downloadedAssetDataCollectionProperties(context).toSet() }.getOrDefault(emptySet())

    /** Gives every engine a refreshed token, session or organisation so their next requests are accepted. */
    fun updateCredentials(credentials: SyncCredentials) {
        if (SyncUtils.adcInstances.isEmpty()) return
        SyncManager.token = credentials.accessToken
        SyncManager.sessionId = credentials.sessionId
        SyncUtils.adcInstances.values.forEach { it.orgId = credentials.organisationId }
        SyncUtils.updateToken(credentials.accessToken)
        SyncUtils.updateSessionId(credentials.sessionId)
    }

    /** Tells [listener] about changes pulled into the selected property's database; the others' aren't on screen. */
    fun setTablesChangedListener(listener: (Set<String>) -> Unit) {
        SyncUtils.tablesChangedListener = SyncUtils.TablesChangedListener { propertyId, tables ->
            if (propertyId == selected) listener(tables)
        }
    }

    /** Stops every engine and removes everything they stored: each property's database, the config rows and the static credentials. */
    fun wipe() = synchronized(lock) {
        selected = null
        reopened.clear()
        runCatching { SyncManagerService.closeAllAssetDataSyncs() }
        SyncUtils.adcInstances.clear()
        SyncUtils.dbInstance = null
        SyncUtils.deleteAssetDataCollectionData(context, emptySet())
        // Every other engine setting too (per-property image and sync switches, counts), as on a fresh install.
        SyncUtils.getSharedPrefs(context).edit().clear().commit()
        if (context.getDatabasePath(GLOBAL_DB).exists()) {
            runCatching {
                val helper = GlobalConfigDatabaseHelper.initializeInstance(context)
                helper.dropTablesForAssetDataCollection(null)
                listOf(GlobalConfigDatabaseHelper.USER_DATA, GlobalConfigDatabaseHelper.USER_INFO_CACHE)
                    .forEach { helper.globalDB.execSQL("DELETE FROM $it") }
            }
        }
        SyncManager.token = null
        SyncManager.sessionId = null
        SyncManager.sessionUrl = null
        SyncManager.cloudUrl = null
        SyncManager.user = null
        SyncManager.userId = null
        SyncManager.role = null
        SyncUtils.assetDataImageProgress = null
        SyncUtils.userData = null
    }

    private companion object {
        const val GLOBAL_DB = "global_config.db"

        /** The engine's tag tables in `global_config.db`, each row keyed to its property by `vdms_id`. */
        val TAG_TABLES = listOf(
            GlobalConfigDatabaseHelper.TABLE_NAME_QR_CODE,
            GlobalConfigDatabaseHelper.TABLE_NAME_CLIENT_QR_CODE,
            GlobalConfigDatabaseHelper.TABLE_NAME_NFC_RECORDS,
            GlobalConfigDatabaseHelper.TABLE_NAME_CLIENT_NFC_RECORDS,
            GlobalConfigDatabaseHelper.TABLE_NAME_CLIENT_BAR_CODE_RECORDS,
        )

        /** Accounts endpoint the engine POSTs (with the session cookie) to get a new access token after a 401. */
        const val SESSION_URL = AuthConfig.SCLERA_API + "/token"

        /**
         * The ADC host alone (`https://adc.…`): the engine adds `/adc-offline-sync/api/v1/…` itself, so the
         * `/api/v1/` that [BuildConfig.ADC_API_URL] ends in would double the path and every sync call would 404.
         */
        val ENGINE_URL: String = URI(BuildConfig.ADC_API_URL).let { "${it.scheme}://${it.rawAuthority}" }
    }
}
