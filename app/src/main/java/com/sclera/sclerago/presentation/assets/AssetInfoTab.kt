package com.sclera.sclerago.presentation.assets

import com.sclera.sclerago.presentation.model.color
import com.sclera.sclerago.presentation.components.pressClickable
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.sclera.sclerago.R
import com.sclera.sclerago.domain.model.ANSWER_NO
import com.sclera.sclerago.domain.model.ANSWER_YES
import com.sclera.sclerago.domain.model.AssetActivity
import com.sclera.sclerago.domain.model.AssetDocument
import com.sclera.sclerago.domain.model.ClonePlace
import com.sclera.sclerago.domain.model.FieldCfg
import com.sclera.sclerago.domain.model.FieldKind
import com.sclera.sclerago.domain.model.FormEntry
import com.sclera.sclerago.domain.model.Media
import com.sclera.sclerago.domain.model.PhotoKind
import com.sclera.sclerago.domain.model.Status
import com.sclera.sclerago.domain.model.UNCLASSIFIED
import com.sclera.sclerago.domain.model.isSubmittedOrSignedOff
import com.sclera.sclerago.domain.model.tagKindOfField
import com.sclera.sclerago.domain.usecase.asset.AssetPerson
import com.sclera.sclerago.domain.usecase.asset.ChangeAction
import com.sclera.sclerago.domain.validation.isServerId
import com.sclera.sclerago.presentation.common.formatDateTimeSeconds
import com.sclera.sclerago.presentation.common.is24Hour
import com.sclera.sclerago.presentation.common.UiText
import com.sclera.sclerago.presentation.components.AccessGate
import com.sclera.sclerago.presentation.components.ActionCard
import com.sclera.sclerago.presentation.components.DockItem
import com.sclera.sclerago.presentation.components.Pill
import com.sclera.sclerago.presentation.components.r
import com.sclera.sclerago.presentation.icons.Icons
import com.sclera.sclerago.presentation.main.changeActionLabel
import com.sclera.sclerago.presentation.model.OnlineOnly
import com.sclera.sclerago.presentation.model.labelRes
import com.sclera.sclerago.presentation.tagging.TagsActions
import com.sclera.sclerago.presentation.theme.C
import com.sclera.sclerago.presentation.theme.Txt

/** Which confirmation the details screen is asking. */
enum class DetailModal { NONE, DELETE, DISCARD, SUBMIT, SUBMIT_EXCEPTION, SAVE_MISSING, MARK_VALIDATED, APPROVE_REQUEST, MOVE_TO_DRAFT }

