package com.sclera.sclerago.presentation.assets

import com.sclera.sclerago.domain.usecase.asset.CHANGE_NOTE_MAX
import com.sclera.sclerago.domain.usecase.asset.ChangeRequestUseCases
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sclera.sclerago.R
import com.sclera.sclerago.domain.model.Asset
import com.sclera.sclerago.domain.model.AssetActivity
import com.sclera.sclerago.domain.model.AssetDocument
import com.sclera.sclerago.domain.model.AssetLock
import com.sclera.sclerago.domain.model.AssetDetails
import com.sclera.sclerago.domain.model.DataException
import com.sclera.sclerago.domain.model.DocumentSource
import com.sclera.sclerago.domain.model.IMAGE_KIND
import com.sclera.sclerago.domain.model.documentKind
import com.sclera.sclerago.domain.model.FieldCfg
import com.sclera.sclerago.domain.model.FormEntry
import com.sclera.sclerago.domain.model.LocNode
import com.sclera.sclerago.domain.model.LocValue
import com.sclera.sclerago.domain.model.MasterFormat
import kotlinx.coroutines.Job
import com.sclera.sclerago.domain.model.UNCLASSIFIED
import com.sclera.sclerago.domain.model.Unheard
import com.sclera.sclerago.domain.model.formatFor
import com.sclera.sclerago.domain.model.SpecLookup
import com.sclera.sclerago.domain.model.SURE_ENOUGH
import com.sclera.sclerago.domain.model.VOICE_SURE_ENOUGH
import com.sclera.sclerago.domain.model.LookupStatus
import com.sclera.sclerago.domain.model.Media
import com.sclera.sclerago.domain.model.fitPhotoRules
import com.sclera.sclerago.domain.model.photoRoom
import com.sclera.sclerago.domain.model.PhotoKind
import com.sclera.sclerago.domain.model.missingRequiredFields
import com.sclera.sclerago.domain.model.photoCounts
import com.sclera.sclerago.domain.model.photosShort
import com.sclera.sclerago.domain.model.PhotoRule
import com.sclera.sclerago.domain.model.PlaceNames
import com.sclera.sclerago.domain.model.OnlineError
import com.sclera.sclerago.domain.usecase.auth.ObserveOnlineUseCase
import com.sclera.sclerago.domain.model.OnlineException
import com.sclera.sclerago.domain.model.Source
import com.sclera.sclerago.domain.model.Status
import com.sclera.sclerago.domain.usecase.asset.AssetFormUseCases
import com.sclera.sclerago.domain.usecase.asset.AssetHandling
import com.sclera.sclerago.domain.usecase.asset.AssetHandlingUseCase
import com.sclera.sclerago.domain.usecase.asset.JacsUseCases
import com.sclera.sclerago.domain.usecase.asset.PhotoFill
import com.sclera.sclerago.domain.usecase.asset.PlanPhotoFillUseCase
import com.sclera.sclerago.domain.usecase.asset.AssetUseCases
import com.sclera.sclerago.domain.usecase.asset.CanEditAssetUseCase
import com.sclera.sclerago.domain.usecase.asset.ClassifyAssetUseCases
import com.sclera.sclerago.domain.usecase.config.ObserveAssetConfigUseCase
import com.sclera.sclerago.domain.validation.AssetValidation
import com.sclera.sclerago.domain.validation.COMMENT_MAX_LENGTH
import com.sclera.sclerago.domain.validation.CommentProblem
import com.sclera.sclerago.presentation.common.UiText
import com.sclera.sclerago.presentation.common.toUiText
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

/** Everything the asset details screen shows; [asset] is null while nothing is open (or after it was deleted). */
data class AssetDetailState(
    val asset: Asset? = null,
    val isNew: Boolean = false,
    /** The names of the building, floor and location the asset is filed under. */
    val place: PlaceNames = PlaceNames(),
    /** The asset type catalogue the System details dropdowns offer. */
    val formats: List<MasterFormat> = emptyList(),
    /** The property's photo rules, which the Media tab's photo cards follow. */
    val photoRules: List<PhotoRule> = emptyList(),
    /** The rows of the Info tab's Asset fields, in the server's order (photo, video and voice memo fields included). */
    val fieldRows: List<FormEntry> = emptyList(),
    /** The Documents tab: the file it was read from, the pages its values came from, its model's manuals. */
    val documents: List<AssetDocument> = emptyList(),
    /** Values as last saved (what the form started from). */
    val saved: Map<String, String> = emptyMap(),
    /** Values typed since, by field key. */
    val edits: Map<String, String> = emptyMap(),
    val fields: List<FieldCfg> = emptyList(),
    val details: AssetDetails? = null,
    /** Photos, videos and documents: the asset own ones, plus the ones picked for a new asset. */
    val media: List<Media> = emptyList(),
    /** Picks made while the asset is new (kept until it is created). */
    val newPicks: List<Media> = emptyList(),
    /** The asset's validation as the server works it out from the synced runs (null: never validated, or new). */
    val validation: AssetValidation? = null,
    /** The asset is being validated again at the server. */
    val validating: Boolean = false,
    /** The change request note being written (null: its dialog is closed). */
    val changeNote: String? = null,
    /** The signed-in user made the asset's change request. */
    val changeRequestMine: Boolean = false,
    /** Who made the asset's change request (name, else email); null when they aren't known on this device. */
    val changeRequester: String? = null,
    /** The signed-in user is an admin (who can decline a change request or reopen the asset). */
    val admin: Boolean = false,
    /** The asset's comments and history, newest first (none for a new asset). */
    val activity: List<AssetActivity> = emptyList(),
    /** Who the asset is assigned to and who submitted it, when (null for a new asset). */
    val handling: AssetHandling? = null,
    /** The comment being typed in the Comments tab, why it can't be posted (if so), and whether it is being posted. */
    val commentDraft: String = "",
    val commentError: UiText? = null,
    val commentPosting: Boolean = false,
    val savedFlash: Boolean = false,
    /** The AI suggestions dialog while it is open (null: closed). */
    val ai: AiDialogState? = null,
    /** A spec look-up is running, shown or not (the AI button spins). */
    val aiBusy: Boolean = false,
    /** The voice fill sheet while it is open: what was heard and the values found in it (null: closed). */
    val voice: VoiceFillState? = null,
    /** What was said is being read by the server's AI (the mic shows it is working). */
    val voiceReading: Boolean = false,
    /** Photos just added are being read on the device to sort them into nameplate, label ID or asset. */
    val sortingPhotos: Boolean = false,
    /**
     * Why the signed-in user may not change this asset (the server's check_editable: only an admin changes a submitted
     * or validated one, or one assigned to someone else); null when they may.
     */
    val lock: AssetLock? = null,
    /** The signed-in user may validate the asset (the server's check permission; validate includes it). */
    val canCheck: Boolean = true,
    /** The JACS code picker while it is open (null: closed). */
    val jacs: JacsPickerState? = null,
    /** The "Suggest type" sheet while it is open (null: closed). */
    val classify: ClassifyState? = null,
    /** A building, floor or location picked for the asset and not saved yet ([place] already shows it). */
    val pendingPlace: LocValue? = null,
    /** The device is online; offline, the server-only actions (AI, voice, Suggest type, Validate, Assign) say so instead. */
    val online: Boolean = true,
) {
    val values: Map<String, String> get() = saved + edits
    fun value(key: String): String = edits[key] ?: saved[key].orEmpty()
    fun dirty(key: String): Boolean = key in edits && edits[key] != saved[key].orEmpty()

    /** Unsaved changes: the fields changed, and the place when one was picked. */
    val dirtyCount: Int get() = edits.keys.count { dirty(it) } + if (pendingPlace != null) 1 else 0

    /** The signed-in user may change this asset. */
    val canEdit: Boolean get() = lock == null

    /** Read-only for anyone but an admin: submitted or validated (until a recollect is requested), or someone else's. */
    val locked: Boolean get() = !isNew && !canEdit

    /** The mandatory fields still empty in the form. */
    val missingRequired: List<FieldCfg> get() = missingRequiredFields(fields, values)

    /** How many more photos of each kind the property's photo rules ask for. */
    val photosNeeded: Map<PhotoKind, Int> get() = photosShort(photoRules, photoCounts(media))

    /** Submitting would keep the asset as submitted, not an exception: no mandatory field empty and enough photos. */
    val complete: Boolean get() = missingRequired.isEmpty() && photosNeeded.isEmpty()
    val fromCamera: Boolean get() = asset?.src == Source.CAMERA

    /** The field the nameplate reading was unsure about, until its value is changed. */
    val lowConfidenceKey: String?
        get() {
            val key = details?.lowConfidence?.takeIf { fromCamera } ?: return null
            val original = details.fields[key].orEmpty()
            return key.takeIf { value(key).isNotEmpty() && value(key) == original }
        }
}

