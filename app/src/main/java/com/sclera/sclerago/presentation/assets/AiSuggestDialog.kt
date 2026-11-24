package com.sclera.sclerago.presentation.assets

import com.sclera.sclerago.presentation.components.pressClickable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.sclera.sclerago.domain.model.AssetDocument
import com.sclera.sclerago.domain.model.DocumentSource
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sclera.sclerago.R
import com.sclera.sclerago.domain.model.JacsSuggestion
import com.sclera.sclerago.domain.model.LookupType
import com.sclera.sclerago.domain.model.PhotoNote
import com.sclera.sclerago.domain.model.SURE_ENOUGH
import com.sclera.sclerago.domain.model.typeFieldName
import com.sclera.sclerago.presentation.common.asString
import com.sclera.sclerago.presentation.components.BottomSheet
import com.sclera.sclerago.presentation.components.GuardedClose
import com.sclera.sclerago.presentation.components.Capsule
import com.sclera.sclerago.presentation.components.CloseButton
import com.sclera.sclerago.presentation.components.Pill
import com.sclera.sclerago.presentation.components.PlainField
import com.sclera.sclerago.presentation.components.r
import com.sclera.sclerago.presentation.components.topBorder
import com.sclera.sclerago.presentation.icons.Icons
import com.sclera.sclerago.presentation.icons.LineIcon
import com.sclera.sclerago.presentation.theme.C
import com.sclera.sclerago.presentation.theme.ScleraFonts
import com.sclera.sclerago.presentation.theme.Txt
import kotlinx.coroutines.delay
import java.net.URI
import kotlin.math.exp
import kotlin.math.roundToInt

/** What the AI suggestions sheet's buttons do. */
class AiDialogActions(
    /** Closes the sheet; a search under way is stopped (Cancel does the same). */
    val onClose: () -> Unit = {},
    val onIdentity: (make: String, model: String) -> Unit = { _, _ -> },
    val onLookUp: () -> Unit = {},
    val onChangeModel: () -> Unit = {},
    val onToggle: (key: String) -> Unit = {},
    /** Ticks or unticks the suggested type. */
    val onToggleType: () -> Unit = {},
    val onEdit: (key: String, value: String) -> Unit = { _, _ -> },
    val onApply: () -> Unit = {},
    /** Ticks or unticks the JACS code suggested for the asset's own type. */
    val onToggleJacs: () -> Unit = {},
)

/** Test tags of the AI suggestions sheet. */
object AiTags {
    const val DIALOG = "ai-dialog"
    const val APPLY = "ai-apply"
    const val TYPE = "ai-type"
    const val JACS = "ai-jacs"
    const val NOTHING = "ai-nothing"
    const val AI_OFF = "ai-off"
    fun row(key: String) = "ai-row-$key"
    fun manual(id: String) = "ai-manual-$id"
}

/** The search's steps, as the iOS app lists them, and when each shows as reached (seconds); the first only with photos. */
private val PHOTO_STEP = R.string.ai_step_photos
private val STEPS = listOf(R.string.ai_step_identify, R.string.ai_step_search, R.string.ai_step_read, R.string.ai_step_match)
private val STEP_AT = listOf(0, 4, 12, 22)
private val PHOTO_STEP_AT = listOf(0, 6, 12, 20, 30)

/**
 * The AI suggestions sheet, as the iOS app's: the asset's type and field values from its photos and its model's
 * published specifications, and the user chooses what to keep. It asks for the model when it has none, shows the
 * search's steps (it keeps going when the sheet is closed), then the type, each field (editable) with how sure it is and where it
 * came from, and what else the photos showed; Apply puts the ticked ones into the form to save.
 */
