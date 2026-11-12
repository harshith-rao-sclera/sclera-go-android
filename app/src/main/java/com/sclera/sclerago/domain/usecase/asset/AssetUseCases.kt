package com.sclera.sclerago.domain.usecase.asset

import com.sclera.sclerago.domain.model.Asset
import com.sclera.sclerago.domain.model.AssetCatalog
import com.sclera.sclerago.domain.model.AssetDetails
import com.sclera.sclerago.domain.model.AssetType
import com.sclera.sclerago.domain.model.Device
import com.sclera.sclerago.domain.model.LocValue
import com.sclera.sclerago.domain.model.Media
import com.sclera.sclerago.domain.model.Source
import com.sclera.sclerago.domain.model.Status
import com.sclera.sclerago.domain.model.captureName
import com.sclera.sclerago.domain.model.mentions
import com.sclera.sclerago.domain.model.scanKey
import com.sclera.sclerago.domain.model.timeLabel
import com.sclera.sclerago.domain.model.typeOf
import com.sclera.sclerago.domain.repository.AssetConfigRepository
import com.sclera.sclerago.domain.repository.AssetRepository
import com.sclera.sclerago.domain.validation.CheckInput
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

class ObserveAssetsUseCase @Inject constructor(private val repo: AssetRepository) {
    operator fun invoke(): StateFlow<AssetCatalog> = repo.catalog
}

/** Code and reference fields an existing asset came with (null for assets added here). */
class GetAssetDetailsUseCase @Inject constructor(private val repo: AssetRepository) {
    operator fun invoke(id: Int): AssetDetails? = repo.details(id)
}

class SetAssetStatusUseCase @Inject constructor(private val repo: AssetRepository) {
    operator fun invoke(ids: Collection<Int>, status: Status) = repo.setStatus(ids, status)
}

/** Keeps the values from an asset's details form and, when given, moves it to [status]. */
class SaveAssetDetailsUseCase @Inject constructor(private val repo: AssetRepository) {
    operator fun invoke(id: Int, values: Map<String, String>, status: Status? = null) {
        repo.saveDetails(id, values)
        if (status != null) repo.setStatus(listOf(id), status)
    }
}

class DeleteAssetsUseCase @Inject constructor(private val repo: AssetRepository) {
    operator fun invoke(ids: Collection<Int>) = repo.delete(ids)
}

class AssignLocationUseCase @Inject constructor(private val repo: AssetRepository) {
    operator fun invoke(ids: Collection<Int>, location: LocValue) = repo.assignLocation(ids, location)
}

/** Adds photos / videos to an existing asset; false when there is nothing to add. */
class AttachMediaUseCase @Inject constructor(private val repo: AssetRepository) {
    operator fun invoke(assetId: Int, media: List<Media>): Boolean {
        if (media.isEmpty() || assetId < 0) return false
        repo.attachMedia(assetId, media)
        return true
    }
}

/** Adds [count] assets, built by [build] from each one's new id and index; returns them. */
class AddAssetsUseCase @Inject constructor(private val repo: AssetRepository) {
    operator fun invoke(count: Int, build: (id: Int, index: Int) -> Asset): List<Asset> {
        if (count <= 0) return emptyList()
        val first = repo.newIds(count)
        val made = List(count) { build(first + it, it) }
        repo.add(made)
        return made
    }
}

/** Where a new asset goes: its property and the location chosen in the location filter. */
data class AssetPlace(val prop: String, val loc: String?, val where: String)

/**
 * Creates an asset from the details form. It comes from camera shots ([captures]), a scanned
 * [device], or is added by hand, and keeps what that source knows unless the form says better.
 */
class CreateAssetUseCase @Inject constructor(private val repo: AssetRepository) {
    enum class Via { CAMERA, SCAN, MANUAL }

    data class Created(val id: Int, val via: Via, val source: Source)