/** Where the AI suggestions dialog is: loading, asking for the model, searching, showing suggestions, or failed. */
enum class AiPhase { LOADING, IDENTIFY, SEARCHING, RESULTS, ERROR }

/** One suggested value: ticked to be saved, and its (editable) text. */
data class AiPick(val on: Boolean, val value: String)

/** The AI suggestions dialog: the manufacturer and model to look up, the look-up and its suggestions by field key. */
data class AiDialogState(
    val phase: AiPhase,
    val make: String = "",
    val model: String = "",
    val lookup: SpecLookup? = null,
    val picks: Map<String, AiPick> = emptyMap(),
    /** When the shown search started (for its progress). */
    val startedAt: Long? = null,
    val error: UiText? = null,
    /** The search under way is the AI reading the asset's photo (then looking up the rest), not a model look-up. */
    val fromPhoto: Boolean = false,
    /** The suggested type is ticked to go into the form; null when no type is offered. */
    val typeOn: Boolean? = null,
)

/** One value heard for a field ([value] as the form stores it), ticked to go into the form. */
data class VoicePick(val on: Boolean, val value: String)

/**
 * What was said and the values found in it by field key, with how sure of each (0..1; none for a value without one),
 * what was said that no field took, and whether the server's AI read it ([byAi]) or the phone did (offline).
 */
data class VoiceFillState(
    val said: String,
    val picks: Map<String, VoicePick>,
    val sure: Map<String, Double> = emptyMap(),
    val unmatched: List<Unheard> = emptyList(),
    val byAi: Boolean = true,
)

/**
 * One asset details screen: the form edits, Save / Submit / Validate, photos, the asset's validation (and
 * validating it again at the server) and the AI suggestions. Creating a new asset and deleting one change what is on screen, so the host of
 * the screen does those (with the values from [AssetDetailState]).
 */
