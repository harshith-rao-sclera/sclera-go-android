package com.sclera.sclerago.presentation.assets

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.sclera.sclerago.R
import com.sclera.sclerago.domain.model.ASSET_TYPES
import com.sclera.sclerago.domain.model.FIELD_OPTIONS
import com.sclera.sclerago.domain.model.FieldKind
import com.sclera.sclerago.domain.model.FieldSection
import com.sclera.sclerago.domain.model.JACS_CODES
import com.sclera.sclerago.domain.model.SUB_TYPES
import com.sclera.sclerago.domain.model.SYSTEM_TYPES
import com.sclera.sclerago.domain.model.Source
import com.sclera.sclerago.domain.model.Status
import com.sclera.sclerago.presentation.components.ActionCard
import com.sclera.sclerago.presentation.components.Capsule
import com.sclera.sclerago.presentation.components.Pill
import com.sclera.sclerago.presentation.components.PlainField
import com.sclera.sclerago.presentation.components.bottomBorder
import com.sclera.sclerago.presentation.components.r
import com.sclera.sclerago.presentation.components.topBorder
import com.sclera.sclerago.presentation.icons.Icons
import com.sclera.sclerago.presentation.icons.LineIcon
import com.sclera.sclerago.presentation.model.labelRes
import com.sclera.sclerago.presentation.theme.C
import com.sclera.sclerago.presentation.theme.Txt

/** Which confirmation the details screen is asking. */
enum class DetailModal { NONE, DELETE, DISCARD, SUBMIT }

/** What the form's buttons do. */
class AssetDetailActions(
    val onBack: () -> Unit = {},
    val onChangeLoc: () -> Unit = {},
    val onEdit: (key: String, value: String) -> Unit = { _, _ -> },
    val onDiscardEdits: () -> Unit = {},
    val onSave: () -> Unit = {},
    /** Submit an existing asset (after the confirmation). */
    val onSubmit: () -> Unit = {},
    /** Create the new asset, as a draft or (true) submitted. */
    val onCreate: (submit: Boolean) -> Unit = {},
    val onValidate: () -> Unit = {},
    val onDelete: () -> Unit = {},
    val onAutofill: () -> Unit = {},
    val onTakePhoto: () -> Unit = {},
    val onPickGallery: () -> Unit = {},
    val onPickFiles: () -> Unit = {},
)

/** The form's own view state: which sections are open, the voice note, and where a Fix button should scroll to. */
@Stable
class FormUi {
    var collapsed by mutableStateOf(setOf("cls", "tags", "notes", "more"))
    var listening by mutableStateOf(false)

    /** The section or field key a Fix button asked to show. */
    var pending by mutableStateOf<String?>(null)
    private val requesters = mutableMapOf<String, BringIntoViewRequester>()

    fun requester(key: String): BringIntoViewRequester = requesters.getOrPut(key) { BringIntoViewRequester() }
    fun toggle(section: String) {
        collapsed = if (section in collapsed) collapsed - section else collapsed + section
    }
}

