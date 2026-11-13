package com.sclera.sclerago.domain.usecase.asset

import com.sclera.sclerago.domain.model.Asset
import com.sclera.sclerago.domain.model.AssetCatalog
import com.sclera.sclerago.domain.model.AssetDetails
import com.sclera.sclerago.domain.model.AssetType
import com.sclera.sclerago.domain.model.Device
import com.sclera.sclerago.domain.model.LocValue
import com.sclera.sclerago.domain.model.Media
import com.sclera.sclerago.domain.model.NewAsset
import com.sclera.sclerago.domain.model.Permission
import com.sclera.sclerago.domain.model.Source
import com.sclera.sclerago.domain.model.Status
import com.sclera.sclerago.domain.model.captureName
import com.sclera.sclerago.domain.model.mentions
import com.sclera.sclerago.domain.model.scanKey
import com.sclera.sclerago.domain.model.timeLabel
import com.sclera.sclerago.domain.model.typeOf
import com.sclera.sclerago.domain.repository.AssetConfigRepository
import com.sclera.sclerago.domain.repository.AssetRepository
import com.sclera.sclerago.domain.system.IdGenerator
import com.sclera.sclerago.domain.system.next
import com.sclera.sclerago.domain.usecase.access.PermissionGuard
import com.sclera.sclerago.domain.validation.CheckInput
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

class ObserveAssetsUseCase @Inject constructor(private val repo: AssetRepository) {
    operator fun invoke(): StateFlow<AssetCatalog> = repo.catalog
}

/** The code and fields of an asset (null when it is not there). */
class GetAssetDetailsUseCase @Inject constructor(private val repo: AssetRepository) {
    suspend operator fun invoke(id: String): AssetDetails? = repo.details(id)
}

/** Moves assets to [status]: signing off needs the Validate permission, everything else Collect. */
class SetAssetStatusUseCase @Inject constructor(private val repo: AssetRepository, private val guard: PermissionGuard) {
    suspend operator fun invoke(ids: Collection<String>, status: Status, allowExceptions: Boolean = true) {
        guard.requireOffline(permissionFor(status))
        repo.setStatus(ids, status, allowExceptions)
    }
}

private fun permissionFor(status: Status) = if (status == Status.VALIDATED) Permission.VALIDATE else Permission.COLLECT

/** Keeps the values from an asset's details form and, when given, moves it to [status]. */
class SaveAssetDetailsUseCase @Inject constructor(private val repo: AssetRepository, private val guard: PermissionGuard) {
    suspend operator fun invoke(id: String, values: Map<String, String>, status: Status? = null) {
        guard.requireOffline(Permission.COLLECT)
        if (status != null) guard.requireOffline(permissionFor(status))
        repo.saveDetails(id, values)
        if (status != null) repo.setStatus(listOf(id), status, allowExceptions = true)
    }
}

class DeleteAssetsUseCase @Inject constructor(private val repo: AssetRepository, private val guard: PermissionGuard) {
    suspend operator fun invoke(ids: Collection<String>) {
        guard.requireOffline(Permission.COLLECT)
        repo.delete(ids)
    }
}

class AssignLocationUseCase @Inject constructor(private val repo: AssetRepository, private val guard: PermissionGuard) {
    suspend operator fun invoke(ids: Collection<String>, location: LocValue) {
        guard.requireOffline(Permission.COLLECT)
        repo.assignLocation(ids, location)
    }
}

/** Adds photos / videos to an existing asset; false when there is nothing to add. */
class AttachMediaUseCase @Inject constructor(private val repo: AssetRepository, private val guard: PermissionGuard) {
    suspend operator fun invoke(assetId: String, media: List<Media>): Boolean {
        if (media.isEmpty() || assetId.isBlank()) return false
        guard.requireOffline(Permission.COLLECT)
        repo.attachMedia(assetId, media)
        return true
    }
}


/** Adds [count] assets, built by [build] from each one's new id and index; returns them. */
class AddAssetsUseCase @Inject constructor(
    private val repo: AssetRepository,
    private val ids: IdGenerator,
    private val guard: PermissionGuard,
) {
    suspend operator fun invoke(count: Int, build: (id: String, index: Int) -> Asset): List<Asset> {
        if (count <= 0) return emptyList()
        guard.requireOffline(Permission.COLLECT)
        val made = List(count) { build(ids.next(), it) }
        repo.add(made.map { NewAsset(it) })
        return made
    }
}

/** Where a new asset goes: its property, the location chosen in the location filter and that place's id (null = nowhere). */
data class AssetPlace(val prop: String, val loc: String?, val where: String, val placeId: String? = null)

