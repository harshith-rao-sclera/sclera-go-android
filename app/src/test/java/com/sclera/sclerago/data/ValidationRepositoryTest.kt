package com.sclera.sclerago.data

import com.sclera.sclerago.data.repository.ValidationRepositoryImpl
import com.sclera.sclerago.domain.validation.DEFAULT_SAVED
import com.sclera.sclerago.domain.validation.ValJob
import com.sclera.sclerago.testutil.FakeRetainedLifecycle
import com.sclera.sclerago.testutil.SequentialIdGenerator
import com.sclera.sclerago.testutil.asset
import com.sclera.sclerago.testutil.checkInput
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ValidationRepositoryTest {
    private val dispatcher = StandardTestDispatcher()
    private val lifecycle = FakeRetainedLifecycle()
    private val repo = ValidationRepositoryImpl(lifecycle, dispatcher, SequentialIdGenerator())
    private val assets = listOf(asset(1) to checkInput(mapOf("name" to "Pump 1")), asset(2, "Pump 2") to checkInput())

    @Test
    fun sourceName_and_rules_resolveTemplatesAndSavedOnes() {
        assertEquals("Quick check", repo.sourceName("quick"))
        assertEquals("Field Validation", repo.sourceName("sv1"))
        assertEquals("Validation", repo.sourceName("nope"))
        assertEquals(9, repo.sourceRules("quick").size)
        assertEquals(DEFAULT_SAVED.first().ruleIds.size, repo.sourceRules("sv1").size)
        assertTrue(repo.sourceRules("nope").isEmpty())
    }

    @Test
    fun instantJob_finishesAtOnceAndReportsResults() {
        var done: ValJob? = null
        repo.start("quick", listOf("1"), "All assets", "p1", { assets }, 5L, instant = true) { done = it }
        val job = repo.jobs.value.single()
        assertFalse(job.running)
        assertEquals(1, job.results.size)
        assertEquals(job, done)
        assertEquals(5L, job.startedAt)
    }

    @Test
    fun backgroundJob_runsThenFinishes() = runTest(dispatcher) {
        repo.start("quick", listOf("1", "2"), "All", "p1", { assets }, 0L, instant = false) {}
        assertTrue(repo.jobs.value.single().running)
        advanceUntilIdle()
        assertFalse(repo.jobs.value.single().running)
        assertEquals(2, repo.jobs.value.single().results.size)
    }

    @Test
    fun newestJobComesFirst() {
        repo.start("quick", listOf("1"), "a", "p1", { assets }, 1L, instant = true) {}
        repo.start("specs", listOf("1"), "b", "p1", { assets }, 2L, instant = true) {}
        assertEquals(listOf("specs", "quick"), repo.jobs.value.map { it.sourceId })
    }

    @Test
    fun save_createsUpdatesAndRejectsIncomplete() {
        assertNull(repo.save(null, " ", "", listOf("mandatory")))
        assertNull(repo.save(null, "Name", "", emptyList()))
        val id = repo.save(null, " Mine ", " d ", listOf("mandatory", "mandatory", "bogus", "location"))!!
        val saved = repo.saved.value.first { it.id == id }
        assertEquals("Mine", saved.name)
        assertEquals(listOf("mandatory", "location"), saved.ruleIds)
        assertEquals("d", saved.description)
        assertEquals(id, repo.save(id, "Renamed", "", listOf("photo")))
        assertEquals(listOf("photo"), repo.saved.value.first { it.id == id }.ruleIds)
    }

    @Test
    fun delete_removesASavedValidation() {
        repo.delete("sv1")
        assertTrue(repo.saved.value.isEmpty())
    }

    @Test
    fun clear_dropsJobsAndRestoresDefaults() {
        repo.start("quick", listOf("1"), "a", "p1", { assets }, 1L, instant = true) {}
        repo.delete("sv1")
        repo.clear()
        assertTrue(repo.jobs.value.isEmpty())
        assertEquals(DEFAULT_SAVED, repo.saved.value)
        repo.start("quick", listOf("1"), "a", "p1", { assets }, 1L, instant = true) {}
        assertEquals("1", repo.jobs.value.single().id)
    }

    @Test
    fun clearingTheActivity_cancelsPendingJobs() = runTest(dispatcher) {
        repo.start("quick", listOf("1"), "a", "p1", { assets }, 1L, instant = false) {}
        lifecycle.clear()
        advanceUntilIdle()
        assertTrue(repo.jobs.value.single().running)
    }
}
