package com.sclera.sclerago.domain.usecase

import com.sclera.sclerago.domain.model.Asset
import com.sclera.sclerago.domain.model.AssetListQuery
import com.sclera.sclerago.domain.model.EmptyReason
import com.sclera.sclerago.domain.model.LocationFilter
import com.sclera.sclerago.domain.model.NodeKind
import com.sclera.sclerago.domain.model.PlaceScope
import com.sclera.sclerago.domain.model.placeScopeOf
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
    // Placed by id, as the offline data has them: L1 and L2 are locations on floor F1 of building B1; asset 3 is nowhere.
    // The search text is what the data layer builds (name, make, model, serial number, description, answers), lowercase.
    private val assets = listOf(
        asset(1, "Fire pump FP-1", st = Status.SUBMITTED, src = Source.FILE, loc = "L1").placed("L1").searching("fire pump fp-1 sn-001"),
        asset(2, "Booster pump BP-2", st = Status.DRAFT, src = Source.CAMERA, loc = "L2").placed("L2").searching("booster pump bp-2 sn-002"),
        asset(3, "Chiller 2", st = Status.DRAFT, src = Source.BT, loc = null).searching("chiller 2 sn-003 rooftop plant"),
        asset(4, "Reception PC", st = Status.VALIDATED, src = Source.WIFI, loc = "L1").placed("L1").searching("reception pc sn-004"),
        asset(5, "Access point", st = Status.INPUT, src = Source.AP, loc = "L1").placed("L1").searching("access point sn-005"),
    )

    private fun Asset.placed(location: String?, floor: String? = "F1", building: String? = "B1") =
        copy(locationId = location, floorId = floor, buildingId = building)

    private fun Asset.searching(text: String) = copy(searchText = text)

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
    fun locationFilter_picksChosenOrUnmappedAssets_byId() {
        assertEquals(listOf(1, 4, 5), ids(AssetListQuery(null, LocationFilter.Specific(PlaceScope(locationIds = listOf("L1"))))))
        assertEquals(listOf(1, 2, 4, 5), ids(AssetListQuery(null, LocationFilter.Specific(PlaceScope(locationIds = listOf("L1", "L2"))))))
        assertEquals(listOf(1, 2, 4, 5), ids(AssetListQuery(null, LocationFilter.Specific(placeScopeOf(NodeKind.FLOOR, "F1")))))
        assertEquals(listOf(3), ids(AssetListQuery(null, LocationFilter.Unmapped)))
    }

    @Test
    fun locationFilter_keepsAssetsOnTheBuildingOrFloor_andTellsSameNamedLocationsApart() {
        val list = listOf(
            asset(1, loc = "Lobby").placed("L1"),
            // Another floor's location with the same name.
            asset(2, loc = "Lobby").placed("L9", floor = "F2"),
            // On the floor, in no location.
            asset(3, loc = null).placed(null),
            // On the building, on no floor.
            asset(4, loc = null).placed(null, floor = null),
            asset(5, loc = null),
        )
        fun pick(f: LocationFilter) = build(list, AssetListQuery(null, f)).items.map { it.id }
        assertEquals(listOf(1), pick(LocationFilter.Specific(placeScopeOf(NodeKind.LOCATION, "L1"))))
        assertEquals(listOf(1, 3), pick(LocationFilter.Specific(placeScopeOf(NodeKind.FLOOR, "F1"))))
        assertEquals(listOf(1, 2, 3, 4), pick(LocationFilter.Specific(placeScopeOf(NodeKind.BUILDING, "B1"))))
        assertEquals(listOf(5), pick(LocationFilter.Unmapped))
    }

    @Test
    fun search_matchesTheSearchText_ignoringCase_andChipCountsFollowIt() {
        val data = build(assets, AssetListQuery(null, text = "  PUMP "))
        assertEquals(listOf(1, 2), data.items.map { it.id })
        assertEquals(2, data.chips.first().count)
        assertEquals(1, data.chips.first { it.status == Status.SUBMITTED }.count)
        assertEquals(1, data.chips.first { it.status == Status.DRAFT }.count)
        assertEquals(0, data.chips.first { it.status == Status.VALIDATED }.count)
        // Serial numbers and other answers are searched too; the row's detail line alone is not.
        assertEquals(listOf(3), ids(AssetListQuery(null, text = "SN-003")))
        assertEquals(listOf(3), ids(AssetListQuery(null, text = "rooftop")))
        assertEquals(0, ids(AssetListQuery(null, text = "detail")).size)
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
