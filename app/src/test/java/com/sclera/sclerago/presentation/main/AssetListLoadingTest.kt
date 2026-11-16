package com.sclera.sclerago.presentation.main

import com.sclera.sclerago.domain.model.AssetCatalog
import com.sclera.sclerago.presentation.preview.SAMPLE_ASSETS
import com.sclera.sclerago.testutil.FakeCatalogRepository
import com.sclera.sclerago.testutil.assetListViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** The asset lists' loader: shown while the property's data is being opened, then the assets or the empty card. */
@OptIn(ExperimentalCoroutinesApi::class)
class AssetListLoadingTest {
    private val assets = SAMPLE_ASSETS.filter { it.propertyId == "p1" }

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun whileThePropertysDataIsBeingOpened_itIsLoading() {
        val vm = assetListViewModel(FakeCatalogRepository(AssetCatalog(loaded = false)))
        assertTrue(vm.state.value.loading)
        assertTrue(vm.state.value.lists[Tab.ASSETS]?.items.orEmpty().isEmpty())
    }

    @Test
    fun onceOpen_theAssetsShow_andLoadingEnds() {
        val repo = FakeCatalogRepository(AssetCatalog(loaded = false))
        val vm = assetListViewModel(repo)
        repo.catalog.value = AssetCatalog(assets, loaded = true)
        assertFalse(vm.state.value.loading)
        assertEquals(assets.size, vm.state.value.lists[Tab.ASSETS]?.items?.size)
    }

    @Test
    fun anOpenPropertyWithNoAssets_isEmpty_notLoading() {
        val vm = assetListViewModel(FakeCatalogRepository(AssetCatalog(emptyList(), loaded = true)))
        assertFalse(vm.state.value.loading)
        assertTrue(vm.state.value.lists[Tab.ASSETS]?.items.orEmpty().isEmpty())
    }

    @Test
    fun switchingProperty_showsTheLoaderAgain_untilTheNewDataIsOpen() {
        val repo = FakeCatalogRepository(AssetCatalog(assets, loaded = true))
        val vm = assetListViewModel(repo)
        repo.catalog.value = AssetCatalog(loaded = false)
        assertTrue(vm.state.value.loading)
        repo.catalog.value = AssetCatalog(assets.take(1), loaded = true)
        assertFalse(vm.state.value.loading)
        assertEquals(1, vm.state.value.lists[Tab.ASSETS]?.items?.size)
    }

    @Test
    fun aCatalogBuiltWithoutSayingSo_countsAsLoaded() = assertTrue(AssetCatalog().loaded)
}
