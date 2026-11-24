package com.sclera.sclerago.domain.usecase.asset

import com.sclera.sclerago.domain.model.clockTimeLabel
import com.sclera.sclerago.domain.system.ClockFormat
import com.sclera.sclerago.domain.model.ActivityKind
import com.sclera.sclerago.domain.model.Asset
import com.sclera.sclerago.domain.model.AssetActivity
import com.sclera.sclerago.domain.model.AssetCatalog
import com.sclera.sclerago.domain.model.AssetDetails
import com.sclera.sclerago.domain.model.AssetLock
import com.sclera.sclerago.domain.model.AssetType
import com.sclera.sclerago.domain.model.AuthState
import com.sclera.sclerago.domain.model.DataError
import com.sclera.sclerago.domain.model.DataException
import com.sclera.sclerago.domain.model.Device
import com.sclera.sclerago.domain.model.LocValue
import com.sclera.sclerago.domain.model.Media
import com.sclera.sclerago.domain.model.NewAsset
import com.sclera.sclerago.domain.model.Permission
import com.sclera.sclerago.domain.model.PhotoKind
import com.sclera.sclerago.domain.model.PhotoRule
import com.sclera.sclerago.domain.model.Source
import com.sclera.sclerago.domain.model.Status
import com.sclera.sclerago.domain.model.SubmitOutcome
import com.sclera.sclerago.domain.model.assetLock
import com.sclera.sclerago.domain.model.FittedPhotos
import com.sclera.sclerago.domain.model.fitPhotoRules
import com.sclera.sclerago.domain.model.typeOf
import com.sclera.sclerago.domain.repository.AccessRepository
import com.sclera.sclerago.domain.repository.AssetActivityRepository
import com.sclera.sclerago.domain.repository.AssetConfigRepository
import com.sclera.sclerago.domain.repository.AssetRepository
import com.sclera.sclerago.domain.repository.AuthRepository
import com.sclera.sclerago.domain.repository.LocalDataReloader
import com.sclera.sclerago.domain.repository.PhotoRulesRepository
import com.sclera.sclerago.domain.system.IdGenerator
import com.sclera.sclerago.domain.system.next
import com.sclera.sclerago.domain.usecase.access.PermissionGuard
import com.sclera.sclerago.domain.usecase.media.DownloadMediaUseCase
import com.sclera.sclerago.domain.usecase.media.SortPhotosUseCase
import com.sclera.sclerago.domain.usecase.media.SortShotUseCase
import com.sclera.sclerago.domain.validation.CheckInput
import com.sclera.sclerago.domain.validation.CommentProblem
import com.sclera.sclerago.domain.validation.checkComment
import com.sclera.sclerago.domain.validation.commentText
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first

class ObserveAssetsUseCase @Inject constructor(private val repo: AssetRepository) {
    operator fun invoke(): StateFlow<AssetCatalog> = repo.catalog
}

/** An asset's comments and history, newest first, again whenever they change. */
/**
 * An asset's comments and history, each with who wrote it as far as this device knows (so it shows offline too: the
 * signed-in user's own name, else the people list, which the server sends to admins only) and, for a comment, whether
 * the signed-in user may change it (its author) or remove it (its author, or anyone who can validate), as the server's
 * comment routes allow.
 */
class ObserveAssetActivityUseCase @Inject constructor(
    private val repo: AssetActivityRepository,
    private val access: AccessRepository,
    private val auth: AuthRepository,
    private val guard: PermissionGuard,
) {
    operator fun invoke(assetId: String): Flow<List<AssetActivity>> {
        val rights = combine(guard.observe(Permission.COLLECT), guard.observe(Permission.VALIDATE)) { collect, validate -> collect to validate }
        return combine(repo.observe(assetId), access.people, auth.state, rights) { entries, people, session, (collect, validate) ->
            // Who wrote each entry, by email (the signed-in user's too), else name.
            val names = people.associate { it.id to it.email.ifBlank { it.name } }
            val myEmail = (session as? AuthState.SignedIn)?.session?.email?.takeIf { it.isNotBlank() }
            val mayComment = collect || validate
            entries.map { e ->
                val comment = e.kind == ActivityKind.COMMENT
                e.copy(
                    authorName = (if (e.byMe) myEmail else null) ?: e.createdBy?.let(names::get),
                    canEdit = comment && mayComment && e.byMe,
                    canDelete = comment && ((mayComment && e.byMe) || validate),
                )
            }
        }
    }
}

/**
 * Comments on an asset, as the server allows: anyone who can collect or validate (else [DataException] FORBIDDEN),
 * with text that passes [checkComment]. Returns the problem with [text] and posts nothing, or null once it is posted.
 */