/** The Info tab: photos, location, the field sections, then the status and the actions. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AssetInfoTab(
    state: AssetDetailState,
    actions: AssetDetailActions,
    ui: FormUi,
    onModal: (DetailModal) -> Unit,
    onPreview: (Int) -> Unit,
) {
    val asset = state.asset ?: return
    val spec = FieldSpec(state.fields)
    val locked = state.locked
    val isNew = state.isNew
    fun v(key: String) = state.value(key)
    val select = stringResource(R.string.detail_select)
    val selectJacs = stringResource(R.string.detail_select_jacs)
    val none = stringResource(R.string.detail_none_yet)

    PhotoStrip(state.media, locked, onPreview, actions.onTakePhoto, actions.onPickGallery, actions.onPickFiles)

    // Location
    Row(
        Modifier.fillMaxWidth().testTag(DetailTags.LOCATION).background(C.Surface).topBorder(C.Border).bottomBorder(C.Border).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(40.dp).background(C.YTintBg, r(12)), contentAlignment = Alignment.Center) { LineIcon(Icons.Pin, 20.dp, C.Yellow, 2.2f) }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Txt(stringResource(R.string.detail_location).uppercase(), 11, 600, C.Muted, spacing = 0.6f)
            Txt(
                asset.where.ifEmpty { stringResource(R.string.detail_no_location) }, 15, 700,
                if (asset.loc != null) C.Text else C.Orange,
            )
        }
        if (!locked) Pill(stringResource(R.string.action_change), actions.onChangeLoc, height = 48.dp, hPad = 14.dp)
    }

    // Identification
    FormSection(
        key = "ident", title = stringResource(R.string.detail_sec_ident),
        note = if (state.fromCamera) stringResource(R.string.detail_note_nameplate) else null,
        open = "ident" !in ui.collapsed, onToggle = { ui.toggle("ident") },
        modifier = Modifier.bringIntoViewRequester(ui.requester("ident")),
        summary = Summary(listOf(v("make"), v("model")).filter { it.isNotEmpty() }.joinToString(" · ").ifEmpty { none }),
    ) {
        if (!isNew) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp).topBorder(C.Border).padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Txt(stringResource(R.string.detail_asset_id), 13, 500, C.Muted, Modifier.width(92.dp))
                Txt(state.code.orEmpty(), 14, 600, C.Soft, Modifier.weight(1f), mono = true)
            }
        }
        spec.visibleIn(FieldSection.IDENT).forEach { f ->
            val k = f.key
            val badge = state.badgeFor(k)
            FieldRow(
                k, spec.label(k), v(k), { actions.onEdit(k, it) }, state.dirty(k), badge,
                rowBg = if (badge == FieldBadge.CHECK || (spec.required(k) && v(k).isEmpty())) C.WarnRow else Color.Transparent,
                requester = ui.requester(k),
            )
        }
    }

    // Classification
    val classes = spec.visibleIn(FieldSection.CLASS)
    fun missing(key: String) = spec.required(key) && (v(key).isEmpty() || (key in setOf("sys", "typ", "sub") && v(key) == "Generic"))
    FormSection(
        key = "cls", title = stringResource(R.string.detail_sec_class),
        note = if (state.fromCamera) stringResource(R.string.detail_note_photos) else null,
        open = "cls" !in ui.collapsed, onToggle = { ui.toggle("cls") },
        modifier = Modifier.bringIntoViewRequester(ui.requester("cls")),
        summary = classes.firstOrNull { missing(it.key) }?.let { Summary(stringResource(R.string.detail_required_summary, it.label), warn = true) }
            ?: Summary(listOf("typ", "sys").map { v(it) }.firstOrNull { it.isNotEmpty() && it != "Generic" } ?: stringResource(R.string.detail_not_set)),
    ) {
        classes.forEach { f ->
            val k = f.key
            val options = when (k) {
                "sys" -> SYSTEM_TYPES.map { it to it }
                "typ" -> ASSET_TYPES.map { it to it }
                "sub" -> SUB_TYPES.map { it to it }
                "jacs" -> listOf("" to selectJacs) + JACS_CODES.drop(1)
                else -> listOf("" to select) + FIELD_OPTIONS[k].orEmpty().map { it to it }
            }
            SelectRow(
                k, spec.label(k), v(k), options, state.dirty(k), { actions.onEdit(k, it) },
                required = missing(k), rowBg = if (missing(k)) C.WarnRow else Color.Transparent, requester = ui.requester(k),
            )
        }
    }

    // Tags & connectivity
    val tags = spec.visibleIn(FieldSection.TAGS)
    FormSection(
        key = "tags", title = stringResource(R.string.detail_sec_tags),
        note = if (isNew && asset.src in setOf(Source.BT, Source.WIFI, Source.AP)) stringResource(R.string.detail_note_scan) else null,
        open = "tags" !in ui.collapsed, onToggle = { ui.toggle("tags") },
        modifier = Modifier.bringIntoViewRequester(ui.requester("tags")),
        summary = tags.map { v(it.key) }.firstOrNull { it.isNotEmpty() }?.let { Summary(it, mono = true) } ?: Summary(none),
    ) {
        tags.forEach { f ->
            val k = f.key
            FieldRow(
                k, spec.label(k), v(k), { actions.onEdit(k, it) }, state.dirty(k),
                if (k in state.aiFilled) FieldBadge.AI else FieldBadge.NONE, mono = true,
                rowBg = if (spec.required(k) && v(k).isEmpty()) C.WarnRow else Color.Transparent, requester = ui.requester(k),
            )
        }
    }

    // More details: extra fields switched on in Configuration (condition, dates, custom fields…)
    val more = spec.visibleIn(FieldSection.MORE)
    if (more.isNotEmpty()) FormSection(
        key = "more", title = stringResource(R.string.detail_sec_more), note = null,
        open = "more" !in ui.collapsed, onToggle = { ui.toggle("more") },
        summary = more.count { v(it.key).isNotEmpty() }.let { n ->
            Summary(if (n == 0) none else stringResource(R.string.detail_filled_summary, n, more.size))
        },
    ) {
        more.forEach { f ->
            val k = f.key
            val missing = spec.required(k) && v(k).isEmpty()
            val bg = if (missing) C.WarnRow else Color.Transparent
            val options = when (f.kind) {
                FieldKind.YESNO, FieldKind.CHECKBOX -> listOf("Yes", "No")
                else -> f.options.ifEmpty { FIELD_OPTIONS[k].orEmpty() }
            }
            if (f.kind in setOf(FieldKind.DROPDOWN, FieldKind.MULTI, FieldKind.YESNO, FieldKind.CHECKBOX) && options.isNotEmpty()) {
                SelectRow(
                    k, spec.label(k), v(k), listOf("" to select) + options.map { it to it }, state.dirty(k), { actions.onEdit(k, it) },
                    required = missing, rowBg = bg,
                )
            } else {
                FieldRow(k, spec.label(k), v(k), { actions.onEdit(k, it) }, state.dirty(k), if (k in state.aiFilled) FieldBadge.AI else FieldBadge.NONE, rowBg = bg)
            }
        }
    }

    // Notes
    if (spec.shown("note")) FormSection(
        key = "notes", title = stringResource(R.string.detail_sec_notes), note = null,
        open = "notes" !in ui.collapsed, onToggle = { ui.toggle("notes") },
        summary = Summary(v("note").replace('\n', ' ').let { if (it.length > 26) it.take(25).trimEnd() + "…" else it }.ifEmpty { none }),
        trailing = if (locked) null else ({
            Row(
                Modifier.height(48.dp).clip(Capsule).clickable { ui.listening = !ui.listening }.padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    Modifier.height(36.dp).clip(Capsule).background(C.Btn).padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    LineIcon(Icons.Mic, 14.dp, C.Yellow, 2.2f)
                    Txt(stringResource(R.string.detail_voice_note), 12, 600)
                }
            }
        }),
    ) {
        PlainField(
            v("note"), { actions.onEdit("note", it) }, stringResource(R.string.detail_note_hint),
            Modifier.padding(horizontal = 12.dp).fillMaxWidth().clip(r(10)).background(C.Bg)
                .border(1.5.dp, if (state.dirty("note")) C.Yellow else C.Border, r(10)).padding(10.dp),
            size = 14, singleLine = false, minLines = 3,
        )
    }

    if (!isNew) {
        StatusLine(asset, Modifier.padding(horizontal = 12.dp).padding(top = 2.dp))
        if (locked) {
            // Submitted: read-only. A recollect request reopens it (for now it just closes the asset).
            SubmittedCard(onRecollect = actions.onBack, modifier = Modifier.padding(horizontal = 12.dp).padding(top = 4.dp))
        } else {
            ActionCard(
                // No button for the status the asset already has.
                onValidate = if (asset.st != Status.VALIDATED) actions.onValidate else null,
                onAssign = actions.onChangeLoc,
                onSubmit = { onModal(DetailModal.SUBMIT) },
                onDelete = { onModal(DetailModal.DELETE) },
                modifier = Modifier.padding(horizontal = 12.dp).padding(top = 4.dp),
            )
            // Save (keeps it a draft) and Submit.
            val dirty = state.dirtyCount
            SaveCard(Modifier.padding(horizontal = 12.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    if (dirty > 0) {
                        Txt(pluralStringResource(R.plurals.detail_unsaved, dirty, dirty), 15, 700)
                        Txt(
                            stringResource(R.string.action_discard), 13, 700, C.Yellow,
                            Modifier.heightIn(min = 48.dp).clip(r(6)).clickable(onClick = actions.onDiscardEdits).padding(vertical = 2.dp),
                        )
                    } else {
                        Txt(stringResource(R.string.detail_no_unsaved), 15, 700)
                        Txt(stringResource(R.string.detail_status, stringResource(asset.st.labelRes)), 13, 400, C.Muted)
                    }
                }
                Pill(
                    stringResource(R.string.action_save), actions.onSave, Modifier.testTag(DetailTags.SAVE).alpha(if (dirty > 0) 1f else 0.45f),
                    height = 48.dp, hPad = 18.dp, size = 14, weight = 700,
                )
                Pill(
                    stringResource(R.string.action_submit), { onModal(DetailModal.SUBMIT) }, Modifier.testTag(DetailTags.SUBMIT),
                    bg = C.Yellow, fg = C.OnYellow, height = 48.dp, hPad = 22.dp, size = 14, weight = 700,
                )
            }
        }
    } else {
        // New asset: save it as a draft, or submit it (then it can not be edited).
        SaveCard(Modifier.padding(horizontal = 12.dp).padding(top = 4.dp)) {
            Pill(
                stringResource(R.string.action_save), { actions.onCreate(false) }, Modifier.weight(1f).testTag(DetailTags.SAVE),
                height = 48.dp, size = 15, weight = 700,
            )
            Pill(
                stringResource(R.string.action_submit), { onModal(DetailModal.SUBMIT) }, Modifier.weight(1f).testTag(DetailTags.SUBMIT),
                bg = C.Yellow, fg = C.OnYellow, height = 48.dp, size = 15, weight = 700,
            )
        }
    }
}
