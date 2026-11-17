package com.sclera.sclerago.presentation.assets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sclera.sclerago.R
import com.sclera.sclerago.domain.model.Asset
import com.sclera.sclerago.domain.model.AssetActivity
import com.sclera.sclerago.domain.model.AssetDocument
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
import com.sclera.sclerago.domain.model.SpecLookup
import com.sclera.sclerago.domain.model.SURE_ENOUGH
import com.sclera.sclerago.domain.model.LookupStatus
import com.sclera.sclerago.domain.model.Media
import com.sclera.sclerago.domain.model.PhotoKind
import com.sclera.sclerago.domain.model.missingRequiredFields
import com.sclera.sclerago.domain.model.photoCounts
import com.sclera.sclerago.domain.model.photosShort
import com.sclera.sclerago.domain.model.PhotoRule
import com.sclera.sclerago.domain.model.PlaceNames
import com.sclera.sclerago.domain.model.OnlineError
import com.sclera.sclerago.domain.model.OnlineException
import com.sclera.sclerago.domain.model.Source
import com.sclera.sclerago.domain.model.Status
import com.sclera.sclerago.domain.usecase.asset.AssetFormUseCases
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
    /** The asset's comments and history, newest first (none for a new asset). */
    val activity: List<AssetActivity> = emptyList(),
    /** The comment being typed in the Comments tab, why it can't be posted (if so), and whether it is being posted. */
    val commentDraft: String = "",
    val commentError: UiText? = null,
    val commentPosting: Boolean = false,
    /** The sheet a comment is written in is open (the Comments tab's +). */
    val commentOpen: Boolean = false,
    val savedFlash: Boolean = false,
    /** The AI suggestions dialog while it is open (null: closed). */
    val ai: AiDialogState? = null,
    /** A spec look-up is running, shown or not (the AI button spins). */
    val aiBusy: Boolean = false,
    /** The voice fill sheet while it is open: what was heard and the values found in it (null: closed). */
    val voice: VoiceFillState? = null,
    /** The signed-in user may change this asset (the server lets only admins change a submitted or validated one). */
    val canEdit: Boolean = true,
    /** The signed-in user may validate the asset (the server's check permission; validate includes it). */
    val canCheck: Boolean = true,
    /** The "Suggest type" sheet while it is open (null: closed). */
    val classify: ClassifyState? = null,
    /** A building, floor or location picked for the asset and not saved yet ([place] already shows it). */
    val pendingPlace: LocValue? = null,
) {
    val values: Map<String, String> get() = saved + edits
    fun value(key: String): String = edits[key] ?: saved[key].orEmpty()
    fun dirty(key: String): Boolean = key in edits && edits[key] != saved[key].orEmpty()

    /** Unsaved changes: the fields changed, and the place when one was picked. */
    val dirtyCount: Int get() = edits.keys.count { dirty(it) } + if (pendingPlace != null) 1 else 0

    /** Read-only: submitted or validated, for anyone but an admin (until a recollect is requested). */
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
)

/** One value heard for a field ([value] as the form stores it), ticked to go into the form. */
data class VoicePick(val on: Boolean, val value: String)