@Composable
fun AiSuggestDialog(d: AiDialogState, state: AssetDetailState, actions: AiDialogActions) {
    // The manufacturer and model typed go into the form with the suggestions, so they count towards Apply.
    val typedCount = listOf("make" to d.make, "model" to d.model).count { (k, v) -> v.trim().isNotEmpty() && v.trim() != state.value(k) }
    // A manufacturer or model typed but not looked up yet would be lost: closing asks first. (Results are kept when it
    // closes, for when it opens again.)
    GuardedClose(d.phase == AiPhase.IDENTIFY && typedCount > 0, actions.onClose) { close ->
        BottomSheet(onDismiss = close) {
            Column(Modifier.weight(1f, fill = false).testTag(AiTags.DIALOG)) {
                Header(state.value("name").ifBlank { state.asset?.name.orEmpty() }, close)
                val nothing = d.nothingToApply(typedCount)
                Column(
                    Modifier.weight(1f, fill = d.phase == AiPhase.SEARCHING).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    when (d.phase) {
                        AiPhase.LOADING -> repeat(3) { Box(Modifier.fillMaxWidth().height(36.dp).background(C.Raised, r(8))) }
                        AiPhase.IDENTIFY -> Identify(d, state, actions)
                        AiPhase.SEARCHING -> Searching(d)
                        AiPhase.ERROR -> Txt(d.error?.asString() ?: stringResource(R.string.ai_failed), 13, 500, maxLines = Int.MAX_VALUE)
                        AiPhase.RESULTS -> if (nothing) NothingFound(d) else Results(d, state, actions)
                    }
                }
                Footer(d, typedCount, nothing, actions, aiOn = state.aiOn)
            }
        }
    }
}

/**
 * A look-up that came back with nothing to tick or save (the web's "nothing"): no fields, no type, no JACS code,
 * nothing typed.
 */
private fun AiDialogState.nothingToApply(typedCount: Int): Boolean =
    phase == AiPhase.RESULTS && picks.values.none { it.value.isNotBlank() } && lookup?.suggestedType == null && jacsOn == null && typedCount == 0

/** Said once, plainly: the server's message (or "No suggestions") and the model it looked up. */
@Composable
private fun NothingFound(d: AiDialogState) {
    val lookedUp = d.lookup?.product?.takeIf { it.isNotBlank() }
        ?: listOf(d.make, d.model).map { it.trim() }.filter { it.isNotEmpty() }.joinToString(" ")
    Column(
        Modifier.fillMaxWidth().padding(vertical = 18.dp).testTag(AiTags.NOTHING),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        LineIcon(Icons.Sparkles, 26.dp, C.Cyan.copy(alpha = 0.6f), 2.2f)
        Txt(
            d.lookup?.message?.trim()?.removeSuffix(".")?.takeIf { it.isNotEmpty() } ?: stringResource(R.string.ai_no_suggestions),
            15, 700, maxLines = Int.MAX_VALUE, align = TextAlign.Center,
        )
        if (lookedUp.isNotEmpty()) Txt(lookedUp, 12, 400, C.Muted, maxLines = Int.MAX_VALUE, align = TextAlign.Center)
    }
}

/** "AI suggestions", what it does for the asset [name], and close. */
@Composable
private fun Header(name: String, onClose: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 10.dp, top = 4.dp, bottom = 10.dp), verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f).padding(end = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Txt(stringResource(R.string.ai_title), 18, 700)
            Txt(stringResource(R.string.ai_sheet_subtitle, name), 12, 400, C.Muted, maxLines = Int.MAX_VALUE)
        }
        CloseButton(onClose, size = 36.dp, iconSize = 16.dp)
    }
}

/** The manufacturer and model to look up, labelled with the property's own field names. */
@Composable
private fun Identify(d: AiDialogState, state: AssetDetailState, actions: AiDialogActions) {
    Txt(d.error?.asString() ?: stringResource(R.string.ai_identify_lead), 13, 400, C.Muted, maxLines = Int.MAX_VALUE)
    val spec = FieldSpec(state.fields)
    LabeledBox(spec.label("make", stringResource(R.string.ai_make_label)).removeSuffix(" *"), d.make, stringResource(R.string.ai_make_hint)) {
        actions.onIdentity(it, d.model)
    }
    LabeledBox(spec.label("model", stringResource(R.string.ai_model_label)).removeSuffix(" *"), d.model, stringResource(R.string.ai_model_hint)) {
        actions.onIdentity(d.make, it)
    }
    Txt(stringResource(R.string.ai_typed_note), 11, 400, C.Muted, maxLines = Int.MAX_VALUE)
}