/** What the form's buttons do. */
class AssetDetailActions(
    val onBack: () -> Unit = {},
    val onChangeLoc: () -> Unit = {},
    /** Assign the asset to a person (the web's Assign asset). */
    val onAssign: (() -> Unit)? = null,
    /** [onAssign] is "Assign to me": the user isn't a reviewer and takes the asset for themselves (Assign then shows greyed out). */
    val assignToMe: Boolean = false,
    val onEdit: (key: String, value: String) -> Unit = { _, _ -> },
    val onDiscardEdits: () -> Unit = {},
    /** Pull to refresh: reads the asset and its tabs again. */
    val onRefresh: () -> Unit = {},
    val onSave: () -> Unit = {},
    /** Submit an existing asset (after the confirmation). */
    val onSubmit: () -> Unit = {},
    /** Move the asset back to draft (after the confirmation). */
    val onMoveToDraft: () -> Unit = {},
    /** An admin marks the submitted asset as validated without a validation run (after the confirmation). */
    val onMarkValidated: () -> Unit = {},
    /** Move the validated asset to Managed assets; null when it isn't offered (not validated, or not an admin). */
    val onMove: (() -> Unit)? = null,
    /** Why Move to Managed assets can't be used now (no Configure, not linked…): greyed out, a tap says this; null: it can. */
    val moveReason: UiText? = null,
    /** Why Assign (or Assign to me) can't be used now: greyed out, a tap says this; null: it can. */
    val assignReason: UiText? = null,
    /** Create the new asset, as a draft or (true) submitted. */
    val onCreate: (submit: Boolean) -> Unit = {},
    val onValidate: () -> Unit = {},
    /** "Open in Validation": the Validation page on the run [jobId] with this asset's result open. */
    val onOpenResult: (jobId: String) -> Unit = {},
    val onDelete: () -> Unit = {},
    /** Change requests on a submitted asset: open the dialog, edit its note, send or cancel, withdraw / decline, reopen. */
    val changeRequest: ChangeRequestActions = ChangeRequestActions(),
    /** Open the AI suggestions dialog. */
    val onAutofill: () -> Unit = {},
    /** What the AI suggestions dialog's buttons do. */
    val ai: AiDialogActions = AiDialogActions(),
    /** Start or stop listening to fill fields by voice. */
    val onMic: () -> Unit = {},
    /** Describe the asset by typing instead (a long press on the mic). */
    val onType: () -> Unit = {},
    /** What the voice fill sheet's buttons do. */
    val voice: VoiceFillActions = VoiceFillActions(),
    /** What the describe sheet (typing the description) does. */
    val typing: DescribeActions = DescribeActions(),
    val onTakePhoto: () -> Unit = {},
    val onPickGallery: () -> Unit = {},
    val onPickFiles: () -> Unit = {},
    /** The Documents tab's +: picks files kept as the asset's media, as [onPickFiles] (Media's Add file). */
    val onUploadDocument: () -> Unit = {},
    /** The Media tab's Record memo: records a voice memo for the asset. */
    val onRecordMemo: () -> Unit = {},
    /** The comment being typed changed / post it (Comments tab). */
    val onCommentChange: (String) -> Unit = {},
    val onPostComment: () -> Unit = {},
    /** Save a comment's new text ([onSaved] once saved) / remove a comment (after the confirmation) (Comments tab). */
    val onUpdateComment: (comment: AssetActivity, text: String, onSaved: () -> Unit) -> Unit = { _, _, _ -> },
    val onRemoveComment: (AssetActivity) -> Unit = {},
    /** Validate the asset again at the server: from its first step to look at, or (true) every step. */
    val onRevalidate: (fromStart: Boolean) -> Unit = {},
    /** Open the camera for a photo of [kind] (null: other photos) (Media tab +). */
    val onAddPhoto: (kind: PhotoKind?) -> Unit = {},
    /** Open the camera for photos that are read and sorted into nameplate, label ID or asset (Info tab's photos +). */
    val onAddSortedPhoto: () -> Unit = {},
    /** Take a web page or the uploaded file off the asset (Documents tab, after the confirmation). */
    val onRemoveDocument: (AssetDocument) -> Unit = {},
    /** Fetches a document's file from the server and opens it. */
    val onOpenDocument: (AssetDocument) -> Unit = {},
    /** A photo or video source file: fetched and shown in the app's viewer. */
    val onPreviewSource: (AssetDocument) -> Unit = {},
    /** Save a copy of a photo, video, voice memo or file (the media viewer's Download). */
    val onDownloadMedia: (Media) -> Unit = {},
    /** Remove a photo, video, voice memo or file from the asset (the media viewer's Remove, after the confirmation). */
    val onRemoveMedia: (Media) -> Unit = {},
    /** A photo dragged to another kind's card (null: Other photos). */
    val onMovePhoto: (Media, PhotoKind?) -> Unit = { _, _ -> },
    /** Open the "Suggest type" sheet (System details). */
    val onSuggestType: () -> Unit = {},
    /** What the JACS code row and its picker do. */
    val jacs: JacsActions = JacsActions(),
    /** What the "Suggest type" sheet's buttons do. */
    val classify: ClassifyActions = ClassifyActions(),
    /** An online-only action was tapped while offline: say why it needs the server. */
    val onOffline: (OnlineOnly) -> Unit = {},
    /** Clone (top bar): open its sheet / make the copies picked in it / close it. */
    val onOpenClone: () -> Unit = {},
    val onClone: (copies: Int, place: ClonePlace, keys: Set<String>) -> Unit = { _, _, _ -> },
    val onCloseClone: () -> Unit = {},
    /** Add field (the end of the Info tab's fields): a field for this asset only. */
    val addField: AddFieldActions = AddFieldActions(),
    /** What the Tags bar's icons do. */
    val tags: TagsActions = TagsActions(),
)