class AddCommentUseCase @Inject constructor(private val repo: AssetActivityRepository, private val guard: PermissionGuard) {
    /** Whether the signed-in user may comment at all (collect or validate), as the web's comment box. */
    fun allowed(): Boolean = guard.allows(Permission.COLLECT) || guard.allows(Permission.VALIDATE)

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

    /**
     * As the web's Submit (`POST /assets/bulk/submit`): complete assets are submitted, incomplete ones go in as
     * exceptions; returns the counts.
     */
    suspend operator fun invoke(ids: Collection<String>, status: Status): SubmitOutcome {
        guard.requireOffline(permissionFor(status))
        // As the server: assets are signed off in a validation run (which keeps a record of who decided), never by a
        // status move.
        if (status == Status.VALIDATED) throw DataException(DataError.FORBIDDEN)
        // Submitting only moves assets still being collected; any other move of a submitted asset (back to draft) is an
        // admin's, as the server's edit. Someone who isn't an admin submits only the assets assigned to them or to nobody
        // (the server's bulk submit refuses the lot otherwise).
        if (status != Status.SUBMITTED) requireUnlocked(repo, guard, ids) else requireAssigned(repo, guard, ids)
        return repo.setStatus(ids, status, photoRules.forMove(status))
    }
}

private fun permissionFor(status: Status) = if (status == Status.VALIDATED) Permission.VALIDATE else Permission.COLLECT

/** The photo rules a move to [status] counts: the property's when submitting (as the server's submit), none otherwise. */
private suspend fun PhotoRulesRepository.forMove(status: Status): List<PhotoRule> =
    if (status == Status.SUBMITTED) rules().first() else emptyList()

/**
 * Keeps the values from an asset's details form and, when given, moves it to [status] (submitting counts [photoRules]).
 * As the server, a submitted or validated asset can only be changed by an admin (else [DataException] FORBIDDEN).
 */
class SaveAssetDetailsUseCase @Inject constructor(
    private val repo: AssetRepository,
    private val guard: PermissionGuard,
    private val photoRules: PhotoRulesRepository,
) {
    /** [sources]: form field key -> the web page an applied AI suggestion came from. [place]: a new place, kept with the rest. */
    suspend operator fun invoke(
        id: String,
        values: Map<String, String>,
        status: Status? = null,
        sources: Map<String, String> = emptyMap(),
        place: LocValue? = null,
    ) {
        guard.requireOffline(Permission.COLLECT)
        if (status != null) guard.requireOffline(permissionFor(status))
        requireUnlocked(repo, guard, listOf(id))
        repo.saveDetails(id, values, sources, place)
        // Without a status the asset keeps its own, as the server's edit does.
        if (status != null) repo.setStatus(listOf(id), status, photoRules.forMove(status))
    }
}

/**
 * Why the signed-in user may not change [asset] (the server's check_editable), or null when they may: a reviewer
 * (Validate) changes any asset; anyone else one assigned to them or to nobody that isn't submitted or validated.
 */
class CanEditAssetUseCase @Inject constructor(private val guard: PermissionGuard) {
    fun lock(asset: Asset): AssetLock? = when {
        asset.managed -> AssetLock.MANAGED
        // A collector away from a property with geofencing on looks only: nothing can be changed until they are on premise.
        guard.viewOnlyByLocation() -> AssetLock.OFF_PREMISE
        // No Collect permission (a viewer, or a group that only checks): nothing can be changed, whoever they are, as the
        // server refuses it (the web's lockedForMe).
        !guard.allows(Permission.COLLECT) -> AssetLock.NO_PERMISSION
        else -> assetLock(asset.st, guard.isReviewer(), guard.isAssignedElsewhere(asset.assignedTo))
    }

    operator fun invoke(asset: Asset): Boolean = lock(asset) == null
}

/**
 * As the server's check_editable: someone who isn't a reviewer changes no asset assigned to someone else
 * ([DataException] NOT_ASSIGNED), and none once submitted or signed off ([DataException] FORBIDDEN); nothing changes.
 * Unknown ids are left to the writer. (An asset assigned to nobody becomes theirs when the writer saves the change.)
 */
internal fun requireUnlocked(repo: AssetRepository, guard: PermissionGuard, ids: Collection<String>) {
    requireNotManaged(repo, ids)
    if (guard.isReviewer()) return
    val wanted = ids.toSet()
    val locks = repo.catalog.value.assets.filter { it.id in wanted }
        .mapNotNull { assetLock(it.st, reviewer = false, assignedElsewhere = guard.isAssignedElsewhere(it.assignedTo)) }
    if (AssetLock.NOT_ASSIGNED in locks) throw DataException(DataError.NOT_ASSIGNED)
    if (AssetLock.SUBMITTED in locks) throw DataException(DataError.FORBIDDEN)
}

/**
 * Nobody changes an asset in Managed Assets, a reviewer neither: it is read-only here, and the server answers 404 to every
 * change ([DataException] MANAGED; nothing changes).
 */