@Composable
private fun LabeledBox(label: String, value: String, hint: String, onChange: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Txt(label, 13, 600)
        PlainField(
            value, onChange, hint,
            Modifier.fillMaxWidth().heightIn(min = 44.dp).clip(r(12)).background(C.Bg).border(1.dp, C.Border, r(12)).padding(horizontal = 12.dp, vertical = 12.dp),
            size = 14,
        )
    }
}

/**
 * The search's progress, as the iOS app's: what it is looking up, a bar that never quite fills, its steps by time
 * (reading the photos first when photos were sent), and how long it has taken.
 */
@Composable
private fun Searching(d: AiDialogState) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000)
            now = System.currentTimeMillis()
        }
    }
    val seconds = ((now - (d.startedAt ?: now)) / 1000).coerceAtLeast(0)
    val what = listOf(d.make, d.model).map { it.trim() }.filter { it.isNotEmpty() }.joinToString(" ")
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        LineIcon(Icons.Sparkles, 20.dp, C.Cyan.copy(alpha = 0.6f), 2.2f)
        Txt(
            if (what.isNotEmpty()) stringResource(R.string.ai_searching_for, what) else stringResource(R.string.ai_searching),
            14, 700, modifier = Modifier.weight(1f), maxLines = Int.MAX_VALUE,
        )
    }
    val percent = (95 * (1 - exp(-seconds / 18.0))).roundToInt()
    Box(Modifier.fillMaxWidth().height(5.dp).background(C.Raised, Capsule)) {
        Box(Modifier.fillMaxWidth(percent / 100f).height(5.dp).background(C.Yellow, Capsule))
    }
    val steps = if (d.fromPhoto) listOf(PHOTO_STEP) + STEPS else STEPS
    val at = if (d.fromPhoto) PHOTO_STEP_AT else STEP_AT
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        steps.forEachIndexed { i, step ->
            val reached = seconds >= at[i]
            val done = i < steps.lastIndex && seconds >= at[i + 1]
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                // Done: a filled check; the step under way: a yellow ring with a dot; the rest: an empty ring.
                Box(
                    Modifier.size(18.dp).background(if (done) C.Yellow else C.Bg, CircleShape)
                        .border(1.5.dp, if (done || reached) C.Yellow else C.Border2, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    when {
                        done -> LineIcon(Icons.Check, 10.dp, C.OnYellow, 3.2f)
                        reached -> Box(Modifier.size(7.dp).background(C.Yellow, CircleShape))
                    }
                }
                Txt(stringResource(step), 13, if (reached && !done) 700 else 500, if (reached && !done) C.Text else C.Soft, maxLines = Int.MAX_VALUE)
            }
        }
    }
    val wait = stringResource(if (seconds < 30) R.string.ai_wait_short else R.string.ai_wait_long)
    Txt(stringResource(R.string.scrt_ai_wait_seconds, wait, seconds), 12, 400, C.Soft, maxLines = Int.MAX_VALUE)
}

/**
 * The suggestions, as the iOS app shows them: a line saying what was read, the type in a card of its own, the fields
 * in one card (each ticked, editable, how sure, from the photo or the page it came from, and what the form has when the
 * photo shows something else), then what else the photos showed.
 */
