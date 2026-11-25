package com.sclera.sclerago.data.repository

import com.sclera.sclerago.data.sync.AdcDatabase
import com.sclera.sclerago.data.sync.SyncManagerBridge
import com.sclera.sclerago.di.IoDispatcher
import com.sclera.sclerago.domain.model.SyncSettings
import com.sclera.sclerago.domain.repository.SyncSettingsRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A property's sync from its offline database and its engine's switches. What waits to upload is what the engine's
 * change triggers flagged and hasn't pushed yet: `assets` rows with `changes`, `asset_media` rows with `changes` or a
 * file in `multimedia` flagged `media_changes`. The last sync is the server time the engine stored with the last changes
 * it pulled (`global_timestamp.seq`). The engine pushes and pulls on its own threads without telling the app, so this is
 * read again every few seconds while collected, as well as whenever the app writes those tables.
 */
@Singleton
class SyncSettingsRepositoryImpl @Inject constructor(
    private val db: AdcDatabase,
    private val bridge: SyncManagerBridge,
    @IoDispatcher private val io: CoroutineDispatcher,
) : SyncSettingsRepository {
    /** Bumped when a switch changes, so it shows at once. */
    private val switched = MutableStateFlow(0)

    override fun observe(propertyId: String): Flow<SyncSettings> =
        combine(merge(db.observe(*TABLES), ticks()), switched) { _, _ -> }
            .map { load(propertyId) }
            .distinctUntilChanged().conflate().flowOn(io)

    override fun setDataSync(propertyId: String, on: Boolean) {
        bridge.setDataSync(propertyId, on)
        switched.value++
    }

    override fun setMediaSync(propertyId: String, on: Boolean) {
        bridge.setMediaSync(propertyId, on)
        switched.value++
    }

    private fun ticks(): Flow<Unit> = flow {
        while (true) {
            emit(Unit)
            delay(REFRESH_MS)
        }
    }

    private fun load(propertyId: String): SyncSettings {
        // Only the open property's database is read; another one shows nothing waiting.
        val open = bridge.isSelected(propertyId) && db.isOpen
        return SyncSettings(
            assetsWaiting = if (open) count { assetsWaiting() } else 0,
            mediaWaiting = if (open) count { mediaWaiting() } else 0,
            lastSyncedAt = if (open) runCatching { lastSynced() }.getOrNull() else null,
            dataSync = bridge.dataSyncOn(propertyId),
            mediaSync = bridge.mediaSyncOn(propertyId),
        )
    }

    /** A count, or 0 when it can't be read (the database closed mid-query, or the sync hasn't made its columns yet). */
    private inline fun count(read: () -> Int?): Int = runCatching(read).getOrNull() ?: 0

    private fun assetsWaiting(): Int? {
        if (!db.hasTable("assets") || !db.hasColumn("assets", "changes")) return 0
        return db.queryOne("SELECT COUNT(*) AS n FROM assets WHERE ${pending("changes")}")?.long("n")?.toInt()
    }

    private fun mediaWaiting(): Int? {
        if (!db.hasTable("asset_media")) return 0
        val row = if (db.hasColumn("asset_media", "changes")) pending("m.changes") else "0"
        val file = if (db.hasTable("multimedia") && db.hasColumn("multimedia", "media_changes")) {
            "EXISTS (SELECT 1 FROM multimedia x WHERE x.table_name = 'asset_media' AND x.row_id = m.id AND x.media_changes IS NOT NULL)"
        } else "0"
        return db.queryOne("SELECT COUNT(*) AS n FROM asset_media m WHERE ($row) OR ($file)")?.long("n")?.toInt()
    }

    private fun lastSynced(): Long? {
        if (!db.hasTable("global_timestamp")) return null
        val seq = db.queryOne("SELECT seq FROM global_timestamp WHERE id = '1'")?.long("seq") ?: return null
        return toMillis(seq)
    }

    private companion object {
        /** A row the engine flagged with changes ([column]) not yet pushed (an empty `{}` is nothing to push). */
        fun pending(column: String) = "$column IS NOT NULL AND LENGTH($column) > 2"

        /** The tables the app writes that change what waits to upload. */
        val TABLES = arrayOf("assets", "asset_media", "multimedia")

        /** How often the counts and last sync are read again while the page is open. */
        const val REFRESH_MS = 5_000L

        /** The server's sync time in milliseconds: it is kept in microseconds (older data: milliseconds or seconds). */
        fun toMillis(value: Long): Long? = when {
            value <= 0 -> null
            value >= 100_000_000_000_000L -> value / 1000
            value >= 100_000_000_000L -> value
            else -> value * 1000
        }
    }
}
