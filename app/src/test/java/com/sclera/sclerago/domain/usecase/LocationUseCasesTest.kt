package com.sclera.sclerago.domain.usecase

import com.sclera.sclerago.data.repository.LocationRepositoryImpl
import com.sclera.sclerago.domain.model.BuildingInfo
import com.sclera.sclerago.domain.model.LocNode
import com.sclera.sclerago.domain.model.NodeKind
import com.sclera.sclerago.domain.model.findNode
import com.sclera.sclerago.domain.model.mapNode
import com.sclera.sclerago.domain.model.removeNodes
import com.sclera.sclerago.domain.usecase.location.AddBuildingUseCase
import com.sclera.sclerago.domain.usecase.location.AddFloorUseCase
import com.sclera.sclerago.domain.usecase.location.AddLocationsUseCase
import com.sclera.sclerago.domain.usecase.location.DeleteLocationNodesUseCase
import com.sclera.sclerago.domain.usecase.location.GetLocationTreeUseCase
import com.sclera.sclerago.domain.usecase.location.ImportBuildingsUseCase
import com.sclera.sclerago.domain.usecase.location.ObserveLocationTreesUseCase
import com.sclera.sclerago.domain.usecase.location.RenameLocationNodeUseCase
import com.sclera.sclerago.domain.usecase.location.SaveBuildingUseCase
import com.sclera.sclerago.domain.usecase.location.SaveFloorsUseCase
import com.sclera.sclerago.domain.usecase.location.UpdateLocationUseCase
import com.sclera.sclerago.testutil.FakeFileRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocationUseCasesTest {
    private val repo = LocationRepositoryImpl()
    private val prop = "px"
    private fun tree() = GetLocationTreeUseCase(repo)(prop)

    private fun buildingWithFloor(): Pair<String, String> {
        val b = AddBuildingUseCase(repo)(prop, " Tower ", BuildingInfo(address1 = " 1 High St ", accessCode = " 1234 "))
        val f = AddFloorUseCase(repo)(prop, b, " Level 1 ")
        return b to f
    }

    @Test
    fun addBuildingAndFloor_buildTheTree() {
        val (b, f) = buildingWithFloor()
        val building = tree().findNode(b)!!
        assertEquals("Tower", building.name)
        assertEquals("1 High St", building.info?.address1)
        assertEquals("1234", building.info?.accessCode)
        assertEquals("Level 1", tree().findNode(f)?.name)
        assertEquals(NodeKind.FLOOR, tree().findNode(f)?.kind)
        assertEquals(repo.trees, ObserveLocationTreesUseCase(repo)())
        assertNotNull(repo.trees.value[prop])
    }

    @Test
    fun nodeIds_areUnique() = assertTrue(repo.newNodeId("b") != repo.newNodeId("b"))

    @Test
    fun saveBuildingAndRename() {
        val (b, f) = buildingWithFloor()
        SaveBuildingUseCase(repo)(prop, b, " Tower B ", BuildingInfo(city = " Leeds "))
        assertEquals("Tower B", tree().findNode(b)?.name)
        assertEquals("Leeds", tree().findNode(b)?.info?.city)
        RenameLocationNodeUseCase(repo)(prop, f, " L1 ")
        assertEquals("L1", tree().findNode(f)?.name)
    }

    @Test
    fun saveFloors_reordersRenamesAndRemovesMissingOnes() {
        val (b, f1) = buildingWithFloor()
        val f2 = AddFloorUseCase(repo)(prop, b, "Level 2")
        val f3 = AddFloorUseCase(repo)(prop, b, "Level 3")
        SaveFloorsUseCase(repo)(
            prop, b,
            listOf(
                LocNode(f3, " Roof ", NodeKind.FLOOR, type = " T ", code = " R "),
                LocNode(f1, "Ground", NodeKind.FLOOR),
                LocNode("unknown", "Ghost", NodeKind.FLOOR),
            ),
        )
        val kids = tree().findNode(b)!!.kids!!
        assertEquals(listOf("Roof", "Ground"), kids.map { it.name })
        assertEquals("R", kids.first().code)
        assertNull(tree().findNode(f2))
    }

    @Test
    fun addLocations_numbersCodesWhenSeveralAreAdded() {
        val (_, f) = buildingWithFloor()
        val add = AddLocationsUseCase(repo)
        assertEquals(0, add(prop, f, listOf(" ", ""), "", ""))
        assertEquals(2, add(prop, f, listOf(" Plant ", "Store", ""), " Room ", " P "))
        assertEquals(1, add(prop, f, listOf("Lift"), "", " L "))
        val kids = tree().findNode(f)!!.kids!!
        assertEquals(listOf("Plant", "Store", "Lift"), kids.map { it.name })
        assertEquals(listOf("P-1", "P-2", "L"), kids.map { it.code })
        assertEquals("Room", kids.first().type)
    }

    @Test
    fun updateLocation_andDeleteNodes() {
        val (b, f) = buildingWithFloor()
        AddLocationsUseCase(repo)(prop, f, listOf("Plant"), "", "")
        val loc = tree().findNode(f)!!.kids!!.single()
        UpdateLocationUseCase(repo)(prop, loc.id, " Boiler ", " Room ", " B ")
        assertEquals(Triple("Boiler", "Room", "B"), tree().findNode(loc.id)!!.let { Triple(it.name, it.type, it.code) })
        val delete = DeleteLocationNodesUseCase(repo)
        assertEquals(false, delete(prop, emptySet()))
        assertTrue(delete(prop, setOf(loc.id)))
        assertNull(tree().findNode(loc.id))
        assertTrue(delete(prop, setOf(b)))
        assertNull(tree().findNode(f))
    }

    @Test
    fun treeHelpers_workOnNestedNodes() {
        val tree = listOf(LocNode("b", "B", NodeKind.BUILDING, listOf(LocNode("f", "F", NodeKind.FLOOR, listOf(LocNode("l", "L", NodeKind.LOCATION))))))
        assertEquals("L", tree.findNode("l")?.name)
        assertNull(tree.findNode("zzz"))
        assertEquals("X", tree.mapNode("l") { it.copy(name = "X") }.findNode("l")?.name)
        assertNull(tree.mapNode("f") { null }.findNode("l"))
        assertNull(tree.removeNodes(setOf("l")).findNode("l"))
    }

    @Test
    fun importBuildings_createsMissingNodesAndSkipsExistingOnes() = runTest {
        val files = FakeFileRepository()
        val import = ImportBuildingsUseCase(repo, files)
        assertEquals(ImportBuildingsUseCase.Result.Unreadable, import(prop, "u"))
        files.sheet = listOf(listOf("Building", "Floor", "Location"), listOf("", "F", "L"))
        assertEquals(ImportBuildingsUseCase.Result.NothingFound, import(prop, "u"))
        files.sheet = listOf(
            listOf("Building", "Floor", "Location", "Type", "Code"),
            listOf("Tower", "L1", "Plant", "Room", "P1"),
            listOf("tower", "l1", "plant"),
            listOf("tower", "L2"),
            listOf("Annex"),
        )
        // 4 rows read: building+floor+location (3), floor L2 (1), building Annex (1)
        assertEquals(ImportBuildingsUseCase.Result.Imported(4, 5), import(prop, "u"))
        val tower = tree().first { it.name == "Tower" }
        assertEquals(listOf("L1", "L2"), tower.kids!!.map { it.name })
        assertEquals("P1", tower.kids!!.first().kids!!.single().code)
        assertEquals(ImportBuildingsUseCase.Result.Imported(4, 0), import(prop, "u"))
    }
}
