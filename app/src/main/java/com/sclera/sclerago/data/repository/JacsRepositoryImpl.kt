package com.sclera.sclerago.data.repository

import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.CoroutineScope
import com.sclera.sclerago.di.ApplicationScope
import com.sclera.sclerago.domain.repository.NetworkMonitor
import com.sclera.sclerago.data.online.OnlineIdentity
import com.sclera.sclerago.data.local.EncryptedCache
import com.sclera.sclerago.data.remote.adc.AdcClassifyApi
import com.sclera.sclerago.data.remote.adc.AdcJson
import com.sclera.sclerago.di.IoDispatcher
import com.sclera.sclerago.domain.model.JacsCatalogCode
import com.sclera.sclerago.domain.model.JacsCode
import com.sclera.sclerago.domain.model.JacsGroup
import com.sclera.sclerago.domain.model.JacsPage
import com.sclera.sclerago.domain.model.JacsType
import com.sclera.sclerago.domain.model.OnlineError
import com.sclera.sclerago.domain.model.OnlineException
import com.sclera.sclerago.domain.repository.JacsRepository
import com.sclera.sclerago.domain.repository.PropertyDownloadRepository
import com.sclera.sclerago.domain.repository.UserDataCleaner
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable

/** The catalogue as kept on the phone: its version (the server's `ETag`) and its codes. */
@Serializable
private data class KeptCatalog(val etag: String? = null, val codes: List<KeptCode> = emptyList())

@Serializable
private data class KeptCode(val code: String, val title: String? = null, val groups: List<KeptGroup> = emptyList())

@Serializable
private data class KeptGroup(val systemType: String = "", val assetType: String? = null, val subtype: String? = null)

/**
 * The JACS catalogue. The whole catalogue (`GET /jacs-codes/catalog`, about 165 KB) is kept on the phone in the
 * [EncryptedCache] (wiped on sign-out with it) and asked for again with its version, so only a changed one is
 * downloaded; codes are searched and named from it, also offline. It is asked for on its own, as the backend asks of
 * the phone: when the app starts signed in, at each sign-in, and each time the phone comes back online, so it is on the
 * phone before anyone works offline. Before it has been downloaded once, a search goes to
 * the server (`/jacs-codes`). The type for a code the phone can't work out is the server's
 * (`/properties/{id}/jacs-codes/{code}/master-format`).
 */
