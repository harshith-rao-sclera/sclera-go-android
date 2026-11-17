package com.sclera.sclerago.domain.usecase.asset

import com.sclera.sclerago.domain.model.Asset
import com.sclera.sclerago.domain.model.AssetActivity
import com.sclera.sclerago.domain.model.AssetCatalog
import com.sclera.sclerago.domain.model.AssetDetails
import com.sclera.sclerago.domain.model.AssetType
import com.sclera.sclerago.domain.model.DataError
import com.sclera.sclerago.domain.model.DataException
import com.sclera.sclerago.domain.model.Device
import com.sclera.sclerago.domain.model.LocValue
import com.sclera.sclerago.domain.model.Media
import com.sclera.sclerago.domain.model.NewAsset
import com.sclera.sclerago.domain.model.Permission
import com.sclera.sclerago.domain.model.PhotoRule
import com.sclera.sclerago.domain.model.Source
import com.sclera.sclerago.domain.model.Status
import com.sclera.sclerago.domain.model.captureName
import com.sclera.sclerago.domain.model.isLockedFor
import com.sclera.sclerago.domain.model.mentions
import com.sclera.sclerago.domain.model.scanKey
import com.sclera.sclerago.domain.model.timeLabel
import com.sclera.sclerago.domain.model.typeOf
import com.sclera.sclerago.domain.model.withDefaultKind
import com.sclera.sclerago.domain.repository.AssetActivityRepository
import com.sclera.sclerago.domain.repository.AssetConfigRepository
import com.sclera.sclerago.domain.repository.AssetRepository
import com.sclera.sclerago.domain.repository.LocalDataReloader
import com.sclera.sclerago.domain.repository.PhotoRulesRepository
import com.sclera.sclerago.domain.system.IdGenerator
import com.sclera.sclerago.domain.system.next
import com.sclera.sclerago.domain.usecase.access.PermissionGuard
import com.sclera.sclerago.domain.validation.CheckInput
import com.sclera.sclerago.domain.validation.CommentProblem
import com.sclera.sclerago.domain.validation.checkComment
import com.sclera.sclerago.domain.validation.commentText
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import javax.inject.Inject

class ObserveAssetsUseCase @Inject constructor(private val repo: AssetRepository) {
    operator fun invoke(): StateFlow<AssetCatalog> = repo.catalog
}

/** An asset's comments and history, newest first, again whenever they change. */
class ObserveAssetActivityUseCase @Inject constructor(private val repo: AssetActivityRepository) {
    operator fun invoke(assetId: String): Flow<List<AssetActivity>> = repo.observe(assetId)
}

/**
 * Comments on an asset, as the server allows: anyone who can collect or validate (else [DataException] FORBIDDEN),
 * with text that passes [checkComment]. Returns the problem with [text] and posts nothing, or null once it is posted.
 */
class AddCommentUseCase @Inject constructor(private val repo: AssetActivityRepository, private val guard: PermissionGuard) {
    suspend operator fun invoke(assetId: String, text: String): CommentProblem? {
        if (!guard.allows(Permission.COLLECT) && !guard.allows(Permission.VALIDATE)) throw DataException(DataError.FORBIDDEN)
        checkComment(text)?.let { return it }
        repo.addComment(assetId, commentText(text))
        return null
    }
}

/** The code and fields of an asset (null when it is not there). */
class GetAssetDetailsUseCase @Inject constructor(private val repo: AssetRepository) {
    suspend operator fun invoke(id: String): AssetDetails? = repo.details(id)
}

/**
 * Moves assets to [status]: signing off needs the Validate permission, everything else Collect. Submitting counts the
 * property's [photoRules] as the server does (too few photos of a kind makes an exception, like an empty mandatory field).
 */
class SetAssetStatusUseCase @Inject constructor(
    private val repo: AssetRepository,
    private val guard: PermissionGuard,
    private val photoRules: PhotoRulesRepository,
) {
    /** The signed-in user may move assets to [status] (signing off is only for reviewers; its button is hidden otherwise). */
    fun allows(status: Status): Boolean = guard.allows(permissionFor(status))

    suspend operator fun invoke(
        ids: Collection<String>,
        status: Status,
        allowExceptions: Boolean = true,
    ) {
        guard.requireOffline(permissionFor(status))
        repo.setStatus(ids, status, allowExceptions, photoRules.forMove(status))
    }
}

private fun permissionFor(status: Status) = if (status == Status.VALIDATED) Permission.VALIDATE else Permission.COLLECT

