package com.sclera.sclerago.presentation.assets

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sclera.sclerago.R
import com.sclera.sclerago.domain.model.LookupType
import com.sclera.sclerago.domain.model.SURE_ENOUGH
import com.sclera.sclerago.domain.model.typeFieldName
import com.sclera.sclerago.presentation.common.asString
import com.sclera.sclerago.presentation.components.BottomSheet
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
    val onClose: () -> Unit = {},
    val onIdentity: (make: String, model: String) -> Unit = { _, _ -> },
    val onLookUp: () -> Unit = {},
    val onChangeModel: () -> Unit = {},
    val onToggle: (key: String) -> Unit = {},
    /** Ticks or unticks the suggested type. */
    val onToggleType: () -> Unit = {},
    val onEdit: (key: String, value: String) -> Unit = { _, _ -> },
    val onApply: () -> Unit = {},
)

/** Test tags of the AI suggestions sheet. */
object AiTags {
    const val DIALOG = "ai-dialog"
    const val APPLY = "ai-apply"
    const val TYPE = "ai-type"
    fun row(key: String) = "ai-row-$key"
}

/** When each step of the search shows as reached, in seconds (the web's). */
private val STEP_AT = listOf(0, 4, 12, 22)
private val STEPS = listOf(R.string.ai_step_identify, R.string.ai_step_search, R.string.ai_step_read, R.string.ai_step_match)

/**
 * The AI suggestions sheet: it fills the asset's empty fields from its model's published specifications. It asks for
 * the model when it has none, shows the search's progress (it can be closed and keeps going), and lists each suggestion
 * with its value (editable), how sure it is and where it came from; Apply puts the ticked ones into the form to save.
 */
@Composable
fun AiSuggestDialog(d: AiDialogState, state: AssetDetailState, actions: AiDialogActions) {
    BottomSheet(onDismiss = actions.onClose) {
        Column(Modifier.weight(1f, fill = false).testTag(AiTags.DIALOG)) {
            SheetTitleRow(
                stringResource(R.string.ai_title),
                stringResource(R.string.ai_subtitle, state.value("name").ifBlank { state.asset?.name.orEmpty() }),
                actions.onClose,
            )
            Column(
                Modifier.weight(1f, fill = d.phase == AiPhase.SEARCHING).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                when (d.phase) {
                    AiPhase.LOADING -> repeat(3) { Box(Modifier.fillMaxWidth().height(36.dp).background(C.Raised, r(8))) }
                    AiPhase.IDENTIFY -> Identify(d, state, actions)
                    AiPhase.SEARCHING -> Searching(d)
                    AiPhase.ERROR -> Txt(d.error?.asString() ?: stringResource(R.string.ai_failed), 13, 500, maxLines = 6)
                    AiPhase.RESULTS -> Results(d, state, actions)
                }
            }
            // The manufacturer and model typed go into the form with the suggestions, so they count towards Apply.
            val typedCount = listOf("make" to d.make, "model" to d.model).count { (k, v) -> v.trim().isNotEmpty() && v.trim() != state.value(k) }
            Footer(d, typedCount, actions)
        }
    }
}

/** The manufacturer and model to look up, labelled with the property's own field names. */
@Composable
private fun Identify(d: AiDialogState, state: AssetDetailState, actions: AiDialogActions) {
    Txt(d.error?.asString() ?: stringResource(R.string.ai_identify_lead), 13, 500, maxLines = 5)
    val spec = FieldSpec(state.fields)
    LabeledBox(spec.label("make", stringResource(R.string.ai_make_label)).removeSuffix(" *"), d.make, stringResource(R.string.ai_make_hint)) {
        actions.onIdentity(it, d.model)
    }
    LabeledBox(spec.label("model", stringResource(R.string.ai_model_label)).removeSuffix(" *"), d.model, stringResource(R.string.ai_model_hint)) {
        actions.onIdentity(d.make, it)
    }
    Txt(stringResource(R.string.ai_typed_note), 11, 400, C.Muted, maxLines = 3)
}

