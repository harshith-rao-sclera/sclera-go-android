package com.sclera.sclerago.data

import com.sclera.sclerago.data.local.EncryptedCache
import com.sclera.sclerago.data.repository.SyncManagerPropertyDownloads
import com.sclera.sclerago.domain.model.PropertyDownload
import com.sclera.sclerago.domain.sync.OfflineSync
import com.sclera.sclerago.domain.sync.SyncFailure
import com.sclera.sclerago.domain.sync.SyncState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Property downloads over the offline sync: several downloaded (and downloading) at once, one current. */
@OptIn(ExperimentalCoroutinesApi::class)
class SyncManagerPropertyDownloadsTest {
    private val dispatcher = StandardTestDispatcher()

    /** A sync the test moves along per property by calling [set]. */
    private class FakeSync : OfflineSync {
        private val flow = MutableStateFlow<Map<String, SyncState>>(emptyMap())
        override val states: StateFlow<Map<String, SyncState>> = flow
        val downloads = mutableListOf<String>()
        val selected = mutableListOf<String?>()
        var onDevice = emptySet<String>()

        override fun download(propertyId: String) {
            downloads += propertyId
            set(propertyId, SyncState.Downloading())
        }

        override fun select(propertyId: String?) {
            selected += propertyId
        }

        override fun downloadedProperties(): Set<String> = onDevice
        override fun reopenAll() = Unit
        override fun stop() {
            flow.value = emptyMap()
        }

        fun set(propertyId: String, state: SyncState) = flow.update { it + (propertyId to state) }
    }

    private class MemoryCache(initial: Map<String, String> = emptyMap()) : EncryptedCache {
        val files = initial.toMutableMap()
        override fun read(name: String): String? = files[name]
        override fun write(name: String, text: String) {
            files[name] = text
        }
    }

    private val sync = FakeSync()
    private var cache = MemoryCache()

    private fun TestScope.repo() = SyncManagerPropertyDownloads(sync, cache, dispatcher).also { runCurrent() }

    /** Takes [id] through a download to the device. */
    private fun TestScope.finish(id: String) {
        sync.set(id, SyncState.Downloading(40, 2.0, 5.0))
        runCurrent()
        sync.onDevice = sync.onDevice + id
        sync.set(id, SyncState.Ready)
        runCurrent()
    }

    @Test
    fun atStart_theSavedCurrentPropertyAndWhatIsOnTheDeviceAreRead() = runTest(dispatcher) {
        cache = MemoryCache(mapOf(RECENT to "p1"))
        sync.onDevice = setOf("p1", "p4")
        val d = repo().downloads.value
        assertTrue(d.restored)
        assertEquals("p1", d.currentId)
        assertEquals(setOf("p1", "p4"), d.downloadedIds)
    }

    @Test
    fun aDownload_reportsProgress_thenIsDownloaded_andTheFirstOneBecomesCurrent() = runTest(dispatcher) {
        val repo = repo()
        repo.start("p5")
        assertEquals(PropertyDownload.Running(0f, 0.0, 0.0), repo.downloads.value.active["p5"])
        assertEquals(listOf("p5"), sync.downloads)

        sync.set("p5", SyncState.Downloading(40, 2.0, 5.0))
        runCurrent()
        assertEquals(PropertyDownload.Running(0.4f, 2.0, 5.0), repo.downloads.value.active["p5"])
        sync.set("p5", SyncState.Unpacking)
        runCurrent()
        assertEquals(PropertyDownload.Unpacking, repo.downloads.value.active["p5"])

        sync.onDevice = setOf("p5")
        sync.set("p5", SyncState.Ready)
        runCurrent()
        val d = repo.downloads.value
        assertEquals(PropertyDownload.Finished, d.active["p5"])
        assertEquals(setOf("p5"), d.downloadedIds)
        assertEquals("p5", d.currentId) // nothing was open, so the first download opens
        assertEquals(listOf<String?>("p5"), sync.selected)
        advanceUntilIdle()
        assertNull(repo.downloads.value.active["p5"]) // the tick goes; the row shows the chevron
        assertEquals("p5", cache.files[RECENT])
    }

    @Test
    fun severalDownloads_runAtOnce_andTheCurrentPropertyStays() = runTest(dispatcher) {
        cache = MemoryCache(mapOf(RECENT to "p1"))
        sync.onDevice = setOf("p1")
        val repo = repo()
        repo.start("p5")
        repo.start("p6")
        assertTrue(repo.downloads.value.active["p5"] is PropertyDownload.Running)
        assertTrue(repo.downloads.value.active["p6"] is PropertyDownload.Running)

        finish("p6")
        assertEquals(setOf("p1", "p6"), repo.downloads.value.downloadedIds)
        assertEquals("p1", repo.downloads.value.currentId) // a finished download doesn't switch the open property
        assertTrue(repo.downloads.value.active["p5"] is PropertyDownload.Running) // still going

        finish("p5")
        assertEquals(setOf("p1", "p5", "p6"), repo.downloads.value.downloadedIds)
        assertEquals("p1", repo.downloads.value.currentId)
        assertTrue(sync.selected.isEmpty())
    }

