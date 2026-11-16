package com.sclera.sclerago.presentation.access

import com.sclera.sclerago.domain.model.Group
import com.sclera.sclerago.domain.model.OnlineError
import com.sclera.sclerago.domain.model.OnlineException
import com.sclera.sclerago.domain.model.Permission
import com.sclera.sclerago.domain.model.Person
import com.sclera.sclerago.testutil.GatedAccessRepository
import com.sclera.sclerago.testutil.accessViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** The Users page's loader, per tab: shown while the first server read runs and the tab has nothing saved. */
@OptIn(ExperimentalCoroutinesApi::class)
class AccessListLoadingTest {
    private val collectors = Group("g1", "Collectors", "", setOf(Permission.COLLECT))
    private val ana = Person("u1", "ana@x.com", "g1", allProperties = true)

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun whileTheFirstReadRuns_withNothingSaved_bothTabsAreLoading() {
        val vm = accessViewModel(GatedAccessRepository())
        assertTrue(vm.listState(null).loading)
        vm.setTab(AccessTab.GROUPS)
        assertTrue(vm.listState(null).loading)
    }

    @Test
    fun whenTheReadReturns_thePeopleShow_andLoadingEnds() {
        val repo = GatedAccessRepository()
        val vm = accessViewModel(repo)
        repo.finish(listOf(collectors), listOf(ana))
        val list = vm.listState(null)
        assertFalse(list.loading)
        assertEquals(listOf("ana@x.com"), list.people.map { it.person.email })
        assertEquals(1, list.groupsTotal)
    }

    @Test
    fun whenTheReadReturnsNothing_theTabsAreEmpty_notLoading() {
        val repo = GatedAccessRepository()
        val vm = accessViewModel(repo)
        repo.finish(emptyList(), emptyList())
        assertFalse(vm.listState(null).loading)
        vm.setTab(AccessTab.GROUPS)
        val groups = vm.listState(null)
        assertFalse(groups.loading)
        assertTrue(groups.groups.isEmpty())
    }

    @Test
    fun whenTheReadFails_loadingEnds_andTheUserIsTold() {
        val repo = GatedAccessRepository()
        val vm = accessViewModel(repo)
        repo.fail(OnlineException(OnlineError.SessionExpired))
        assertFalse(vm.listState(null).loading)
        assertNotNull(vm.state.notice)
    }

    @Test
    fun offline_loadingEnds() {
        val repo = GatedAccessRepository()
        val vm = accessViewModel(repo)
        repo.fail(OnlineException(OnlineError.Offline))
        assertFalse(vm.listState(null).loading)
    }

    @Test
    fun savedPeople_showStraightAway_butAnEmptyGroupsTabStillWaits() {
        val vm = accessViewModel(GatedAccessRepository(groups = emptyList(), people = listOf(ana)))
        assertFalse(vm.listState(null).loading)
        vm.setTab(AccessTab.GROUPS)
        assertTrue(vm.listState(null).loading)
    }
}
