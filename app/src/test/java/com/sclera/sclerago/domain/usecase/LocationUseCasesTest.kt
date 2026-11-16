package com.sclera.sclerago.domain.usecase

import com.sclera.sclerago.domain.model.AccessState
import com.sclera.sclerago.domain.model.BuildingInfo
import com.sclera.sclerago.domain.model.CurrentAccess
import com.sclera.sclerago.domain.model.DataError
import com.sclera.sclerago.domain.model.DataException
import com.sclera.sclerago.domain.model.LocNode
import com.sclera.sclerago.domain.model.NewLocation
import com.sclera.sclerago.domain.model.NodeKind
import com.sclera.sclerago.domain.model.Permission
import com.sclera.sclerago.domain.model.PlaceRow
import com.sclera.sclerago.domain.model.findNode
import com.sclera.sclerago.domain.repository.CurrentAccessRepository
import com.sclera.sclerago.domain.repository.LocationRepository
import com.sclera.sclerago.domain.usecase.access.PermissionGuard
import com.sclera.sclerago.domain.usecase.location.AddBuildingUseCase
import com.sclera.sclerago.domain.usecase.location.AddFloorUseCase
import com.sclera.sclerago.domain.usecase.location.AddLocationsUseCase
import com.sclera.sclerago.domain.usecase.location.DeleteLocationNodesUseCase
import com.sclera.sclerago.domain.usecase.location.ImportBuildingsUseCase
import com.sclera.sclerago.domain.usecase.location.ObserveLocationTreeUseCase
import com.sclera.sclerago.domain.usecase.location.RenameLocationNodeUseCase
import com.sclera.sclerago.domain.usecase.location.SaveBuildingUseCase
import com.sclera.sclerago.domain.usecase.location.SaveFloorsUseCase
import com.sclera.sclerago.domain.usecase.location.UpdateLocationUseCase
import com.sclera.sclerago.testutil.FakeFileRepository
import com.sclera.sclerago.testutil.allowAllGuard
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Configuration → Building info: what the use cases hand the repository (trimmed, numbered, parsed) and that they
 * refuse users who can't configure. The tree itself is built by the offline database.
 */
class LocationUseCasesTest {
    /** Records every call; [rows] is what the last import was given. */
    private class RecordingLocations : LocationRepository {
        override val tree: StateFlow<List<LocNode>> = MutableStateFlow(emptyList())
        val calls = mutableListOf<String>()
        var savedInfo: BuildingInfo? = null
        var floors: List<LocNode> = emptyList()
        var locations: List<NewLocation> = emptyList()
        var rows: List<PlaceRow> = emptyList()
        var deleted: Set<String> = emptySet()

        override suspend fun addBuilding(name: String, info: BuildingInfo?): String {
            calls += "addBuilding $name"
            savedInfo = info
            return "b-1"
        }

        override suspend fun addFloor(buildingId: String, name: String): String {
            calls += "addFloor $buildingId $name"
            return "f-1"
        }

        override suspend fun saveBuilding(id: String, name: String, info: BuildingInfo) {
            calls += "saveBuilding $id $name"
            savedInfo = info
        }

        override suspend fun saveFloors(buildingId: String, floors: List<LocNode>) {
            this.floors = floors
        }

        override suspend fun renameNode(id: String, name: String) {
            calls += "rename $id $name"
        }

        override suspend fun updateLocation(id: String, name: String, type: String, code: String) {
            calls += "update $id $name|$type|$code"
        }

        override suspend fun deleteNodes(ids: Set<String>) {
            deleted = ids
        }

        override suspend fun addLocations(floorId: String, locations: List<NewLocation>): Int {
            this.locations = locations
            return locations.size
        }

        override suspend fun importRows(rows: List<PlaceRow>): Int {
            this.rows = rows
            return rows.size
        }
    }

    private val repo = RecordingLocations()
    private val guard = allowAllGuard()

    /** A signed-in user whose group can't configure. */
    private val noConfigure = PermissionGuard(
        object : CurrentAccessRepository {
            override val state: StateFlow<AccessState> = MutableStateFlow(
                AccessState.Loaded(
                    CurrentAccess(
                        userId = "u", email = "u@x.com", name = null, role = null, organisationId = "org", groupId = "g",
                        groupName = "Collectors", permissions = setOf(Permission.COLLECT), allProperties = true, canManageProperties = false,
                    ),
                ),
            )
        },
    )

    @Test
    fun observe_exposesTheRepositoryTree() = assertEquals(repo.tree, ObserveLocationTreeUseCase(repo)())