@Composable
private fun Results(d: AiDialogState, state: AssetDetailState, actions: AiDialogActions) {
    val lookup = d.lookup
    val spec = FieldSpec(state.fields)
    val typed = buildList {
        d.make.trim().takeIf { it.isNotEmpty() && it != state.value("make") }?.let { add(spec.label("make").removeSuffix(" *") to it) }
        d.model.trim().takeIf { it.isNotEmpty() && it != state.value("model") }?.let { add(spec.label("model").removeSuffix(" *") to it) }
    }
    val type = lookup?.suggestedType?.takeIf { d.typeOn != null }
    // The type's fields' values show only while the type is ticked (they go in only with it).
    val hidden = if (d.typeOn == false) type?.values?.keys.orEmpty() else emptySet()
    val rows = d.picks.entries.filter { it.key !in hidden }

    Summary(d, rows.map { it.key })
    if (typed.isNotEmpty()) {
        Txt(stringResource(R.string.ai_also_saves, typed.map { (f, v) -> stringResource(R.string.scrt_ai_typed_value, f, v) }.joinToString(", ")), 12, 400, C.Soft, maxLines = Int.MAX_VALUE)
    }
    if (type != null) TypeCard(type, d.typeOn == true, actions.onToggleType)
    // A JACS code for the type the asset has, offered only while it has none and no other type is suggested.
    lookup?.suggestedJacsCode?.takeIf { d.jacsOn != null }?.let { JacsCard(it, d.jacsOn == true, actions.onToggleJacs) }
    if (rows.isNotEmpty()) FieldsCard(d, rows, state, actions)
    lookup?.extra?.takeIf { it.isNotEmpty() }?.let { AlsoOnPhotos(it) }
    // The manuals the look-up pulled (the server's list for the model, else those synced here); while the server still
    // looks for them, it says so.
    val manuals = state.aiManuals.ifEmpty { state.documents.filter { it.source == DocumentSource.MANUAL } }
    if (manuals.isNotEmpty()) ManualsCard(manuals)
    if (state.manualsSearching || (manuals.isEmpty() && lookup?.manualsSearch == true)) {
        Txt(stringResource(R.string.ai_manuals_search), 12, 400, C.Soft, maxLines = Int.MAX_VALUE)
    }
}

/** The asset model's manuals (manual, installation guide, datasheet, parts list), each opening its PDF or page. */
@Composable
private fun ManualsCard(manuals: List<AssetDocument>) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Txt(stringResource(R.string.ai_manuals_title), 13, 700)
        Column(Modifier.fillMaxWidth().clip(r(8)).background(C.Bg).border(1.dp, C.Border, r(8))) {
            manuals.forEachIndexed { i, m ->
                val open = documentOpener(m)
                val label = stringResource(R.string.doc_open_cd, m.title)
                val kind = stringResource(R.string.doc_manufacturer_kind, stringResource(m.manualKind.labelRes()))
                Row(
                    Modifier.fillMaxWidth().then(if (i > 0) Modifier.topBorder(C.Border) else Modifier)
                        .then(if (open != null) Modifier.pressClickable(role = Role.Button, onClick = open) else Modifier)
                        .semantics { contentDescription = label }.testTag(AiTags.manual(m.id))
                        .heightIn(min = 48.dp).padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    LineIcon(Icons.FileDoc, 16.dp, C.Yellow, 2f)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        Txt(m.title, 13, 600, maxLines = 2)
                        Txt(listOfNotNull(kind, m.publisher).joinToString(" · "), 11, 500, C.Muted, maxLines = 1)
                    }
                    if (open != null) LineIcon(Icons.Eye, 15.dp, C.Muted, 2.2f)
                }
            }
        }
    }
}

/**
 * What was found, in one line: "Read 3 fields. 2 different from saved. Check them. Nothing to fill." A look-up of the
 * published specs only (no photos) leads with the product it found, the product in bold.
 */
@Composable
private fun Summary(d: AiDialogState, shown: List<String>) {
    val lookup = d.lookup ?: return
    val read = lookup.fromPhoto.size
    val differ = shown.count { it in lookup.differs }
    val fill = shown.count { it !in lookup.differs }
    val parts = buildList {
        if (read > 0) add(pluralStringResource(R.plurals.ai_sum_read, read, read))
        if (differ > 0) add(pluralStringResource(R.plurals.ai_sum_differ, differ, differ))
        add(if (fill == 0) stringResource(R.string.ai_sum_nothing) else pluralStringResource(R.plurals.ai_sum_fill, fill, fill))
    }
    val product = lookup.product?.takeIf { read == 0 }
    if (product != null) {
        val lead = stringResource(R.string.ai_found, PRODUCT_MARK, "").trim()
        Text(
            buildAnnotatedString {
                append(lead.substringBefore(PRODUCT_MARK))
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = C.Text)) { append(product) }
                append(lead.substringAfter(PRODUCT_MARK, ""))
                append(" ")
                append(parts.joinToString(" "))
            },
            color = C.Soft, fontSize = 13.sp, fontFamily = ScleraFonts.Inter, lineHeight = 18.sp,
        )
    } else {
        Txt(parts.joinToString(" "), 13, 400, C.Soft, maxLines = Int.MAX_VALUE)
    }
}