/** What was said, the values found in it by field key, and how sure the recognizer was (null: it didn't say). */
data class VoiceFillState(val said: String, val picks: Map<String, VoicePick>, val sure: Double?)

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

    private data class Form(
        val edits: Map<String, String> = emptyMap(),
        val place: LocValue? = null,
        val savedFlash: Boolean = false,
        val ai: AiDialogState? = null,
        val aiOpen: Boolean = false,
        val voice: VoiceFillState? = null,
        val newPicks: List<Media> = emptyList(),
        val commentDraft: String = "",
        val commentProblem: CommentProblem? = null,
        val commentPosting: Boolean = false,
        val commentOpen: Boolean = false,
        val validating: Boolean = false,
    )

    private val target = MutableStateFlow<Target?>(null)
    private val form = MutableStateFlow(Form())
    private val message = MutableSharedFlow<UiText>(extraBufferCapacity = 8)
    private val classifier = AssetClassifier(classifying, viewModelScope)

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
        combine(opened, form, observeConfig(), fromDb, classifier.ui) { o, f, config, db, classified ->
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
                ai = f.ai?.takeIf { f.aiOpen }, aiBusy = f.ai?.phase == AiPhase.SEARCHING, voice = f.voice,
                activity = if (t.isNew) emptyList() else db.activity,
                commentDraft = f.commentDraft, commentError = f.commentProblem?.problemText(), commentPosting = f.commentPosting,
                commentOpen = f.commentOpen && !t.isNew,
                validation = if (t.isNew) null else db.validation, validating = f.validating,
                canEdit = t.isNew || canEditAsset(asset.st),
                canCheck = formData.revalidate.allowed(),
                classify = classified.sheet,
            )
        }.stateIn(viewModelScope, SharingStarted.Eagerly, AssetDetailState())

    /** Shows [asset]; does nothing when that asset is already open, so edits survive recomposition. */
    fun open(asset: Asset, isNew: Boolean, prefill: Map<String, String> = emptyMap()) {
        val current = target.value
        if (current != null && current.asset.id == asset.id && current.isNew == isNew) return
        aiJob?.cancel()
        classifier.reset()
        form.value = Form()
        target.value = Target(asset, isNew, prefill)
        classifier.refreshOnce()
    }

    fun close() {
        aiJob?.cancel()
        classifier.reset()
        target.value = null
        form.value = Form()
    }

    fun edit(key: String, value: String) {
        val s = state.value
        if (s.locked) return
        // A new system or asset type also resets the types below it that the catalogue doesn't file under it.
        val changes = if (key in CLASS_KEYS) formData.reclassify(s.formats, s.values, key, value) else mapOf(key to value)
        form.update { it.copy(edits = it.edits + changes, savedFlash = false) }
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
        form.update { it.copy(edits = emptyMap(), place = null) }
        classifier.clearTypeFields()
    }

    /** Keeps the edits; the asset keeps its status, as the server's edit does. */
    fun save() {
        val s = state.value
        val id = s.asset?.id ?: return
        if (s.dirtyCount == 0) return
        write {
            // The place picked goes in with the rest of the changes, only now.
            s.pendingPlace?.let { useCases.assignLocation(listOf(id), it) }
            // Saving makes the asset drafted with the AI flag on (Status.AI is stored that way), as saving a new one does.
            useCases.saveDetails(id, s.values, Status.AI)
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
            s.pendingPlace?.let { useCases.assignLocation(listOf(id), it) }
            useCases.saveDetails(id, s.values, Status.SUBMITTED)
            clearEditsOnceRefreshed(s)
            message.emit(UiText.Res(if (s.complete) R.string.asset_submitted_notice else R.string.asset_exception_notice))
        }
    }

    /** Drops the typed edits once the screen shows what was saved, so the form does not flash the old values in between. */
    private suspend fun clearEditsOnceRefreshed(before: AssetDetailState) {
        withTimeoutOrNull(REFRESH_MS) { state.first { it.asset != before.asset || it.saved != before.saved } }
        // The picked place goes too: the asset now has it, so it shows from the asset itself.
        form.update { it.copy(edits = emptyMap(), place = null) }
    }

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

    fun choosePhotoKind(kind: PhotoKind?) {
        photoKind = kind
    }

    /** Photos, videos or documents added to the asset (kept in the form until a new asset is created). */
    fun attach(picked: List<Media>) {
        // Photos added from a photo card are of that card's kind; videos and files never are.
        val kind = photoKind.also { photoKind = null }
        addPicked(picked.map { if (!it.video && !it.isDoc && !it.audio) it.copy(category = kind) else it })
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

    private fun addPicked(media: List<Media>) {
        val s = state.value
        val asset = s.asset ?: return
        if (media.isEmpty()) return
        if (s.isNew) form.update { it.copy(newPicks = it.newPicks + media) }
        else write {
            if (useCases.attachMedia(asset.id, media)) message.emit(UiText.Plural(R.plurals.items_added_to_asset, media.size))
        }
    }

    /** Opens the "Suggest type" sheet: the server reads the asset's photos and what is typed, and suggests its type and JACS code. */
    fun openClassify() {
        val s = state.value
        if (s.asset == null || s.locked) return
        classifier.open(s.fields, s::value, s.media)
    }

    fun pickSuggestedType(masterFormatId: String) = classifier.pickType(masterFormatId)

    fun pickSuggestedCode(code: String) = classifier.pickCode(code)

    fun toggleClassifyReading() = classifier.toggleReading()

    fun closeClassify() = classifier.close()

    /** Puts the picked type (and JACS code, and what the photos said) into the form, which then shows the type's fields; nothing is saved until Save. */
    fun applyClassify() {
        val s = state.value
        if (s.locked) return
        val update = classifier.chosenValues(s::value) ?: return
        form.update { it.copy(edits = it.edits + update, savedFlash = false) }
        classifier.close()
        classifier.showTypeFields(s.formats, s.fields, s.values + update)
        message.tryEmit(UiText.Res(R.string.classify_applied))
    }

    /** The running look-up of the open asset (it keeps going when the dialog is closed). */
    private var aiJob: Job? = null

    private fun setAi(f: (AiDialogState) -> AiDialogState) = form.update { it.copy(ai = it.ai?.let(f)) }

    /**
     * Opens the AI suggestions dialog as the web does: a look-up still running is followed, one that finished with values
     * for still-empty fields is shown, else a new one starts when the model is known, else it asks for the model.
     */
    fun openAi() {
        val s = state.value
        val asset = s.asset ?: return
        if (s.isNew || s.locked) return
        form.update { it.copy(aiOpen = true) }
        if (form.value.ai?.phase == AiPhase.SEARCHING || form.value.ai?.phase == AiPhase.RESULTS) return
        form.update { it.copy(ai = AiDialogState(AiPhase.LOADING, s.value("make"), s.value("model"))) }
        aiJob?.cancel()
        aiJob = viewModelScope.launch {
            try {
                val latest = formData.lookups.latest(asset.id)
                latest?.typed?.let { t -> setAi { it.copy(make = t["make"] ?: it.make, model = t["model"] ?: it.model) } }
                when {
                    latest != null && latest.status.waiting -> follow(asset.id, latest)
                    latest != null && latest.status == LookupStatus.COMPLETED && fillable(latest).isNotEmpty() -> show(latest)
                    s.value("model").isNotBlank() -> runLookup()
                    else -> setAi { it.copy(phase = AiPhase.IDENTIFY) }
                }
            } catch (e: OnlineException) {
                setAi { it.copy(phase = AiPhase.ERROR, error = e.error.aiText()) }
            }
        }
    }

    /** Closes the dialog; a look-up still running carries on and says when it is done. */
    fun closeAi() = form.update { it.copy(aiOpen = false) }

    fun editAiIdentity(make: String, model: String) = setAi { it.copy(make = make, model = model) }

    /** Starts (again) a look-up with the manufacturer and model typed in the dialog. */
    fun lookUp() {
        aiJob?.cancel()
        aiJob = viewModelScope.launch { runLookup() }
    }

    /** Back to typing the manufacturer and model. */
    fun changeAiModel() = setAi { it.copy(phase = AiPhase.IDENTIFY, error = null) }

    fun toggleAiPick(key: String) = setAi { d -> d.copy(picks = d.picks + (key to (d.picks[key] ?: return@setAi d).let { it.copy(on = !it.on) })) }

    /** Editing a suggested value ticks it. */
    fun editAiPick(key: String, value: String) = setAi { it.copy(picks = it.picks + (key to AiPick(true, value))) }

    /** Puts the ticked suggestions (and the manufacturer / model typed) into the form; nothing is saved until Save. */
    fun applyAi() {
        val d = form.value.ai ?: return
        val update = typedIdentity(d) + d.picks.filterValues { it.on && it.value.isNotBlank() }.mapValues { it.value.value.trim() }
        if (update.isEmpty()) return
        form.update { it.copy(edits = it.edits + update, savedFlash = false, ai = null, aiOpen = false) }
        message.tryEmit(UiText.Plural(R.plurals.ai_applied, update.size))
    }

    /**
     * What was said by voice ([sure] is the recognizer's confidence, null when it gives none): the field values found in
     * it open the voice fill sheet, all ticked, to put into the form.
     */
    fun heard(said: String, sure: Float?) {
        val s = state.value
        if (s.asset == null || s.locked || said.isBlank()) return
        val found = formData.spokenFields(said, s.fields)
        val voice = VoiceFillState(said.trim(), found.mapValues { VoicePick(true, it.value) }, sure?.takeIf { it in 0f..1f }?.toDouble())
        form.update { it.copy(voice = voice) }
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
        form.update { it.copy(edits = it.edits + update, savedFlash = false, voice = null) }
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
        setAi { it.copy(phase = AiPhase.SEARCHING, error = null, startedAt = System.currentTimeMillis()) }
        try {
            follow(asset.id, formData.lookups.start(asset.id, typedIdentity(d)))
        } catch (e: OnlineException) {
            // The server refuses a look-up without a model (422): ask for it.
            if (e.error == OnlineError.Invalid) setAi { it.copy(phase = AiPhase.IDENTIFY, error = UiText.Res(R.string.ai_need_model)) }
            else setAi { it.copy(phase = AiPhase.ERROR, error = e.error.aiText()) }
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
        val picks = fillable(lookup).mapValues { (k, v) -> AiPick((lookup.confidence[k] ?: 1.0) >= SURE_ENOUGH, v) }
        setAi { it.copy(phase = AiPhase.RESULTS, lookup = lookup, picks = picks) }
        if (!lookup.seen && form.value.aiOpen) {
            try {
                formData.lookups.seen(lookup.id)
            } catch (_: OnlineException) {
                // Only tidies the asset's "ready" mark at the server.
            }
        }
    }

    /** The suggestions for fields that are still empty (the web offers nothing else). */
    private fun fillable(lookup: SpecLookup): Map<String, String> =
        lookup.values.filterKeys { k -> state.value.value(k).let { it.isBlank() || (k in CLASS_KEYS && it == UNCLASSIFIED) } }

    private fun doneText(lookup: SpecLookup, name: String): UiText {
        val n = fillable(lookup).size
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
    fun removeWebPage(documentId: String) = write {
        formData.removeWebPage(documentId)
        message.emit(UiText.Res(R.string.doc_removed))
    }

    fun editComment(text: String) = form.update { it.copy(commentDraft = text, commentProblem = null) }

    /** The Comments tab's +: opens (true) or closes the sheet a comment is written in; the draft is kept either way. */
    fun showCommentSheet(open: Boolean) = form.update { it.copy(commentOpen = open, commentProblem = null) }

    /** Posts the typed comment: the box empties and the sheet closes once it is kept, or it says what is wrong with it. */
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
                    if (problem == null) it.copy(commentDraft = "", commentProblem = null, commentOpen = false)
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
)
