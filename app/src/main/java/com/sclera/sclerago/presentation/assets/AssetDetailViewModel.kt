package com.sclera.sclerago.presentation.assets

import com.sclera.sclerago.domain.model.jacsCodeForType
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sclera.sclerago.R
import com.sclera.sclerago.domain.model.AddFieldOutcome
import com.sclera.sclerago.domain.model.ASSET_DETAIL_KEYS
import com.sclera.sclerago.domain.model.AttachRefusal
import com.sclera.sclerago.domain.model.MAX_ATTACHED_FILE_BYTES
import com.sclera.sclerago.domain.model.attachRefusal
import com.sclera.sclerago.domain.model.VIDEO_KIND
import com.sclera.sclerago.domain.model.Asset
import com.sclera.sclerago.domain.model.AssetActivity
import com.sclera.sclerago.domain.model.AssetDetails
import com.sclera.sclerago.domain.model.AssetDocument
import com.sclera.sclerago.domain.model.documentKind
import com.sclera.sclerago.domain.model.DocumentSource
import com.sclera.sclerago.domain.model.AssetLock
import com.sclera.sclerago.domain.model.ClonePlace
import com.sclera.sclerago.domain.model.CloneRequest
import com.sclera.sclerago.domain.model.DataError
import com.sclera.sclerago.domain.model.DataException
import com.sclera.sclerago.domain.model.OpenableFile
import com.sclera.sclerago.domain.model.PhotoRefusal
import com.sclera.sclerago.domain.usecase.media.SortedShot
import com.sclera.sclerago.domain.model.FieldCfg
import com.sclera.sclerago.domain.model.FieldKind
import com.sclera.sclerago.domain.model.FormEntry
import com.sclera.sclerago.domain.model.JacsSuggestion
import com.sclera.sclerago.domain.model.LocNode
import com.sclera.sclerago.domain.model.LocValue
import com.sclera.sclerago.domain.model.LookupStatus
import com.sclera.sclerago.domain.model.MasterFormat
import com.sclera.sclerago.domain.model.Media
import com.sclera.sclerago.domain.model.OnlineError
import com.sclera.sclerago.domain.model.OnlineException
import com.sclera.sclerago.domain.model.Currency
import com.sclera.sclerago.domain.model.PhotoKind
import com.sclera.sclerago.domain.model.PhotoRule
import com.sclera.sclerago.domain.model.PlaceNames
import com.sclera.sclerago.domain.model.SURE_ENOUGH
import com.sclera.sclerago.domain.model.ScanFacts
import com.sclera.sclerago.domain.model.Source
import com.sclera.sclerago.domain.model.SpecLookup
import com.sclera.sclerago.domain.model.Status
import com.sclera.sclerago.domain.model.UNCLASSIFIED
import com.sclera.sclerago.domain.model.Unheard
import com.sclera.sclerago.domain.model.VOICE_SURE_ENOUGH
import com.sclera.sclerago.domain.model.fitPhotoRules
import com.sclera.sclerago.domain.model.formatFor
import com.sclera.sclerago.domain.model.typeFieldName
import com.sclera.sclerago.domain.model.missingRequiredFields
import com.sclera.sclerago.domain.model.photoCounts
import com.sclera.sclerago.domain.model.photoRoom
import com.sclera.sclerago.domain.model.photosShort
import com.sclera.sclerago.domain.usecase.access.ObserveAccessChangesUseCase
import com.sclera.sclerago.domain.usecase.asset.AddAssetFieldUseCase
import com.sclera.sclerago.domain.usecase.asset.ReloadAssetsUseCase
import com.sclera.sclerago.domain.usecase.asset.AssetDocumentUseCases
import com.sclera.sclerago.domain.usecase.asset.AssetFormUseCases
import com.sclera.sclerago.domain.usecase.asset.AssetHandling
import com.sclera.sclerago.domain.usecase.asset.AssetHandlingUseCase
import com.sclera.sclerago.domain.usecase.asset.AssetUseCases
import com.sclera.sclerago.domain.usecase.asset.CHANGE_NOTE_MAX
import com.sclera.sclerago.domain.usecase.asset.CanEditAssetUseCase
import com.sclera.sclerago.domain.usecase.asset.AssetButtonBlocksUseCase
import com.sclera.sclerago.domain.usecase.asset.ChangeRequestUseCases
import com.sclera.sclerago.domain.usecase.asset.ClassifyAssetUseCases
import com.sclera.sclerago.domain.usecase.asset.CloneAssetUseCase
import com.sclera.sclerago.domain.usecase.asset.JacsUseCases
import com.sclera.sclerago.domain.usecase.asset.PhotoFill
import com.sclera.sclerago.domain.usecase.asset.PlanPhotoFillUseCase
import com.sclera.sclerago.domain.usecase.asset.RemoveAssetFieldUseCase
import com.sclera.sclerago.domain.usecase.auth.ObserveOnlineUseCase
import com.sclera.sclerago.domain.usecase.config.AiSwitchUseCases
import com.sclera.sclerago.domain.usecase.config.GetCurrenciesUseCase
import com.sclera.sclerago.domain.usecase.config.ObserveAssetConfigUseCase
import com.sclera.sclerago.domain.usecase.media.DeleteOwnedMediaUseCase
import com.sclera.sclerago.domain.validation.isServerId
import com.sclera.sclerago.presentation.model.OnlineOnly
import com.sclera.sclerago.domain.validation.AssetValidation
import com.sclera.sclerago.domain.validation.COMMENT_MAX_LENGTH
import com.sclera.sclerago.domain.validation.CommentProblem
import com.sclera.sclerago.presentation.common.UiText
import com.sclera.sclerago.presentation.common.toUiText
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    /**
     * The Documents tab, read from the server (documents are not synced): the ones added to the asset, the file it was
     * read from, the pages its values came from, its model's manuals.
     */
    val documents: List<AssetDocument> = emptyList(),
    /** The Documents tab is fetching them, offline, failed, opening one ([AssetDocumentsPanel]). */
    val documentsUi: DocumentsUi = DocumentsUi(),
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
    /** The manuals the AI look-up pulled for the asset's model (from the server), and whether it still looks for more. */
    val aiManuals: List<AssetDocument> = emptyList(),
    val manualsSearching: Boolean = false,
    /** The Clone sheet is open / its copies are being made. */
    val cloneOpen: Boolean = false,
    val cloning: Boolean = false,
    /** The user may add fields to this (saved) asset alone, as the web's "Add field" (when they can also edit it). */
    val canAddField: Boolean = false,
    /** The Add field sheet is open / the field is being added / why it wasn't (e.g. the name is taken). */
    val addFieldOpen: Boolean = false,
    val addingField: Boolean = false,
    val addFieldError: UiText? = null,
    /** The currencies Add field offers a currency field (read when the sheet opens; empty until then, or offline). */
    val currencies: List<Currency> = emptyList(),
    /** The signed-in user made the asset's change request. */
    val changeRequestMine: Boolean = false,
    /** Who made the asset's change request (name, else email); null when they aren't known on this device. */
    val changeRequester: String? = null,
    /**
     * The signed-in user is a reviewer (Validate): a submitted asset is theirs to change or move back to draft, and a
     * change request theirs to decline.
     */
    val reviewer: Boolean = false,
    /** The asset's comments and history, newest first (none for a new asset). */
    val activity: List<AssetActivity> = emptyList(),
    /** Who the asset is assigned to and who submitted it, when (null for a new asset). */
    val handling: AssetHandling? = null,
    /** The comment being typed in the Comments tab, why it can't be posted (if so), and whether it is being posted. */
    val commentDraft: String = "",
    val commentError: UiText? = null,
    val commentPosting: Boolean = false,
    /** The AI suggestions dialog while it is open (null: closed). */
    val ai: AiDialogState? = null,
    /** A spec look-up is running, shown or not (the AI button spins). */
    val aiBusy: Boolean = false,
    /** The voice fill sheet while it is open: what was heard and the values found in it (null: closed). */
    val voice: VoiceFillState? = null,
    /** What was said is being read by the server's AI (the mic shows it is working). */
    val voiceReading: Boolean = false,
    /** The describe sheet (typing instead of speaking) while it is open: its text (null: closed). */
    val typing: String? = null,
    /** Photos just added are being read on the device to sort them into nameplate, label ID or asset. */
    val sortingPhotos: Boolean = false,
    /**
     * Why the signed-in user may not change this asset (the server's check_editable: only an admin changes a submitted
     * or validated one, or one assigned to someone else); null when they may.
     */
    val lock: AssetLock? = null,
    /** Clone is offered: a saved asset (one in Managed Assets too), for a group that can collect (the copy would be refused). */
    val canClone: Boolean = true,
    /** Why each of the asset's buttons is greyed out for the signed-in user (the User Access Guide); null where it can be used. */
    val buttons: AssetButtons = AssetButtons(),
    /** The JACS code picker while it is open (null: closed). */
    val jacs: JacsPickerState? = null,
    /** The title of the JACS code in the form, from the catalogue kept on the phone (null: unknown, or no code). */
    val jacsTitle: String? = null,
    /** The "Suggest type" sheet while it is open (null: closed). */
    val classify: ClassifyState? = null,
    /** A building, floor or location picked for the asset and not saved yet ([place] already shows it). */
    val pendingPlace: LocValue? = null,
    /** The device is online; offline, the server-only actions (AI, voice, Suggest type, Validate, Assign) say so instead. */
    val online: Boolean = true,
    /** A pull to refresh is reading the asset and its tabs again (the ring shows). */
    val refreshing: Boolean = false,
    /**
     * The property's AI switch is on. Off, the AI buttons (AI suggestions, its look-ups, voice fill) stay where they are,
     * dimmed, and a tap says "AI off. An admin can turn it on."; photos and scans no longer fill the form by AI.
     */
    val aiOn: Boolean = true,
    /** A look-up's results are here to read again (kept until the asset is closed), whether or not its sheet is open. */
    val aiHasResults: Boolean = false,
) {
    /**
     * The AI suggestions button can be used: AI is on, or a look-up's results (or one still running) are here to read, as
     * the web keeps it usable while a look-up's suggestions wait.
     */
    val aiUsable: Boolean get() = aiOn || aiBusy || aiHasResults

    val values: Map<String, String> get() = saved + edits
    fun value(key: String): String = edits[key] ?: saved[key].orEmpty()
    fun dirty(key: String): Boolean = key in edits && edits[key] != saved[key].orEmpty()

    /** Unsaved changes: the fields changed, and the place when one was picked. */
    val dirtyCount: Int get() = edits.keys.count { dirty(it) } + if (pendingPlace != null) 1 else 0

    /** The signed-in user may change this asset. */
    val canEdit: Boolean get() = lock == null

    /**
     * Read-only for anyone but an admin: submitted or validated (until a recollect is requested), or someone else's; and
     * for everyone while it is in Managed Assets.
     */
    val locked: Boolean get() = !isNew && !canEdit

    /**
     * In Managed Assets ([Asset.managed]): read-only for everyone, admins too. No AI suggestions, validating, comments,
     * media, change requests or moves; a note says so where its validation would show.
     */
    val managed: Boolean get() = !isNew && asset?.managed == true

    /**
     * Documents can't be added now: they are kept on the server only (not synced), so not offline, nor to an asset the
     * server doesn't have yet (new, or saved here and not synced).
     */

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
    /**
     * The JACS code suggested for the asset's own type ([SpecLookup.suggestedJacsCode]) is ticked to go into the form;
     * null when none is offered (a type is suggested, or the asset already has a code).
     */
    val jacsOn: Boolean? = null,
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
    private val documentUseCases: AssetDocumentUseCases,
    private val canEditAsset: CanEditAssetUseCase,
    private val buttonBlocks: AssetButtonBlocksUseCase,
    private val classifying: ClassifyAssetUseCases,
    private val changeRequests: ChangeRequestUseCases,
    private val handling: AssetHandlingUseCase,
    private val jacsCodes: JacsUseCases,
    private val planPhotoFill: PlanPhotoFillUseCase,
    private val observeOnline: ObserveOnlineUseCase,
    private val cloneAsset: CloneAssetUseCase,
    private val addField: AddAssetFieldUseCase,
    private val getCurrencies: GetCurrenciesUseCase,
    private val removeField: RemoveAssetFieldUseCase,
    private val deleteOwned: DeleteOwnedMediaUseCase,
    observeAccess: ObserveAccessChangesUseCase,
    private val reloadData: ReloadAssetsUseCase,
    private val aiSwitch: AiSwitchUseCases,
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
        val documents: DocumentsUi = DocumentsUi(),
    )

    /** Where an applied AI suggestion came from ([page]) and the [value] it put in the form. */
    private data class AiSource(val page: String, val value: String)

    private data class Form(
        val edits: Map<String, String> = emptyMap(),
        /** Field key -> the page each applied AI suggestion came from; dropped when that field is typed over. */
        val aiSources: Map<String, AiSource> = emptyMap(),
        val place: LocValue? = null,
        val ai: AiDialogState? = null,
        val aiOpen: Boolean = false,
        val voice: VoiceFillState? = null,
        val voiceReading: Boolean = false,
        /** The describe sheet's text while it is open (null: closed). */
        val typing: String? = null,
        val newPicks: List<Media> = emptyList(),
        /**
         * A saved asset's photos just moved to another kind (photo id → kind, null: Other photos): shown there at once,
         * until the asset read again from the database has them there too.
         */
        val movedKinds: Map<String, PhotoKind?> = emptyMap(),
        val commentDraft: String = "",
        val commentProblem: CommentProblem? = null,
        val commentPosting: Boolean = false,
        val validating: Boolean = false,
        /** The form is being filled from photos just added (the AI button turns). */
        /** How many batches of added photos are being sorted on the device. */
        val sorting: Int = 0,
        /** The change request note being written; null while its dialog is closed. */
        val changeNote: String? = null,
        val cloneOpen: Boolean = false,
        val cloning: Boolean = false,
        val aiManuals: List<AssetDocument> = emptyList(),
        val manualsSearching: Boolean = false,
        val addFieldOpen: Boolean = false,
        val addingField: Boolean = false,
        val addFieldError: UiText? = null,
        val currencies: List<Currency> = emptyList(),
        /** A pull to refresh is reading the asset, its activity, checks and documents again. */
        val refreshing: Boolean = false,
    )

    private val target = MutableStateFlow<Target?>(null)
    private val form = MutableStateFlow(Form())
    private val message = MutableSharedFlow<UiText>(extraBufferCapacity = 8)
    private val done = MutableSharedFlow<UiText>(extraBufferCapacity = 1)
    private val classifier = AssetClassifier(classifying, viewModelScope)
    private val jacsPicker = JacsPicker(jacsCodes, viewModelScope)
    private val photoFill = PhotoAutoFill(viewModelScope) { fillFromPhotos() }
    private val openedFiles = MutableSharedFlow<OpenableFile>(extraBufferCapacity = 1)
    private val dropped = MutableSharedFlow<Media>(extraBufferCapacity = DROPPED_BUFFER)

    /**
     * Shots a new asset was opened with that were left out (their kind already had the most photos allowed): the host
     * takes them off the shots it makes the asset from, and deletes their files.
     */
    val droppedCaptures: SharedFlow<Media> = dropped.asSharedFlow()
    private val documents = AssetDocumentsPanel(documentUseCases, viewModelScope, observeOnline(), message::emit, openedFiles::emit)

    /** A document's file fetched from the server, for the screen to open in another app. */
    val openedDocuments: SharedFlow<OpenableFile> = openedFiles.asSharedFlow()

    init {
        // The Documents tab follows the open asset once it is saved (the server's documents too once the server has it).
        viewModelScope.launch {
            target.map { t -> t?.takeIf { !it.isNew }?.asset?.id }.distinctUntilChanged().collect { documents.show(it, it != null && isServerId(it)) }
        }
    }

    /** What [pickers] puts together. */
    private data class Pickers(
        val classified: ClassifierUi,
        val jacs: JacsPickerState?,
        val online: Boolean,
        val aiOn: Boolean,
        /** JACS code → title, from the catalogue kept on the phone. */
        val jacsTitles: Map<String, String>,
    )

    /**
     * The classifier's part of the screen, the JACS code picker, whether the device is online and the property's AI switch,
     * as one (the screen's state takes five sources), again whenever the signed-in user's access changes.
     */
    private val pickers = combine(
        classifier.ui, combine(jacsPicker.state, jacsCodes.titles()) { p, t -> p to t }, observeOnline(), observeAccess(), aiSwitch.observe(),
    ) { classified, (jacs, jacsTitles), online, _, aiOn ->
        // The access itself isn't kept: a change to it (fetched, changed, failed) only makes the screen work out its
        // permission checks again (lock, Check, Clone, comments, own fields, change actions, tags).
        Pickers(classified, jacs, online, aiOn, jacsTitles)
    }

    /** Messages to flash after saving, submitting, validating or adding media. */
    val notices: SharedFlow<UiText> = message.asSharedFlow()

    /**
     * The asset was saved, or its status or change request changed: what to flash, and the page closes back to the list
     * (which shows it where it now belongs, with the new counts).
     */
    val finished: SharedFlow<UiText> = done.asSharedFlow()

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
            val checksAndDocs = combine(validation, documents.state) { v, d -> v to d }
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
                // One in Managed Assets is kept apart from the others; it opens read-only.
                else -> (catalog.assets.firstOrNull { it.id == t.asset.id } ?: catalog.managed.firstOrNull { it.id == t.asset.id })
                    .let { a -> Opened(t, a, a?.let { useCases.details(it.id) }) }
            }
        }

    val state: StateFlow<AssetDetailState> =
        combine(opened, form, observeConfig(), fromDb, pickers) { o, f, config, db, (classified, jacs, online, aiOn, jacsTitles) ->
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
                // The files added to it (kept on the phone, so also offline and while new), then from the server its source
                // file, its web pages and manuals, and any file added on the web that hasn't reached the phone yet.
                documents = documentsOf(
                    asset.media.filter { m -> m.isDocument() && m.fieldId == null } + f.newPicks.filter { it.isDocument() },
                    if (t.isNew) emptyList() else db.documents.documents,
                ),
                documentsUi = db.documents,
                saved = saved, edits = f.edits, fields = fields, details = details,
                media = asset.media.map { m -> if (m.id in f.movedKinds) m.copy(category = f.movedKinds[m.id]) else m } + f.newPicks,
                newPicks = f.newPicks,
                ai = f.ai?.takeIf { f.aiOpen }, aiBusy = f.ai?.phase.let { it == AiPhase.SEARCHING || it == AiPhase.LOADING }, voice = f.voice, voiceReading = f.voiceReading, typing = f.typing, sortingPhotos = f.sorting > 0,
                activity = if (t.isNew) emptyList() else db.activity,
                handling = if (t.isNew) null else handling(asset, db.activity),
                commentDraft = f.commentDraft, commentError = f.commentProblem?.problemText(), commentPosting = f.commentPosting,
                validation = if (t.isNew) null else db.validation, validating = f.validating,
                changeNote = f.changeNote, changeRequestMine = changeRequests.isMine(asset.changeRequest),
                reviewer = changeRequests.isReviewer(),
                cloneOpen = f.cloneOpen && !t.isNew, cloning = f.cloning,
                aiManuals = f.aiManuals, manualsSearching = f.manualsSearching,
                canAddField = !t.isNew && !asset.managed && addField.allowed(),
                addFieldOpen = f.addFieldOpen && !t.isNew, addingField = f.addingField, addFieldError = f.addFieldError,
                currencies = f.currencies,
                changeRequester = changeRequests.requesterName(asset.changeRequest),
                lock = if (t.isNew) null else canEditAsset.lock(asset),
                // Every saved asset, one in Managed Assets too (as the web's: the copies are new drafts), for a group that
                // can collect.
                canClone = !t.isNew && useCases.canWorkOn.canCollect(),
                buttons = buttonBlocks(asset, t.isNew).toUi(changeRequests.isReviewer(), changeRequests.isMine(asset.changeRequest)),
                classify = classified.sheet,
                jacs = jacs,
                jacsTitle = (f.edits["jacs"] ?: saved["jacs"])?.trim()?.let(jacsTitles::get),
                online = online,
                refreshing = f.refreshing,
                aiOn = aiOn,
                aiHasResults = f.ai?.phase == AiPhase.RESULTS,
            )
        }.stateIn(viewModelScope, SharingStarted.Eagerly, AssetDetailState())

    /** What decides which of its type's fields the form adds: the asset, its type, and the names of the fields it has. */
    private data class TypeFieldsFor(val assetId: String?, val formatId: String?, val own: List<String>)

    init {
        // As the web's form: the fields of the asset's type (saved, or picked and not saved yet) that it doesn't have yet
        // follow its own, so they show and can be filled; saving an answer makes the asset's own copy (TypeFieldsWriter).
        // Before, they showed only right after the type was picked, so an asset classified elsewhere showed none.
        viewModelScope.launch {
            state.map { s -> TypeFieldsFor(s.asset?.id, s.formats.formatFor(s.values)?.id, s.fields.filter { typeFieldName(it.key) == null }.map { it.label }) }
                .distinctUntilChanged()
                .collect { k ->
                    val s = state.value
                    if (k.assetId == null) classifier.clearTypeFields() else classifier.showTypeFields(s.formats, s.fields, s.values)
                }
        }
    }

    /**
     * Shows [asset]; when that asset is already open its edits are kept (they survive recomposition). A new asset that is
     * already open only takes [asset]'s place: the top bar's location, which changes when another location tab is chosen
     * (a place picked in the form itself still comes first).
     */
    fun open(asset: Asset, isNew: Boolean, prefill: Map<String, String> = emptyMap(), scan: ScanFacts? = null) {
        val current = target.value
        if (current != null && current.asset.id == asset.id && current.isNew == isNew) {
            if (isNew) {
                target.value = current.copy(
                    asset = current.asset.copy(
                        loc = asset.loc, where = asset.where, locationId = asset.locationId,
                        buildingId = asset.buildingId, floorId = asset.floorId,
                    ),
                )
            }
            return
        }
        aiJob?.cancel()
        manualsJob?.cancel()
        scanFill?.cancel()
        classifier.reset()
        jacsPicker.close()
        photoFill.cancel()
        form.value = Form()
        target.value = Target(asset, isNew, prefill)
        // Someone may have flipped the property's AI switch: read again when the last read is old (the web's askAiOn).
        viewModelScope.launch { aiSwitch.refresh() }
        if (isNew) startWithCaptures(asset)
        if (isNew && scan != null) startScanFill(asset.id)
    }

    private var scanFill: Job? = null

    /**
     * A new asset opened from a scanned device (Bluetooth, network or access point): once its form is ready (with the
     * scan's make, model and Description in it), the AI is asked about what the scan found. With a model, the product is
     * looked up and the form filled as photos fill it (the AI button blinks meanwhile; only empty fields, as unsaved
     * edits); the server looks a product up by its model, so without one Suggest type runs on its name, make and
     * Description instead. The scan's identifiers are never on the form, so none is sent.
     */
    private fun startScanFill(assetId: String) {
        scanFill = viewModelScope.launch {
            val ready = withTimeoutOrNull(FORM_WAIT_MS) {
                state.first { it.asset?.id == assetId && it.isNew && it.fields.isNotEmpty() }
            } ?: return@launch
            when {
                ready.locked -> Unit
                // With AI off the look-up isn't asked; Suggest type still answers (from same-model assets only).
                ready.value("model").isNotBlank() && ready.aiOn -> autoFill(emptyList(), fromScan = true)
                else -> openClassify()
            }
        }
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
            // Several shots may sort into one kind: those past its most are left out (as the server refuses them), and
            // taken off the shots the asset is made from.
            val shots = target.value?.takeIf { it.isNew && it.asset.id == asset.id }?.asset?.media.orEmpty()
            val fitted = fitPhotoRules(state.value.photoRules, emptyList(), shots)
            if (fitted.refused.isNotEmpty()) {
                target.update { t -> if (t != null && t.isNew && t.asset.id == asset.id) t.copy(asset = t.asset.copy(media = fitted.kept)) else t }
                photoLimitNotices(fitted).forEach { message.emit(it) }
                fitted.refused.forEach { dropped.emit(it) }
            }
            if (target.value?.let { it.isNew && it.asset.id == asset.id } == true) photoFill.schedule()
        }
    }

    /**
     * Pull to refresh, as on the asset list: the asset, its comments and history, checks and documents are read again
     * from the property's data (what the last sync brought). The ring stays a moment so the pull is seen to have worked.
     */
    fun refresh() {
        if (form.value.refreshing || target.value?.isNew != false) return
        form.update { it.copy(refreshing = true) }
        viewModelScope.launch {
            try {
                launch { aiSwitch.refresh(maxAgeMs = 0) }
                documents.reload()
                reloadData()
                delay(REFRESH_SHOW_MS)
            } finally {
                form.update { it.copy(refreshing = false) }
            }
        }
    }

    fun close() {
        aiJob?.cancel()
        manualsJob?.cancel()
        scanFill?.cancel()
        classifier.reset()
        photoFill.cancel()
        target.value = null
        form.value = Form()
        documents.show(null, onServer = false)
    }

    fun edit(key: String, value: String) {
        val s = state.value
        if (s.locked) return
        // A new system or asset type also resets the types below it that the catalogue doesn't file under it.
        val typed = if (key in CLASS_KEYS) formData.reclassify(s.formats, s.values, key, value) else mapOf(key to value)
        // A type picked brings its own JACS code, unless the code in the form is already under the same number (as the
        // web and the server: 238219-TRM stays when Fan Coil Units, 238219, is picked).
        val code = if (key in CLASS_KEYS) jacsCodeForType(s.formats.formatFor(s.values + typed), s.value("jacs")) else null
        val changes = if (code != null) typed + ("jacs" to code) else typed
        if (code != null) classifier.noteCode(code)
        // A value typed over an applied AI suggestion no longer came from its page, as the server drops it.
        form.update { it.copy(edits = it.edits + changes, aiSources = it.aiSources - changes.keys) }
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
        form.update { it.copy(place = place.takeIf { p -> p.placeId != current }) }
    }

    fun discardEdits() {
        // The type's fields follow the type left after discarding (see the init above).
        form.update { it.copy(edits = emptyMap(), aiSources = emptyMap(), place = null) }
    }

    /** Keeps the edits; the asset keeps its status, as the server's edit does. */
    fun save() {
        val s = state.value
        val id = s.asset?.id ?: return
        if (s.dirtyCount == 0 || s.managed) return
        write {
            // An edit keeps the asset's status, as the server's; its AI flag is worked out again from its empty fields. The
            // place picked goes in with the rest of the changes, in one history line.
            useCases.saveDetails(id, s.values, sources = aiPages(s), place = s.pendingPlace)
            clearEditsOnceRefreshed(s)
            done.emit(UiText.Res(R.string.asset_changes_saved))
        }
    }

    /**
     * Keeps the edits and submits the asset, as the server's submit: with a mandatory field empty or too few photos it
     * becomes an exception (the screen asks first).
     */
    fun submit() {
        val s = state.value
        val id = s.asset?.id ?: return
        if (s.managed) return
        write {
            useCases.saveDetails(id, s.values, Status.SUBMITTED, sources = aiPages(s), place = s.pendingPlace)
            clearEditsOnceRefreshed(s)
            done.emit(UiText.Res(if (s.complete) R.string.asset_submitted_notice else R.string.asset_exception_notice))
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

    /** Opened for one kind of photo: how many more it takes (the camera's gallery picks up to that); null otherwise. */
    fun galleryLimit(): Int? = photoKind?.let { cameraRoom() }

    /**
     * A shot just taken in the camera, sorted (the card's kind, or read off the photo) and checked against the photo
     * rules beside the asset's photos and the shots already [ticked].
     */
    suspend fun sortShot(shot: Media, ticked: List<Media>): SortedShot =
        useCases.sortShot(shot, state.value.media + ticked, photoKind, readKind = !asOther)

    /** Why shot [shot] can't be ticked beside the asset's photos and the shots already [ticked]; null when it can. */
    suspend fun shotRefusal(shot: Media, ticked: List<Media>): PhotoRefusal? = useCases.sortShot.refusal(shot, state.value.media + ticked)

    fun choosePhotoKind(kind: PhotoKind?) {
        photoKind = kind
        asOther = false
    }

    /**
     * Media's Camera: what it takes (and picks from its gallery) goes to Other photos as it is, not read and sorted into
     * nameplate, asset or label ID.
     */
    fun chooseOtherPhotos() {
        photoKind = null
        asOther = true
    }

    /** The camera was opened from Media's Camera: its photos go to Other photos unsorted ([chooseOtherPhotos]). */
    private var asOther = false

    /**
     * Media's Add file and the Documents tab's +: Excel, CSV, PDF, Word and text files only (no photos or videos), at most
     * 10 MB each. They are kept on the phone as the asset's media and synced like its photos, online or offline (a new
     * asset keeps them until it is saved); the Documents tab lists them too. What is refused is said.
     */
    fun attachFiles(picked: List<Media>) {
        photoKind = null
        asOther = false
        val refusals = picked.associateWith(::attachRefusal)
        val taken = picked.filter { refusals[it] == null }
        deleteOwned(picked - taken.toSet())
        if (AttachRefusal.TYPE in refusals.values) message.tryEmit(UiText.Res(R.string.media_file_type_refused))
        if (AttachRefusal.TOO_BIG in refusals.values) {
            message.tryEmit(UiText.Res(R.string.media_file_too_big, listOf(MAX_ATTACHED_FILE_BYTES / (1024 * 1024))))
        }
        addPicked(taken)
    }

    /**
     * Photos, videos or documents added to the asset (kept in the form until a new asset is created). Photos added from a
     * photo card are of that card's kind; videos and files never are. Those from Media's Camera go to Other photos as
     * they are; any other photo without a kind is read on the device first and sorted into nameplate, label ID or asset
     * (one that can't be read gets no kind).
     */
    fun attach(picked: List<Media>) {
        val kind = photoKind.also { photoKind = null }
        // From Media's Camera: Other photos, as taken (not sorted).
        val other = asOther.also { asOther = false }
        // Files (not photos, videos or voice memos) are kept as the asset's media, as Add file keeps them ([attachFiles]).
        val (docs, rest) = picked.partition { it.isDocument() }
        if (docs.isNotEmpty()) attachFiles(docs)
        if (rest.isEmpty()) return
        val media = rest.map { if (!it.video && !it.isDoc && !it.audio) it.copy(category = kind ?: it.category) else it }
        if (other || !useCases.sortPhotos.needed(media)) {
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

    /** Fetches [document]'s file from the server, then the screen opens it ([openedDocuments]). */
    fun openDocument(document: AssetDocument) = documents.open(document)

    private val sourcePreviewFlow = MutableStateFlow<Media?>(null)

    /** A photo or video the asset was read from, fetched and shown in the app's viewer ([previewSource]); null: none. */
    val sourcePreview: StateFlow<Media?> = sourcePreviewFlow.asStateFlow()

    /** Fetches the photo or video [document] (a source file) and shows it in the app's viewer, as the web's media viewer. */
    fun previewSource(document: AssetDocument) = documents.fetchFile(document) { file ->
        sourcePreviewFlow.value = Media(id = document.id, uri = file.uri, video = document.fileKind == VIDEO_KIND)
    }

    fun closeSourcePreview() {
        sourcePreviewFlow.value = null
    }

    private fun addPicked(picked: List<Media>) {
        val s = state.value
        val asset = s.asset ?: return
        if (picked.isEmpty() || s.managed) return
        // A kind that is full (by its card, or as sorted off the photo) takes no more: the extra photos are left out.
        val fitted = fitPhotoRules(s.photoRules, s.media, picked)
        photoLimitNotices(fitted).forEach { message.tryEmit(it) }
        deleteOwned(fitted.refused)
        val media = fitted.kept
        if (media.isEmpty()) return
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
        if (s.asset == null || s.locked) return
        val photos = formData.lookups.photosToRead(s.media)
        if (photos.isEmpty()) return
        autoFill(photos, fromScan = false)
    }

    /**
     * The AI's look-up of [photos] (or, from a scan, of what it found) and what is typed (`POST /lookup`): the AI sheet's
     * own look-up, run as its request, so the AI button blinks until it is done and opening the sheet shows where it got
     * to, then what it found (kept until the asset is closed). What it finds also goes into the form by
     * [PlanPhotoFillUseCase]'s rules, never over what is there. A newer look-up replaces one still running.
     * [fromScan]: there are no photos, only what a scan found (its make and model).
     */
    private fun autoFill(photos: List<Media>, fromScan: Boolean) {
        val s = state.value
        val asset = s.asset ?: return
        // AI off for the property: nothing is filled by AI, as the server's background work skips it.
        if (s.locked || !s.aiOn) return
        aiJob?.cancel()
        form.update {
            it.copy(ai = AiDialogState(AiPhase.SEARCHING, s.value("make"), s.value("model"), startedAt = System.currentTimeMillis(), fromPhoto = photos.isNotEmpty()))
        }
        aiJob = viewModelScope.launch {
            message.emit(UiText.Res(if (fromScan) R.string.ai_scan_reading else R.string.ai_auto_reading))
            val lookup = try {
                // No type is sent: the answer then names the type with its JACS code, and fills the fields of the type it
                // names (which is the asset's own type when it already has the right one).
                formData.lookups.lookUpOnPhone(asset.id, photos, s.values.filterValues { it.isNotBlank() }, masterFormatId = null)
            } catch (e: OnlineException) {
                val why = e.error.aiText()
                setAi { it.copy(phase = AiPhase.ERROR, error = why) }
                message.emit(
                    when {
                        e.error == OnlineError.Offline -> UiText.Res(R.string.ai_auto_offline)
                        // Turned off meanwhile: said as it is, and nothing more is asked of the AI.
                        why == AI_OFF -> AI_OFF
                        else -> UiText.Res(if (fromScan) R.string.ai_scan_failed else R.string.ai_auto_failed, listOf(why))
                    },
                )
                return@launch
            }
            // What it found, as the sheet shows it (open now, or when the AI button is tapped).
            show(lookup)
            // What is in the form now decides (it may have changed while the look-up ran).
            val now = state.value
            if (now.locked || now.asset?.id != asset.id) return@launch
            val fill = planPhotoFill(lookup, now::value, now.formats.formatFor(now.values)?.id)
            if (fill.values.isEmpty()) {
                message.emit(UiText.Res(if (fromScan) R.string.ai_scan_nothing else R.string.ai_auto_nothing))
                return@launch
            }
            // Each looked-up field's page goes with it, kept on Save while the value stays as suggested.
            val pages = fill.sources.mapValues { (key, page) -> AiSource(page, fill.values.getValue(key)) }
            form.update { it.copy(edits = it.edits + fill.values, aiSources = it.aiSources - fill.values.keys + pages) }
            if (fill.typeSet) classifier.showTypeFields(now.formats, now.fields, now.values + fill.values)
            message.emit(photoFillText(fill))
        }
    }

    /** "AI filled the type, the JACS code and 5 fields. Check the yellow ones, then save." */
    private fun photoFillText(fill: PhotoFill): UiText =
        UiText.Res(R.string.ai_auto_done, listOf(filledText(fill.typeSet, fill.jacsSet, fill.fields)))

    /** What the AI put into the form, in words: "the type, the JACS code and 5 fields" (at least one of them is set). */
    private fun filledText(type: Boolean, jacs: Boolean, fields: Int): UiText {
        val parts = buildList<UiText> {
            if (type) add(UiText.Res(R.string.ai_auto_type))
            if (jacs) add(UiText.Res(R.string.ai_auto_jacs))
            if (fields > 0) add(UiText.Plural(R.plurals.ai_auto_fields, fields))
        }
        return parts.dropLast(1).reduceOrNull { a, b -> UiText.Res(R.string.ai_auto_list, listOf(a, b)) }
            ?.let { UiText.Res(R.string.ai_auto_and, listOf(it, parts.last())) } ?: parts.last()
    }

    /**
     * Removes [m]. From a saved asset, as the web's Remove (not on a read-only one); the sync pushes it. From a new asset,
     * before it is saved: a photo added on the page, or one it was opened with ([onDropCapture] also takes that off the
     * shots it will be made from). A file the app made for it is deleted.
     */
    fun removeMedia(m: Media, onDropCapture: (Media) -> Unit = {}) {
        val s = state.value
        val asset = s.asset ?: return
        if (s.locked) return
        if (s.isNew) {
            if (form.value.newPicks.any { it.id == m.id }) {
                form.update { f -> f.copy(newPicks = f.newPicks.filterNot { it.id == m.id }) }
                deleteOwned(listOf(m))
            } else {
                target.update { t ->
                    if (t != null && t.isNew && t.asset.id == asset.id) t.copy(asset = t.asset.copy(media = t.asset.media.filterNot { it.id == m.id })) else t
                }
                onDropCapture(m)
            }
            message.tryEmit(UiText.Res(R.string.media_removed))
            return
        }
        write {
            useCases.removeMedia(asset.id, m.id)
            message.emit(UiText.Res(R.string.media_removed))
        }
    }

    /**
     * Moves photo [m] to the card of [kind] (null: Other photos), within that kind's most photos. A new asset's is saved
     * with the asset in that kind; a saved asset's is saved straight away, as the web's photo kind (the sync pushes it).
     */
    fun movePhoto(m: Media, kind: PhotoKind?) {
        val s = state.value
        val asset = s.asset ?: return
        if (s.locked || m.category == kind || m.video || m.audio || m.isDoc || m.fieldId != null) return
        if (kind != null) {
            val max = s.photoRules.firstOrNull { it.kind == kind }?.max ?: 0
            if (s.media.count { it.category == kind && !it.video && !it.audio && !it.isDoc } >= max) {
                message.tryEmit(UiText.Res(R.string.media_kind_full, listOf(UiText.Res(kind.nameRes()), max)))
                return
            }
        }
        if (!s.isNew) {
            // It shows in its new card at once; reading the asset again from the database takes a moment.
            form.update { it.copy(movedKinds = it.movedKinds + (m.id to kind)) }
            viewModelScope.launch {
                try {
                    useCases.setPhotoKind(asset.id, m, kind)
                    message.emit(UiText.Res(R.string.media_moved_to, listOf(UiText.Res(kind?.nameRes() ?: R.string.media_other))))
                    // Kept until the asset read again has it there (or a few seconds, whichever is first).
                    withTimeoutOrNull(MOVE_SETTLE_MS) {
                        useCases.observe().first { c -> c.assets.firstOrNull { it.id == asset.id }?.media?.firstOrNull { it.id == m.id }?.category == kind }
                    }
                } catch (e: DataException) {
                    message.emit(e.error.toUiText())
                } finally {
                    form.update { f -> if (f.movedKinds[m.id] == kind) f.copy(movedKinds = f.movedKinds - m.id) else f }
                }
            }
            return
        }
        val moved = { x: Media -> if (x.id == m.id) x.copy(category = kind) else x }
        if (form.value.newPicks.any { it.id == m.id }) {
            form.update { f -> f.copy(newPicks = f.newPicks.map(moved)) }
        } else {
            target.update { t -> if (t != null && t.isNew) t.copy(asset = t.asset.copy(media = t.asset.media.map(moved))) else t }
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
     * system, asset and sub type, whose fields then show ([JacsTypeUseCase], offline too). Without a type for the code,
     * or when the asset's type already has a code under the same number, only the code changes. Nothing is saved until Save.
     */
    fun pickJacs(code: String) {
        if (state.value.locked) return
        jacsPicker.close()
        val value = code.trim()
        classifier.noteCode(value)
        form.update { it.copy(edits = it.edits + ("jacs" to value), aiSources = it.aiSources - "jacs") }
        if (value.isEmpty()) return
        viewModelScope.launch {
            // Worked out on the phone (also offline): the type whose own code it is, or the one the catalogue files it
            // under; the server's match when neither (online). The type stays when its own code is under the same number.
            val now = state.value
            val type = jacsCodes.typeFor(value, now.formats, now.formats.formatFor(now.values)) ?: return@launch
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
            f.copy(edits = kept + add, aiSources = f.aiSources - add.keys - remove.keys)
        }
    }

    /** Fetches (and, while the server still searches, keeps fetching) the manuals of the open asset's model. */
    private var manualsJob: Job? = null

    /**
     * The manuals the look-up pulled, as the web's AI sheet shows them: the server's list for the asset's model, asked
     * again every [MANUALS_POLL_MS] while its search for them runs. Only an asset the server has; offline it says nothing.
     */
    private fun followManuals() {
        val s = state.value
        val asset = s.asset ?: return
        if (s.isNew) return
        manualsJob?.cancel()
        manualsJob = viewModelScope.launch {
            while (true) {
                val found = try {
                    formData.lookups.manuals(asset.id)
                } catch (_: OnlineException) {
                    return@launch
                }
                form.update { it.copy(aiManuals = found.manuals, manualsSearching = found.searching) }
                if (!found.searching) return@launch
                delay(MANUALS_POLL_MS)
            }
        }
    }

    /** The running look-up of the open asset (it keeps going when the dialog is closed). */
    private var aiJob: Job? = null

    private fun setAi(f: (AiDialogState) -> AiDialogState) = form.update { it.copy(ai = it.ai?.let(f)) }

    /**
     * Opens the AI suggestions dialog, as the server and the web do (mobile team notes, section 11): a look-up still
     * running is followed; a finished one with something new (values for still-empty fields, a type, or a JACS code
     * while the asset has none) is shown; else a look-up starts. An asset the server has is looked up by its model in the background (`lookup-jobs`); one not on
     * the server yet, or with no model typed, by the phone's look-up from the photos here (`POST /lookup`). Either may
     * suggest a type. With neither a photo nor a model it asks for the model.
     */
    fun openAi() {
        val s = state.value
        val asset = s.asset ?: return
        if (s.locked) return
        form.update { it.copy(aiOpen = true) }
        // Picks up where it was left: a look-up still running (the button blinked meanwhile), its results, or the
        // manufacturer and model being typed.
        when (form.value.ai?.phase) {
            AiPhase.LOADING, AiPhase.SEARCHING -> return
            // It was waiting for the manufacturer and model: what the form has now (typed there meanwhile, saved or
            // not) goes in, and with a model the look-up goes ahead.
            AiPhase.IDENTIFY -> {
                setAi { it.copy(make = s.value("make").ifBlank { it.make }, model = s.value("model").ifBlank { it.model }) }
                if (form.value.ai?.model?.isNotBlank() == true && s.aiOn) lookUp()
                return
            }
            AiPhase.RESULTS -> {
                markSeen()
                return
            }
            else -> Unit
        }
        form.update { it.copy(ai = AiDialogState(AiPhase.LOADING, s.value("make"), s.value("model"))) }
        aiJob?.cancel()
        aiJob = viewModelScope.launch {
            // An asset being added is only on the phone: the phone's look-up, from its photos and what is typed (not with
            // AI off for the property).
            if (s.isNew) {
                if (state.value.aiOn) lookUpOnPhone() else setAi { it.copy(phase = AiPhase.ERROR, error = AI_OFF) }
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
                    // AI off: what was already found can still be reviewed (above), and nothing new is started (the web's).
                    !state.value.aiOn -> setAi { it.copy(phase = AiPhase.ERROR, error = AI_OFF) }
                    s.value("model").isNotBlank() -> runLookup()
                    else -> lookUpOnPhone()
                }
            } catch (e: OnlineException) {
                failAi(e.error)
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
            else failAi(e.error)
            return
        }
        show(found)
        if (!form.value.aiOpen) message.emit(doneText(found, state.value.value("name").ifBlank { state.value.asset?.name.orEmpty() }))
    }

    /**
     * Closes the AI suggestions sheet. A look-up under way carries on: the AI button blinks meanwhile, a toast says when
     * it is done, and opening the sheet again shows where it got to (or its results). Results stay to come back to.
     */
    fun closeAi() = form.update { it.copy(aiOpen = false) }

    /** The results on show were seen, which tidies the asset's "ready" mark at the server. */
    private fun markSeen() {
        val lookup = form.value.ai?.lookup?.takeIf { !it.seen } ?: return
        viewModelScope.launch {
            try {
                formData.lookups.seen(lookup.id)
            } catch (_: OnlineException) {
                // Only tidies the mark.
            }
        }
    }

    // ---- Change requests (a submitted asset): ask an admin to reopen it, withdraw or decline, or reopen it (admins)

    /** Opens the change request dialog, with the note already sent when asking again. */
    /**
     * Clone (the web's header button): opens its sheet. It copies the asset as saved, so with unsaved changes it says to
     * save or discard them first.
     */
    fun openClone() {
        val s = state.value
        if (s.asset == null || !s.canClone) return
        if (s.dirtyCount > 0) {
            message.tryEmit(UiText.Res(R.string.clone_save_first))
            return
        }
        form.update { it.copy(cloneOpen = true) }
    }

    fun closeClone() = form.update { if (it.cloning) it else it.copy(cloneOpen = false) }

    /**
     * Makes [copies] copies of the open asset taking its place down to [place] and the saved values of the fields [keys]
     * (app field keys; the type stays only with its system, asset and sub type all picked). Photos are never copied.
     */
    fun clone(copies: Int, place: ClonePlace, keys: Set<String>) {
        val s = state.value
        val source = s.asset ?: return
        if (s.isNew || !s.canClone || form.value.cloning) return
        // Its Type and Location tag are copied too, as the web's clone does.
        val values = s.saved.filterKeys { it in keys || it in ASSET_DETAIL_KEYS }.filterValues { it.isNotBlank() }
        val request = CloneRequest(copies = copies, place = place, values = values, keepType = CLONE_TYPE_KEYS.all { it in keys })
        form.update { it.copy(cloning = true) }
        viewModelScope.launch {
            try {
                cloneAsset(source, request)
                form.update { it.copy(cloneOpen = false) }
                message.emit(UiText.Res(R.string.clone_done))
            } catch (e: DataException) {
                message.emit(if (e.error == DataError.FAILED) UiText.Res(R.string.clone_failed) else e.error.toUiText())
            } finally {
                form.update { it.copy(cloning = false) }
            }
        }
    }

    // ---- Add field (the web's "+ Add field", for this asset only)

    fun openAddField() {
        val s = state.value
        if (s.isNew || !s.canAddField || !s.canEdit) return
        form.update { it.copy(addFieldOpen = true, addFieldError = null) }
        // For a currency field's currency; offline (and nothing kept) the list stays empty and the sheet says so.
        if (form.value.currencies.isEmpty()) viewModelScope.launch {
            try {
                val list = getCurrencies()
                form.update { it.copy(currencies = list) }
            } catch (_: OnlineException) {
            }
        }
    }

    fun closeAddField() = form.update { if (it.addingField) it else it.copy(addFieldOpen = false, addFieldError = null) }

    /** The name was changed: a "name taken" message no longer applies. */
    fun clearAddFieldError() = form.update { if (it.addFieldError == null) it else it.copy(addFieldError = null) }

    /**
     * Adds a field named [name] of [kind] to the open asset alone, with its [options] (a dropdown or multiple check boxes)
     * or [currency] (a currency field). It shows at the end of the asset's fields, empty, to fill in like the rest; what is
     * already typed in the form stays (as the web's).
     */
    fun addOwnField(name: String, kind: FieldKind, options: List<String>, currency: String?) {
        val s = state.value
        val asset = s.asset ?: return
        if (s.isNew || s.managed || form.value.addingField) return
        form.update { it.copy(addingField = true, addFieldError = null) }
        viewModelScope.launch {
            try {
                when (addField(asset.id, name, kind, options, currency)) {
                    is AddFieldOutcome.Added -> {
                        form.update { it.copy(addFieldOpen = false) }
                        message.emit(UiText.Res(R.string.own_field_added, listOf(UiText.Raw(name.trim()))))
                    }
                    AddFieldOutcome.NameTaken ->
                        form.update { it.copy(addFieldError = UiText.Res(R.string.own_field_name_taken, listOf(UiText.Raw(name.trim())))) }
                    AddFieldOutcome.NoName -> form.update { it.copy(addFieldError = UiText.Res(R.string.own_field_name_needed)) }
                    AddFieldOutcome.NoOptions -> form.update { it.copy(addFieldError = UiText.Res(R.string.own_field_options_needed)) }
                    AddFieldOutcome.NoCurrency -> form.update { it.copy(addFieldError = UiText.Res(R.string.own_field_currency_needed)) }
                }
            } catch (e: DataException) {
                form.update { it.copy(addFieldError = e.error.toUiText()) }
            } finally {
                form.update { it.copy(addingField = false) }
            }
        }
    }

    /**
     * Removes the open asset's own field [key] with its answer (the server's DELETE of an asset's field); an unsaved
     * value typed into it goes too. Fields every asset has can't be removed.
     */
    fun removeOwnField(key: String) {
        val s = state.value
        val asset = s.asset ?: return
        val field = s.fields.firstOrNull { it.key == key }?.takeIf { it.ownField } ?: return
        if (s.isNew || !s.canAddField || !s.canEdit) return
        viewModelScope.launch {
            try {
                if (removeField(asset.id, key)) {
                    form.update { it.copy(edits = it.edits - key, aiSources = it.aiSources - key) }
                    message.emit(UiText.Res(R.string.own_field_removed, listOf(UiText.Raw(field.label))))
                } else {
                    message.emit(UiText.Res(R.string.own_field_remove_failed))
                }
            } catch (e: DataException) {
                message.emit(e.error.toUiText())
            }
        }
    }

    fun openChangeRequest() {
        if (state.value.managed) return
        form.update { it.copy(changeNote = state.value.asset?.changeRequest?.note.orEmpty()) }
    }

    fun editChangeNote(text: String) = form.update { it.copy(changeNote = text.take(CHANGE_NOTE_MAX)) }

    fun cancelChangeRequest() = form.update { it.copy(changeNote = null) }

    fun sendChangeRequest() {
        if (state.value.managed) return
        val id = state.value.asset?.id ?: return
        val note = form.value.changeNote ?: return
        form.update { it.copy(changeNote = null) }
        write {
            changeRequests.request(id, note)
            done.emit(UiText.Res(R.string.change_request_sent))
        }
    }

    /** Withdraws one's own change request, or (a reviewer) declines someone else's; the asset stays submitted. */
    fun closeChangeRequest() {
        val s = state.value
        val asset = s.asset ?: return
        val request = asset.changeRequest ?: return
        if (s.managed) return
        val declining = s.reviewer && !s.changeRequestMine
        write {
            changeRequests.close(asset.id, request)
            done.emit(UiText.Res(if (declining) R.string.change_request_declined else R.string.change_request_withdrawn))
        }
    }

    /** An admin or a reviewer reopens the asset: back to draft, which approves its change request, as the server's. */
    fun reopenAsset() {
        if (state.value.managed) return
        val id = state.value.asset?.id ?: return
        write {
            changeRequests.reopen(listOf(id))
            done.emit(UiText.Res(R.string.change_request_reopened))
        }
    }

    /**
     * Back to draft (the web's Move to draft): an exception to be collected again, or an admin or a reviewer reopening a
     * submitted asset.
     */
    fun moveToDraft() {
        if (state.value.managed) return
        val id = state.value.asset?.id ?: return
        write {
            changeRequests.reopen(listOf(id))
            done.emit(UiText.Plural(R.plurals.change_bulk_drafted, 1))
        }
    }

    /** A reviewer or an admin signs the submitted asset off without a validation run (the server's bulk validate). */
    fun markValidated() {
        if (state.value.managed) return
        val id = state.value.asset?.id ?: return
        write {
            val outcome = changeRequests.markValidated(listOf(id))
            if (outcome.validated > 0) done.emit(UiText.Plural(R.plurals.sign_off_done, outcome.validated))
            else message.emit(UiText.Plural(R.plurals.sign_off_not_submitted, 1))
        }
    }

    fun editAiIdentity(make: String, model: String) = setAi { it.copy(make = make, model = model) }

    /** Starts (again) a look-up with the manufacturer and model typed in the dialog (not while the property has AI off). */
    fun lookUp() {
        if (!state.value.aiOn) {
            message.tryEmit(AI_OFF)
            return
        }
        aiJob?.cancel()
        aiJob = viewModelScope.launch { runLookup() }
    }

    /** Back to typing the manufacturer and model. */
    fun changeAiModel() = setAi { it.copy(phase = AiPhase.IDENTIFY, error = null) }

    fun toggleAiPick(key: String) = setAi { d -> d.copy(picks = d.picks + (key to (d.picks[key] ?: return@setAi d).let { it.copy(on = !it.on) })) }

    /** Ticks or unticks the suggested type (its fields' values go with it). */
    fun toggleAiType() = setAi { it.copy(typeOn = it.typeOn?.not()) }

    /** Ticks or unticks the JACS code suggested for the asset's own type. */
    fun toggleAiJacs() = setAi { it.copy(jacsOn = it.jacsOn?.not()) }

    /** Editing a suggested value ticks it. */
    fun editAiPick(key: String, value: String) = setAi { it.copy(picks = it.picks + (key to AiPick(true, value))) }

    /**
     * Puts the ticked suggestions (and the manufacturer / model typed) into the form; nothing is saved until Save. A
     * ticked type goes in first, as the web classifies first: its system, asset and sub type and its JACS code, then its
     * fields show and take their values. An unticked type's values are dropped. A ticked JACS code for the asset's own
     * type goes in as the code alone (the type stays), and is saved with the values, as the web's `jacsCodeId`.
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
        val jacs = d.lookup?.suggestedJacsCode?.code?.takeIf { d.jacsOn == true }
        val update = typedIdentity(d) + typeValues + jacs?.let { mapOf("jacs" to it) }.orEmpty() + picked
        if (update.isEmpty()) return
        if (type != null) classifier.showTypeFields(s.formats, s.fields, s.values + update)
        // Each suggestion's page goes with it, kept on Save while the value stays as suggested (as the web sends `sources`).
        val suggested = d.lookup?.values.orEmpty()
        val pages = picked.mapNotNull { (key, value) ->
            d.lookup?.sources?.get(key)?.takeIf { suggested[key]?.trim() == value }?.let { key to AiSource(it, value) }
        }.toMap()
        // What the AI found stays (the AI button shows it again) until the asset is closed.
        form.update {
            it.copy(edits = it.edits + update, aiSources = it.aiSources - update.keys + pages, aiOpen = false)
        }
        // What was applied, as the web's toast: "Filled the type, the JACS code and 3 fields · review and save".
        val fields = (typedIdentity(d) + picked).keys.count { it !in typeValues && !(jacs != null && it == "jacs") }
        message.tryEmit(UiText.Res(R.string.ai_applied_what, listOf(filledText(type != null, jacs != null, fields))))
    }

    /**
     * Everything said between starting and stopping the mic ([sure] is the recognizer's confidence, null when it gives
     * none). The server's AI decides which fields it was about (offline, the phone reads "<field name> <value>"), and the
     * values open the voice fill sheet, ticked unless the AI was unsure of them, to put into the form.
     */
    fun heard(said: String, sure: Float?) {
        val s = state.value
        if (s.asset == null || s.locked || said.isBlank() || form.value.voiceReading) return
        // Voice fill asks the AI: with AI off for the property its button is off too (turned off while listening).
        if (!s.aiOn) {
            message.tryEmit(AI_OFF)
            return
        }
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

    /**
     * Describe the asset by typing (the web's "Describe the asset"): opens the sheet with [prefill] (what was heard, to
     * correct it; empty to start). The same AI fill as the voice button, so not with AI off.
     */
    fun openTyping(prefill: String = "") {
        val s = state.value
        if (s.asset == null || s.locked) return
        if (!s.aiOn) {
            message.tryEmit(AI_OFF)
            return
        }
        form.update { it.copy(typing = prefill.take(DESCRIBE_MAX), voice = null) }
    }

    fun setTyping(text: String) = form.update { f -> f.typing?.let { f.copy(typing = text.take(DESCRIBE_MAX)) } ?: f }

    fun closeTyping() = form.update { it.copy(typing = null) }

    /** Fill form: what was typed goes to the AI fill as if spoken; the voice fill sheet then shows what it found. */
    fun submitTyping() {
        val text = form.value.typing?.trim().orEmpty()
        if (text.isEmpty() || form.value.voiceReading) return
        form.update { it.copy(typing = null) }
        heard(text, null)
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
        form.update { it.copy(edits = it.edits + update, aiSources = it.aiSources - update.keys, voice = null) }
        message.tryEmit(UiText.Plural(R.plurals.ai_applied, update.size))
    }

    /** The manufacturer and model typed in the dialog that differ from the saved ones (sent with the look-up, saved on Apply). */
    private fun typedIdentity(d: AiDialogState): Map<String, String> {
        val s = state.value
        return buildMap {
            // Against the saved values: one typed in the form but not saved yet is sent too, so the look-up uses it.
            d.make.trim().takeIf { it.isNotEmpty() && it != s.saved["make"].orEmpty() }?.let { put("make", it) }
            d.model.trim().takeIf { it.isNotEmpty() && it != s.saved["model"].orEmpty() }?.let { put("model", it) }
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
                else -> failAi(e.error)
            }
        }
    }

    private suspend fun follow(assetId: String, lookup: SpecLookup) {
        setAi { it.copy(phase = AiPhase.SEARCHING, startedAt = it.startedAt ?: lookup.createdAt ?: System.currentTimeMillis()) }
        val done = try {
            formData.lookups.follow(assetId, lookup)
        } catch (e: OnlineException) {
            failAi(e.error)
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
        // A JACS code for the asset's own type starts ticked when the AI is sure enough (as the web).
        val jacsOn = offeredJacs(lookup)?.let { it.confidence >= SURE_ENOUGH }
        setAi { it.copy(phase = AiPhase.RESULTS, lookup = lookup, picks = picks, typeOn = typeOn, jacsOn = jacsOn) }
        followManuals()
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

    /**
     * The JACS code a look-up suggests for the asset's own type, as the web offers it: only while no other type is
     * suggested (a type brings its own code) and the form has no code yet; null otherwise.
     */
    private fun offeredJacs(lookup: SpecLookup): JacsSuggestion? =
        lookup.suggestedJacsCode?.takeIf { !offersType(lookup) && state.value.value("jacs").isBlank() }

    /** The look-up has something the form doesn't: a value for an empty field, a type, or a JACS code for an asset with none. */
    private fun hasNews(lookup: SpecLookup): Boolean = fillable(lookup).isNotEmpty() || offersType(lookup) || offeredJacs(lookup) != null

    private fun doneText(lookup: SpecLookup, name: String): UiText {
        val n = (fillable(lookup) + typeFillable(lookup)).size + if (offeredJacs(lookup) != null) 1 else 0
        return when {
            lookup.status == LookupStatus.FAILED -> UiText.Res(R.string.ai_done_failed, listOf(name, lookup.statusDetail.orEmpty()))
            n > 0 -> UiText.Plural(R.plurals.ai_done_found, n, listOf(name, n))
            else -> UiText.Res(R.string.ai_done_nothing, listOf(name, lookup.message?.let { UiText.Raw(it) } ?: UiText.Res(R.string.ai_nothing_new)))
        }
    }

    /**
     * Why something asked of the AI failed: a new asset, or one not synced yet, is not known to the server (it has to be
     * synced before it can be looked up); a 403 because the property has AI off says so ("AI off. An admin can turn it
     * on."), which also turns the AI buttons off.
     */
    private suspend fun OnlineError.aiText(): UiText = when {
        this == OnlineError.NotFound -> UiText.Res(R.string.detail_ai_sync_first)
        aiSwitch.isAiOffRefusal(this) -> AI_OFF
        else -> toUiText()
    }

    /** The AI suggestions sheet shows why [error] stopped it. */
    private suspend fun failAi(error: OnlineError) {
        val why = error.aiText()
        setAi { it.copy(phase = AiPhase.ERROR, error = why) }
    }

    /**
     * Validates the asset again at the server ([fromStart]: every step, else from its first step to look at), as the web's
     * Revalidate does, then says how it went once the results have synced (or that they will show when they have).
     */
    fun revalidate(fromStart: Boolean = false) {
        val s = state.value
        val id = s.asset?.id ?: return
        if (s.isNew || s.managed || form.value.validating) return
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

    /** Takes [document] off the asset on the server (after the confirmation): one added to it, a web page, or its uploaded file. */
    fun removeDocument(document: AssetDocument) {
        if (state.value.managed) return
        documents.remove(document)
    }

    fun editComment(text: String) = form.update { it.copy(commentDraft = text, commentProblem = null) }

    /** Posts the typed comment: the box empties once it is kept, or it says what is wrong with it. */
    fun postComment() {
        val s = state.value
        val id = s.asset?.id ?: return
        val draft = form.value.commentDraft
        if (s.isNew || s.managed || form.value.commentPosting) return
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
        if (state.value.managed) return
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
    fun removeComment(comment: AssetActivity) {
        if (state.value.managed) return
        write { useCases.removeComment(comment) }
    }

    private fun CommentProblem.problemText(): UiText = when (this) {
        CommentProblem.EMPTY -> UiText.Res(R.string.detail_comment_empty)
        CommentProblem.TOO_LONG -> UiText.Res(R.string.detail_comment_too_long, listOf(COMMENT_MAX_LENGTH))
    }

    private companion object {
        const val REFRESH_MS = 1000L

        /** What an AI button says while the property has AI off (the web's AI_OFF). */
        val AI_OFF: UiText = UiText.Res(R.string.ai_off)

        /** How often the manuals are asked for again while the server searches for them (as the web's sheet). */
        const val MANUALS_POLL_MS = 2_500L
        /** How long the pull-to-refresh ring shows after the data is read again. */
        const val REFRESH_SHOW_MS = 600L

        /** How long a new asset opened from a scan waits for its form before the AI is asked (it takes a moment to load). */
        const val FORM_WAIT_MS = 5_000L
        val CLASS_KEYS = setOf("sys", "typ", "sub")

        /** Room for the shots left out at once (more than an asset can hold). */
        const val DROPPED_BUFFER = 64

        /** The longest a moved photo is shown in its new card before the asset read again from the database must agree. */
        const val MOVE_SETTLE_MS = 5_000L

        /** How many [REFRESH_MS] to wait for a new validation to sync before saying it will show later. */
        const val SYNC_WAITS = 15L
    }
}

/** A picked file that is a document (not a photo, video or voice memo). */
private fun Media.isDocument() = isDoc && !audio && !video

/**
 * The Documents tab's list: the file the asset was read from first, then the files added to it ([files]: the Documents
 * tab's + and Media's Add file, kept on the phone as its media, so they show offline too), then from the server its web
 * pages and manuals, and any file added on the web that hasn't reached the phone yet. A file the server lists as well
 * keeps who added it from there.
 */
private fun documentsOf(files: List<Media>, server: List<AssetDocument>): List<AssetDocument> {
    val byId = server.associateBy { it.id }
    val own = files.map { m ->
        val name = m.fileName.orEmpty()
        val known = byId[m.id]
        AssetDocument(
            id = m.id, source = DocumentSource.ATTACHMENT, title = name, url = null, publisher = null,
            hasFile = true, fileKind = documentKind(name, known?.contentType), uploadedBy = known?.uploadedBy,
            uploaderName = known?.uploaderName, uploadedAt = known?.uploadedAt ?: m.createdAt,
            sizeBytes = m.sizeBytes ?: known?.sizeBytes, contentType = known?.contentType, removable = true, onDevice = true,
        )
    }
    val ids = own.map { it.id }.toSet()
    val (sources, rest) = server.filterNot { it.source == DocumentSource.ATTACHMENT && it.id in ids }
        .partition { it.source == DocumentSource.SOURCE_FILE }
    return sources + own + rest
}
