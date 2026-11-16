package com.sclera.sclerago.data.sync

import android.content.Context
import com.sclera.sclerago.data.remote.AuthConfig
import com.sclera.sclerago.services.syncManager.GlobalConfigDatabaseHelper
import com.sclera.sclerago.services.syncManager.SyncManager
import com.sclera.sclerago.services.syncManager.SyncManagerService
import com.sclera.sclerago.services.syncManager.constants.SyncConfigTables
import com.sclera.sclerago.services.syncManager.enums.OfflineFeatures
import com.sclera.sclerago.services.syncManager.utils.SyncUtils
import com.sclera.sclerago.utility.Utility
import dagger.hilt.android.qualifiers.ApplicationContext
import io.requery.android.database.sqlite.SQLiteDatabase
import org.json.JSONObject
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

    /** True when a debug test database is present: it is read instead of the engine's, and no sync runs. */
    fun usesTestDatabase(): Boolean = testConnection() != null

    /** The test database when present, else the engine's open connection; null until the first download has finished or after a wipe. */
    fun connection(): SQLiteDatabase? = testConnection() ?: SyncUtils.dbInstance?.dbConnection?.takeIf { it.isOpen }

    fun snapshot(): EngineSnapshot {
        val engine = SyncUtils.dbInstance ?: return EngineSnapshot()
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

    /** True when the device is too full for the download the engine was told about. */
    fun storageLow(): Boolean = runCatching { SyncUtils.checkStorage(MODULE) }.getOrDefault(false)

    /**
     * Hands the signed-in user and [propertyId] to the engine, which downloads that property's database and starts
     * syncing on its own threads. Blocking but short.
     */
    fun begin(credentials: SyncCredentials, propertyId: String) = synchronized(lock) {
        Utility.context = context
        // A different user's data on this device is dropped before the new user's download starts.
        SyncManagerService.deleteDatabaseIfWrongUser(credentials.email)
        // So is another property's: the engine holds one property's database at a time.
        dropOtherProperty(propertyId)
        val config = JSONObject()
            .put("propertyId", propertyId)
            .put("id", credentials.userId)
            .put("url", AuthConfig.APP_URL)
            .put("email", credentials.email)
            .put("organisationId", credentials.organisationId)
            .put("role", credentials.role)
            .put("accessToken", credentials.accessToken)
            .put("sessionUrl", SESSION_URL)
            .put("sessionId", credentials.sessionId)
            .put("cloudUrl", AuthConfig.APP_URL)
        SyncManagerService.initAssetDataSync(config)
    }

    /** Starts over with a fresh engine instance (a failed one cannot be reused cleanly). */
    fun restart(credentials: SyncCredentials, propertyId: String) = synchronized(lock) {
        // Dropped first while the engine still says which property it held, so its data isn't kept for another one.
        dropOtherProperty(propertyId)
        SyncUtils.dbInstance?.let { runCatching { it.closeConnection() } }
        SyncUtils.dbInstance = null
        begin(credentials, propertyId)
    }

    /**
     * Stops the engine and deletes the downloaded database when it belongs to a property other than [propertyId]
     * (or to one it can't name), so a new engine never opens another property's data as its own.
     */
    private fun dropOtherProperty(propertyId: String) {
        val running = SyncUtils.dbInstance
        val held = running?.propertyId ?: storedPropertyId()
        val hasDatabase = context.getDatabasePath("$NAME.db").exists() || context.getDatabasePath("$NAME.zip").exists()
        if (held == propertyId || (running == null && !hasDatabase)) return
        running?.let {
            it.setContinueDataSync(false)
            it.setContinueImageSync(false)
            it.setContinueLargeFileSync(false)
            runCatching { it.closeConnection() }
        }
        SyncUtils.dbInstance = null
        listOf("$NAME.db", "$NAME.zip").forEach { context.deleteDatabase(it) }
        SyncUtils.getSharedPrefs(context).edit().remove(STATUS_KEY).remove("adc_sync_timestamp").remove("adc_total_images").apply()
    }

    /** The property the engine last saved in its config table, or null when there is none (or no such column yet). */
    private fun storedPropertyId(): String? {
        if (!context.getDatabasePath(GLOBAL_DB).exists()) return null
        return runCatching {
            GlobalConfigDatabaseHelper.initializeInstance(context).globalDB.rawQuery(
                "SELECT ${GlobalConfigDatabaseHelper.ASSET_CONFIG_TABLE_COLUMN_PROPERTY_ID} FROM ${GlobalConfigDatabaseHelper.TABLE_NAME_ASSET_CONFIG} LIMIT 1",
                null,
            ).use { if (it.moveToFirst() && !it.isNull(0)) it.getString(0) else null }
        }.getOrNull()
    }

    /** Gives the engine a refreshed token, session or organisation so its next request is accepted. */
    fun updateCredentials(credentials: SyncCredentials) {
        val engine = SyncUtils.dbInstance ?: return
        SyncManager.token = credentials.accessToken
        SyncManager.sessionId = credentials.sessionId
        engine.orgId = credentials.organisationId
        SyncUtils.updateToken(credentials.accessToken)
        SyncUtils.updateSessionId(credentials.sessionId)
    }

    fun setTablesChangedListener(listener: (Set<String>) -> Unit) {
        SyncUtils.tablesChangedListener = SyncUtils.TablesChangedListener { listener(it) }
    }

    /** True when the engine holds or has stored anything for a user. */
    fun hasLocalData(): Boolean = SyncUtils.dbInstance != null ||
        context.getDatabasePath("$NAME.db").exists() || context.getDatabasePath("$NAME.zip").exists() ||
        SyncUtils.getSharedPrefs(context).contains(STATUS_KEY)

    /** Stops the engine and removes everything it stored: the downloaded database, the config rows and the static credentials. */
    fun wipe() = synchronized(lock) {
        SyncUtils.dbInstance?.let { runCatching { it.closeConnection() } }
        SyncUtils.dbInstance = null
        listOf("$NAME.db", "$NAME.zip").forEach { context.deleteDatabase(it) }
        if (context.getDatabasePath(GLOBAL_DB).exists()) {
            runCatching {
                val helper = GlobalConfigDatabaseHelper.initializeInstance(context)
                helper.dropTablesForAssetDataCollection(null)
                listOf(GlobalConfigDatabaseHelper.USER_DATA, GlobalConfigDatabaseHelper.USER_INFO_CACHE)
                    .forEach { helper.globalDB.execSQL("DELETE FROM $it") }
            }
        }
        SyncUtils.getSharedPrefs(context).edit().remove(STATUS_KEY).remove("adc_sync_timestamp").remove("adc_total_images").apply()
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
        const val MODULE = "asset-data-collection"
        val NAME = OfflineFeatures.ASSET_DATA_COLLECTION.toString()
        const val GLOBAL_DB = "global_config.db"
        const val STATUS_KEY = "adc_sync_status"

        /** Accounts endpoint the engine POSTs (with the session cookie) to get a new access token after a 401. */
        const val SESSION_URL = AuthConfig.SCLERA_API + "/token"
    }
}