    operator fun invoke(
        name: String,
        detail: String,
        media: List<Media>,
        values: Map<String, String>,
        submit: Boolean,
        place: AssetPlace,
        captures: List<Media>,
        device: Device?,
        deviceSource: Source,
    ): Created {
        val id = repo.newIds(1)
        val st = if (submit) Status.SUBMITTED else Status.DRAFT
        if (values.isNotEmpty()) repo.saveDetails(id, values)
        val formDetail = detail.takeUnless { it.isBlank() || it == "Added manually" }

        if (captures.isNotEmpty()) {
            // From the Camera tab: the shot(s), plus anything added in the form.
            val first = captures.first()
            val captured = (if (first.video) "Video ${first.durationText}" else "Photo") + " · captured " + timeLabel(first.createdAt).take(5)
            repo.add(listOf(Asset(id, name.ifBlank { captureName(first) }, formDetail ?: captured, place.loc, place.where, Source.CAMERA, st, typeOf(name), place.prop, captures)))
            if (media.isNotEmpty()) repo.attachMedia(id, media)
            return Created(id, Via.CAMERA, Source.CAMERA)
        }
        if (media.isNotEmpty()) repo.attachMedia(id, media)
        if (device != null) {
            // From a scan: keep the device's own line, and the type the scan identified unless the name says what it is.
            val type = typeOf(name).takeIf { name.isNotBlank() && it != AssetType.GENERIC } ?: device.type
            repo.add(listOf(Asset(id, name.ifBlank { device.name }, formDetail ?: device.sub, place.loc, place.where, deviceSource, st, type, place.prop)))
            repo.linkDevice(device.id, id)
            return Created(id, Via.SCAN, deviceSource)
        }
        repo.add(listOf(Asset(id, name.ifBlank { "Untitled asset" }, detail.ifBlank { "Added manually" }, place.loc, place.where, Source.MANUAL, st, typeOf(name), place.prop)))
        return Created(id, Via.MANUAL, Source.MANUAL)
    }
}

/**
 * Which asset each scanned device already belongs to: one made from it here, or one of [src]'s
 * assets whose subtitle, BLE tag, IP address, label or notes mention it.
 */
class MatchDevicesUseCase @Inject constructor(private val repo: AssetRepository) {
    /** Device id → the id of its asset among [assets], or null. */
    operator fun invoke(devices: List<Device>, src: Source, assets: List<Asset>): Map<String, Int?> {
        val catalog = repo.catalog.value
        val texts = assets.filter { it.src == src }.map { it.id to keys(it, catalog).joinToString("\n") }
        return devices.associate { d -> d.id to match(d, assets, texts, catalog) }
    }

    private fun keys(a: Asset, catalog: AssetCatalog): List<String> {
        val f = repo.details(a.id)?.fields.orEmpty() + catalog.savedDetails[a.id].orEmpty()
        return listOf(a.detail) + listOf("ble", "ip", "label", "note").mapNotNull { f[it] }
    }

    private fun match(d: Device, assets: List<Asset>, texts: List<Pair<Int, String>>, catalog: AssetCatalog): Int? {
        catalog.deviceAsset[d.id]?.takeIf { id -> assets.any { it.id == id } }?.let { return it }
        val key = scanKey(d)
        return texts.firstOrNull { (_, text) -> mentions(text, key) }?.first
    }
}

/** The values and facts the validation rules look at for one asset (same as its Validation tab). */
class BuildCheckInputUseCase @Inject constructor(
    private val assets: AssetRepository,
    private val config: AssetConfigRepository,
) {
    operator fun invoke(a: Asset): CheckInput {
        val values = mapOf("name" to a.name, "sys" to "Generic", "typ" to "Generic", "sub" to "Generic") +
            assets.details(a.id)?.fields.orEmpty() + assets.catalog.value.savedDetails[a.id].orEmpty()
        val cfg = config.config.value
        val fromCam = a.src == Source.CAMERA
        return CheckInput(
            values, a.loc != null, a.st, fromCam, a.media.size + if (fromCam && a.media.isEmpty()) 3 else 0,
            required = cfg.requiredFields, minPhotos = cfg.minPhotos, fieldChecks = cfg.fields,
        )
    }
}

/** The asset use cases, for injecting as one. */
class AssetUseCases @Inject constructor(
    val observe: ObserveAssetsUseCase,
    val details: GetAssetDetailsUseCase,
    val setStatus: SetAssetStatusUseCase,
    val saveDetails: SaveAssetDetailsUseCase,
    val delete: DeleteAssetsUseCase,
    val assignLocation: AssignLocationUseCase,
    val attachMedia: AttachMediaUseCase,
    val add: AddAssetsUseCase,
    val create: CreateAssetUseCase,
    val matchDevices: MatchDevicesUseCase,
    val checkInput: BuildCheckInputUseCase,
)
