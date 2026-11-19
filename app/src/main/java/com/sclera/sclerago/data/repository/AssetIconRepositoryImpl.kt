package com.sclera.sclerago.data.repository

import android.util.Log
import com.sclera.sclerago.data.local.AssetIconStore
import com.sclera.sclerago.data.remote.adc.AdcClassifyApi
import com.sclera.sclerago.di.IoDispatcher
import com.sclera.sclerago.domain.model.AssetIcons
import com.sclera.sclerago.domain.model.OnlineException
import com.sclera.sclerago.domain.repository.AssetIconRepository
import com.sclera.sclerago.domain.system.TimeProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The server's asset icons (`/v1/asset-icons`, each `{name}.svg`) and a property's type icons (`/properties/{id}/
 * classification`), kept in `global_config.db` ([AssetIconStore]) so the lists show them offline. An icon is downloaded
 * when it is new or older than a day (the server's cache time); the type icons are replaced on each open.
 */
@Singleton
class AssetIconRepositoryImpl @Inject constructor(
    private val api: AdcClassifyApi,
    private val store: AssetIconStore,
    private val time: TimeProvider,
    @IoDispatcher private val io: CoroutineDispatcher,
) : AssetIconRepository {
    private val state = MutableStateFlow(AssetIcons())
    override val icons: StateFlow<AssetIcons> = state.asStateFlow()

    override suspend fun refreshIcons() {
        state.value = state.value.copy(svg = withContext(io) { store.icons().mapValues { it.value.svg } })
        try {
            downloadIcons()
        } catch (e: OnlineException) {
            Log.w(TAG, "Asset icons not downloaded: ${e.error}")
        }
        state.value = state.value.copy(svg = withContext(io) { store.icons().mapValues { it.value.svg } })
    }

    override suspend fun open(propertyId: String) {
        state.value = kept(propertyId)
        // Each part on its own: the pictures without the type names (or the other way round) still help.
        try {
            downloadIcons()
        } catch (e: OnlineException) {
            Log.w(TAG, "Asset icons not downloaded: ${e.error}")
        }
        try {
            val byFormat = typeIcons(propertyId)
            withContext(io) { store.replaceTypeIcons(propertyId, byFormat) }
        } catch (e: OnlineException) {
            Log.w(TAG, "Type icons not downloaded: ${e.error}")
        }
        state.value = kept(propertyId)
    }

    /** The icons and [propertyId]'s type icons kept on the device. */
    private suspend fun kept(propertyId: String) =
        withContext(io) { AssetIcons(store.icons().mapValues { it.value.svg }, store.typeIcons(propertyId)) }

    /** The server's icons that are new, or older than [MAX_AGE_MS] here, downloaded and kept. */
    private suspend fun downloadIcons() {
        val now = time.nowMillis()
        val kept = withContext(io) { store.icons() }
        val listed = api.assetIcons().data.filter { it.name.isNotBlank() }
        val stale = listed.filter { icon -> kept[icon.name]?.let { now - it.updatedAt > MAX_AGE_MS } ?: true }
        // An SVG may start with a comment (the server's carry their licence: <!-- @license lucide-static … -->).
        val fresh = stale.mapNotNull { icon ->
            val svg = api.assetIconSvg(icon.name).trim()
            svg.takeIf { it.contains("<svg") }?.let { icon.name to (icon.label to it) }
        }.toMap()
        withContext(io) { store.putIcons(fresh, now) }
    }

    /** Which icon each master format of [propertyId] uses: its sub type's, asset type's or system type's row. */
    private suspend fun typeIcons(propertyId: String): Map<String, String> = buildMap {
        api.classification(propertyId).systemTypes.forEach { system ->
            system.masterFormatId?.let { put(it, system.icon) }
            system.assetTypes.forEach { type ->
                type.masterFormatId?.let { put(it, type.icon) }
                type.subtypes.forEach { sub -> put(sub.masterFormatId, sub.icon) }
            }
        }
    }

    private companion object {
        const val TAG = "AssetIcons"

        /** The server says an icon may be cached a day. */
        const val MAX_AGE_MS = 24L * 60 * 60 * 1000
    }
}
