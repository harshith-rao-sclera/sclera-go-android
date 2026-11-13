package com.sclera.sclerago.presentation.main

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sclera.sclerago.R
import com.sclera.sclerago.data.repository.AssetRepositoryImpl
import com.sclera.sclerago.domain.model.EmptyReason
import com.sclera.sclerago.domain.model.LocValue
import com.sclera.sclerago.domain.model.LocationFilter
import com.sclera.sclerago.domain.model.Source
import com.sclera.sclerago.domain.model.Status
import com.sclera.sclerago.presentation.common.UiText
import com.sclera.sclerago.testutil.assetUseCases
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class AssetListViewModelTest {
    private val repo = AssetRepositoryImpl()

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun vm() = AssetListViewModel(assetUseCases(repo))
    private fun catalogIds() = repo.catalog.value.assetsIn("p1").map { it.id }
    private fun AssetListViewModel.ids(tab: Tab = Tab.ASSETS) = state.value.lists.getValue(tab).items.map { it.id }

    @Test
    fun everyTabHasItsOwnList_ofTheCurrentProperty() {
        val vm = vm()
        assertEquals(catalogIds().toSet(), vm.ids().toSet())
        assertTrue(vm.ids(Tab.BT).isNotEmpty())
        assertTrue(vm.state.value.lists.getValue(Tab.BT).items.all { it.src == Source.BT })
        assertTrue(vm.state.value.lists.getValue(Tab.NET).items.all { it.src == Source.WIFI || it.src == Source.AP })
        vm.setScope("p2", LocationFilter.All)
        assertTrue(vm.ids().isEmpty())
        assertEquals(EmptyReason.ASSETS_TAB, vm.state.value.lists.getValue(Tab.ASSETS).empty)
    }

    @Test
    fun locationFilter_narrowsTheLists() {
        val vm = vm()
        vm.setScope("p1", LocationFilter.Unmapped)
        assertTrue(vm.state.value.lists.getValue(Tab.ASSETS).items.all { it.loc == null })
        vm.setScope("p1", LocationFilter.Specific(listOf("location2")))
        assertTrue(vm.state.value.lists.getValue(Tab.ASSETS).items.all { it.loc == "location2" })
    }

    @Test
    fun search_filtersRowsAndChips_andClosingClearsIt() {
        val vm = vm()
        val all = vm.ids().size
        vm.openSearch()
        vm.setQuery("pump")
        val s = vm.state.value
        assertTrue(s.searchOpen)
        val list = s.lists.getValue(Tab.ASSETS)
        assertTrue(list.items.isNotEmpty() && list.items.size < all)
        assertEquals(list.items.size, list.chips.first().count)
        vm.setQuery("zzz")
        assertEquals(EmptyReason.SEARCH, vm.state.value.lists.getValue(Tab.ASSETS).empty)
        vm.closeSearch()
        assertEquals("", vm.state.value.query)
        assertFalse(vm.state.value.searchOpen)
        assertEquals(all, vm.ids().size)
    }

    @Test
    fun statusFilter_isPerTab() {
        val vm = vm()
        vm.setStatusFilter(Tab.ASSETS, Status.DRAFT)
        assertTrue(vm.state.value.lists.getValue(Tab.ASSETS).items.all { it.st == Status.DRAFT })
        assertEquals(repo.catalog.value.assetsIn("p1").count { it.src == Source.BT }, vm.ids(Tab.BT).size)
        vm.setStatusFilter(Tab.ASSETS, null)
        assertEquals(catalogIds().toSet(), vm.ids().toSet())
    }

    @Test
    fun selection_togglesAndSelectsAllOfATab() {
        val vm = vm()
        val first = vm.ids().first()
        vm.toggle(first)
        assertTrue(vm.state.value.selecting)
        assertEquals(setOf(first), vm.state.value.picked)
        vm.selectAll(Tab.ASSETS)
        assertEquals(vm.ids().toSet(), vm.state.value.picked)
        vm.selectAll(Tab.ASSETS)
        assertTrue(vm.state.value.picked.isEmpty())
        vm.toggle(first)
        vm.clearSelection()
        assertFalse(vm.state.value.selecting)
    }

    @Test
    fun selectionOfAssetsThatLeftTheProperty_isDropped() {
        val vm = vm()
        vm.toggle(vm.ids().first())
        vm.setScope("p2", LocationFilter.All)
        assertTrue(vm.state.value.picked.isEmpty())
    }

    @Test
    fun validateAndSubmit_changeStatusClearTheSelection_andSayHowMany() = runTest {
        val vm = vm()
        val notices = mutableListOf<UiText>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.notices.toList(notices) }
        val (a, b, c) = vm.ids()
        vm.toggle(a)
        vm.toggle(b)
        vm.validateSelected()
        assertEquals(Status.VALIDATED, repo.catalog.value.assetsIn("p1").first { it.id == a }.st)
        assertTrue(vm.state.value.picked.isEmpty())
        vm.toggle(c)
        vm.submitSelected()
        assertEquals(Status.SUBMITTED, repo.catalog.value.assetsIn("p1").first { it.id == c }.st)
        assertEquals(listOf(UiText.Plural(R.plurals.assets_validated, 2), UiText.Plural(R.plurals.assets_submitted, 1)), notices)
        // Nothing selected: nothing happens, nothing is said.
        vm.validateSelected()
        assertEquals(2, notices.size)
        job.cancel()
    }

    @Test
    fun delete_asksFirst_thenRemovesTheSelection() = runTest {
        val vm = vm()
        val notices = mutableListOf<UiText>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.notices.toList(notices) }
        val target = vm.ids().first()
        vm.toggle(target)
        vm.askDelete()
        assertTrue(vm.state.value.confirmDelete)
        vm.cancelDelete()
        assertFalse(vm.state.value.confirmDelete)
        assertTrue(target in catalogIds())
        vm.askDelete()
        vm.deleteSelected()
        assertFalse(target in catalogIds())
        assertFalse(vm.state.value.confirmDelete)
        assertTrue(vm.state.value.picked.isEmpty())
        assertEquals(listOf<UiText>(UiText.Plural(R.plurals.assets_deleted, 1)), notices)
        job.cancel()
    }

    @Test
    fun assign_givesTheSelectionALocation() = runTest {
        val vm = vm()
        val notices = mutableListOf<UiText>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.notices.toList(notices) }
        val target = vm.ids().first()
        vm.toggle(target)
        vm.assignSelected(LocValue("lx", "lx · floor 2"), "Plant room")
        val moved = repo.catalog.value.assetsIn("p1").first { it.id == target }
        assertEquals("lx", moved.loc)
        assertEquals(listOf<UiText>(UiText.Plural(R.plurals.assets_assigned, 1, listOf(1, "Plant room"))), notices)
        assertTrue(vm.state.value.picked.isEmpty())
        job.cancel()
    }
}