/** The type the AI thinks the asset is, in a card of its own, ticked to go into the form: its path, why, and how sure. */
@Composable
private fun TypeCard(t: LookupType, on: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().testTag(AiTags.TYPE).clip(r(12)).background(C.Surface).border(1.dp, C.Border, r(12))
            .padding(start = 4.dp, end = 12.dp, top = 6.dp, bottom = 10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        AiTick(on, onToggle)
        Column(Modifier.weight(1f).padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Txt(stringResource(R.string.ai_type_title), 13, 700)
            Txt(t.label ?: listOfNotNull(t.systemType, t.assetType, t.subtype).filter { it.isNotBlank() }.joinToString(" › "), 14, 700, C.Yellow, maxLines = Int.MAX_VALUE)
            t.jacsCode?.let { Txt(stringResource(R.string.ai_type_jacs, it), 12, 500, C.Soft, maxLines = 1) }
            if (t.reason.isNotBlank()) Txt(t.reason, 12, 400, C.Soft, maxLines = Int.MAX_VALUE)
            SureText(t.confidence)
        }
    }
}

/**
 * The JACS code suggested for the asset's own type, in a card like the type's, ticked to go into the form: "JACS code",
 * the code and its title, why, and how sure.
 */
@Composable
private fun JacsCard(c: JacsSuggestion, on: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().testTag(AiTags.JACS).clip(r(12)).background(C.Surface).border(1.dp, C.Border, r(12))
            .padding(start = 4.dp, end = 12.dp, top = 6.dp, bottom = 10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        AiTick(on, onToggle)
        Column(Modifier.weight(1f).padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Txt(stringResource(R.string.jacs_title), 13, 700)
            Txt(c.title?.let { stringResource(R.string.jacs_code_title, c.code, it) } ?: c.code, 14, 700, C.Yellow, maxLines = Int.MAX_VALUE)
            c.reason?.let { Txt(it, 12, 400, C.Soft, maxLines = Int.MAX_VALUE) }
            SureText(c.confidence)
        }
    }
}

