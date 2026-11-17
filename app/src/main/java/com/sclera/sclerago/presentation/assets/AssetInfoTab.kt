package com.sclera.sclerago.presentation.assets

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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.sclera.sclerago.R
import com.sclera.sclerago.domain.model.FieldCfg
import com.sclera.sclerago.domain.model.FieldKind
import com.sclera.sclerago.domain.model.FormEntry
import com.sclera.sclerago.domain.model.PhotoKind
import com.sclera.sclerago.domain.model.ANSWER_NO
import com.sclera.sclerago.domain.model.ANSWER_YES
import com.sclera.sclerago.domain.model.CHECKED
import com.sclera.sclerago.domain.validation.isServerId
import com.sclera.sclerago.domain.model.UNCHECKED
import com.sclera.sclerago.domain.model.UNCLASSIFIED
import com.sclera.sclerago.domain.model.assetTypes
import com.sclera.sclerago.domain.model.subtypes
import com.sclera.sclerago.domain.model.systemTypes
import com.sclera.sclerago.presentation.components.ActionCard
import com.sclera.sclerago.presentation.components.Pill
import com.sclera.sclerago.presentation.components.r
import com.sclera.sclerago.presentation.model.labelRes
import com.sclera.sclerago.presentation.theme.C
import com.sclera.sclerago.presentation.theme.Txt

/** Which confirmation the details screen is asking. */
enum class DetailModal { NONE, DELETE, DISCARD, SUBMIT, SUBMIT_EXCEPTION, SAVE_MISSING }

/** What the form's buttons do. */
class AssetDetailActions(
    val onBack: () -> Unit = {},
    val onChangeLoc: () -> Unit = {},
    /** Assign the asset to a person (the web's Assign asset). */
    val onAssign: () -> Unit = {},
    val onEdit: (key: String, value: String) -> Unit = { _, _ -> },
    val onDiscardEdits: () -> Unit = {},
    val onSave: () -> Unit = {},
    /** Submit an existing asset (after the confirmation). */
    val onSubmit: () -> Unit = {},
    /** Create the new asset, as a draft or (true) submitted. */
    val onCreate: (submit: Boolean) -> Unit = {},
    val onValidate: () -> Unit = {},
    val onDelete: () -> Unit = {},
    /** Open the AI suggestions dialog. */
    val onAutofill: () -> Unit = {},
    /** What the AI suggestions dialog's buttons do. */
    val ai: AiDialogActions = AiDialogActions(),
    /** Start or stop listening to fill fields by voice. */
    val onMic: () -> Unit = {},
    /** What the voice fill sheet's buttons do. */
    val voice: VoiceFillActions = VoiceFillActions(),
    val onTakePhoto: () -> Unit = {},
    val onPickGallery: () -> Unit = {},
    val onPickFiles: () -> Unit = {},
    /** The Documents tab's Upload: one file, kept as the asset's source document. */
    val onUploadDocument: () -> Unit = {},
    /** The Media tab's Record memo: records a voice memo for the asset. */
    val onRecordMemo: () -> Unit = {},
    /** The comment being typed changed / post it (Comments tab). */
    val onCommentChange: (String) -> Unit = {},
    val onPostComment: () -> Unit = {},
    /** The Comments tab's +: opens (true) or closes the sheet a comment is written in. */
    val onCommentSheet: (Boolean) -> Unit = {},
    /** Validate the asset again at the server: from its first step to look at, or (true) every step. */
    val onRevalidate: (fromStart: Boolean) -> Unit = {},
    /** Open the camera for a photo of [kind] (null: other photos) (Media tab +). */
    val onAddPhoto: (kind: PhotoKind?) -> Unit = {},
    /** Take a web page off the asset (Documents tab, after the confirmation). */
    val onRemoveWebPage: (documentId: String) -> Unit = {},
    /** Open the "Suggest type" sheet (System details). */
    val onSuggestType: () -> Unit = {},
    /** What the "Suggest type" sheet's buttons do. */
    val classify: ClassifyActions = ClassifyActions(),
)

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
    const val LOCATION = "loc"
    const val SYSTEM = "cls"
    const val FIELDS = "fields"
}

/** The System details fields: system, asset and sub type, and the JACS code. */
private val SYSTEM_KEYS = listOf("sys", "typ", "sub", "jacs")

/** Fields not listed in Asset fields: the System details ones, and the name (edited in the title bar). */
private val NOT_IN_FIELDS = SYSTEM_KEYS.toSet() + "name"

/** Fields whose values are codes and addresses, shown in a fixed-width font. */
private val CODE_KEYS = setOf("label", "ble", "ip", "mac", "serial")

