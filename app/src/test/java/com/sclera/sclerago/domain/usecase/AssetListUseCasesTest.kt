package com.sclera.sclerago.domain.usecase

import com.sclera.sclerago.domain.model.AssetListQuery
import com.sclera.sclerago.domain.model.EmptyReason
import com.sclera.sclerago.domain.model.LocationFilter
import com.sclera.sclerago.domain.model.Source
import com.sclera.sclerago.domain.model.Status
import com.sclera.sclerago.domain.usecase.asset.BuildAssetListUseCase
import com.sclera.sclerago.domain.usecase.asset.SelectAllUseCase
import com.sclera.sclerago.domain.usecase.asset.ToggleSelectionUseCase
import com.sclera.sclerago.testutil.asset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AssetListUseCasesTest {
    private val build = BuildAssetListUseCase()
    private val assets = listOf(
        asset(1, "Fire pump FP-1", st = Status.SUBMITTED, src = Source.FILE, loc = "L1"),
        asset(2, "Booster pump BP-2", st = Status.DRAFT, src = Source.CAMERA, loc = "L2"),
        asset(3, "Chiller 2", st = Status.DRAFT, src = Source.BT, loc = null),
        asset(4, "Reception PC", st = Status.VALIDATED, src = Source.WIFI, loc = "L1"),
        asset(5, "Access point", st = Status.INPUT, src = Source.AP, loc = "L1"),
    )

    private fun ids(q: AssetListQuery) = build(assets, q).items.map { it.id }

    @Test
    fun noFilters_showEveryAsset_andCountEachStatus() {
        val data = build(assets, AssetListQuery(sources = null))
        assertEquals(listOf(1, 2, 3, 4, 5), data.items.map { it.id })
        assertEquals(5, data.chips.first().count)
        assertTrue(data.chips.first().selected)
        assertEquals(2, data.chips.first { it.status == Status.DRAFT }.count)
        // "Input needed" has no chip of its own.
        assertTrue(data.chips.none { it.status == Status.INPUT })
    }

    @Test
    fun tabsShowOnlyTheirSources() {
        assertEquals(listOf(3), ids(AssetListQuery(setOf(Source.BT))))
        assertEquals(listOf(4, 5), ids(AssetListQuery(setOf(Source.WIFI, Source.AP))))
        assertEquals(listOf(1), ids(AssetListQuery(setOf(Source.FILE))))
    }

    @Test
    fun locationFilter_picksChosenOrUnmappedAssets() {
        assertEquals(listOf(1, 4, 5), ids(AssetListQuery(null, LocationFilter.Specific(listOf("L1")))))
        assertEquals(listOf(1, 2, 4, 5), ids(AssetListQuery(null, LocationFilter.Specific(listOf("L1", "L2")))))
        assertEquals(listOf(3), ids(AssetListQuery(null, LocationFilter.Unmapped)))
    }

    @Test
    fun search_matchesNameAndDetail_ignoringCase_andChipCountsFollowIt() {
        val data = build(assets, AssetListQuery(null, text = "  PUMP "))
        assertEquals(listOf(1, 2), data.items.map { it.id })
        assertEquals(2, data.chips.first().count)
        assertEquals(1, data.chips.first { it.status == Status.SUBMITTED }.count)
        assertEquals(1, data.chips.first { it.status == Status.DRAFT }.count)
        assertEquals(0, data.chips.first { it.status == Status.VALIDATED }.count)
        // "detail" is the second line of every test asset.
        assertEquals(5, ids(AssetListQuery(null, text = "detail")).size)
    }

    @Test
    fun statusFilter_narrowsRowsButNotTheOtherChipCounts() {
        val data = build(assets, AssetListQuery(null, status = Status.DRAFT))
        assertEquals(listOf(2, 3), data.items.map { it.id })
        assertEquals(5, data.chips.first().count)
        assertTrue(data.chips.first { it.status == Status.DRAFT }.selected)
        assertTrue(!data.chips.first().selected)
    }

    @Test
    fun emptyReason_saysWhyThereIsNothing() {
        assertEquals(EmptyReason.SEARCH, build(assets, AssetListQuery(null, text = "zzz")).empty)
        assertEquals(EmptyReason.STATUS, build(assets, AssetListQuery(null, status = Status.EXCEPTION)).empty)
        assertEquals(EmptyReason.ASSETS_TAB, build(emptyList(), AssetListQuery(null)).empty)
        assertEquals(EmptyReason.SOURCE_TAB, build(emptyList(), AssetListQuery(setOf(Source.FILE))).empty)
    }

    @Test
    fun toggleSelection_picksAndUnpicks() {
        val toggle = ToggleSelectionUseCase()
        assertEquals(setOf("1"), toggle(emptySet(), "1"))
        assertEquals(setOf("1", "2"), toggle(setOf("1"), "2"))
        assertEquals(emptySet<String>(), toggle(setOf("1"), "1"))
    }

    @Test
    fun selectAll_picksEverythingVisible_orClearsWhenAllArePicked() {
        val selectAll = SelectAllUseCase()
        assertEquals(setOf("5", "1", "2"), selectAll(setOf("5"), listOf("1", "2")))
        assertEquals(emptySet<String>(), selectAll(setOf("1", "2", "5"), listOf("1", "2")))
        assertEquals(emptySet<String>(), selectAll(emptySet(), emptyList()))
    }
}
