package com.sclera.sclerago.presentation.property

import com.sclera.sclerago.domain.model.OnlineError
import com.sclera.sclerago.domain.model.OnlineException
import com.sclera.sclerago.domain.model.Property
import com.sclera.sclerago.testutil.GatedPropertyRepository
import com.sclera.sclerago.testutil.propertyListViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** The Property list's loader: shown while the first server read runs with nothing saved, then the list or "none". */
@OptIn(ExperimentalCoroutinesApi::class)
class PropertyListLoadingTest {
    private val two = listOf(Property("p1", "Harbour Point", false), Property("p2", "Northgate Hub", false))

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun whileTheFirstReadRuns_withNothingSaved_itIsLoading() {
        val repo = GatedPropertyRepository()
        val vm = propertyListViewModel(repo)
        assertEquals(1, repo.refreshes)
        assertTrue(vm.state.value.loading)
        assertTrue(vm.state.value.rows.isEmpty())
    }

    @Test
    fun whenTheReadReturnsProperties_theyShowAndLoadingEnds() {
        val repo = GatedPropertyRepository()
        val vm = propertyListViewModel(repo)
        repo.finish(two)
        val s = vm.state.value
        assertFalse(s.loading)
        assertEquals(listOf("p1", "p2"), s.rows.map { it.property.id })
        assertEquals(2, s.propertyTotal)
    }

    @Test
    fun whenTheReadReturnsNothing_itIsEmpty_notLoading() {
        val repo = GatedPropertyRepository()
        val vm = propertyListViewModel(repo)
        repo.finish(emptyList())
        assertFalse(vm.state.value.loading)
        assertTrue(vm.state.value.rows.isEmpty())
        assertEquals(0, vm.state.value.propertyTotal)
    }

    @Test
    fun whenTheReadFails_loadingEnds_andTheUserIsTold() {
        val repo = GatedPropertyRepository()
        val vm = propertyListViewModel(repo)
        repo.fail(OnlineException(OnlineError.SessionExpired))
        assertFalse(vm.state.value.loading)
        assertNotNull(vm.state.value.notice)
    }

    @Test
    fun offline_loadingEnds_quietly() {
        val repo = GatedPropertyRepository()
        val vm = propertyListViewModel(repo)
        repo.fail(OnlineException(OnlineError.Offline))
        assertFalse(vm.state.value.loading)
        assertNull(vm.state.value.notice)
    }

    @Test
    fun savedProperties_showStraightAway_whileTheReadRuns() {
        val repo = GatedPropertyRepository(two)
        val vm = propertyListViewModel(repo)
        assertFalse(vm.state.value.loading)
        assertEquals(2, vm.state.value.rows.size)
    }

    @Test
    fun leavingTheScreen_doesNotStartTheLoaderAgain() {
        val repo = GatedPropertyRepository()
        val vm = propertyListViewModel(repo)
        repo.finish(emptyList())
        vm.reset()
        assertFalse(vm.state.value.loading)
    }

    @Test
    fun leavingTheScreen_whileTheReadRuns_keepsLoading() {
        val repo = GatedPropertyRepository()
        val vm = propertyListViewModel(repo)
        vm.reset()
        assertTrue(vm.state.value.loading)
        repo.finish(two)
        assertFalse(vm.state.value.loading)
    }
}