@HiltViewModel
class AssetDetailViewModel @Inject constructor(
    private val useCases: AssetUseCases,
    observeConfig: ObserveAssetConfigUseCase,
    private val formData: AssetFormUseCases,
    private val canEditAsset: CanEditAssetUseCase,
    private val classifying: ClassifyAssetUseCases,
    private val changeRequests: ChangeRequestUseCases,
    private val handling: AssetHandlingUseCase,
    private val jacsCodes: JacsUseCases,
    private val planPhotoFill: PlanPhotoFillUseCase,
    observeOnline: ObserveOnlineUseCase,
) : ViewModel() {
    private data class Target(val asset: Asset, val isNew: Boolean, val prefill: Map<String, String>)

    /** What the open asset's tabs read from the property's data besides the asset itself ([fields] null: use the standard ones). */
    private data class FromDb(
        val activity: List<AssetActivity> = emptyList(),
        val fields: List<FieldCfg>? = null,
        val formats: List<MasterFormat> = emptyList(),
        val tree: List<LocNode> = emptyList(),
        val validation: AssetValidation? = null,
        val photoRules: List<PhotoRule> = emptyList(),
        val rows: List<FormEntry>? = null,
        val documents: List<AssetDocument> = emptyList(),
    )

    /** Where an applied AI suggestion came from ([page]) and the [value] it put in the form. */
    private data class AiSource(val page: String, val value: String)

    private data class Form(
        val edits: Map<String, String> = emptyMap(),
        /** Field key -> the page each applied AI suggestion came from; dropped when that field is typed over. */
        val aiSources: Map<String, AiSource> = emptyMap(),
        val place: LocValue? = null,
        val savedFlash: Boolean = false,
        val ai: AiDialogState? = null,
        val aiOpen: Boolean = false,
        val voice: VoiceFillState? = null,
        val voiceReading: Boolean = false,
        val newPicks: List<Media> = emptyList(),
        val commentDraft: String = "",
        val commentProblem: CommentProblem? = null,
        val commentPosting: Boolean = false,
        val validating: Boolean = false,
        /** The form is being filled from photos just added (the AI button turns). */
        val photoFilling: Boolean = false,
        /** How many batches of added photos are being sorted on the device. */
        val sorting: Int = 0,
        /** The change request note being written; null while its dialog is closed. */
        val changeNote: String? = null,
    )

    private val target = MutableStateFlow<Target?>(null)
    private val form = MutableStateFlow(Form())
    private val message = MutableSharedFlow<UiText>(extraBufferCapacity = 8)
    private val classifier = AssetClassifier(classifying, viewModelScope)
    private val jacsPicker = JacsPicker(jacsCodes, viewModelScope)
    private val photoFill = PhotoAutoFill(viewModelScope) { fillFromPhotos() }

    /**
     * The classifier's part of the screen, the JACS code picker and whether the device is online, as one (the screen's
     * state takes five sources).
     */
    private val pickers = combine(classifier.ui, jacsPicker.state, observeOnline()) { classified, jacs, online -> Triple(classified, jacs, online) }

    /** Messages to flash after saving, submitting, validating or adding media. */
    val notices: SharedFlow<UiText> = message.asSharedFlow()

    /**
     * The open asset's comments and history and its fields (the standard ones plus its own), with the type catalogue and
     * the place tree; a new asset has no activity or fields of its own yet.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val fromDb: Flow<FromDb> = target
        .map { t -> t?.takeIf { !it.isNew }?.asset?.id }
        .distinctUntilChanged()
        .flatMapLatest { id ->
            val log = if (id == null) flowOf(emptyList()) else useCases.activity(id)
            val fields: Flow<List<FieldCfg>?> = if (id == null) flowOf(null) else formData.fields(id)
            val validation: Flow<AssetValidation?> = if (id == null) flowOf(null) else formData.validation(id)
            val docs: Flow<List<AssetDocument>> = if (id == null) flowOf(emptyList()) else formData.documents(id)
            val checksAndDocs = combine(validation, docs) { v, d -> v to d }
            val property = combine(formData.formats(), formData.placeTree(), formData.photoRules()) { m, tree, rules -> Triple(m, tree, rules) }
            val rows: Flow<List<FormEntry>?> = if (id == null) flowOf(null) else formData.fieldRows(id)
            combine(log, fields, checksAndDocs, property, rows) { a, f, (v, d), (m, tree, rules), r -> FromDb(a, f, m, tree, v, rules, r, d) }
        }

    /** The open asset as the catalog has it, with its details: read again when either changes, not on every keystroke. */
    private data class Opened(val target: Target, val asset: Asset?, val details: AssetDetails?)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val opened: Flow<Opened?> = combine(target, useCases.observe()) { t, catalog -> t to catalog }
        .mapLatest { (t, catalog) ->
            when {
                t == null -> null
                t.isNew -> Opened(t, t.asset, null)
                else -> catalog.assets.firstOrNull { it.id == t.asset.id }.let { a -> Opened(t, a, a?.let { useCases.details(it.id) }) }
            }
        }

    val state: StateFlow<AssetDetailState> =
        combine(opened, form, observeConfig(), fromDb, pickers) { o, f, config, db, (classified, jacs, online) ->
            val t = o?.target ?: return@combine AssetDetailState()
            val asset = o.asset ?: return@combine AssetDetailState()
            val details = o.details
            val own = db.fields ?: config.fields
            // The chosen type's fields the asset doesn't have yet come after its own, until saving makes them.
            val typeExtra = classified.typeFields.filter { tf -> own.none { it.label.equals(tf.label, true) } }
            val fields = own + typeExtra
            val saved = useCases.formValues(asset.name, t.isNew, details, own, t.prefill)
            AssetDetailState(
                asset = asset, isNew = t.isNew,
                // A place picked and not saved yet shows instead of the saved one.
                place = f.place?.placeId?.let { formData.placeNames(db.tree, null, null, it) }
                    ?: formData.placeNames(db.tree, asset.buildingId, asset.floorId, asset.locationId),
                pendingPlace = f.place,
                formats = db.formats, photoRules = db.photoRules,
                fieldRows = (db.rows ?: own.filter { it.visible }.map { FormEntry.Input(it.key) }) + typeExtra.map { FormEntry.Input(it.key) },
                // A new asset's document is only picked so far: it shows here until the asset is created.
                documents = if (t.isNew) f.newPicks.filter { it.sourceDocument }.map { it.toPendingDocument() } else db.documents,
                saved = saved, edits = f.edits, fields = fields, details = details,
                media = asset.media + f.newPicks.filterNot { it.sourceDocument }, newPicks = f.newPicks, savedFlash = f.savedFlash,
                ai = f.ai?.takeIf { f.aiOpen }, aiBusy = f.ai?.phase == AiPhase.SEARCHING || f.photoFilling, voice = f.voice, voiceReading = f.voiceReading, sortingPhotos = f.sorting > 0,
                activity = if (t.isNew) emptyList() else db.activity,
                handling = if (t.isNew) null else handling(asset, db.activity),
                commentDraft = f.commentDraft, commentError = f.commentProblem?.problemText(), commentPosting = f.commentPosting,
                validation = if (t.isNew) null else db.validation, validating = f.validating,
                changeNote = f.changeNote, changeRequestMine = changeRequests.isMine(asset.changeRequest), admin = changeRequests.isAdmin(),
                changeRequester = changeRequests.requesterName(asset.changeRequest),
                lock = if (t.isNew) null else canEditAsset.lock(asset),
                canCheck = formData.revalidate.allowed(),
                classify = classified.sheet,
                jacs = jacs,
                online = online,
            )
        }.stateIn(viewModelScope, SharingStarted.Eagerly, AssetDetailState())

    /** Shows [asset]; does nothing when that asset is already open, so edits survive recomposition. */
    fun open(asset: Asset, isNew: Boolean, prefill: Map<String, String> = emptyMap()) {
        val current = target.value
        if (current != null && current.asset.id == asset.id && current.isNew == isNew) return
        aiJob?.cancel()
        classifier.reset()
        jacsPicker.close()
        photoFill.cancel()
        form.value = Form()
        target.value = Target(asset, isNew, prefill)
        classifier.refreshOnce()
        if (isNew) startWithCaptures(asset)
    }

    /**
     * A new asset opened with photos already taken (the Camera tab): they are handled as photos added on the page are —
     * sorted on the device into nameplate, label ID or asset (one that can't be read keeps no kind), then the form is
     * filled from them by the AI.
     */
    private fun startWithCaptures(asset: Asset) {
        if (asset.media.none { !it.video && !it.audio && !it.isDoc }) return
        viewModelScope.launch {
            if (useCases.sortPhotos.needed(asset.media)) {
                form.update { it.copy(sorting = it.sorting + 1) }
                val sorted = try {
                    useCases.sortPhotos(asset.media, fallback = null)
                } finally {
                    form.update { it.copy(sorting = it.sorting - 1) }
                }
                target.update { t -> if (t != null && t.isNew && t.asset.id == asset.id) t.copy(asset = t.asset.copy(media = sorted)) else t }
            }
            // Several shots may sort into one kind: those past its most go to Other photos.
            val shots = target.value?.takeIf { it.isNew && it.asset.id == asset.id }?.asset?.media.orEmpty()
            val (fitted, moved) = fitPhotoRules(state.value.photoRules, emptyList(), shots)
            if (moved > 0) {
                target.update { t -> if (t != null && t.isNew && t.asset.id == asset.id) t.copy(asset = t.asset.copy(media = fitted)) else t }
                message.emit(UiText.Plural(R.plurals.photos_to_other, moved))
            }
            if (target.value?.let { it.isNew && it.asset.id == asset.id } == true) photoFill.schedule()
        }
    }

    fun close() {
        aiJob?.cancel()
        classifier.reset()
        photoFill.cancel()
        target.value = null
        form.value = Form()
    }

    fun edit(key: String, value: String) {
        val s = state.value
        if (s.locked) return
        // A new system or asset type also resets the types below it that the catalogue doesn't file under it.
        val changes = if (key in CLASS_KEYS) formData.reclassify(s.formats, s.values, key, value) else mapOf(key to value)
        // A value typed over an applied AI suggestion no longer came from its page, as the server drops it.
        form.update { it.copy(edits = it.edits + changes, aiSources = it.aiSources - changes.keys, savedFlash = false) }
        // A new type shows its fields.
        if (key in CLASS_KEYS) classifier.showTypeFields(s.formats, s.fields, s.values + changes)
    }

    /**
     * The building, floor or location picked for the open asset: kept as an unsaved change (written on Save, dropped
     * on Discard), or dropped when it is where the asset already is.
     */
    fun choosePlace(place: LocValue) {
        val asset = state.value.asset ?: return
        if (state.value.locked) return
        val current = asset.locationId ?: asset.floorId ?: asset.buildingId
        form.update { it.copy(place = place.takeIf { p -> p.placeId != current }, savedFlash = false) }
    }

    fun discardEdits() {
        form.update { it.copy(edits = emptyMap(), aiSources = emptyMap(), place = null) }
        classifier.clearTypeFields()
    }

    /** Keeps the edits; the asset keeps its status, as the server's edit does. */
    fun save() {
        val s = state.value
        val id = s.asset?.id ?: return
        if (s.dirtyCount == 0) return
        write {
            // An edit keeps the asset's status, as the server's; its AI flag is worked out again from its empty fields. The
            // place picked goes in with the rest of the changes, in one history line.
            useCases.saveDetails(id, s.values, sources = aiPages(s), place = s.pendingPlace)
            clearEditsOnceRefreshed(s)
            form.update { it.copy(savedFlash = true) }
            state.value.let { classifier.showTypeFields(it.formats, it.fields, it.values) }
            message.emit(UiText.Res(R.string.asset_changes_saved))
        }
    }

    /**
     * Keeps the edits and submits the asset, as the server's submit: with a mandatory field empty or too few photos it
     * becomes an exception (the screen asks first).
     */
    fun submit() {
        val s = state.value
        val id = s.asset?.id ?: return
        write {
            useCases.saveDetails(id, s.values, Status.SUBMITTED, sources = aiPages(s), place = s.pendingPlace)
            clearEditsOnceRefreshed(s)
            message.emit(UiText.Res(if (s.complete) R.string.asset_submitted_notice else R.string.asset_exception_notice))
        }
    }

    /** Drops the typed edits once the screen shows what was saved, so the form does not flash the old values in between. */
    private suspend fun clearEditsOnceRefreshed(before: AssetDetailState) {
        withTimeoutOrNull(REFRESH_MS) { state.first { it.asset != before.asset || it.saved != before.saved } }
        // The picked place goes too: the asset now has it, so it shows from the asset itself.
        form.update { it.copy(edits = emptyMap(), aiSources = emptyMap(), place = null) }
    }

    /** Field key -> page, for the applied AI suggestions the form still holds as they were applied. */
    private fun aiPages(s: AssetDetailState): Map<String, String> =
        form.value.aiSources.filter { (key, src) -> s.value(key).trim() == src.value }.mapValues { it.value.page }

    /** Makes a change to the offline data in the background; when it is refused or fails the user is told why. */
    private fun write(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: DataException) {
                message.emit(e.error.toUiText())
            }
        }
    }

    /** The kind of photo the next photos added are (a Media tab card's Add); null: other photos. */
    private var photoKind: PhotoKind? = null

    /** How many more photos the camera may add now: room in the kind of the card it was opened from, else in the asset. */
    fun cameraRoom(): Int = photoRoom(state.value.photoRules, state.value.media, photoKind)

    fun choosePhotoKind(kind: PhotoKind?) {
        photoKind = kind
    }

    /**
     * Photos, videos or documents added to the asset (kept in the form until a new asset is created). Photos added from a
     * photo card are of that card's kind; videos and files never are. Other photos (the camera's Other photo, or picked
     * from Files) are read on the device first and sorted into nameplate, label ID or asset; one that can't be read gets
     * no kind, as before.
     */
    fun attach(picked: List<Media>) {
        val kind = photoKind.also { photoKind = null }
        val media = picked.map { if (!it.video && !it.isDoc && !it.audio) it.copy(category = kind) else it }
        if (!useCases.sortPhotos.needed(media)) {
            addPicked(media)
            return
        }
        val assetId = state.value.asset?.id
        viewModelScope.launch {
            form.update { it.copy(sorting = it.sorting + 1) }
            val sorted = try {
                useCases.sortPhotos(media, fallback = null)
            } finally {
                form.update { it.copy(sorting = it.sorting - 1) }
            }
            // Another asset was opened meanwhile: these photos were for the one before.
            if (state.value.asset?.id == assetId) addPicked(sorted)
        }
    }

    /**
     * The Documents tab's upload: [doc] becomes the asset's source document (kept in the form until a new asset is
     * created). A file the server doesn't take as a document is added as an attachment instead.
     */
    fun attachDocument(doc: Media) {
        photoKind = null
        val isDocument = documentKind(doc.fileName.orEmpty(), null).let { it != null && it != IMAGE_KIND }
        addPicked(listOf(if (isDocument) doc.copy(sourceDocument = true, category = null) else doc))
    }

    private fun addPicked(picked: List<Media>) {
        val s = state.value
        val asset = s.asset ?: return
        if (picked.isEmpty()) return
        // A kind that is full (by its card, or as sorted off the photo) passes the extra photos to Other photos.
        val (media, moved) = fitPhotoRules(s.photoRules, s.media, picked)
        if (moved > 0) message.tryEmit(UiText.Plural(R.plurals.photos_to_other, moved))
        // Photos (not videos, memos or files alone) fill the form, as on iOS, once they have joined the page.
        val photos = media.any { !it.video && !it.audio && !it.isDoc }
        if (s.isNew) {
            form.update { it.copy(newPicks = it.newPicks + media) }
            if (photos) photoFill.schedule()
        } else write {
            if (useCases.attachMedia(asset.id, media)) message.emit(UiText.Plural(R.plurals.items_added_to_asset, media.size))
            if (photos) photoFill.schedule()
        }
    }

    /**
     * Fills the form from the asset's photos once new ones are added, as the iOS app does: the phone's look-up of the
     * photos (`POST /lookup`: the type, its JACS code and field values in one answer), then [PlanPhotoFillUseCase]'s
     * rules, which never overwrite what is there. Everything goes in as unsaved edits. Offline it says why it didn't run.
     */
    private suspend fun fillFromPhotos() {
        val s = state.value
        val asset = s.asset ?: return
        if (s.locked) return
        val photos = formData.lookups.photosToRead(s.media)
        if (photos.isEmpty()) return
        form.update { it.copy(photoFilling = true) }
        message.emit(UiText.Res(R.string.ai_auto_reading))
        val lookup = try {
            // No type is sent: the answer then names the type with its JACS code, and fills the fields of the type it
            // names (which is the asset's own type when it already has the right one).
            formData.lookups.lookUpOnPhone(asset.id, photos, s.values.filterValues { it.isNotBlank() }, masterFormatId = null)
        } catch (e: OnlineException) {
            message.emit(
                if (e.error == OnlineError.Offline) UiText.Res(R.string.ai_auto_offline)
                else UiText.Res(R.string.ai_auto_failed, listOf(e.error.aiText())),
            )
            return
        } finally {
            form.update { it.copy(photoFilling = false) }
        }
        // What is in the form now decides (it may have changed while the photos were read).
        val now = state.value
        if (now.locked || now.asset?.id != asset.id) return
        val fill = planPhotoFill(lookup, now::value, now.formats.formatFor(now.values)?.id)
        if (fill.values.isEmpty()) {
            message.emit(UiText.Res(R.string.ai_auto_nothing))
            return
        }
        // Each looked-up field's page goes with it, kept on Save while the value stays as suggested.
        val pages = fill.sources.mapValues { (key, page) -> AiSource(page, fill.values.getValue(key)) }
        form.update { it.copy(edits = it.edits + fill.values, aiSources = it.aiSources - fill.values.keys + pages, savedFlash = false) }
        if (fill.typeSet) classifier.showTypeFields(now.formats, now.fields, now.values + fill.values)
        message.emit(photoFillText(fill))
    }

    /** "AI filled the type, the JACS code and 5 fields. Check the yellow ones, then save." */
    private fun photoFillText(fill: PhotoFill): UiText {
        val parts = buildList<UiText> {
            if (fill.typeSet) add(UiText.Res(R.string.ai_auto_type))
            if (fill.jacsSet) add(UiText.Res(R.string.ai_auto_jacs))
            if (fill.fields > 0) add(UiText.Plural(R.plurals.ai_auto_fields, fill.fields))
        }
        val listed = parts.dropLast(1).reduceOrNull { a, b -> UiText.Res(R.string.ai_auto_list, listOf(a, b)) }
            ?.let { UiText.Res(R.string.ai_auto_and, listOf(it, parts.last())) } ?: parts.last()
        return UiText.Res(R.string.ai_auto_done, listOf(listed))
    }

    /** Removes [m] from the saved asset, as the web's Remove (not on a read-only asset); the sync pushes it. */
    fun removeMedia(m: Media) {
        val s = state.value
        val asset = s.asset ?: return
        if (s.isNew || s.locked) return
        write {
            useCases.removeMedia(asset.id, m.id)
            message.emit(UiText.Res(R.string.media_removed))
        }
    }

    /** Saves a copy of [m] to [target], the document the user picked to save it as (null: they cancelled). */
    fun downloadMedia(m: Media, target: String?) {
        if (target == null) return
        viewModelScope.launch {
            message.emit(UiText.Res(if (useCases.downloadMedia(m, target)) R.string.media_downloaded else R.string.media_download_failed))
        }
    }

    /** Opens the "Suggest type" sheet: the server reads the asset's photos and what is typed, and suggests its type and JACS code. */
    // ---- JACS code: picked from the catalog, which sets the type too (as the web)

    fun openJacs() {
        if (state.value.locked) return
        jacsPicker.open()
    }

    fun searchJacs(text: String) = jacsPicker.query(text)

    fun moreJacs() = jacsPicker.more()

    fun closeJacs() = jacsPicker.close()

    /**
     * Puts the JACS [code] in the form (blank: none) and, as the web does, the type the property has for it: its
     * system, asset and sub type, whose fields then show. Without a connection, or a type for the code, only the code
     * changes. Nothing is saved until Save.
     */
    fun pickJacs(code: String) {
        if (state.value.locked) return
        jacsPicker.close()
        val value = code.trim()
        classifier.noteCode(value)
        form.update { it.copy(edits = it.edits + ("jacs" to value), aiSources = it.aiSources - "jacs", savedFlash = false) }
        if (value.isEmpty()) return
        viewModelScope.launch {
            val type = try {
                jacsCodes.typeFor(value)
            } catch (_: OnlineException) {
                null
            } ?: return@launch
            // Picked another code meanwhile: that one decides.
            if (state.value.value("jacs") != value) return@launch
            val update = mapOf("sys" to type.systemType, "typ" to (type.assetType ?: UNCLASSIFIED), "sub" to (type.subtype ?: UNCLASSIFIED))
            form.update { it.copy(edits = it.edits + update, aiSources = it.aiSources - update.keys) }
            val s = state.value
            classifier.showTypeFields(s.formats, s.fields, s.values)
            val path = listOfNotNull(type.systemType, type.assetType, type.subtype).filter { it.isNotBlank() }.joinToString(" › ")
            message.emit(UiText.Res(R.string.jacs_type_set, listOf(path)))
        }
    }

    fun openClassify() {
        val s = state.value
        if (s.asset == null || s.locked) return
        // As the server takes it: a saved asset by its id (its saved answers and stored photos, with the photos here
        // first); with unsaved edits, by what is typed, so those count.
        classifier.open(s.fields, s::value, s.media, s.asset.id.takeIf { !s.isNew && s.dirtyCount == 0 })
    }

    /**
     * Picks a suggested type and puts its system, asset and sub type into the form at once (the form then shows the
     * type's fields), then its likeliest JACS code once that is known; the code that came with the last type picked is
     * taken out first. Nothing is saved until Save.
     */
    fun pickSuggestedType(masterFormatId: String) {
        val s = state.value
        if (s.locked || s.classify?.picked == masterFormatId) return
        val oldCode = classifier.pickedCode()
        classifier.pickType(masterFormatId)
        val update = classifier.typeValues() ?: return
        editFromSuggestion(update, remove = oldCode?.let { mapOf("jacs" to it) }.orEmpty())
        classifier.showTypeFields(s.formats, s.fields, s.values + update)
        viewModelScope.launch {
            val code = classifier.likeliestCode(masterFormatId) ?: return@launch
            if (state.value.classify?.picked == masterFormatId) editFromSuggestion(mapOf("jacs" to code))
        }
    }

    /** Picks another of the picked type's JACS codes into the form, in place of the one that went in with the type. */
    fun pickSuggestedCode(code: String) {
        if (state.value.locked) return
        val oldCode = classifier.pickedCode()
        if (code == oldCode) return
        classifier.pickCode(code)
        editFromSuggestion(mapOf("jacs" to code), remove = oldCode?.let { mapOf("jacs" to it) }.orEmpty())
    }

    /** Adds [add] to the form's edits and drops the edits in [remove] that still hold what the suggestion put there. */
    private fun editFromSuggestion(add: Map<String, String>, remove: Map<String, String> = emptyMap()) {
        form.update { f ->
            val kept = f.edits.filterNot { (k, v) -> k !in add && remove[k] == v }
            f.copy(edits = kept + add, aiSources = f.aiSources - add.keys - remove.keys, savedFlash = false)
        }
    }

    /** The running look-up of the open asset (it keeps going when the dialog is closed). */
    private var aiJob: Job? = null

    private fun setAi(f: (AiDialogState) -> AiDialogState) = form.update { it.copy(ai = it.ai?.let(f)) }

    /**
     * Opens the AI suggestions dialog, as the server and the web do (mobile team notes, section 11): a look-up still
     * running is followed; a finished one with something new (values for still-empty fields, or a type) is shown; else a
     * look-up starts. An asset the server has is looked up by its model in the background (`lookup-jobs`); one not on
     * the server yet, or with no model typed, by the phone's look-up from the photos here (`POST /lookup`). Either may
     * suggest a type. With neither a photo nor a model it asks for the model.
     */
    fun openAi() {
        val s = state.value
        val asset = s.asset ?: return
        if (s.locked) return
        form.update { it.copy(aiOpen = true) }
        if (form.value.ai?.phase == AiPhase.SEARCHING || form.value.ai?.phase == AiPhase.RESULTS) return
        form.update { it.copy(ai = AiDialogState(AiPhase.LOADING, s.value("make"), s.value("model"))) }
        aiJob?.cancel()
        aiJob = viewModelScope.launch {
            // An asset being added is only on the phone: the phone's look-up, from its photos and what is typed.
            if (s.isNew) {
                lookUpOnPhone()
                return@launch
            }
            try {
                // Not on the server yet (404): it has no look-ups there.
                val latest = try {
                    formData.lookups.latest(asset.id)
                } catch (e: OnlineException) {
                    if (e.error == OnlineError.NotFound) null else throw e
                }
                latest?.typed?.let { t -> setAi { it.copy(make = t["make"] ?: it.make, model = t["model"] ?: it.model) } }
                when {
                    latest != null && latest.status.waiting -> follow(asset.id, latest)
                    latest != null && latest.status == LookupStatus.COMPLETED && hasNews(latest) -> show(latest)
                    s.value("model").isNotBlank() -> runLookup()
                    else -> lookUpOnPhone()
                }
            } catch (e: OnlineException) {
                setAi { it.copy(phase = AiPhase.ERROR, error = e.error.aiText()) }
            }
        }
    }

    /**
     * The phone's look-up (`POST /properties/{id}/lookup`): the AI reads the asset's nameplate, label and equipment photos
     * here, the fields still empty are looked up online, and a type is suggested. With no such photo and no model, it
     * asks for the model.
     */
    private suspend fun lookUpOnPhone() {
        val s = state.value
        val asset = s.asset ?: return
        val d = form.value.ai ?: return
        val photos = formData.lookups.photosToRead(s.media)
        val typed = s.values.filterValues { it.isNotBlank() } + typedIdentity(d)
        if (photos.isEmpty() && typed["model"].isNullOrBlank()) {
            setAi { it.copy(phase = AiPhase.IDENTIFY, fromPhoto = false) }
            return
        }
        setAi { it.copy(phase = AiPhase.SEARCHING, error = null, startedAt = System.currentTimeMillis(), fromPhoto = photos.isNotEmpty()) }
        val found = try {
            formData.lookups.lookUpOnPhone(asset.id, photos, typed, s.formats.formatFor(s.values)?.id)
        } catch (e: OnlineException) {
            // Nothing to go on (422): no photo could be read here and no model is typed.
            if (e.error == OnlineError.Invalid) setAi { it.copy(phase = AiPhase.IDENTIFY, error = UiText.Res(R.string.ai_need_model)) }
            else setAi { it.copy(phase = AiPhase.ERROR, error = e.error.aiText()) }
            return
        }
        show(found)
        if (!form.value.aiOpen) message.emit(doneText(found, state.value.value("name").ifBlank { state.value.asset?.name.orEmpty() }))
    }

    /** Closes the dialog; a look-up still running carries on and says when it is done. */
    /**
     * Closes the AI suggestions sheet. As the iOS app, a search under way is stopped (the sheet says so); results stay
     * to come back to.
     */
    fun closeAi() {
        val phase = form.value.ai?.phase
        if (phase == AiPhase.SEARCHING || phase == AiPhase.LOADING) {
            aiJob?.cancel()
            form.update { it.copy(ai = null, aiOpen = false) }
        } else {
            form.update { it.copy(aiOpen = false) }
        }
    }

    // ---- Change requests (a submitted asset): ask an admin to reopen it, withdraw or decline, or reopen it (admins)

    /** Opens the change request dialog, with the note already sent when asking again. */
    fun openChangeRequest() = form.update { it.copy(changeNote = state.value.asset?.changeRequest?.note.orEmpty()) }

    fun editChangeNote(text: String) = form.update { it.copy(changeNote = text.take(CHANGE_NOTE_MAX)) }

    fun cancelChangeRequest() = form.update { it.copy(changeNote = null) }

    fun sendChangeRequest() {
        val id = state.value.asset?.id ?: return
        val note = form.value.changeNote ?: return
        form.update { it.copy(changeNote = null) }
        write {
            changeRequests.request(id, note)
            message.emit(UiText.Res(R.string.change_request_sent))
        }
    }

    /** Withdraws one's own change request, or (an admin) declines someone else's; the asset stays submitted. */
    fun closeChangeRequest() {
        val s = state.value
        val asset = s.asset ?: return
        val request = asset.changeRequest ?: return
        val declining = s.admin && !s.changeRequestMine
        write {
            changeRequests.close(asset.id, request)
            message.emit(UiText.Res(if (declining) R.string.change_request_declined else R.string.change_request_withdrawn))
        }
    }

    /** An admin reopens the asset: back to draft, which closes its change request, as the server's. */
    fun reopenAsset() {
        val id = state.value.asset?.id ?: return
        write {
            useCases.setStatus(listOf(id), Status.DRAFT)
            message.emit(UiText.Res(R.string.change_request_reopened))
        }
    }

    fun editAiIdentity(make: String, model: String) = setAi { it.copy(make = make, model = model) }

    /** Starts (again) a look-up with the manufacturer and model typed in the dialog. */
    fun lookUp() {
        aiJob?.cancel()
        aiJob = viewModelScope.launch { runLookup() }
    }

    /** Back to typing the manufacturer and model. */
    fun changeAiModel() = setAi { it.copy(phase = AiPhase.IDENTIFY, error = null) }

    fun toggleAiPick(key: String) = setAi { d -> d.copy(picks = d.picks + (key to (d.picks[key] ?: return@setAi d).let { it.copy(on = !it.on) })) }

    /** Ticks or unticks the suggested type (its fields' values go with it). */
    fun toggleAiType() = setAi { it.copy(typeOn = it.typeOn?.not()) }

    /** Editing a suggested value ticks it. */
    fun editAiPick(key: String, value: String) = setAi { it.copy(picks = it.picks + (key to AiPick(true, value))) }

    /**
     * Puts the ticked suggestions (and the manufacturer / model typed) into the form; nothing is saved until Save. A
     * ticked type goes in first, as the web classifies first: its system, asset and sub type and its JACS code, then its
     * fields show and take their values. An unticked type's values are dropped.
     */
    fun applyAi() {
        val d = form.value.ai ?: return
        val s = state.value
        val type = d.lookup?.suggestedType?.takeIf { d.typeOn == true }
        val dropped = if (d.typeOn == false) d.lookup?.suggestedType?.values?.keys.orEmpty() else emptySet()
        val picked = d.picks.filter { (k, p) -> p.on && p.value.isNotBlank() && k !in dropped }.mapValues { it.value.value.trim() }
        val typeValues = type?.let { t ->
            buildMap {
                put("sys", t.systemType)
                put("typ", t.assetType ?: UNCLASSIFIED)
                put("sub", t.subtype ?: UNCLASSIFIED)
                // As the web, which classifies with the type's JACS code: it replaces the asset's when the type changes
                // (the asset's own type keeps the code it has, unless it has none).
                t.jacsCode?.takeIf { d.lookup?.let(::offersType) == true || s.value("jacs").isBlank() }?.let { put("jacs", it) }
            }
        }.orEmpty()
        val update = typedIdentity(d) + typeValues + picked
        if (update.isEmpty()) return
        if (type != null) classifier.showTypeFields(s.formats, s.fields, s.values + update)
        // Each suggestion's page goes with it, kept on Save while the value stays as suggested (as the web sends `sources`).
        val suggested = d.lookup?.values.orEmpty()
        val pages = picked.mapNotNull { (key, value) ->
            d.lookup?.sources?.get(key)?.takeIf { suggested[key]?.trim() == value }?.let { key to AiSource(it, value) }
        }.toMap()
        form.update {
            it.copy(edits = it.edits + update, aiSources = it.aiSources - update.keys + pages, savedFlash = false, ai = null, aiOpen = false)
        }
        message.tryEmit(UiText.Plural(R.plurals.ai_applied, update.size))
    }

    /**
     * Everything said between starting and stopping the mic ([sure] is the recognizer's confidence, null when it gives
     * none). The server's AI decides which fields it was about (offline, the phone reads "<field name> <value>"), and the
     * values open the voice fill sheet, ticked unless the AI was unsure of them, to put into the form.
     */
    fun heard(said: String, sure: Float?) {
        val s = state.value
        if (s.asset == null || s.locked || said.isBlank() || form.value.voiceReading) return
        form.update { it.copy(voiceReading = true) }
        viewModelScope.launch {
            try {
                val reading = formData.voiceFill(said, s.fields, state.value::value)
                val r = reading.result
                val heardSure = sure?.takeIf { it in 0f..1f }?.toDouble()
                // The AI says how sure it is of each value; the phone's own reading has only the recognizer's word for it.
                val confidence = if (reading.byAi) r.confidence else heardSure?.let { h -> r.values.keys.associateWith { h } }.orEmpty()
                val picks = r.values.mapValues { (key, v) -> VoicePick(on = (confidence[key] ?: 1.0) >= VOICE_SURE_ENOUGH, value = v) }
                form.update { it.copy(voice = VoiceFillState(said.trim(), picks, confidence, r.unmatched, reading.byAi)) }
            } catch (e: OnlineException) {
                message.emit(e.error.aiText())
            } finally {
                form.update { it.copy(voiceReading = false) }
            }
        }
    }

    fun toggleVoicePick(key: String) = form.update { f ->
        val v = f.voice ?: return@update f
        val pick = v.picks[key] ?: return@update f
        f.copy(voice = v.copy(picks = v.picks + (key to pick.copy(on = !pick.on))))
    }

    fun closeVoice() = form.update { it.copy(voice = null) }

    /** Puts the ticked values heard into the form; nothing is saved until Save. */
    fun applyVoice() {
        val v = form.value.voice ?: return
        val update = v.picks.filterValues { it.on }.mapValues { it.value.value }
        if (update.isEmpty()) return
        form.update { it.copy(edits = it.edits + update, aiSources = it.aiSources - update.keys, savedFlash = false, voice = null) }
        message.tryEmit(UiText.Plural(R.plurals.ai_applied, update.size))
    }

    /** The manufacturer and model typed in the dialog that differ from the saved ones (sent with the look-up, saved on Apply). */
    private fun typedIdentity(d: AiDialogState): Map<String, String> {
        val s = state.value
        return buildMap {
            d.make.trim().takeIf { it.isNotEmpty() && it != s.value("make") }?.let { put("make", it) }
            d.model.trim().takeIf { it.isNotEmpty() && it != s.value("model") }?.let { put("model", it) }
        }
    }

    private suspend fun runLookup() {
        val asset = state.value.asset ?: return
        val d = form.value.ai ?: return
        // Not on the server: the phone's look-up, with the manufacturer and model typed in the dialog.
        if (state.value.isNew) return lookUpOnPhone()
        setAi { it.copy(phase = AiPhase.SEARCHING, error = null, startedAt = System.currentTimeMillis(), fromPhoto = false) }
        try {
            follow(asset.id, formData.lookups.start(asset.id, typedIdentity(d)))
        } catch (e: OnlineException) {
            when {
                // Not on the server yet, or no model to look up by (422): the phone's look-up reads the photos here
                // (it asks for the model when there are none).
                e.error == OnlineError.NotFound || e.error == OnlineError.Invalid -> lookUpOnPhone()
                else -> setAi { it.copy(phase = AiPhase.ERROR, error = e.error.aiText()) }
            }
        }
    }

    private suspend fun follow(assetId: String, lookup: SpecLookup) {
        setAi { it.copy(phase = AiPhase.SEARCHING, startedAt = it.startedAt ?: lookup.createdAt ?: System.currentTimeMillis()) }
        val done = try {
            formData.lookups.follow(assetId, lookup)
        } catch (e: OnlineException) {
            setAi { it.copy(phase = AiPhase.ERROR, error = e.error.aiText()) }
            return
        }
        show(done)
        // Closed meanwhile: say how it went, as the web's toast does.
        if (!form.value.aiOpen) message.emit(doneText(done, state.value.value("name").ifBlank { state.value.asset?.name.orEmpty() }))
    }

    private suspend fun show(lookup: SpecLookup) {
        if (lookup.status == LookupStatus.FAILED) {
            setAi { it.copy(phase = AiPhase.ERROR, error = lookup.statusDetail?.let { d -> UiText.Raw(d) } ?: UiText.Res(R.string.ai_failed)) }
            return
        }
        // As the iOS app: each value at least SURE_ENOUGH sure starts ticked, a value the photo shows differently too
        // (the sheet shows what the form has beside it, so nothing is replaced unseen).
        val values = fillable(lookup) + typeFillable(lookup)
        val picks = values.mapValues { (k, v) -> AiPick((lookup.confidence[k] ?: 1.0) >= SURE_ENOUGH, v) }
        // The type shows even when it is the asset's own (ticked: it changes nothing); another type starts ticked only
        // when the asset has none and the AI is sure enough.
        val type = lookup.suggestedType
        val typeOn = type?.let { t ->
            !offersType(lookup) || (state.value.value("sys").let { it.isBlank() || it == UNCLASSIFIED } && t.confidence >= SURE_ENOUGH)
        }
        setAi { it.copy(phase = AiPhase.RESULTS, lookup = lookup, picks = picks, typeOn = typeOn) }
        if (!lookup.seen && form.value.aiOpen) {
            try {
                formData.lookups.seen(lookup.id)
            } catch (_: OnlineException) {
                // Only tidies the asset's "ready" mark at the server.
            }
        }
    }

    /**
     * The suggestions for fields that are still empty (the web offers nothing else), and for fields whose saved value a
     * photo read disagrees with ([SpecLookup.differs]) when the form still holds that value.
     */
    private fun fillable(lookup: SpecLookup): Map<String, String> =
        lookup.values.filter { (k, v) ->
            val now = state.value.value(k)
            now.isBlank() || (k in CLASS_KEYS && now == UNCLASSIFIED) || (k in lookup.differs && now.trim() != v.trim())
        }

    /** The suggested type's values for its fields that are still empty in the form (they go in only with the type). */
    private fun typeFillable(lookup: SpecLookup): Map<String, String> =
        lookup.suggestedType?.values.orEmpty().filterKeys { state.value.value(it).isBlank() }

    /** A suggested type that isn't the asset's own (the phone's look-up may name the type it already has). */
    private fun offersType(lookup: SpecLookup): Boolean {
        val type = lookup.suggestedType ?: return false
        val s = state.value
        return s.formats.formatFor(s.values)?.id != type.masterFormatId
    }

    /** The look-up has something the form doesn't: a value for an empty field, or a type. */
    private fun hasNews(lookup: SpecLookup): Boolean = fillable(lookup).isNotEmpty() || offersType(lookup)

    private fun doneText(lookup: SpecLookup, name: String): UiText {
        val n = (fillable(lookup) + typeFillable(lookup)).size
        return when {
            lookup.status == LookupStatus.FAILED -> UiText.Res(R.string.ai_done_failed, listOf(name, lookup.statusDetail.orEmpty()))
            n > 0 -> UiText.Plural(R.plurals.ai_done_found, n, listOf(name, n))
            else -> UiText.Res(R.string.ai_done_nothing, listOf(name, lookup.message?.let { UiText.Raw(it) } ?: UiText.Res(R.string.ai_nothing_new)))
        }
    }

    /** A new asset, or one not synced yet, is not known to the server: it has to be synced before it can be looked up. */
    private fun OnlineError.aiText(): UiText =
        if (this == OnlineError.NotFound) UiText.Res(R.string.detail_ai_sync_first) else toUiText()

    /**
     * Validates the asset again at the server ([fromStart]: every step, else from its first step to look at), as the web's
     * Revalidate does, then says how it went once the results have synced (or that they will show when they have).
     */
    fun revalidate(fromStart: Boolean = false) {
        val s = state.value
        val id = s.asset?.id ?: return
        if (s.isNew || form.value.validating) return
        val before = s.validation
        form.update { it.copy(validating = true) }
        viewModelScope.launch {
            try {
                formData.revalidate(id, fromStart)
                // The new result is read from the phone's data once the sync has brought it.
                val after = withTimeoutOrNull(REFRESH_MS * SYNC_WAITS) { state.first { it.validation != null && it.validation != before }.validation }
                message.emit(after?.let(::validatedText) ?: UiText.Res(R.string.val_done_sync))
            } catch (e: OnlineException) {
                message.emit(e.error.toUiText())
            } finally {
                form.update { it.copy(validating = false) }
            }
        }
    }

    /** The web's message after validating: where the first problem is, or that every step passed. */
    private fun validatedText(v: AssetValidation): UiText {
        val first = v.firstProblem ?: return UiText.Res(R.string.val_done_passed)
        val step = v.steps[first - 1]
        return UiText.Res(R.string.val_done_problem, listOf(UiText.Res(outcomeRes(step.outcome)), first, stepNameText(step.rule)))
    }

    /** Takes a web page off the asset (after the confirmation), as the web's remove on a document does. */
    /** Takes [document] off the asset: a web page it came from, or its uploaded file. */
    fun removeDocument(document: AssetDocument) = write {
        val id = state.value.asset?.id ?: return@write
        if (document.source == DocumentSource.SOURCE_FILE) formData.removeSourceFile(id, document) else formData.removeWebPage(id, document.id)
        message.emit(UiText.Res(R.string.doc_removed))
    }

    fun editComment(text: String) = form.update { it.copy(commentDraft = text, commentProblem = null) }

    /** Posts the typed comment: the box empties once it is kept, or it says what is wrong with it. */
    fun postComment() {
        val s = state.value
        val id = s.asset?.id ?: return
        val draft = form.value.commentDraft
        if (s.isNew || form.value.commentPosting) return
        form.update { it.copy(commentPosting = true) }
        viewModelScope.launch {
            try {
                val problem = useCases.addComment(id, draft)
                form.update {
                    if (problem == null) it.copy(commentDraft = "", commentProblem = null)
                    else it.copy(commentProblem = problem)
                }
                if (problem == null) message.emit(UiText.Res(R.string.detail_comment_added))
            } catch (e: DataException) {
                message.emit(e.error.toUiText())
            } finally {
                form.update { it.copy(commentPosting = false) }
            }
        }
    }

    /** Saves the new [text] of [comment]; [onSaved] once it is saved (otherwise what is wrong is flashed). */
    fun updateComment(comment: AssetActivity, text: String, onSaved: () -> Unit) {
        viewModelScope.launch {
            try {
                val problem = useCases.updateComment(comment, text)
                if (problem == null) onSaved() else message.emit(problem.problemText())
            } catch (e: DataException) {
                message.emit(e.error.toUiText())
            }
        }
    }

    /** Removes [comment] (after the screen asked), as the web's Delete. */
    fun removeComment(comment: AssetActivity) = write { useCases.removeComment(comment) }

    private fun CommentProblem.problemText(): UiText = when (this) {
        CommentProblem.EMPTY -> UiText.Res(R.string.detail_comment_empty)
        CommentProblem.TOO_LONG -> UiText.Res(R.string.detail_comment_too_long, listOf(COMMENT_MAX_LENGTH))
    }

    private companion object {
        const val REFRESH_MS = 1000L
        val CLASS_KEYS = setOf("sys", "typ", "sub")

        /** How many [REFRESH_MS] to wait for a new validation to sync before saying it will show later. */
        const val SYNC_WAITS = 15L
    }
}

/** A document picked for a new asset, as the Documents tab lists the file an asset was read from. */
private fun Media.toPendingDocument() = AssetDocument(
    id = id, source = DocumentSource.SOURCE_FILE, title = fileName.orEmpty(), url = null, publisher = null,
    hasFile = true, fileKind = documentKind(fileName.orEmpty(), null),
    uploadedAt = createdAt, sizeBytes = sizeBytes, fileUri = uri,
)