    @Test
    fun addBuildingAndFloor_trimTheNamesAndDetails() = runTest {
        val b = AddBuildingUseCase(repo, guard)(" Tower ", BuildingInfo(address1 = " 1 High St ", accessCode = " 1234 "))
        assertEquals("b-1", b)
        assertEquals("1 High St", repo.savedInfo?.address1)
        assertEquals("1234", repo.savedInfo?.accessCode)
        assertEquals("f-1", AddFloorUseCase(repo, guard)(b, " Level 1 "))
        assertEquals(listOf("addBuilding Tower", "addFloor b-1 Level 1"), repo.calls)
    }

    @Test
    fun saveBuilding_renameAndUpdate_trimWhatTheyAreGiven() = runTest {
        SaveBuildingUseCase(repo, guard)("b-1", " Tower B ", BuildingInfo(city = " Leeds "))
        assertEquals("Leeds", repo.savedInfo?.city)
        RenameLocationNodeUseCase(repo, guard)("f-1", " L1 ")
        UpdateLocationUseCase(repo, guard)("l-1", " Boiler ", " Room ", " B ")
        assertEquals(listOf("saveBuilding b-1 Tower B", "rename f-1 L1", "update l-1 Boiler|Room|B"), repo.calls)
    }

    @Test
    fun saveFloors_trimsEveryFloor_inTheGivenOrder() = runTest {
        SaveFloorsUseCase(repo, guard)(
            "b-1",
            listOf(LocNode("f3", " Roof ", NodeKind.FLOOR, type = " T ", code = " R "), LocNode("f1", "Ground", NodeKind.FLOOR)),
        )
        assertEquals(listOf("Roof", "Ground"), repo.floors.map { it.name })
        assertEquals("T", repo.floors.first().type)
        assertEquals("R", repo.floors.first().code)
    }

    @Test
    fun addLocations_skipsBlankNames_andNumbersCodesWhenSeveralAreAdded() = runTest {
        val add = AddLocationsUseCase(repo, guard)
        assertEquals(0, add("f-1", listOf(" ", ""), "", ""))
        assertEquals(2, add("f-1", listOf(" Plant ", "Store", ""), " Room ", " P "))
        assertEquals(listOf(NewLocation("Plant", "Room", "P-1"), NewLocation("Store", "Room", "P-2")), repo.locations)
        assertEquals(1, add("f-1", listOf("Lift"), "", " L "))
        assertEquals(listOf(NewLocation("Lift", "", "L")), repo.locations) // a single location keeps its code as typed
    }

    @Test
    fun deleteNodes_nothingToDelete_isFalse() = runTest {
        val delete = DeleteLocationNodesUseCase(repo, guard)
        assertFalse(delete(emptySet()))
        assertTrue(delete(setOf("l-1", "f-1")))
        assertEquals(setOf("l-1", "f-1"), repo.deleted)
    }

    @Test
    fun importBuildings_readsTheSheet_skippingTheHeaderAndRowsWithoutABuilding() = runTest {
        val files = FakeFileRepository()
        val import = ImportBuildingsUseCase(repo, files, guard)
        assertEquals(ImportBuildingsUseCase.Result.Unreadable, import("u"))
        files.sheet = listOf(listOf("Building", "Floor", "Location"), listOf("", "F", "L"))
        assertEquals(ImportBuildingsUseCase.Result.NothingFound, import("u"))
        files.sheet = listOf(
            listOf("Building", "Floor", "Location", "Type", "Code"),
            listOf(" Tower ", "L1", "Plant", "Room", "P1"),
            listOf("Tower", "L2"),
            listOf("Annex"),
            listOf("", "orphan floor"),
        )
        assertEquals(ImportBuildingsUseCase.Result.Imported(3, 3), import("u"))
        assertEquals(
            listOf(PlaceRow("Tower", "L1", "Plant", "Room", "P1"), PlaceRow("Tower", "L2"), PlaceRow("Annex")),
            repo.rows,
        )
    }

    @Test
    fun withoutConfigurePermission_changesAreRefused() = runTest {
        try {
            AddBuildingUseCase(repo, noConfigure)("Tower")
            fail("expected Forbidden")
        } catch (e: DataException) {
            assertEquals(DataError.FORBIDDEN, e.error)
        }
        assertTrue(repo.calls.isEmpty())
    }

    @Test
    fun findNode_searchesNestedNodes() {
        val tree = listOf(LocNode("b", "B", NodeKind.BUILDING, listOf(LocNode("f", "F", NodeKind.FLOOR, listOf(LocNode("l", "L", NodeKind.LOCATION))))))
        assertEquals("L", tree.findNode("l")?.name)
        assertEquals(NodeKind.FLOOR, tree.findNode("f")?.kind)
        assertNull(tree.findNode("zzz"))
    }
}