/** Opening the JACS code picker, and what it does. */
class JacsActions(val onOpen: () -> Unit = {}, val picker: JacsPickerActions = JacsPickerActions())

/** The form's own view state: which sections are closed and where a Fix button should scroll to. */
@Stable
class FormUi {
    var collapsed by mutableStateOf(emptySet<String>())

    /** The section or field key a Fix button asked to show. */
    var pending by mutableStateOf<String?>(null)
    private val requesters = mutableMapOf<String, BringIntoViewRequester>()

    fun requester(key: String): BringIntoViewRequester = requesters.getOrPut(key) { BringIntoViewRequester() }
    fun toggle(section: String) {
        collapsed = if (section in collapsed) collapsed - section else collapsed + section
    }
}

/** The Info tab's sections, by key (also what a Validation Fix button opens). */
object InfoSections {
    const val ASSIGNMENT = "assignment"
    const val LOCATION = "loc"
    const val SYSTEM = "cls"
    const val FIELDS = "fields"
}

/** The System details fields: system, asset and sub type, and the JACS code. */
private val SYSTEM_KEYS = listOf("sys", "typ", "sub", "jacs")

/** Fields not listed in Asset fields: the System details ones, and the name (edited in the title bar). */
private val NOT_IN_FIELDS = SYSTEM_KEYS.toSet() + "name"

/** Fields whose values are codes and addresses, shown in a fixed-width font. */
private val CODE_KEYS = setOf("label", "serial")