@Composable
private fun LabeledBox(label: String, value: String, hint: String, onChange: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Txt(label, 13, 700)
        PlainField(
            value, onChange, hint,
            Modifier.fillMaxWidth().heightIn(min = 44.dp).clip(r(10)).background(C.Bg).border(1.dp, C.Border2, r(10)).padding(horizontal = 12.dp, vertical = 12.dp),
            size = 14,
        )
    }
}

/** The search's progress: a bar that never quite fills, the four steps by time, and how long it has taken. */
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
        LineIcon(Icons.Sparkles, 20.dp, C.Cyan, 2.2f)
        Txt(
            when {
                d.fromPhoto -> stringResource(R.string.ai_reading_photo)
                what.isNotEmpty() -> stringResource(R.string.ai_searching_for, what)
                else -> stringResource(R.string.ai_searching)
            },
            14, 700, modifier = Modifier.weight(1f), maxLines = 3,
        )
    }
    val percent = (95 * (1 - exp(-seconds / 18.0))).roundToInt()
    Box(Modifier.fillMaxWidth().height(5.dp).background(C.Raised, Capsule)) {
        Box(Modifier.fillMaxWidth(percent / 100f).height(5.dp).background(C.Yellow, Capsule))
    }
    STEPS.forEachIndexed { i, step ->
        val reached = seconds >= STEP_AT[i]
        val done = i < STEPS.lastIndex && seconds >= STEP_AT[i + 1]
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            // Done: a filled check; the step under way: a ring with a dot; the rest: an empty ring.
            Box(
                Modifier.size(16.dp).background(if (done) C.Yellow else C.Bg, CircleShape)
                    .border(1.5.dp, if (done || reached) C.Yellow else C.Border2, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    done -> LineIcon(Icons.Check, 9.dp, C.OnYellow, 3.2f)
                    reached -> Box(Modifier.size(6.dp).background(C.Yellow, CircleShape))
                }
            }
            Txt(stringResource(step), 12, if (reached && !done) 700 else 500, if (reached) C.Text else C.Soft)
        }
    }
    val wait = stringResource(if (seconds < 30) R.string.ai_wait_short else R.string.ai_wait_long)
    Txt("$wait ${seconds}s", 11, 400, C.Soft, maxLines = 3)
}

/** The suggestions: what was found, then one row per field with a tick, its name, the value, how sure, and the source. */
@Composable
private fun Results(d: AiDialogState, state: AssetDetailState, actions: AiDialogActions) {
    val lookup = d.lookup
    val spec = FieldSpec(state.fields)
    val typed = buildList {
        d.make.trim().takeIf { it.isNotEmpty() && it != state.value("make") }?.let { add(spec.label("make").removeSuffix(" *") to it) }
        d.model.trim().takeIf { it.isNotEmpty() && it != state.value("model") }?.let { add(spec.label("model").removeSuffix(" *") to it) }
    }
    val product = lookup?.product
    // "Found Baltimore Aircoil Series 3000 cooling tower. Filled 2 fields from …", the product in bold.
    when {
        product != null -> {
            val lead = stringResource(R.string.ai_found, PRODUCT_MARK, lookup?.message.orEmpty()).trim()
            val before = lead.substringBefore(PRODUCT_MARK)
            val after = lead.substringAfter(PRODUCT_MARK, "")
            Text(
                buildAnnotatedString {
                    append(before)
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = C.Text)) { append(product) }
                    append(after)
                },
                color = C.Soft, fontSize = 13.sp, fontFamily = ScleraFonts.Inter, lineHeight = 18.sp,
            )
        }
        d.picks.isEmpty() && typed.isNotEmpty() -> Txt(stringResource(R.string.ai_nothing_else), 13, 500, C.Soft, maxLines = 5)
        !lookup?.message.isNullOrBlank() -> Txt(lookup?.message.orEmpty(), 13, 500, C.Soft, maxLines = 5)
    }
    if (typed.isNotEmpty()) {
        Txt(stringResource(R.string.ai_also_saves, typed.joinToString(", ") { (f, v) -> "$f “$v”" }), 12, 400, C.Soft, maxLines = 4)
    }
    // The type the AI thinks it is (when it isn't the asset's): its fields' values go in only with it.
    val type = lookup?.suggestedType?.takeIf { d.typeOn != null }
    if (type != null) TypeRow(type, d.typeOn == true, actions.onToggleType)
    // As the web: the type's fields' values show only while the type is ticked (they go in only with it).
    val hidden = if (d.typeOn == false) type?.values?.keys.orEmpty() else emptySet()
    val rows = d.picks.entries.filter { it.key !in hidden }
    if (rows.isNotEmpty()) PickedRows(d, rows, state, actions)
    // What the photos say that no field takes, shown as the server sends it (never saved).
    val extra = lookup?.extra.orEmpty()
    if (extra.isNotEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Txt(stringResource(R.string.ai_also_on_photo), 12, 700, C.Muted)
            extra.forEach { Txt(stringResource(R.string.ai_photo_note, it.name, it.value), 12, 500, C.Soft, maxLines = 3) }
        }
    }
    if (lookup?.manualsSearch == true) Txt(stringResource(R.string.ai_manuals_search), 12, 400, C.Soft, maxLines = 3)
}