/** The photo rules a move to [status] counts: the property's when submitting (as the server's submit), none otherwise. */
private suspend fun PhotoRulesRepository.forMove(status: Status): List<PhotoRule> =
    if (status == Status.SUBMITTED) rules().first() else emptyList()

/** Still being drafted: saving such an asset gives it the AI flag of a saved draft; any other status is kept. */
private val DRAFTING = setOf(Status.DRAFT, Status.INPUT, Status.AI)

/**
 * Keeps the values from an asset's details form and, when given, moves it to [status] (submitting counts [photoRules]).
 * As the server, a submitted or validated asset can only be changed by an admin (else [DataException] FORBIDDEN).
 */
class SaveAssetDetailsUseCase @Inject constructor(
    private val repo: AssetRepository,
    private val guard: PermissionGuard,
    private val photoRules: PhotoRulesRepository,
) {
    suspend operator fun invoke(id: String, values: Map<String, String>, status: Status? = null) {
        guard.requireOffline(Permission.COLLECT)
        if (status != null) guard.requireOffline(permissionFor(status))
        val current = repo.catalog.value.assets.firstOrNull { it.id == id }?.st
        if (current != null && isLockedFor(current, guard.isAdmin())) throw DataException(DataError.FORBIDDEN)
        repo.saveDetails(id, values)
        // As the server's edit: saving keeps the status of an asset that isn't a draft any more (an exception stays one).
        val target = if (status == Status.AI && current != null && current !in DRAFTING) null else status
        if (target != null) repo.setStatus(listOf(id), target, allowExceptions = true, photoRules = photoRules.forMove(target))
    }
}

/** Whether the signed-in user may change an asset in [status]: only an admin may change a submitted or validated one. */
class CanEditAssetUseCase @Inject constructor(private val guard: PermissionGuard) {
    operator fun invoke(status: Status): Boolean = !isLockedFor(status, guard.isAdmin())
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


/** Pull to refresh on the asset lists: reads the property's assets on this device again (the sync keeps them up to date). */
class ReloadAssetsUseCase @Inject constructor(private val reloader: LocalDataReloader) {
    operator fun invoke() = reloader.reload()
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
    private val photoRules: PhotoRulesRepository,
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
        // As the server: an asset is created as a draft; submitting it is a move after that, by the submit rules.
        val st = Status.DRAFT
        val formDetail = detail.takeUnless { it.isBlank() || it == "Added manually" }
        val via = viaFor(captures, device)

        val (asset, source) = when (via) {
            Via.CAMERA -> {
                // From the Camera tab: the shot(s), plus anything added in the form. The shots are equipment photos, so the server reads them.
                val first = captures.first()
                val captured = (if (first.video) "Video ${first.durationText}" else "Photo") + " · captured " + timeLabel(first.createdAt).take(5)
                val shots = captures.map { it.withDefaultKind() }
                Asset(id, name.ifBlank { captureName(first) }, formDetail ?: captured, place.loc, place.where, Source.CAMERA, st, typeOf(name), place.prop, shots + media, locationId = place.placeId) to Source.CAMERA
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
        // Saved (drafted) or submitted, a new asset has the AI flag on.
        repo.add(listOf(NewAsset(asset.copy(aiSuggestion = true), values, device?.id)))
        // With a mandatory field empty or too few photos it goes in as an exception (the screen asks about that first).
        if (submit) repo.setStatus(listOf(id), Status.SUBMITTED, allowExceptions = true, photoRules = photoRules.forMove(Status.SUBMITTED))
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
    private val photoRules: PhotoRulesRepository,
) {
    suspend operator fun invoke(list: List<Asset>): List<Pair<Asset, CheckInput>> {
        val details = assets.detailsFor(list.map { it.id })
        val cfg = config.config.value
        // Photos each asset needs: the property's photo rules (its settings at the server), all kinds together.
        val minPhotos = photoRules.rules().first().sumOf { it.min }
        return list.map { a ->
            val values = mapOf("name" to a.name, "sys" to "Generic", "typ" to "Generic", "sub" to "Generic") + details[a.id]?.fields.orEmpty()
            val fromCam = a.src == Source.CAMERA
            a to CheckInput(
                values, a.loc != null, a.st, fromCam, a.media.size + if (fromCam && a.media.isEmpty()) 3 else 0,
                required = cfg.requiredFields, minPhotos = minPhotos, fieldChecks = cfg.fields,
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
    val activity: ObserveAssetActivityUseCase,
    val addComment: AddCommentUseCase,
)