/**
 * The Info tab, laid out as the web's asset panel: the Location row (the asset's building, floor and location), System
 * details (its type from the property's catalogue and its JACS code), then every other field the property's data gives
 * the asset, in their order; then the status and the actions. Nothing here is made up: what the data lacks shows as not set.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AssetInfoTab(
    state: AssetDetailState,
    actions: AssetDetailActions,
    ui: FormUi,
    onModal: (DetailModal) -> Unit,
    /** The remove button of one of the asset's own fields was tapped (the screen asks first). */
    onRemoveField: (FieldCfg) -> Unit = {},
    /** A type row (system, asset or sub type) was tapped: the screen opens its picker. */
    onPickType: (TypePick) -> Unit = {},
    /** The Location tag row was tapped: the screen opens its sheet. */
    onTagLocation: () -> Unit = {},
) {
    val asset = state.asset ?: return
    val spec = FieldSpec(state.fields)
    val locked = state.locked
    val isNew = state.isNew
    fun v(key: String) = state.value(key)
    fun open(key: String) = key !in ui.collapsed
    val notSet = stringResource(R.string.detail_value_none)
    val select = stringResource(R.string.detail_select)

    // Location: the asset's place (building, floor and location) in one row; tapping it opens the location picker (unless the
    // asset is locked). A Validation Fix button for the place scrolls here.
    LocationRow(
        state.place, stringResource(R.string.detail_location),
        Modifier.testTag(DetailTags.LOCATION).bringIntoViewRequester(ui.requester(InfoSections.LOCATION)),
        onClick = actions.onChangeLoc.takeIf { !locked },
    )

    // Assignment: who has the asset, and who submitted it and when (as the web's asset card). Not for a new asset.
    state.handling?.let { h ->
        val locale = LocalConfiguration.current.locales[0]
        // Assigned to shows the person's email (their name, or "You", only when their email isn't on this device).
        val assignee = h.assignedTo?.let { personText(it) }
        FormSection(
            key = InfoSections.ASSIGNMENT, title = stringResource(R.string.detail_sec_assignment), note = null,
            open = open(InfoSections.ASSIGNMENT), onToggle = { ui.toggle(InfoSections.ASSIGNMENT) },
            summary = Summary(assignee ?: stringResource(R.string.detail_unassigned)),
        ) {
            ValueRow(stringResource(R.string.detail_assigned_to), assignee)
            ValueRow(stringResource(R.string.detail_submitted_by), h.submittedBy?.let { personText(it) })
            ValueRow(stringResource(R.string.detail_submitted_on), h.submittedAt?.let { formatDateTimeSeconds(it, locale, use24Hour = is24Hour()) })
        }
    }

    // System details: the type comes from the property's catalogue, the JACS code is written as it is (no list exists).
    fun missing(key: String) = spec.required(key) && (v(key).isEmpty() || (key != "jacs" && v(key) == UNCLASSIFIED))
    // The web always lists all four rows.
    val systemShown = SYSTEM_KEYS
    val labels = mapOf(
        "sys" to spec.label("sys", stringResource(R.string.detail_system_type)),
        "typ" to spec.label("typ", stringResource(R.string.detail_asset_type)),
        "sub" to spec.label("sub", stringResource(R.string.detail_sub_type)),
        "jacs" to spec.label("jacs", stringResource(R.string.detail_jacs_code)),
    )
    val firstMissing = systemShown.firstOrNull { missing(it) }
    val missingNote = firstMissing?.let { stringResource(R.string.detail_required_summary, labels.getValue(it).removeSuffix(" *")) }
    FormSection(
        key = InfoSections.SYSTEM, title = stringResource(R.string.detail_sec_system), note = missingNote, noteWarn = true,
        open = open(InfoSections.SYSTEM), onToggle = { ui.toggle(InfoSections.SYSTEM) },
        modifier = Modifier.bringIntoViewRequester(ui.requester(InfoSections.SYSTEM)),
        summary = missingNote?.let { Summary(it, warn = true) }
            ?: Summary(listOf("sub", "typ", "sys").map { v(it) }.firstOrNull { it.isNotEmpty() && it != UNCLASSIFIED } ?: notSet),
    ) {
        systemShown.forEach { k ->
            if (k == "jacs") {
                // Picked from the JACS catalog (or typed there), which sets the type too.
                // A mandatory code still missing is highlighted and tagged REQUIRED, as the design canvas.
                val jacsMissing = missing(k)
                // With its title, as the web: "238219 · Fan Coil Units" (the code alone when the catalogue lacks it).
                val code = v(k)
                PickRow(
                    k, labels.getValue(k), state.jacsTitle?.takeIf { code.isNotBlank() }?.let { stringResource(R.string.jacs_with_title, code, it) } ?: code,
                    stringResource(if (jacsMissing) R.string.detail_select_jacs else R.string.jacs_none), state.dirty(k), actions.jacs.onOpen,
                    rowBg = if (jacsMissing) C.WarnRow else Color.Transparent,
                    mono = true, badge = state.badgeFor(k), requester = ui.requester(k),
                    tag = if (jacsMissing) stringResource(R.string.detail_required_tag) else null,
                )
            } else {
                // Opens the type picker (search and the types' icons); not classified yet shows as "-", as on the web.
                PickRow(
                    k, labels.getValue(k), v(k).takeUnless { it == UNCLASSIFIED }.orEmpty(), notSet, state.dirty(k),
                    { onPickType(TypePick(k, labels.getValue(k))) }, requester = ui.requester(k),
                )
            }
        }
        // The server suggests the type and JACS code from the photos and what is typed (the asset need not be synced);
        // its suggestions show here, and a pick goes straight into the rows above. Greyed out, saying why, for whoever may
        // not change the asset.
        AccessGate(state.buttons.change, Modifier.fillMaxWidth()) {
            ClassifyPanel(
                state.classify, actions.onSuggestType, actions.classify, Modifier.padding(horizontal = 12.dp).padding(top = 8.dp),
                offline = !state.online, onOffline = { actions.onOffline(OnlineOnly.SUGGEST_TYPE) },
            )
        }
    }

    // Asset fields: every other field of this asset in the server's order (the standard ones, then its own), with its
    // photo, video and voice memo fields, as on the web.
    // "QR / Barcode / NFC Tagged" fields show as the Tags bar's icons (green when tagged), not here.
    val others = spec.visibleExcept(NOT_IN_FIELDS).filter { tagKindOfField(it.label) == null }
    FormSection(
        key = InfoSections.FIELDS, title = stringResource(R.string.detail_sec_fields), note = null,
        open = open(InfoSections.FIELDS), onToggle = { ui.toggle(InfoSections.FIELDS) },
        modifier = Modifier.bringIntoViewRequester(ui.requester(InfoSections.FIELDS)),
        summary = others.firstOrNull { spec.required(it.key) && v(it.key).isEmpty() }
            ?.let { Summary(stringResource(R.string.detail_required_summary, it.label), warn = true) }
            ?: Summary(stringResource(R.string.detail_filled_summary, others.count { v(it.key).isNotEmpty() }, others.size)),
    ) {
        // The asset's own Type and Location tag (not fields) come first, as on the web.
        TypeAndLocationTagRows(state, actions.onEdit, onTagLocation)
        state.fieldRows.forEach { row ->
            // Each row is its own for its asset and field, so a text box never carries what it showed for another asset
            // or field (the web's "empty Description shows the previous asset's text").
            key(asset.id, row) {
                when (row) {
                    is FormEntry.Input -> spec.cfg(row.key)?.takeIf { it.key !in NOT_IN_FIELDS && tagKindOfField(it.label) == null }?.let { f ->
                        // A field of this asset alone can be removed by whoever may change the asset, as the server allows;
                        // for anyone else its button is greyed out and says why.
                        if (f.ownField && !isNew) {
                            OwnFieldRow(f.label, onRemove = { onRemoveField(f) }, blocked = state.buttons.change) {
                                AssetFieldRow(f, state, spec, select, ui) { actions.onEdit(f.key, it) }
                            }
                        } else {
                            AssetFieldRow(f, state, spec, select, ui) { actions.onEdit(f.key, it) }
                        }
                    }
                    is FormEntry.MediaField -> {
                        val files = state.media.count { it.fieldId == row.id }
                        // A mandatory one shows its * as the other fields do (the web marks them whether or not it is editing).
                        ValueRow(
                            row.name,
                            if (files > 0) pluralStringResource(R.plurals.detail_media_field_files, files, files) else stringResource(R.string.detail_media_field_none),
                            required = row.required,
                        )
                    }
                }
            }
        }
        // As the web's: a field for this asset alone, added after its other fields (a saved asset; greyed out, saying why,
        // for whoever may not change it).
        if (!isNew) AddFieldRow(actions.addField.onOpen, blocked = state.buttons.change)
    }

    if (!isNew) {
        StatusLine(asset, Modifier.padding(horizontal = 12.dp).padding(top = 2.dp))
        // As the server: an asset with an empty mandatory field or too few photos is submitted as an exception, so ask about that first.
        val submitModal = if (state.complete) DetailModal.SUBMIT else DetailModal.SUBMIT_EXCEPTION
        // Submitting is for drafts (a submitted or exception asset already was); greyed out for whoever may not change it.
        val canSubmit = asset.st == Status.DRAFT || asset.st == Status.INPUT || asset.st == Status.AI
        val blocked = state.buttons.change
        // Save (makes it drafted with the AI flag on; asks first while a mandatory field is empty, as the web) and Submit,
        // with the other actions under it. Read-only for this user: both stay, greyed out, and a tap says why (the
        // banner under the top bar says it too).
        val dirty = state.dirtyCount
        val saveAlpha by animateFloatAsState(if (dirty > 0) 1f else 0.45f, tween(120), label = "save")
        SaveCard(Modifier.padding(horizontal = 12.dp).padding(top = 4.dp)) {
            AnimatedContent(
                dirty > 0, Modifier.weight(1f),
                transitionSpec = { fadeIn(tween(120)).togetherWith(fadeOut(tween(90))).using(SizeTransform(clip = false)) },
                label = "unsaved",
            ) { unsaved -> Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (unsaved) {
                    Txt(pluralStringResource(R.plurals.detail_unsaved, dirty, dirty), 14, 700)
                    Txt(
                        stringResource(R.string.action_discard), 12, 700, C.Yellow,
                        Modifier.heightIn(min = 48.dp).pressClickable(shape = r(6), onClick = actions.onDiscardEdits).padding(vertical = 2.dp),
                    )
                } else {
                    Txt(stringResource(R.string.detail_no_unsaved), 14, 700)
                    Txt(stringResource(R.string.detail_status, stringResource(asset.st.labelRes)), 12, 400, C.Muted)
                }
            } }
            AccessGate(blocked) {
                Pill(
                    stringResource(R.string.action_save),
                    { if (dirty > 0 && state.missingRequired.isNotEmpty()) onModal(DetailModal.SAVE_MISSING) else actions.onSave() },
                    Modifier.testTag(DetailTags.SAVE).alpha(saveAlpha),
                    height = 48.dp, hPad = 18.dp, size = 14, weight = 700,
                )
            }
            if (canSubmit) {
                AccessGate(blocked) {
                    Pill(
                        stringResource(R.string.action_submit), { onModal(submitModal) }, Modifier.testTag(DetailTags.SUBMIT),
                        bg = C.Yellow, fg = C.OnYellow, height = 48.dp, hPad = 22.dp, size = 14, weight = 700,
                    )
                }
            }
        }
        ActionCard(
            assetBarItems(state, asset, canSubmit, actions, onModal = { onModal(it) }, submitModal = submitModal),
            Modifier.padding(horizontal = 12.dp).padding(top = 4.dp),
            offline = !state.online,
            onOffline = actions.onOffline,
        )
    } else {
        // New asset: save it as a draft, or submit it (then it can not be edited). As the server, one with an empty mandatory
        // field or too few photos is submitted as an exception, so ask about that first.
        val submitModal = if (state.complete) DetailModal.SUBMIT else DetailModal.SUBMIT_EXCEPTION
        // Adding an asset needs Collect: without it both are greyed out and a tap says why.
        SaveCard(Modifier.padding(horizontal = 12.dp).padding(top = 4.dp)) {
            AccessGate(state.buttons.change, Modifier.weight(1f)) {
                Pill(
                    stringResource(R.string.action_save), { actions.onCreate(false) }, Modifier.fillMaxWidth().testTag(DetailTags.SAVE),
                    height = 48.dp, size = 14, weight = 700,
                )
            }
            AccessGate(state.buttons.change, Modifier.weight(1f)) {
                Pill(
                    stringResource(R.string.action_submit), { onModal(submitModal) }, Modifier.fillMaxWidth().testTag(DetailTags.SUBMIT),
                    bg = C.Yellow, fg = C.OnYellow, height = 48.dp, size = 14, weight = 700,
                )
            }
        }
    }
}

