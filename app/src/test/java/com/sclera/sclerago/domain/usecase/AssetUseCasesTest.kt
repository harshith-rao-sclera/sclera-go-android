package com.sclera.sclerago.domain.usecase

import com.sclera.sclerago.domain.model.AccessState
import com.sclera.sclerago.domain.model.AssetType
import com.sclera.sclerago.domain.model.CurrentAccess
import com.sclera.sclerago.domain.model.DataError
import com.sclera.sclerago.domain.model.DataException
import com.sclera.sclerago.domain.model.Device
import com.sclera.sclerago.domain.model.LocValue
import com.sclera.sclerago.domain.model.Media
import com.sclera.sclerago.domain.model.Permission
import com.sclera.sclerago.domain.model.Source
import com.sclera.sclerago.domain.model.Status
import com.sclera.sclerago.domain.repository.CurrentAccessRepository
import com.sclera.sclerago.domain.usecase.access.PermissionGuard
import com.sclera.sclerago.domain.usecase.asset.AddAssetsUseCase
import com.sclera.sclerago.domain.usecase.asset.AssetPlace
import com.sclera.sclerago.domain.usecase.asset.AssignLocationUseCase
import com.sclera.sclerago.domain.usecase.asset.AttachMediaUseCase
import com.sclera.sclerago.domain.usecase.asset.BuildCheckInputUseCase
import com.sclera.sclerago.domain.usecase.asset.CreateAssetUseCase
import com.sclera.sclerago.domain.usecase.asset.DeleteAssetsUseCase
import com.sclera.sclerago.domain.usecase.asset.GetAssetDetailsUseCase
import com.sclera.sclerago.domain.usecase.asset.ObserveAssetsUseCase
import com.sclera.sclerago.domain.usecase.asset.SaveAssetDetailsUseCase
import com.sclera.sclerago.domain.usecase.asset.SetAssetStatusUseCase
import com.sclera.sclerago.domain.usecase.media.SortPhotosUseCase
import com.sclera.sclerago.testutil.FakePhotoRulesRepository
import com.sclera.sclerago.testutil.InMemoryAssetConfig
import com.sclera.sclerago.testutil.InMemoryAssetRepository
import com.sclera.sclerago.testutil.SequentialIdGenerator
import com.sclera.sclerago.testutil.UnreadablePhotos
import com.sclera.sclerago.testutil.allowAllGuard
import com.sclera.sclerago.testutil.asset
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class AssetUseCasesTest {
    private val repo = InMemoryAssetRepository()
    private val guard = allowAllGuard()
    private val place = AssetPlace("p1", "loc1", "Building A", placeId = "l-1")
    private val photo = Media("m1", "content://m1", video = false, createdAt = 0L)
    private fun assetNow(id: String) = repo.catalog.value.assets.first { it.id == id }

    /** A signed-in user whose group can do only [permissions]. */
    private fun guardFor(vararg permissions: Permission) = PermissionGuard(
        object : CurrentAccessRepository {
            override val state: StateFlow<AccessState> = MutableStateFlow(
                AccessState.Loaded(
                    CurrentAccess(
                        userId = "u", email = "u@x.com", name = null, role = null, organisationId = "org", groupId = "g",
                        groupName = "G", permissions = permissions.toSet(), allProperties = true, canManageProperties = false,
                    ),
                ),
            )
        },
    )

    private suspend fun assertForbidden(block: suspend () -> Unit) {
        try {
            block()
            fail("expected FORBIDDEN")
        } catch (e: DataException) {
            assertEquals(DataError.FORBIDDEN, e.error)
        }
    }

    @Test
    fun observeAndDetails_readTheRepository() = runTest {
        assertEquals(repo.catalog, ObserveAssetsUseCase(repo)())
        assertNull(GetAssetDetailsUseCase(repo)("missing"))
        assertNotNull(GetAssetDetailsUseCase(repo)("a2"))
    }

    @Test
    fun statusLocationAndDelete_editTheCatalog() = runTest {
        val id = repo.catalog.value.assets.first().id
        SetAssetStatusUseCase(repo, guard, FakePhotoRulesRepository())(listOf(id), Status.SUBMITTED)
        assertEquals(Status.SUBMITTED, assetNow(id).st)
        AssignLocationUseCase(repo, guard)(listOf(id), LocValue("lx", "lx · floor 2", "l-9"))
        assertEquals("lx", assetNow(id).loc)
        assertEquals("l-9", assetNow(id).locationId)
        DeleteAssetsUseCase(repo, guard)(listOf(id))
        assertTrue(repo.catalog.value.assets.none { it.id == id })
    }

    @Test
    fun signingOff_needsValidate_whileOtherChangesNeedCollect() = runTest {
        val collector = guardFor(Permission.COLLECT)
        val id = repo.catalog.value.assets.first().id
        SetAssetStatusUseCase(repo, collector, FakePhotoRulesRepository())(listOf(id), Status.SUBMITTED)
        assertForbidden { SetAssetStatusUseCase(repo, collector, FakePhotoRulesRepository())(listOf(id), Status.VALIDATED) }
        assertForbidden { SaveAssetDetailsUseCase(repo, collector, FakePhotoRulesRepository())(id, mapOf("name" to "X"), Status.VALIDATED) }
        assertEquals(Status.SUBMITTED, assetNow(id).st)
        val viewer = guardFor()
        assertForbidden { DeleteAssetsUseCase(repo, viewer)(listOf(id)) }
        assertForbidden { AssignLocationUseCase(repo, viewer)(listOf(id), LocValue("l", "l")) }
        assertTrue(repo.catalog.value.assets.any { it.id == id })
    }

    @Test
    fun saveDetails_keepsValuesAndOptionallyMovesStatus() = runTest {
        val id = repo.catalog.value.assets.first().id
        val save = SaveAssetDetailsUseCase(repo, guard, FakePhotoRulesRepository())
        save(id, mapOf("name" to "Renamed", "make" to "Acme"))
        assertEquals("Renamed", assetNow(id).name)
        assertEquals("Acme", repo.details[id]?.fields?.get("make"))
        save(id, mapOf("name" to "Again"), Status.VALIDATED)
        assertEquals(Status.VALIDATED, assetNow(id).st)
    }

    @Test
    fun attachMedia_ignoresEmptyOrInvalidRequests() = runTest {
        val attach = AttachMediaUseCase(repo, guard, FakePhotoRulesRepository())
        val before = assetNow("a1").media.size
        assertFalse(attach("a1", emptyList()))
        assertFalse(attach("", listOf(photo)))
        assertTrue(attach("a1", listOf(photo)))
        assertEquals(before + 1, assetNow("a1").media.size)
    }

    @Test
    fun addAssets_buildsWithConsecutiveIds() = runTest {
        val add = AddAssetsUseCase(repo, SequentialIdGenerator(), guard)
        assertTrue(add(0) { _, _ -> error("not called") }.isEmpty())
        val made = add(3) { id, i -> asset(id, "New $i") }
        assertEquals(listOf("1", "2", "3"), made.map { it.id })
        assertEquals("New 0", repo.catalog.value.assets.first().name)
    }

    @Test
    fun create_manual() = runTest {
        val create = CreateAssetUseCase(repo, SequentialIdGenerator(), guard, FakePhotoRulesRepository(), SortPhotosUseCase(UnreadablePhotos()))
        val r = create("Pump A", "", emptyList(), mapOf("make" to "Acme"), submit = true, place, emptyList(), null, Source.MANUAL)
        assertEquals(CreateAssetUseCase.Via.MANUAL, r.via)
        val a = assetNow(r.id)
        assertEquals(Status.SUBMITTED, a.st)
        assertEquals("Added manually", a.detail)
        assertEquals(AssetType.PUMP, a.type)
        assertEquals("l-1", a.locationId)
        assertEquals("Untitled asset", assetNow(create("", "", emptyList(), emptyMap(), false, place, emptyList(), null, Source.MANUAL).id).name)
        assertEquals(mapOf("make" to "Acme"), repo.details[r.id]?.fields)
    }

    @Test
    fun create_fromCameraShots() = runTest {
        val create = CreateAssetUseCase(repo, SequentialIdGenerator(), guard, FakePhotoRulesRepository(), SortPhotosUseCase(UnreadablePhotos()))
        val video = photo.copy(video = true, durationMs = 65_000)
        val r = create("", "Added manually", listOf(photo), emptyMap(), false, place, listOf(video), null, Source.MANUAL)
        assertEquals(CreateAssetUseCase.Via.CAMERA, r.via)
        val a = assetNow(r.id)
        assertEquals(Source.CAMERA, a.src)
        assertTrue(a.name.startsWith("Asset from video "))
        assertTrue(a.detail.startsWith("Video 1:05"))
        assertEquals(2, a.media.size)
        assertEquals(Status.DRAFT, a.st)
    }

    @Test
    fun create_fromAScannedDevice_takesItsNameAndLine() = runTest {
        val create = CreateAssetUseCase(repo, SequentialIdGenerator(), guard, FakePhotoRulesRepository(), SortPhotosUseCase(UnreadablePhotos()))
        val device = Device("net:1", "Printer", "10.0.0.5 · HP", 3, false, AssetType.COMPUTER)
        val r = create("", "", emptyList(), emptyMap(), false, place, emptyList(), device, Source.WIFI)
        assertEquals(CreateAssetUseCase.Via.SCAN, r.via)
        val a = assetNow(r.id)
        assertEquals("Printer", a.name)
        assertEquals("10.0.0.5 · HP", a.detail)
        assertEquals(AssetType.COMPUTER, a.type)
        val named = create("Main pump", "", listOf(photo), emptyMap(), false, place, emptyList(), device, Source.WIFI)
        assertEquals(AssetType.PUMP, assetNow(named.id).type)
    }

    @Test
    fun create_withoutCollect_isRefused() = runTest {
        assertForbidden { CreateAssetUseCase(repo, SequentialIdGenerator(), guardFor(), FakePhotoRulesRepository(), SortPhotosUseCase(UnreadablePhotos()))("A", "", emptyList(), emptyMap(), false, place, emptyList(), null, Source.MANUAL) }
    }

    @Test
    fun buildCheckInput_combinesFieldsDetailsAndConfig() = runTest {
        val config = InMemoryAssetConfig()
        val build = BuildCheckInputUseCase(repo, config, FakePhotoRulesRepository())
        repo.saveDetails("a1", mapOf("make" to "Acme"))
        val inputs = build(listOf(assetNow("a1"), asset(901), asset(900, loc = null)))
        val (_, input) = inputs[0]
        assertEquals("Acme", input.values["make"])
        assertEquals("Generic", inputs[1].second.values["sys"])
        assertTrue(input.hasLocation)
        assertEquals(config.config.value.requiredFields, input.required)
        val noLoc = inputs[2].second
        assertFalse(noLoc.hasLocation)
        assertNotNull(noLoc.values["name"])
    }
}