/**
 * Creates an asset from the details form. It comes from camera shots ([captures]), a scanned
 * [device], or is added by hand, and keeps what that source knows unless the form says better.
 */
class CreateAssetUseCase @Inject constructor(
    private val repo: AssetRepository,
    private val ids: IdGenerator,
    private val guard: PermissionGuard,
) {
    enum class Via { CAMERA, SCAN, MANUAL }

    data class Created(val id: String, val via: Via, val source: Source)

    /** How an asset made from [captures] or [device] (or neither) is added. */
    fun viaFor(captures: List<Media>, device: Device?): Via = when {
        captures.isNotEmpty() -> Via.CAMERA
        device != null -> Via.SCAN
        else -> Via.MANUAL
    }

    suspend operator fun invoke(
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
        guard.requireOffline(Permission.COLLECT)
        val id = ids.next()
        val st = if (submit) Status.SUBMITTED else Status.DRAFT
        val formDetail = detail.takeUnless { it.isBlank() || it == "Added manually" }
        val via = viaFor(captures, device)

        val (asset, source) = when (via) {
            Via.CAMERA -> {
                // From the Camera tab: the shot(s), plus anything added in the form.
                val first = captures.first()
                val captured = (if (first.video) "Video ${first.durationText}" else "Photo") + " · captured " + timeLabel(first.createdAt).take(5)
                Asset(id, name.ifBlank { captureName(first) }, formDetail ?: captured, place.loc, place.where, Source.CAMERA, st, typeOf(name), place.prop, captures + media, locationId = place.placeId) to Source.CAMERA
            }
            Via.SCAN -> {
                // From a scan: keep the device's own line, and the type the scan identified unless the name says what it is.
                val scanned = checkNotNull(device)
                val type = typeOf(name).takeIf { name.isNotBlank() && it != AssetType.GENERIC } ?: scanned.type
                Asset(id, name.ifBlank { scanned.name }, formDetail ?: scanned.sub, place.loc, place.where, deviceSource, st, type, place.prop, media, locationId = place.placeId) to deviceSource
            }
            Via.MANUAL ->
                Asset(id, name.ifBlank { "Untitled asset" }, detail.ifBlank { "Added manually" }, place.loc, place.where, Source.MANUAL, st, typeOf(name), place.prop, media, locationId = place.placeId) to Source.MANUAL
        }
        repo.add(listOf(NewAsset(asset, values, device?.id)))
        return Created(id, via, source)
    }
}

/**
 * Which asset each scanned device already belongs to: one made from it here, or one of [src]'s
 * assets whose subtitle, BLE tag, IP address, label or notes mention it.
 */
class MatchDevicesUseCase @Inject constructor(private val repo: AssetRepository) {
    /** Device id → the id of its asset among [assets], or null. */
    operator fun invoke(devices: List<Device>, src: Source, assets: List<Asset>): Map<String, String?> {
        val catalog = repo.catalog.value
        val texts = assets.filter { it.src == src }.map { it.id to (listOf(it.detail) + catalog.scanText[it.id].orEmpty()).joinToString("\n") }
        return devices.associate { d -> d.id to match(d, assets, texts, catalog) }
    }

    private fun match(d: Device, assets: List<Asset>, texts: List<Pair<String, String>>, catalog: AssetCatalog): String? {
        catalog.deviceAsset[d.id]?.takeIf { id -> assets.any { it.id == id } }?.let { return it }
        val key = scanKey(d)
        return texts.firstOrNull { (_, text) -> mentions(text, key) }?.first
    }
}

/** The values and facts the validation rules look at for assets (same as their Validation tab). */
class BuildCheckInputUseCase @Inject constructor(
    private val assets: AssetRepository,
    private val config: AssetConfigRepository,
) {
    suspend operator fun invoke(list: List<Asset>): List<Pair<Asset, CheckInput>> {
        val details = assets.detailsFor(list.map { it.id })
        val cfg = config.config.value
        return list.map { a ->
            val values = mapOf("name" to a.name, "sys" to "Generic", "typ" to "Generic", "sub" to "Generic") + details[a.id]?.fields.orEmpty()
            val fromCam = a.src == Source.CAMERA
            a to CheckInput(
                values, a.loc != null, a.st, fromCam, a.media.size + if (fromCam && a.media.isEmpty()) 3 else 0,
                required = cfg.requiredFields, minPhotos = cfg.minPhotos, fieldChecks = cfg.fields,
            )
        }
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
    val buildList: BuildAssetListUseCase,
    val toggleSelection: ToggleSelectionUseCase,
    val selectAll: SelectAllUseCase,
    val evaluateChecks: EvaluateAssetChecksUseCase,
    val autofill: AutofillAssetUseCase,
    val formValues: BuildFormValuesUseCase,
)