    @Test
    fun startingWhileRunning_isIgnored() = runTest(dispatcher) {
        val repo = repo()
        repo.start("p5")
        repo.start("p5")
        assertEquals(listOf("p5"), sync.downloads)
    }

    @Test
    fun aFailedDownload_isFailed_andCanBeStartedAgain() = runTest(dispatcher) {
        val repo = repo()
        repo.start("p5")
        sync.set("p5", SyncState.Downloading(10, 1.0, 5.0))
        runCurrent()
        sync.set("p5", SyncState.Failed(SyncFailure.NO_NETWORK))
        runCurrent()
        assertEquals(PropertyDownload.Failed, repo.downloads.value.active["p5"])
        assertTrue(repo.downloads.value.downloadedIds.isEmpty())
        repo.start("p5")
        assertEquals(listOf("p5", "p5"), sync.downloads)
    }

    @Test
    fun aSyncThatNeverStarts_failsAfterTheTimeout() = runTest(dispatcher) {
        val repo = repo()
        repo.start("p5")
        advanceTimeBy(START_TIMEOUT_MS - 1_000)
        assertTrue(repo.downloads.value.active["p5"] is PropertyDownload.Running)
        advanceTimeBy(2_000)
        assertEquals(PropertyDownload.Failed, repo.downloads.value.active["p5"])
    }

    @Test
    fun openingADownloadedProperty_switchesStraightAway_withoutDownloadingIt() = runTest(dispatcher) {
        cache = MemoryCache(mapOf(RECENT to "p1"))
        sync.onDevice = setOf("p1", "p4")
        val repo = repo()
        repo.open("p4")
        assertEquals("p4", repo.downloads.value.currentId)
        assertEquals(listOf<String?>("p4"), sync.selected)
        assertEquals(listOf("p4"), sync.downloads) // only to make sure it syncs; the engine reuses the database
        assertNull(repo.downloads.value.active["p4"]) // no progress shown for opening
        sync.set("p4", SyncState.Ready)
        advanceUntilIdle()
        assertEquals("p4", cache.files[RECENT])
        assertEquals("p4", repo.downloads.value.currentId)
    }

    @Test
    fun openingAPropertyThatIsNotDownloaded_doesNothing() = runTest(dispatcher) {
        cache = MemoryCache(mapOf(RECENT to "p1"))
        sync.onDevice = setOf("p1")
        val repo = repo()
        repo.open("p9")
        assertEquals("p1", repo.downloads.value.currentId)
        assertTrue(sync.selected.isEmpty())
        assertTrue(sync.downloads.isEmpty())
    }

    @Test
    fun aFailedOpen_whoseDataIsGone_isNoLongerCurrent() = runTest(dispatcher) {
        cache = MemoryCache(mapOf(RECENT to "p1"))
        sync.onDevice = setOf("p1", "p4")
        val repo = repo()
        repo.open("p4")
        sync.onDevice = setOf("p1") // its database went missing
        sync.set("p4", SyncState.Failed(SyncFailure.SERVER_ERROR))
        runCurrent()
        val d = repo.downloads.value
        assertNull(d.currentId)
        assertEquals(setOf("p1"), d.downloadedIds)
        assertEquals(PropertyDownload.Failed, d.active["p4"])
        assertEquals(listOf<String?>("p4", null), sync.selected)
    }

    @Test
    fun resume_restartsEveryDownloadedProperty_andReopensTheCurrentOne_quietly() = runTest(dispatcher) {
        cache = MemoryCache(mapOf(RECENT to "p1"))
        sync.onDevice = setOf("p1", "p4")
        val repo = repo()
        repo.resume()
        runCurrent()
        assertEquals(setOf("p1", "p4"), sync.downloads.toSet())
        assertEquals(listOf<String?>("p1"), sync.selected)
        sync.set("p1", SyncState.Failed(SyncFailure.SESSION_EXPIRED))
        runCurrent()
        assertTrue(repo.downloads.value.active.isEmpty()) // resuming shows no progress and no failure
        assertEquals("p1", repo.downloads.value.currentId)
    }

    @Test
    fun clear_forgetsEverything() = runTest(dispatcher) {
        val repo = repo()
        repo.start("p5")
        finish("p5")
        repo.clear()
        advanceUntilIdle()
        val d = repo.downloads.value
        assertNull(d.currentId)
        assertTrue(d.downloadedIds.isEmpty() && d.active.isEmpty())
        assertTrue(d.restored)
    }

    private companion object {
        const val RECENT = "recent_property"
        const val START_TIMEOUT_MS = 30_000L
    }
}