internal fun requireNotManaged(repo: AssetRepository, ids: Collection<String>) {
    val wanted = ids.toSet()
    if (repo.catalog.value.managed.any { it.id in wanted }) throw DataException(DataError.MANAGED)
}

/**
 * Whether the signed-in user may pick asset [id] for a bulk action (the web's canWorkOn): nobody whose group can only
 * view ([viewOnly]); otherwise a reviewer (Validate) any asset, anyone else one assigned to them or to nobody.
 */
class CanWorkOnAssetUseCase @Inject constructor(private val repo: AssetRepository, private val guard: PermissionGuard) {
    operator fun invoke(id: String): Boolean = checker()(id)

    /** The signed-in user is a reviewer (Validate): they work on every asset (and act on change requests). */
    fun isReviewer(): Boolean = guard.isReviewer()

    /** The signed-in user may add, edit, submit and delete assets ([Permission.COLLECT]); a viewer may not. */
    fun canCollect(): Boolean = guard.allows(Permission.COLLECT)

    /**
     * The signed-in user's group can neither collect nor check (Viewers): nothing changes an asset and there are no
     * validations to run, so there is nothing to select assets for. A collector held to view only by the geofence is
     * the same: nothing to select.
     */
    fun viewOnly(): Boolean = guard.viewOnlyByLocation() || (!guard.allows(Permission.COLLECT) && !guard.allows(Permission.CHECK))

    /** The same check for many ids (say a select-all), worked out once over the property's assets. */
    fun checker(): (String) -> Boolean {
        if (viewOnly()) return { false }
        if (guard.isReviewer()) return { true }
        val elsewhere = repo.catalog.value.assets.filter { guard.isAssignedElsewhere(it.assignedTo) }.mapTo(HashSet()) { it.id }
        return { it !in elsewhere }
    }
}

/**
 * Someone who isn't a reviewer submits no asset assigned to someone else (the server's bulk submit refuses the lot with
 * NOT_ASSIGNED).
 */
private fun requireAssigned(repo: AssetRepository, guard: PermissionGuard, ids: Collection<String>) {
    requireNotManaged(repo, ids)
    if (guard.isReviewer()) return
    val wanted = ids.toSet()
    if (repo.catalog.value.assets.any { it.id in wanted && guard.isAssignedElsewhere(it.assignedTo) }) {
        throw DataException(DataError.NOT_ASSIGNED)
    }
}

class DeleteAssetsUseCase @Inject constructor(private val repo: AssetRepository, private val guard: PermissionGuard) {
    suspend operator fun invoke(ids: Collection<String>) {
        guard.requireOffline(Permission.COLLECT)
        requireUnlocked(repo, guard, ids)
        repo.delete(ids)
    }
}

class AssignLocationUseCase @Inject constructor(private val repo: AssetRepository, private val guard: PermissionGuard) {
    suspend operator fun invoke(ids: Collection<String>, location: LocValue) {
        guard.requireOffline(Permission.COLLECT)
        requireUnlocked(repo, guard, ids)
        repo.assignLocation(ids, location)
    }
}

/**
 * As the server's _check_room: refuses ([DataException] PHOTO_LIMIT) photos of a kind that would take the asset past the
 * most the property's [rules] allow, or of a kind the property doesn't collect. [have] is the asset's media now.
 */
internal fun checkPhotoRoom(rules: List<PhotoRule>, have: List<Media>, adding: List<Media>) {
    adding.filter { it.fieldId == null && it.category != null && !it.video && !it.audio && !it.isDoc }.groupBy { it.category }.forEach { (kind, added) ->
        val rule = rules.firstOrNull { it.kind == kind } ?: return@forEach
        val count = have.count { it.fieldId == null && it.category == kind }
        if (rule.max == 0 || count + added.size > rule.max) throw DataException(DataError.PHOTO_LIMIT)
    }
}

/** Adds photos / videos to an existing asset; false when there is nothing to add. */
class AttachMediaUseCase @Inject constructor(
    private val repo: AssetRepository,
    private val guard: PermissionGuard,
    private val photoRules: PhotoRulesRepository,
) {
    suspend operator fun invoke(assetId: String, media: List<Media>): Boolean {
        if (media.isEmpty() || assetId.isBlank()) return false
        guard.requireOffline(Permission.COLLECT)
        requireUnlocked(repo, guard, listOf(assetId))
        val have = repo.catalog.value.assets.firstOrNull { it.id == assetId }?.media.orEmpty()
        checkPhotoRoom(photoRules.rules().first(), have, media)
        repo.attachMedia(assetId, media)
        return true
    }
}