/** The suggested values: a tick, the field, the value (editable), how sure, and the page it came from. */
@Composable
private fun PickedRows(d: AiDialogState, rows: List<Map.Entry<String, AiPick>>, state: AssetDetailState, actions: AiDialogActions) {
    val lookup = d.lookup
    val spec = FieldSpec(state.fields)
    val uri = LocalUriHandler.current
    Column(Modifier.fillMaxWidth().clip(r(12)).border(1.dp, C.Border, r(12))) {
        rows.forEachIndexed { n, (key, pick) ->
            val sure = lookup?.confidence?.get(key) ?: 1.0
            val source = lookup?.sources?.get(key)
            Column(
                Modifier.fillMaxWidth().testTag(AiTags.row(key)).then(if (n > 0) Modifier.topBorder(C.Border) else Modifier).padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TickBox(pick.on) { actions.onToggle(key) }
                    // A field the suggested type brings isn't on the asset yet: it is named by the type.
                    Txt(typeFieldName(key) ?: spec.label(key).removeSuffix(" *"), 13, 700, modifier = Modifier.weight(1f), maxLines = 2)
                }
                // The photo disagrees with what is saved: both are shown, and it is only changed when ticked.
                if (lookup?.differs?.contains(key) == true) {
                    Txt(stringResource(R.string.ai_saved_value, state.value(key)), 12, 500, C.Orange, Modifier.padding(start = 46.dp), maxLines = 3)
                }
                PlainField(
                    pick.value, { actions.onEdit(key, it) }, "",
                    Modifier.padding(start = 46.dp).fillMaxWidth().clip(r(8)).background(C.Bg).border(1.dp, C.Border2, r(8))
                        .padding(horizontal = 10.dp, vertical = 10.dp),
                    size = 14, singleLine = false,
                )
                Row(Modifier.padding(start = 46.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Txt(stringResource(R.string.ai_sure, (sure * 100).roundToInt()), 11, 700, if (sure < SURE_ENOUGH) C.Orange else C.Green)
                    val host = source?.let { runCatching { URI(it).host?.removePrefix("www.") }.getOrNull() }
                    if (host != null) {
                        Txt(host, 11, 500, C.Soft, Modifier.clip(r(6)).clickable { runCatching { uri.openUri(source) } }.heightIn(min = 32.dp).padding(vertical = 8.dp))
                    }
                }
            }
        }
    }
}

/**
 * The type the AI thinks the asset is, ticked to go into the form: "Type", its path, how sure and where from, its JACS
 * code, and why.
 */
@Composable
private fun TypeRow(t: LookupType, on: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().testTag(AiTags.TYPE).clip(r(12)).border(1.dp, if (on) C.Yellow else C.Border, r(12))
            .background(if (on) C.YTintBg else C.Surface).padding(end = 10.dp, top = 6.dp, bottom = 8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        TickBox(on, onToggle)
        Column(Modifier.weight(1f).padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Txt(stringResource(R.string.ai_type_title), 11, 700, C.Muted)
            Txt(t.label ?: listOfNotNull(t.systemType, t.assetType, t.subtype).filter { it.isNotBlank() }.joinToString(" › "), 14, 700, maxLines = 3)
            Txt(stringResource(R.string.ai_sure, (t.confidence * 100).roundToInt()), 11, 700, if (t.confidence < SURE_ENOUGH) C.Orange else C.Green)
            t.jacsCode?.let { Txt(stringResource(R.string.ai_type_jacs, it), 12, 500, C.Soft, maxLines = 1) }
            if (t.reason.isNotBlank()) Txt(t.reason, 12, 400, C.Soft, maxLines = 4)
        }
    }
}