/**
 * The Info tab, laid out as the web's asset panel: Location details (the asset's building, floor and location), System
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
) {
    val asset = state.asset ?: return
    val spec = FieldSpec(state.fields)
    val locked = state.locked
    val isNew = state.isNew
    fun v(key: String) = state.value(key)
    fun open(key: String) = key !in ui.collapsed
    val notSet = stringResource(R.string.detail_value_none)
    val select = stringResource(R.string.detail_select)

    // Location details
    val place = state.place
    FormSection(
        key = InfoSections.LOCATION, title = stringResource(R.string.detail_sec_location), note = null,
        open = open(InfoSections.LOCATION), onToggle = { ui.toggle(InfoSections.LOCATION) },
        modifier = Modifier.bringIntoViewRequester(ui.requester(InfoSections.LOCATION)),
        summary = Summary(place.location ?: place.floor ?: place.building ?: notSet, warn = place.building == null),
    ) {
        ValueRow(stringResource(R.string.detail_building), place.building)
        ValueRow(stringResource(R.string.detail_floor), place.floor)
        ValueRow(stringResource(R.string.detail_location), place.location)
        if (!locked) {
            Pill(
                stringResource(R.string.detail_set_location), actions.onChangeLoc,
                Modifier.padding(horizontal = 12.dp).padding(top = 8.dp).fillMaxWidth().testTag(DetailTags.LOCATION),
                height = 44.dp, size = 13, weight = 600,
            )
        }
    }

    // System details: the type comes from the property's catalogue, the JACS code is written as it is (no list exists).
    val formats = state.formats
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
                FieldRow(
                    k, labels.getValue(k), v(k), { actions.onEdit(k, it) }, state.dirty(k), state.badgeFor(k),
                    mono = true, requester = ui.requester(k),
                )
            } else {
                val choices = when (k) {
                    "sys" -> formats.systemTypes()
                    "typ" -> formats.assetTypes(v("sys"))
                    else -> formats.subtypes(v("sys"), v("typ"))
                }
                // Not classified yet shows as "-", as on the web.
                SelectRow(
                    k, labels.getValue(k), v(k), listOf(UNCLASSIFIED to notSet) + choices.filter { it != UNCLASSIFIED }.map { it to it },
                    state.dirty(k), { actions.onEdit(k, it) }, requester = ui.requester(k),
                )
            }
        }
        // The server suggests the type and JACS code from the photos and what is typed (the asset need not be synced).
        if (!locked) {
            Pill(
                stringResource(R.string.classify_suggest), actions.onSuggestType,
                Modifier.padding(horizontal = 12.dp).padding(top = 8.dp).fillMaxWidth().testTag(DetailTags.SUGGEST_TYPE),
                height = 44.dp, size = 13, weight = 600,
            )
        }
    }

    // Asset fields: every other field of this asset in the server's order (the standard ones, then its own), with its
    // photo, video and voice memo fields, as on the web.
    val others = spec.visibleExcept(NOT_IN_FIELDS)
    FormSection(
        key = InfoSections.FIELDS, title = stringResource(R.string.detail_sec_fields), note = null,
        open = open(InfoSections.FIELDS), onToggle = { ui.toggle(InfoSections.FIELDS) },
        modifier = Modifier.bringIntoViewRequester(ui.requester(InfoSections.FIELDS)),
        summary = others.firstOrNull { spec.required(it.key) && v(it.key).isEmpty() }
            ?.let { Summary(stringResource(R.string.detail_required_summary, it.label), warn = true) }
            ?: Summary(stringResource(R.string.detail_filled_summary, others.count { v(it.key).isNotEmpty() }, others.size)),
    ) {
        state.fieldRows.forEach { row ->
            when (row) {
                is FormEntry.Input -> spec.cfg(row.key)?.takeIf { it.key !in NOT_IN_FIELDS }
                    ?.let { f -> AssetFieldRow(f, state, spec, select, ui) { actions.onEdit(f.key, it) } }
                is FormEntry.MediaField -> {
                    val files = state.media.count { it.fieldId == row.id }
                    ValueRow(
                        row.name,
                        if (files > 0) pluralStringResource(R.plurals.detail_media_field_files, files, files) else stringResource(R.string.detail_media_field_none),
                    )
                }
            }
        }
    }

    if (!isNew) {
        StatusLine(asset, Modifier.padding(horizontal = 12.dp).padding(top = 2.dp))
        if (locked) {
            // Submitted: read-only. A recollect request reopens it (for now it just closes the asset).
            SubmittedCard(
                onRecollect = actions.onBack, modifier = Modifier.padding(horizontal = 12.dp).padding(top = 4.dp),
                onValidate = if (state.canCheck && isServerId(asset.id)) actions.onValidate else null,
            )
        } else {
            // As the server: an asset with an empty mandatory field or too few photos is submitted as an exception, so ask about that first.
            val submitModal = if (state.complete) DetailModal.SUBMIT else DetailModal.SUBMIT_EXCEPTION
            ActionCard(
                // As the web: for anyone who may run checks, once the server has the asset.
                onValidate = if (state.canCheck && isServerId(asset.id)) actions.onValidate else null,
                onAssign = actions.onAssign,
                onSubmit = { onModal(submitModal) },
                onDelete = { onModal(DetailModal.DELETE) },
                modifier = Modifier.padding(horizontal = 12.dp).padding(top = 4.dp),
            )
            // Save (makes it drafted with the AI flag on; asks first while a mandatory field is empty, as the web) and Submit.
            val dirty = state.dirtyCount
            SaveCard(Modifier.padding(horizontal = 12.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    if (dirty > 0) {
                        Txt(pluralStringResource(R.plurals.detail_unsaved, dirty, dirty), 14, 700)
                        Txt(
                            stringResource(R.string.action_discard), 12, 700, C.Yellow,
                            Modifier.heightIn(min = 48.dp).clip(r(6)).clickable(onClick = actions.onDiscardEdits).padding(vertical = 2.dp),
                        )
                    } else {
                        Txt(stringResource(R.string.detail_no_unsaved), 14, 700)
                        Txt(stringResource(R.string.detail_status, stringResource(asset.st.labelRes)), 12, 400, C.Muted)
                    }
                }
                Pill(
                    stringResource(R.string.action_save),
                    { if (dirty > 0 && state.missingRequired.isNotEmpty()) onModal(DetailModal.SAVE_MISSING) else actions.onSave() },
                    Modifier.testTag(DetailTags.SAVE).alpha(if (dirty > 0) 1f else 0.45f),
                    height = 48.dp, hPad = 18.dp, size = 14, weight = 700,
                )
                Pill(
                    stringResource(R.string.action_submit), { onModal(submitModal) }, Modifier.testTag(DetailTags.SUBMIT),
                    bg = C.Yellow, fg = C.OnYellow, height = 48.dp, hPad = 22.dp, size = 14, weight = 700,
                )
            }
        }
    } else {
        // New asset: save it as a draft, or submit it (then it can not be edited). As the server, one with an empty mandatory
        // field or too few photos is submitted as an exception, so ask about that first.
        val submitModal = if (state.complete) DetailModal.SUBMIT else DetailModal.SUBMIT_EXCEPTION
        SaveCard(Modifier.padding(horizontal = 12.dp).padding(top = 4.dp)) {
            Pill(
                stringResource(R.string.action_save), { actions.onCreate(false) }, Modifier.weight(1f).testTag(DetailTags.SAVE),
                height = 48.dp, size = 14, weight = 700,
            )
            Pill(
                stringResource(R.string.action_submit), { onModal(submitModal) }, Modifier.weight(1f).testTag(DetailTags.SUBMIT),
                bg = C.Yellow, fg = C.OnYellow, height = 48.dp, size = 14, weight = 700,
            )
        }
    }
}

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
            WhenRow(k, label, value, if (f.kind == FieldKind.DATE) WhenKind.DATE else WhenKind.TIME, state.dirty(k), onChange, bg)
            return
        }
        FieldKind.MULTI -> if (f.options.isNotEmpty()) {
            MultiCheckRow(k, label, value, f.options, state.dirty(k), onChange, bg)
            return
        }
        else -> {}
    }
    val choices = when (f.kind) {
        // Yes / no answers are stored as the words, checkboxes as true / false (as the web stores them).
        FieldKind.YESNO -> listOf(ANSWER_YES to stringResource(R.string.detail_yes), ANSWER_NO to stringResource(R.string.detail_no))
        FieldKind.CHECKBOX -> listOf(CHECKED to stringResource(R.string.detail_yes), UNCHECKED to stringResource(R.string.detail_no))
        FieldKind.DROPDOWN -> f.options.map { it to it }
        else -> emptyList()
    }
    if (choices.isNotEmpty()) {
        SelectRow(
            k, label, value, listOf("" to select) + choices, state.dirty(k), onChange,
            rowBg = bg, requester = ui.requester(k),
        )
    } else {
        FieldRow(
            k, label, value, onChange, state.dirty(k), state.badgeFor(k), rowBg = bg,
            mono = k in CODE_KEYS, requester = ui.requester(k), multiLine = f.kind == FieldKind.TEXTAREA,
        )
    }
}
