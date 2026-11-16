package com.sclera.sclerago.data

import com.sclera.sclerago.data.repository.AssetSync
import com.sclera.sclerago.data.repository.SyncManagerPropertyDownloads
import com.sclera.sclerago.data.repository.SyncSnapshot
import com.sclera.sclerago.domain.model.PropertyDownload
import com.sclera.sclerago.domain.system.TimeProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Following the asset sync's progress for the Property list's download states. */
@OptIn(ExperimentalCoroutinesApi::class)
class SyncManagerPropertyDownloadsTest {
    private val dispatcher = StandardTestDispatcher()

    /** A sync the test moves along by setting [state]; [failOnDownload] makes starting it throw. */
    private class FakeSync : AssetSync {
        var state: SyncSnapshot? = null
        val downloads = mutableListOf<String>()
        var failOnDownload = false
        override fun snapshot() = state
        override fun download(propertyId: String) {
            if (failOnDownload) error("boom")
            downloads += propertyId
        }
    }

    private val sync = FakeSync()

    private fun TestScope.repo() = SyncManagerPropertyDownloads(sync, TimeProvider { testScheduler.currentTime }, dispatcher)

    private fun snap(id: String, status: Int, progress: Int = 0, mb: Double = 0.0, total: Double = 0.0) = SyncSnapshot(id, status, progress, mb, total)

    @Test
    fun download_reportsProgress_thenFinishes_andIsDownloaded() = runTest(dispatcher) {
        val repo = repo()
        repo.start("p5")
        assertEquals(PropertyDownload.Running(0f, 0.0, 0.0), repo.downloads.value.active["p5"])
        runCurrent()
        assertEquals(listOf("p5"), sync.downloads)

        sync.state = snap("p5", status = 1, progress = 40, mb = 2.0, total = 5.0)
        advanceTimeBy(SyncManagerPropertyDownloads.POLL_MS + 1)
        assertEquals(PropertyDownload.Running(0.4f, 2.0, 5.0), repo.downloads.value.active["p5"])

        sync.state = snap("p5", status = 2)
        advanceTimeBy(SyncManagerPropertyDownloads.POLL_MS + 1)
        assertEquals(PropertyDownload.Finished, repo.downloads.value.active["p5"])
        assertEquals("p5", repo.downloads.value.downloadedId)

        advanceTimeBy(SyncManagerPropertyDownloads.FINISHED_SHOWN_MS + 1)
        assertNull(repo.downloads.value.active["p5"]) // the tick goes; the row shows the chevron
        assertEquals("p5", repo.downloads.value.downloadedId)
    }

    @Test
    fun aFailedSync_isFailed_andCanBeStartedAgain() = runTest(dispatcher) {
        val repo = repo()
        repo.start("p5")
        runCurrent()
        sync.state = snap("p5", status = 3)
        advanceTimeBy(SyncManagerPropertyDownloads.POLL_MS + 1)
        assertEquals(PropertyDownload.Failed, repo.downloads.value.active["p5"])
        repo.start("p5")
        runCurrent()
        assertEquals(listOf("p5", "p5"), sync.downloads)
    }

    @Test
    fun aSyncThatNeverStarts_failsAfterTheTimeout() = runTest(dispatcher) {
        val repo = repo()
        repo.start("p5")
        advanceTimeBy(SyncManagerPropertyDownloads.START_TIMEOUT_MS - 1_000)
        assertTrue(repo.downloads.value.active["p5"] is PropertyDownload.Running)
        advanceTimeBy(2_000)
        assertEquals(PropertyDownload.Failed, repo.downloads.value.active["p5"])
    }

    @Test
    fun theOldPropertysState_doesNotCountForTheNewOne() = runTest(dispatcher) {
        val repo = repo()
        sync.state = snap("p1", status = 2) // the property downloaded before
        repo.start("p5")
        advanceTimeBy(SyncManagerPropertyDownloads.POLL_MS * 3)
        assertTrue(repo.downloads.value.active["p5"] is PropertyDownload.Running)
        assertNull(repo.downloads.value.downloadedId)
    }

    @Test
    fun startingWhileRunning_isIgnored() = runTest(dispatcher) {
        val repo = repo()
        repo.start("p5")
        runCurrent()
        repo.start("p5")
        runCurrent()
        assertEquals(listOf("p5"), sync.downloads)
    }

    @Test
    fun aSyncThatThrows_isFailed() = runTest(dispatcher) {
        sync.failOnDownload = true
        val repo = repo()
        repo.start("p5")
        runCurrent()
        assertEquals(PropertyDownload.Failed, repo.downloads.value.active["p5"])
    }

    @Test
    fun refresh_readsTheDownloadedProperty() = runTest(dispatcher) {
        val repo = repo()
        repo.refresh()
        runCurrent()
        assertNull(repo.downloads.value.downloadedId) // asset sync not started yet
        sync.state = snap("p3", status = 1)
        repo.refresh()
        runCurrent()
        assertNull(repo.downloads.value.downloadedId) // still downloading
        sync.state = snap("p3", status = 2)
        repo.refresh()
        runCurrent()
        assertEquals("p3", repo.downloads.value.downloadedId)
    }
}