/** A suggestion sheet's header: its title, what it says about what it found or does, and close. */
@Composable
internal fun SheetTitleRow(title: String, subtitle: String, onClose: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 10.dp, top = 4.dp, bottom = 8.dp), verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Txt(title, 18, 800)
            Txt(subtitle, 12, 400, C.Soft, maxLines = 4)
        }
        CloseButton(onClose, size = 36.dp, iconSize = 16.dp)
    }
}

/** The tick of a suggested value: yellow with a check when it goes into the form (a 36dp target around a 20dp box). */
@Composable
internal fun TickBox(on: Boolean, onClick: () -> Unit) {
    Box(Modifier.size(36.dp).clip(r(8)).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Box(
            Modifier.size(20.dp).background(if (on) C.Yellow else C.Bg, r(5)).border(1.5.dp, if (on) C.Yellow else C.Border2, r(5)),
            contentAlignment = Alignment.Center,
        ) { if (on) LineIcon(Icons.Check, 12.dp, C.OnYellow, 3.2f) }
    }
}

/** Stands in for the product name while the "Found …" sentence is split to set it in bold. */
private const val PRODUCT_MARK = "\u0001"

/** The sheet's buttons for its phase. */
@Composable
private fun Footer(d: AiDialogState, typedCount: Int, actions: AiDialogActions) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 10.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when (d.phase) {
            AiPhase.LOADING -> {}
            AiPhase.IDENTIFY -> {
                val ready = d.model.isNotBlank()
                Pill(
                    stringResource(R.string.ai_find), { if (ready) actions.onLookUp() }, Modifier.fillMaxWidth().alpha(if (ready) 1f else 0.45f),
                    bg = C.Yellow, fg = C.OnYellow, height = 46.dp, size = 14, weight = 700,
                )
            }
            AiPhase.SEARCHING -> {
                Txt(stringResource(R.string.ai_close_note), 11, 400, C.Muted, Modifier.fillMaxWidth(), align = TextAlign.Center)
                Pill(stringResource(R.string.ai_close_keep), actions.onClose, Modifier.fillMaxWidth(), height = 46.dp, size = 13, weight = 700)
            }
            AiPhase.ERROR -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill(stringResource(R.string.action_close), actions.onClose, Modifier.weight(1f), height = 46.dp, size = 13, weight = 700)
                if (d.model.isNotBlank()) {
                    Pill(stringResource(R.string.ai_try_again), actions.onLookUp, Modifier.weight(1f), bg = C.Yellow, fg = C.OnYellow, height = 46.dp, size = 13, weight = 700)
                }
            }
            AiPhase.RESULTS -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val dropped = if (d.typeOn == false) d.lookup?.suggestedType?.values?.keys.orEmpty() else emptySet()
                val n = d.picks.count { (k, p) -> p.on && p.value.isNotBlank() && k !in dropped } + typedCount + (if (d.typeOn == true) 1 else 0)
                Pill(stringResource(R.string.ai_change_model), actions.onChangeModel, height = 40.dp, hPad = 12.dp, size = 12, weight = 700)
                Pill(stringResource(R.string.ai_look_again), actions.onLookUp, height = 40.dp, hPad = 12.dp, size = 12, weight = 700)
                Box(Modifier.weight(1f))
                Pill(
                    stringResource(R.string.ai_apply_count, n), { if (n > 0) actions.onApply() },
                    Modifier.testTag(AiTags.APPLY).alpha(if (n > 0) 1f else 0.45f),
                    bg = C.Yellow, fg = C.OnYellow, height = 40.dp, hPad = 18.dp, size = 13, weight = 700,
                )
            }
        }
    }
}
