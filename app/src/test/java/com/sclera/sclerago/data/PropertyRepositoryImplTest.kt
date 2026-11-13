package com.sclera.sclerago.data

import com.sclera.sclerago.data.local.PropertyRecord
import com.sclera.sclerago.data.remote.PropertyRemoteSource
import com.sclera.sclerago.data.repository.PropertyRepositoryImpl
import com.sclera.sclerago.domain.model.AuthError
import com.sclera.sclerago.domain.model.Property
import com.sclera.sclerago.testutil.FakePropertyRemote
import com.sclera.sclerago.testutil.FakePropertyStore
import com.sclera.sclerago.testutil.record
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** The `properties` table behind the property screens: load, refresh from the API, add, sign-out. */
@OptIn(ExperimentalCoroutinesApi::class)
class PropertyRepositoryImplTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = FakePropertyStore()
    private val remote = FakePropertyRemote()

    private fun TestScope.repo(): PropertyRepositoryImpl = PropertyRepositoryImpl(store, remote, dispatcher).also { advanceUntilIdle() }

    @Test
    fun start_showsWhatIsSaved() = runTest(dispatcher) {
        store.upsert(listOf(record("b", "Beta"), record("a", "alpha")))
        assertEquals(listOf("alpha", "Beta"), repo().properties.value.map { it.name })
    }

    @Test
    fun refresh_replacesTheServerRowsAndKeepsLocalOnes() = runTest(dispatcher) {
        store.upsert(listOf(record("old", "Gone"), record("local", "Mine", synced = false)))
        remote.records = listOf(record("s1", "Tower"), record("s2", "Hub"))
        val repo = repo()
        repo.refresh("org-1")
        advanceUntilIdle()
        assertEquals(listOf("org-1"), remote.fetchedFor)
        assertEquals(listOf("Hub", "Mine", "Tower"), repo.properties.value.map { it.name })
        assertFalse(store.rows.getValue("local").isSynced)
        assertTrue(store.rows.getValue("s1").isSynced)
        assertFalse("old" in store.rows)
    }

    @Test
    fun refresh_failure_keepsTheSavedList() = runTest(dispatcher) {
        store.upsert(listOf(record("s1", "Tower")))
        remote.failure = AuthError.Network()
        val repo = repo()
        try {
            repo.refresh("org-1")
            fail("expected AuthError.Network")
        } catch (_: AuthError.Network) {
            // expected
        }
        assertEquals(listOf("Tower"), repo.properties.value.map { it.name })
    }

    @Test
    fun add_showsAtOnceAndSavesAnUnsyncedRowForTheOrganisation() = runTest(dispatcher) {
        val repo = repo()
        repo.refresh("org-7")
        advanceUntilIdle()
        repo.add(Property("p1", "Depot", gps = false))
        assertEquals(listOf("Depot"), repo.properties.value.map { it.name })
        advanceUntilIdle()
        val row = store.rows.getValue("p1")
        assertFalse(row.isSynced)
        assertEquals("org-7", row.organisationId)
        assertEquals(listOf("Depot"), repo.properties.value.map { it.name })
    }

    @Test
    fun clear_wipesTheTable() = runTest(dispatcher) {
        store.upsert(listOf(record("s1", "Tower"), record("l1", "Mine", synced = false)))
        val repo = repo()
        repo.clear()
        assertTrue(repo.properties.value.isEmpty())
        advanceUntilIdle()
        assertTrue(store.rows.isEmpty())
    }

    @Test
    fun refreshFinishingAfterSignOut_savesNothing() = runTest(dispatcher) {
        lateinit var repo: PropertyRepositoryImpl
        // The sign-out lands while the pages are still being fetched.
        val signsOutDuringFetch = object : PropertyRemoteSource {
            override suspend fun fetchAll(organisationId: String): List<PropertyRecord> {
                repo.clear()
                return listOf(record("s1", "Tower"))
            }
        }
        repo = PropertyRepositoryImpl(store, signsOutDuringFetch, dispatcher)
        advanceUntilIdle()
        repo.refresh("org-1")
        advanceUntilIdle()
        assertTrue(store.rows.isEmpty())
        assertTrue(repo.properties.value.isEmpty())
    }
}