/** Removes one of an asset's photos, videos, voice memos or files, as the server's media DELETE (collectors only). */
class RemoveMediaUseCase @Inject constructor(private val repo: AssetRepository, private val guard: PermissionGuard) {
    suspend operator fun invoke(assetId: String, mediaId: String) {
        guard.requireOffline(Permission.COLLECT)
        requireUnlocked(repo, guard, listOf(assetId))
        repo.removeMedia(assetId, mediaId)
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
    /**
     * Whether the signed-in user may add assets ([Permission.COLLECT]), as the web shows its Add only then: checked before
     * anything is captured or typed, so the work isn't lost to a refusal when it is saved.
     */
    fun allowed(): Boolean = guard.allows(Permission.COLLECT)

    /** [allowed], again whenever the access is fetched or changes (for showing or hiding a way to add). */
    fun observeAllowed(): Flow<Boolean> = guard.observe(Permission.COLLECT)

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
    private val sortPhotos: SortPhotosUseCase,
    /** The phone's 12/24-hour setting, for the "captured" time on a camera asset's detail line. */
    private val clock: ClockFormat = ClockFormat { true },
) {
    enum class Via { CAMERA, SCAN, MANUAL }

    /** The asset made; [leftOut]: shots left out as their kind already had the most photos the property allows. */
    data class Created(val id: String, val via: Via, val source: Source, val leftOut: FittedPhotos = FittedPhotos(emptyList(), emptyList()))

    /**
     * Whether the signed-in user may create an asset ([Permission.COLLECT]), as the web shows its Add only then. The
     * screens check it before opening the camera or a new-asset form, so nobody types an asset that is refused at save.
     */
    fun allowed(): Boolean = guard.allows(Permission.COLLECT)

    /** [allowed], again whenever the access is fetched or changes (for showing or hiding a way to add). */
    fun observeAllowed(): Flow<Boolean> = guard.observe(Permission.COLLECT)

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
        /** False when [captures] are already sorted (on the page, perhaps moved between kinds by hand): kept as they are. */
        sortCaptures: Boolean = true,
    ): Created {
        guard.requireOffline(Permission.COLLECT)
        val id = ids.next()
        // As the server: an asset is created as a draft; submitting it is a move after that, by the submit rules.
        val st = Status.DRAFT
        val formDetail = detail.takeUnless { it.isBlank() || it == "Added manually" }
        val via = viaFor(captures, device)
        var fitted = FittedPhotos(emptyList(), emptyList())

        val (asset, source) = when (via) {
            Via.CAMERA -> {
                // From the Camera tab: the shot(s), plus anything added in the form. Each shot is read on the device and
                // sorted into nameplate, label ID or asset; one that can't be read is an equipment (asset) photo, as before.
                val first = captures.first()
                val captured = (if (first.video) "Video ${first.durationText}" else "Photo") + " · captured " + clockTimeLabel(first.createdAt, clock.is24Hour())
                // Several shots may sort into one kind: those past its most are left out, as the server refuses them.
                val sorted = if (sortCaptures) sortPhotos(captures, fallback = PhotoKind.ASSET) else captures
                fitted = fitPhotoRules(photoRules.rules().first(), emptyList(), sorted + media)
                val shots = fitted.kept
                // The name is what the form has (typed, or read off the photo by the AI); no "Asset from photo …" stand-in.
                Asset(id, name, formDetail ?: captured, place.loc, place.where, Source.CAMERA, st, typeOf(name), place.prop, shots, locationId = place.placeId) to Source.CAMERA
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
        // Its photos of each kind stay within the property's photo rules, as each upload to the server is checked.
        checkPhotoRoom(photoRules.rules().first(), emptyList(), asset.media)
        // Its AI flag is worked out from its empty fields once it is written, as the server's POST /assets.
        repo.add(listOf(NewAsset(asset, values)))
        // With a mandatory field empty or too few photos it goes in as an exception (the screen asks about that first).
        if (submit) repo.setStatus(listOf(id), Status.SUBMITTED, photoRules.forMove(Status.SUBMITTED))
        return Created(id, via, source, fitted.copy(kept = emptyList()))
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
    val removeMedia: RemoveMediaUseCase,
    val downloadMedia: DownloadMediaUseCase,
    val sortPhotos: SortPhotosUseCase,
    val sortShot: SortShotUseCase,
    val add: AddAssetsUseCase,
    val create: CreateAssetUseCase,
    val checkInput: BuildCheckInputUseCase,
    val buildList: BuildAssetListUseCase,
    val toggleSelection: ToggleSelectionUseCase,
    val selectAll: SelectAllUseCase,
    val canWorkOn: CanWorkOnAssetUseCase,
    val evaluateChecks: EvaluateAssetChecksUseCase,
    val autofill: AutofillAssetUseCase,
    val formValues: BuildFormValuesUseCase,
    val activity: ObserveAssetActivityUseCase,
    val addComment: AddCommentUseCase,
    val updateComment: UpdateCommentUseCase,
    val removeComment: RemoveCommentUseCase,
)