/**
 * The asset bar's actions, as the web's asset bar (AssetsInfoContainer's assetActions), in its order. Which apply depends on
 * the asset's status; of those, one the user's group doesn't allow is left out ([AssetButtons.hidden]), and one blocked by
 * the asset's state or a passing one stays, greyed out, and a tap says why ([AssetButtons]):
 * - Validate: once the server has the asset (Check).
 * - Mark as validated: a submitted asset (Validate).
 * - Assign (a reviewer), and Assign to me (anyone but a reviewer): except on a signed-off asset.
 * - Submit: a draft (whoever may change it).
 * - Move to Managed assets: a validated asset not there yet (Configure, and the property linked).
 * - Move to draft: an exception (whoever may work on it), or a submitted or signed-off asset nobody asked to change (a
 *   reviewer); with a request, Approve is in the change request banner, with Decline and Withdraw.
 * - Request change (or update it): a submitted or signed-off asset (not for a reviewer, who changes it instead).
 * - Delete: except on a signed-off asset (whoever may change it).
 */
@Composable
private fun assetBarItems(
    state: AssetDetailState,
    asset: com.sclera.sclerago.domain.model.Asset,
    canSubmit: Boolean,
    actions: AssetDetailActions,
    onModal: (DetailModal) -> Unit,
    submitModal: DetailModal,
): List<DockItem> {
    val signedOff = asset.st == Status.VALIDATED
    val submitted = isSubmittedOrSignedOff(asset.st)
    val b = state.buttons
    fun shows(action: BarAction) = action !in b.hidden
    return buildList {
        if (isServerId(asset.id) && shows(BarAction.VALIDATE)) {
            add(DockItem(stringResource(R.string.action_validate), Icons.ValidationChecks, C.Yellow, actions.onValidate, OnlineOnly.VALIDATE, blocked = b.validate))
        }
        if (asset.st == Status.SUBMITTED && shows(BarAction.SIGN_OFF)) {
            add(
                DockItem(
                    stringResource(R.string.sign_off_action), Icons.StatusValidated, Status.VALIDATED.color, { onModal(DetailModal.MARK_VALIDATED) },
                    tag = DetailTags.MARK_VALIDATED, blocked = b.signOff,
                ),
            )
        }
        if (!signedOff) {
            // Assign: a reviewer picks who has it (left out for anyone else).
            if (shows(BarAction.ASSIGN)) {
                val assign = if (actions.assignToMe) null else actions.onAssign
                add(DockItem(stringResource(R.string.action_assign), Icons.UserPlus, C.Cyan, assign ?: {}, OnlineOnly.ASSIGN, blocked = b.assign ?: actions.assignReason))
            }
            // Assign to me, for anyone but a reviewer (whose Assign offers themselves): greyed out, saying why, when they
            // can't take this asset.
            if (!state.reviewer && shows(BarAction.TAKE)) {
                val take = if (actions.assignToMe) actions.onAssign else null
                val takeWhy = b.take ?: actions.assignReason ?: UiText.Res(R.string.access_take_unavailable).takeIf { take == null }
                add(DockItem(stringResource(R.string.action_assign_to_me), Icons.UserPlus, C.Cyan, take ?: {}, OnlineOnly.ASSIGN, blocked = takeWhy))
            }
        }
        if (canSubmit && shows(BarAction.SUBMIT)) {
            add(DockItem(stringResource(R.string.action_submit), Icons.StatusSubmitted, Status.SUBMITTED.color, { onModal(submitModal) }, blocked = b.change))
        }
        val moveWhy = b.moveToManaged ?: actions.moveReason
        if (signedOff && !asset.managed && shows(BarAction.MOVE_TO_MANAGED) && (actions.onMove != null || moveWhy != null)) {
            add(DockItem(stringResource(R.string.mv_action), Icons.Managed, C.Yellow, actions.onMove ?: {}, OnlineOnly.MOVE_TO_MANAGED, blocked = moveWhy))
        }
        if ((asset.st == Status.EXCEPTION || (submitted && asset.changeRequest == null)) && shows(BarAction.MOVE_TO_DRAFT)) {
            add(
                DockItem(
                    // The plain action colour, as the web's default tone: Draft's own status grey reads as greyed out.
                    changeActionLabel(ChangeAction.MOVE_TO_DRAFT, 1), Icons.StatusDraft, C.Text, { onModal(DetailModal.MOVE_TO_DRAFT) },
                    tag = DetailTags.MOVE_TO_DRAFT, blocked = b.moveToDraft,
                ),
            )
        }
        // Not for a reviewer, who changes the asset instead.
        if (submitted && shows(BarAction.REQUEST_CHANGE)) {
            val ask = if (asset.changeRequest != null) ChangeAction.UPDATE_REQUEST else ChangeAction.REQUEST
            add(DockItem(changeActionLabel(ask, 1), Icons.ChangeRequest, C.Orange, actions.changeRequest.onOpen, tag = ChangeRequestTags.ASK, blocked = b.requestChange))
        }
        if (!signedOff && shows(BarAction.DELETE)) {
            add(DockItem(stringResource(R.string.action_delete), Icons.Trash, C.Red, { onModal(DetailModal.DELETE) }, blocked = b.change))
        }
    }
}

