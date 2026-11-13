package com.sclera.sclerago.domain.usecase

import com.sclera.sclerago.data.repository.AssetConfigRepositoryImpl
import com.sclera.sclerago.data.repository.AssetRepositoryImpl
import com.sclera.sclerago.domain.model.AssetType
import com.sclera.sclerago.domain.model.Device
import com.sclera.sclerago.domain.model.LocValue
import com.sclera.sclerago.domain.model.Media
import com.sclera.sclerago.domain.model.Source
import com.sclera.sclerago.domain.model.Status
import com.sclera.sclerago.domain.usecase.asset.AddAssetsUseCase
import com.sclera.sclerago.domain.usecase.asset.AssetPlace
import com.sclera.sclerago.domain.usecase.asset.AssignLocationUseCase
import com.sclera.sclerago.domain.usecase.asset.AttachMediaUseCase
import com.sclera.sclerago.domain.usecase.asset.BuildCheckInputUseCase
import com.sclera.sclerago.domain.usecase.asset.CreateAssetUseCase
import com.sclera.sclerago.domain.usecase.asset.DeleteAssetsUseCase
import com.sclera.sclerago.domain.usecase.asset.GetAssetDetailsUseCase
import com.sclera.sclerago.domain.usecase.asset.MatchDevicesUseCase
import com.sclera.sclerago.domain.usecase.asset.ObserveAssetsUseCase
import com.sclera.sclerago.domain.usecase.asset.SaveAssetDetailsUseCase
import com.sclera.sclerago.domain.usecase.asset.SetAssetStatusUseCase
import com.sclera.sclerago.testutil.asset
import com.sclera.sclerago.testutil.SequentialIdGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AssetUseCasesTest {
    private val repo = AssetRepositoryImpl()
    private val place = AssetPlace("p1", "loc1", "Building A")
    private val photo = Media("m1", "content://m1", video = false, createdAt = 0L)
    private fun assetNow(id: String) = repo.catalog.value.assetsIn("p1").first { it.id == id }

    @Test
    fun observeAndDetails_readTheRepository() {
        assertEquals(repo.catalog, ObserveAssetsUseCase(repo)())
        assertNull(GetAssetDetailsUseCase(repo)("missing"))
    }

    @Test
    fun statusLocationAndDelete_editTheCatalog() {
        val id = repo.catalog.value.assetsIn("p1").first().id
        SetAssetStatusUseCase(repo)(listOf(id), Status.SUBMITTED)
        assertEquals(Status.SUBMITTED, assetNow(id).st)
        AssignLocationUseCase(repo)(listOf(id), LocValue("lx", "lx · floor 2"))
        assertEquals("lx", assetNow(id).loc)
        assertTrue(repo.catalog.value.touched.containsKey(id))
        DeleteAssetsUseCase(repo)(listOf(id))
        assertTrue(repo.catalog.value.assetsIn("p1").none { it.id == id })
    }

    @Test
    fun saveDetails_keepsValuesAndOptionallyMovesStatus() {
        val id = repo.catalog.value.assetsIn("p1").first().id
        val save = SaveAssetDetailsUseCase(repo)
        save(id, mapOf("name" to "Renamed"))
        assertEquals("Renamed", assetNow(id).name)
        save(id, mapOf("name" to "Again"), Status.VALIDATED)
        assertEquals(Status.VALIDATED, assetNow(id).st)
    }

    @Test
    fun attachMedia_ignoresEmptyOrInvalidRequests() {
        val attach = AttachMediaUseCase(repo)
        assertFalse(attach("a1", emptyList()))
        assertFalse(attach("", listOf(photo)))
        assertTrue(attach("a1", listOf(photo)))
        assertEquals(1, repo.catalog.value.mediaAdded["a1"]?.size)
    }

    @Test
    fun addAssets_buildsWithConsecutiveIds() {
        val add = AddAssetsUseCase(repo, SequentialIdGenerator())
        assertTrue(add(0) { _, _ -> error("not called") }.isEmpty())
        val made = add(3) { id, i -> asset(id, "New $i") }
        assertEquals(made.map { it.id }, listOf("1", "2", "3"))
        assertEquals("New 0", repo.catalog.value.assets.first().name)
    }

    @Test
    fun create_manual() {
        val create = CreateAssetUseCase(repo, SequentialIdGenerator())
        val r = create("Pump A", "", emptyList(), mapOf("make" to "Acme"), submit = true, place, emptyList(), null, Source.MANUAL)
        assertEquals(CreateAssetUseCase.Via.MANUAL, r.via)
        val a = assetNow(r.id)
        assertEquals(Status.SUBMITTED, a.st)
        assertEquals("Added manually", a.detail)
        assertEquals(AssetType.PUMP, a.type)
        assertEquals("Untitled asset", assetNow(create("", "", emptyList(), emptyMap(), false, place, emptyList(), null, Source.MANUAL).id).name)
        assertEquals(mapOf("make" to "Acme"), repo.catalog.value.savedDetails[r.id])
    }

    @Test
    fun create_fromCameraShots() {
        val create = CreateAssetUseCase(repo, SequentialIdGenerator())
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
    fun create_fromAScannedDevice_linksIt() {
        val create = CreateAssetUseCase(repo, SequentialIdGenerator())
        val device = Device("net:1", "Printer", "10.0.0.5 · HP", 3, false, AssetType.COMPUTER)
        val r = create("", "", emptyList(), emptyMap(), false, place, emptyList(), device, Source.WIFI)
        assertEquals(CreateAssetUseCase.Via.SCAN, r.via)
        val a = assetNow(r.id)
        assertEquals("Printer", a.name)
        assertEquals("10.0.0.5 · HP", a.detail)
        assertEquals(AssetType.COMPUTER, a.type)
        assertEquals(r.id, repo.catalog.value.deviceAsset["net:1"])
        val named = create("Main pump", "", listOf(photo), emptyMap(), false, place, emptyList(), device, Source.WIFI)
        assertEquals(AssetType.PUMP, assetNow(named.id).type)
    }

    @Test
    fun matchDevices_findsAssetsThatMentionTheDevice() {
        val create = CreateAssetUseCase(repo, SequentialIdGenerator())
        val made = asset(id = 500, name = "Router 1", src = Source.WIFI).copy(detail = "10.0.0.5 · edge").also { repo.add(listOf(it)) }
        val devices = listOf(
            Device("net:a", "R", "10.0.0.5 · HP", 2, false),
            Device("net:b", "R", "10.0.0.55 · HP", 2, false),
            Device("net:c", "R", "10.9.9.9", 2, false),
        )
        val all = repo.catalog.value.assetsIn("p1")
        val matched = MatchDevicesUseCase(repo)(devices, Source.WIFI, all)
        assertEquals(made.id, matched["net:a"])
        assertNull(matched["net:b"])
        assertNull(matched["net:c"])
        repo.linkDevice("net:c", "a1")
        assertEquals("a1", MatchDevicesUseCase(repo)(devices, Source.WIFI, all)["net:c"])
    }

    @Test
    fun buildCheckInput_combinesFieldsDetailsAndConfig() {
        val config = AssetConfigRepositoryImpl()
        val build = BuildCheckInputUseCase(repo, config)
        repo.saveDetails("a1", mapOf("make" to "Acme"))
        val input = build(assetNow("a1"))
        assertEquals("Acme", input.values["make"])
        assertEquals("Generic", build(asset(901)).values["sys"])
        assertTrue(input.hasLocation)
        assertTrue(input.fromCamera)
        assertEquals(config.config.value.requiredFields, input.required)
        val noLoc = build(asset(900, loc = null))
        assertFalse(noLoc.hasLocation)
        assertNotNull(noLoc.values["name"])
    }
}