/** The suggested values in one card: a tick, the field, the value (editable), the form's value when the photo differs, how sure and where from. */
@Composable
private fun FieldsCard(d: AiDialogState, rows: List<Map.Entry<String, AiPick>>, state: AssetDetailState, actions: AiDialogActions) {
    val lookup = d.lookup
    val spec = FieldSpec(state.fields)
    val uri = LocalUriHandler.current
    Column(Modifier.fillMaxWidth().clip(r(12)).background(C.Surface).border(1.dp, C.Border, r(12))) {
        rows.forEachIndexed { n, (key, pick) ->
            val sure = lookup?.confidence?.get(key) ?: 1.0
            val source = lookup?.sources?.get(key)
            Row(
                Modifier.fillMaxWidth().testTag(AiTags.row(key)).then(if (n > 0) Modifier.topBorder(C.Border) else Modifier)
                    .padding(start = 4.dp, end = 12.dp, top = 6.dp, bottom = 10.dp),
                verticalAlignment = Alignment.Top,
            ) {
                AiTick(pick.on) { actions.onToggle(key) }
                Column(Modifier.weight(1f).padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    // A field the suggested type brings isn't on the asset yet: it is named by the type.
                    Txt(typeFieldName(key) ?: spec.label(key).removeSuffix(" *"), 13, 600, maxLines = 2)
                    PlainField(
                        pick.value, { actions.onEdit(key, it) }, "",
                        Modifier.fillMaxWidth().heightIn(min = 40.dp).clip(r(12)).background(C.Bg).border(1.dp, C.Border, r(12))
                            .padding(horizontal = 10.dp, vertical = 10.dp),
                        size = 14, singleLine = false,
                    )
                    // The photo shows something else than the form has: both are shown, and Apply replaces it when ticked.
                    if (lookup?.differs?.contains(key) == true) {
                        Txt(stringResource(R.string.ai_photo_differs, state.value(key)), 12, 500, C.Orange, maxLines = Int.MAX_VALUE)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        SureText(sure)
                        val host = source?.let { runCatching { URI(it).host?.removePrefix("www.") }.getOrNull() }
                        when {
                            lookup?.fromPhoto?.contains(key) == true -> {
                                Box(Modifier.width(4.dp))
                                LineIcon(Icons.Camera, 13.dp, C.Cyan, 2.2f)
                                Txt(stringResource(R.string.ai_from_photo), 11, 600, C.Cyan)
                            }
                            host != null -> Txt(
                                host, 11, 600, C.Cyan,
                                Modifier.pressClickable(shape = r(6)) { runCatching { uri.openUri(source) } }.heightIn(min = 32.dp).padding(vertical = 8.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** What the photos show that no field takes, in a card: "voltage: 240 volt". Shown only, never saved. */
@Composable
private fun AlsoOnPhotos(notes: List<PhotoNote>) {
    Column(
        Modifier.fillMaxWidth().clip(r(12)).background(C.Surface).border(1.dp, C.Border, r(12)).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LineIcon(Icons.Camera, 15.dp, C.Text, 2.2f)
            Txt(stringResource(R.string.ai_also_on_photo), 13, 700)
        }
        notes.forEach { note ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Txt(stringResource(R.string.ai_photo_note_name, note.name), 12, 500, C.Muted, maxLines = 2)
                Txt(note.value, 12, 500, modifier = Modifier.weight(1f), maxLines = Int.MAX_VALUE)
            }
        }
    }
}

@Composable
private fun SureText(confidence: Double) =
    Txt(stringResource(R.string.ai_sure, (confidence * 100).roundToInt()), 11, 700, if (confidence < SURE_ENOUGH) C.Orange else C.Green)

/** The sheet's tick: a yellow rounded square with a check when it goes into the form (a 40dp target). */
@Composable
private fun AiTick(on: Boolean, onClick: () -> Unit) {
    Box(Modifier.size(40.dp).pressClickable(shape = r(8), onClick = onClick), contentAlignment = Alignment.Center) {
        Box(
            Modifier.size(22.dp).background(if (on) C.Yellow else C.Bg, r(6)).border(1.5.dp, if (on) C.Yellow else C.Border2, r(6)),
            contentAlignment = Alignment.Center,
        ) { if (on) LineIcon(Icons.Check, 13.dp, C.OnYellow, 3.2f) }
    }
}

/** A suggestion sheet's header: its title, what it says about what it found or does, and close. */
@Composable
internal fun SheetTitleRow(title: String, subtitle: String, onClose: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 10.dp, top = 4.dp, bottom = 8.dp), verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Txt(title, 18, 700)
            Txt(subtitle, 12, 400, C.Muted, maxLines = Int.MAX_VALUE)
        }
        CloseButton(onClose, size = 36.dp, iconSize = 16.dp)
    }
}

/** The tick of a suggested value: yellow with a check when it goes into the form (a 36dp target around a 20dp box). */
@Composable
internal fun TickBox(on: Boolean, onClick: () -> Unit) {
    Box(Modifier.size(36.dp).pressClickable(shape = r(8), onClick = onClick), contentAlignment = Alignment.Center) {
        Box(
            Modifier.size(20.dp).background(if (on) C.Yellow else C.Bg, r(5)).border(1.5.dp, if (on) C.Yellow else C.Border2, r(5)),
            contentAlignment = Alignment.Center,
        ) { if (on) LineIcon(Icons.Check, 12.dp, C.OnYellow, 3.2f) }
    }
}

/** How a look-up button shows while the property has AI off. */
private const val AI_OFF_ALPHA = 0.45f

/** Stands in for the product name while the "Found …" sentence is split to set it in bold. */
private const val PRODUCT_MARK = "\u0001"

/**
 * The sheet's buttons for its phase. With the property's AI switch off ([aiOn] false) the buttons that start a look-up
 * stay, dimmed, and the sheet says "AI off. An admin can turn it on." (what was already found can still be applied).
 */
@Composable
private fun Footer(d: AiDialogState, typedCount: Int, nothing: Boolean, actions: AiDialogActions, aiOn: Boolean) {
    // A look-up button: dimmed and doing nothing while AI is off.
    val lookUp = { if (aiOn) actions.onLookUp() }
    val lookUpAlpha = if (aiOn) 1f else AI_OFF_ALPHA
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 10.dp, bottom = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // The error itself says so already.
        if (!aiOn && (d.phase == AiPhase.IDENTIFY || d.phase == AiPhase.RESULTS)) {
            Txt(stringResource(R.string.ai_off), 12, 500, C.Soft, Modifier.testTag(AiTags.AI_OFF), maxLines = Int.MAX_VALUE)
        }
        when (d.phase) {
            AiPhase.LOADING -> {}
            AiPhase.IDENTIFY -> {
                val ready = d.model.isNotBlank() && aiOn
                Pill(
                    stringResource(R.string.ai_find), { if (ready) actions.onLookUp() }, Modifier.fillMaxWidth(),
                    bg = if (ready) C.Yellow else C.Raised, fg = if (ready) C.OnYellow else C.Muted, height = 46.dp, size = 14, weight = 700,
                )
            }
            // The search keeps going when the sheet is closed (the AI button blinks): no buttons while it runs.
            AiPhase.SEARCHING -> {}
            AiPhase.ERROR -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill(stringResource(R.string.action_close), actions.onClose, Modifier.weight(1f), height = 46.dp, size = 13, weight = 700)
                if (d.model.isNotBlank()) {
                    Pill(
                        stringResource(R.string.ai_try_again), lookUp, Modifier.weight(1f).alpha(lookUpAlpha),
                        bg = C.Yellow, fg = C.OnYellow, height = 46.dp, size = 13, weight = 700,
                    )
                }
            }
            // Nothing to apply: no Apply to press, just another model, another look, or Done.
            AiPhase.RESULTS -> if (nothing) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill(stringResource(R.string.ai_change_model), actions.onChangeModel, height = 40.dp, hPad = 12.dp, size = 12, weight = 700)
                Pill(stringResource(R.string.ai_look_again), lookUp, Modifier.alpha(lookUpAlpha), height = 40.dp, hPad = 12.dp, size = 12, weight = 700)
                Pill(
                    stringResource(R.string.action_done), actions.onClose, Modifier.weight(1f),
                    bg = C.Yellow, fg = C.OnYellow, height = 40.dp, hPad = 12.dp, size = 13, weight = 700,
                )
            } else Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val dropped = if (d.typeOn == false) d.lookup?.suggestedType?.values?.keys.orEmpty() else emptySet()
                val n = d.picks.count { (k, p) -> p.on && p.value.isNotBlank() && k !in dropped } + typedCount
                val ready = d.typeOn == true || d.jacsOn == true || n > 0
                Pill(stringResource(R.string.ai_change_model), actions.onChangeModel, height = 40.dp, hPad = 12.dp, size = 12, weight = 700)
                Pill(stringResource(R.string.ai_look_again), lookUp, Modifier.alpha(lookUpAlpha), height = 40.dp, hPad = 12.dp, size = 12, weight = 700)
                Pill(
                    stringResource(R.string.ai_apply),
                    { if (ready) actions.onApply() },
                    Modifier.weight(1f).testTag(AiTags.APPLY).alpha(if (ready) 1f else 0.45f),
                    bg = C.Yellow, fg = C.OnYellow, height = 40.dp, hPad = 12.dp, size = 13, weight = 700,
                )
            }
        }
    }
}