/** A user the asset names: their email (the signed-in user's too), else their name, else "Unknown user". */
@Composable
private fun personText(p: AssetPerson): String = p.email ?: p.name ?: stringResource(R.string.detail_person_unknown)

/**
 * One of the asset's fields, by its kind: a dropdown for the choice kinds (their choices are the field's own, from the
 * property's data; yes / no fields offer yes and no), a text row otherwise (a long text wraps).
 */
@Composable
private fun AssetFieldRow(f: FieldCfg, state: AssetDetailState, spec: FieldSpec, select: String, ui: FormUi, onChange: (String) -> Unit) {
    val k = f.key
    val value = state.value(k)
    // Only a value the nameplate reading was unsure of is highlighted; a mandatory field shows its orange * alone.
    val bg = if (state.badgeFor(k) == FieldBadge.CHECK) C.WarnRow else Color.Transparent
    val label = spec.label(k, f.label)
    when (f.kind) {
        FieldKind.DATE, FieldKind.TIME -> {
            WhenRow(k, label, value, if (f.kind == FieldKind.DATE) WhenKind.DATE else WhenKind.TIME, state.dirty(k), onChange, bg) {
                onChange(state.saved[k].orEmpty())
            }
            return
        }
        FieldKind.MULTI -> if (f.options.isNotEmpty()) {
            MultiCheckRow(k, label, value, f.options, state.dirty(k), onChange, bg)
            return
        }
        FieldKind.CHECKBOX -> {
            CheckboxRow(k, label, value, state.saved[k].orEmpty(), onChange, bg, ui.requester(k))
            return
        }
        else -> {}
    }
    val choices = when (f.kind) {
        // Yes / no answers are stored as words. Checkboxes use their own checkbox row above.
        FieldKind.YESNO -> listOf(ANSWER_YES to stringResource(R.string.detail_yes), ANSWER_NO to stringResource(R.string.detail_no))
        FieldKind.DROPDOWN -> f.options.map { it to it }
        else -> emptyList()
    }
    if (choices.isNotEmpty()) {
        SelectRow(
            k, label, value, listOf("" to select) + choices, state.dirty(k), onChange,
            rowBg = bg, requester = ui.requester(k),
        )
    } else {
        val isAmount = f.kind == FieldKind.NUMBER || f.kind == FieldKind.CURRENCY
        FieldRow(
            k, label, value, if (isAmount) { typed -> onChange(numericValue(typed)) } else onChange, state.dirty(k), state.badgeFor(k), rowBg = bg,
            // Text wraps over as many lines as it needs (no "…"); codes and addresses (serial, MAC, IP…) stay on one line.
            mono = k in CODE_KEYS, requester = ui.requester(k),
            multiLine = f.kind == FieldKind.TEXTAREA || (f.kind == FieldKind.TEXT && k !in CODE_KEYS),
            keyboard = if (isAmount) androidx.compose.ui.text.input.KeyboardType.Decimal else androidx.compose.ui.text.input.KeyboardType.Text,
            onUndo = { onChange(state.saved[k].orEmpty()) },
        )
    }
}

/** Allows only a signed decimal amount while the user types. */
private fun numericValue(value: String) = buildString {
    var decimal = false
    value.forEach { char ->
        when {
            char.isDigit() -> append(char)
            char == '-' && isEmpty() -> append(char)
            char == '.' && !decimal -> {
                append(char)
                decimal = true
            }
        }
    }
}