@Singleton
class JacsRepositoryImpl @Inject constructor(
    private val api: AdcClassifyApi,
    private val downloads: PropertyDownloadRepository,
    private val cache: EncryptedCache,
    @IoDispatcher private val io: CoroutineDispatcher,
    identity: OnlineIdentity,
    network: NetworkMonitor,
    @ApplicationScope scope: CoroutineScope,
) : JacsRepository, UserDataCleaner {
    private val _catalog = MutableStateFlow<List<JacsCatalogCode>>(emptyList())
    override val catalog: StateFlow<List<JacsCatalogCode>> = _catalog.asStateFlow()

    private val _titles = MutableStateFlow<Map<String, String>>(emptyMap())
    override val titles: StateFlow<Map<String, String>> = _titles.asStateFlow()

    /** The version kept, sent back so an unchanged catalogue isn't downloaded again. */
    @Volatile
    private var etag: String? = null

    /** The kept copy has been read from the device this session. */
    @Volatile
    private var loaded = false

    private val lock = Mutex()

    init {
        // Signed in (known to the server) and online: at start, at each sign-in and each time the connection comes back.
        // An unchanged catalogue answers 304, so this costs one small call.
        scope.launch {
            combine(identity.identity.map { it != null }, network.observeOnline()) { signedIn, online -> signedIn && online }
                .distinctUntilChanged()
                .filter { it }
                .collect { refreshCatalog() }
        }
    }

    /** Asks for the catalogue with the version kept; only a changed one is downloaded and kept. Offline: nothing. */
    private suspend fun refreshCatalog() {
        lock.withLock {
            load()
            val answer = try {
                api.jacsCatalog(etag)
            } catch (_: OnlineException) {
                return // Offline, or the server refused: the kept one stays.
            } ?: return // Not changed.
            val (tag, dto) = answer
            val codes = dto.codes.filter { it.code.isNotBlank() }.map { c ->
                KeptCode(
                    c.code.trim(), c.title?.trim()?.takeIf { it.isNotEmpty() },
                    c.groups.mapNotNull { g ->
                        g.systemType?.trim()?.takeIf { it.isNotEmpty() }?.let { KeptGroup(it, g.assetType?.trim()?.takeIf { a -> a.isNotEmpty() }, g.subAssetType?.trim()?.takeIf { s -> s.isNotEmpty() }) }
                    },
                )
            }
            val kept = KeptCatalog(tag, codes)
            withContext(io) { runCatching { cache.write(CACHE, AdcJson.encodeToString(KeptCatalog.serializer(), kept)) } }
            show(kept)
        }
    }

    /** Reads the kept catalogue from the device once. */
    private suspend fun load() {
        if (loaded) return
        loaded = true
        val kept = withContext(io) {
            cache.read(CACHE)?.let { runCatching { AdcJson.decodeFromString(KeptCatalog.serializer(), it) }.getOrNull() }
        } ?: return
        show(kept)
    }

    private fun show(kept: KeptCatalog) {
        etag = kept.etag
        val codes = kept.codes.map { c -> JacsCatalogCode(c.code, c.title, c.groups.map { JacsGroup(it.systemType, it.assetType, it.subtype) }) }
        _catalog.value = codes
        _titles.value = codes.mapNotNull { c -> c.title?.let { c.code to it } }.toMap()
    }

    override suspend fun search(query: String, cursor: String?): JacsPage {
        lock.withLock { load() }
        val codes = _catalog.value
        if (codes.isEmpty()) return online(query, cursor)
        val found = withContext(io) { matching(codes, query) }
        val from = cursor?.toIntOrNull()?.coerceAtLeast(0) ?: 0
        val page = found.drop(from).take(PAGE_SIZE)
        val next = (from + PAGE_SIZE).takeIf { it < found.size }?.toString()
        return JacsPage(page.map { JacsCode(it.code, it.title) }, next)
    }

    /**
     * As the server's `?q=`: codes holding every word of [query] (in any order) in their code, title or type names; the
     * code typed itself first, then codes starting with it, then the catalogue's order (by code).
     */
    private fun matching(codes: List<JacsCatalogCode>, query: String): List<JacsCatalogCode> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return codes
        val words = q.split(Regex("\\s+")).filter { it.isNotEmpty() }
        val hits = codes.filter { c ->
            val text = buildString {
                append(c.code.lowercase()).append(' ').append(c.title.orEmpty().lowercase())
                c.groups.forEach { g -> append(' ').append(listOfNotNull(g.systemType, g.assetType, g.subtype).joinToString(" ").lowercase()) }
            }
            words.all { it in text }
        }
        return hits.sortedBy { c ->
            val code = c.code.lowercase()
            when {
                code == q -> 0
                code.startsWith(q) -> 1
                else -> 2
            }
        }
    }

    private suspend fun online(query: String, cursor: String?): JacsPage {
        val page = api.jacsCodes(query.ifBlank { null }, cursor, PAGE_SIZE)
        return JacsPage(
            codes = page.data.filter { it.code.isNotBlank() }.map { JacsCode(it.code, it.title?.trim()?.takeIf { t -> t.isNotEmpty() }) },
            next = page.nextCursor?.takeIf { page.hasMore },
        )
    }

    override suspend fun typeFor(code: String): JacsType? {
        val property = downloads.downloads.value.currentId ?: throw OnlineException(OnlineError.NotFound)
        val mf = api.jacsMasterFormat(property, code).masterFormat ?: return null
        return JacsType(mf.id, mf.systemType, mf.assetType?.takeIf { it.isNotBlank() }, mf.subtype?.takeIf { it.isNotBlank() })
    }

    /** Signed out: the cache is wiped with the rest; what is in memory goes too. */
    override fun clear() {
        loaded = false
        etag = null
        _catalog.value = emptyList()
        _titles.value = emptyMap()
    }

    private companion object {
        const val PAGE_SIZE = 50
        const val CACHE = "jacs_catalog"
    }
}
