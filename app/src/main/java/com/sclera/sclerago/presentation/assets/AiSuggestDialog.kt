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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.sclera.sclerago.R
import com.sclera.sclerago.domain.model.SURE_ENOUGH
import com.sclera.sclerago.presentation.common.asString
import com.sclera.sclerago.presentation.components.Capsule
import com.sclera.sclerago.presentation.components.CloseButton
import com.sclera.sclerago.presentation.components.Pill
import com.sclera.sclerago.presentation.components.PlainField
import com.sclera.sclerago.presentation.components.SheetMaxWidth
import com.sclera.sclerago.presentation.components.r
import com.sclera.sclerago.presentation.icons.Icons
import com.sclera.sclerago.presentation.icons.LineIcon
import com.sclera.sclerago.presentation.theme.C
import com.sclera.sclerago.presentation.theme.Txt
import kotlinx.coroutines.delay
import java.net.URI
import kotlin.math.exp
import kotlin.math.roundToInt

/** What the AI suggestions dialog's buttons do. */
class AiDialogActions(
    val onClose: () -> Unit = {},
    val onIdentity: (make: String, model: String) -> Unit = { _, _ -> },
    val onLookUp: () -> Unit = {},
    val onChangeModel: () -> Unit = {},
    val onToggle: (key: String) -> Unit = {},
    val onEdit: (key: String, value: String) -> Unit = { _, _ -> },
    val onApply: () -> Unit = {},
)

/** Test tags of the AI suggestions dialog. */
object AiTags {
    const val DIALOG = "ai-dialog"
    const val APPLY = "ai-apply"
    fun row(key: String) = "ai-row-$key"
}

/** When each step of the search shows as reached, in seconds (the web's). */
private val STEP_AT = listOf(0, 4, 12, 22)
private val STEPS = listOf(R.string.ai_step_identify, R.string.ai_step_search, R.string.ai_step_read, R.string.ai_step_match)

/**
 * The web's AI suggestions dialog: it fills the asset's empty fields from its model's published specifications, and
 * saves nothing until Apply. It asks for the model when it has none, shows the search's progress (it can be closed and
 * keeps going), and lists each suggestion with its value (editable), how sure it is and where it came from.
 */
@Composable
fun AiSuggestDialog(d: AiDialogState, state: AssetDetailState, actions: AiDialogActions) {
    Dialog(onDismissRequest = actions.onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.padding(16.dp).widthIn(max = SheetMaxWidth).fillMaxWidth().testTag(AiTags.DIALOG).clip(r(18)).background(C.Surface)
                .border(1.dp, C.Border, r(18)).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        LineIcon(Icons.Sparkles, 16.dp, C.Cyan, 2.2f)
                        Txt(stringResource(R.string.ai_title), 17, 800)
                    }
                    Txt(stringResource(R.string.ai_subtitle, state.value("name").ifBlank { state.asset?.name.orEmpty() }), 12, 400, C.Soft, maxLines = 4)
                }
                CloseButton(actions.onClose, size = 40.dp)
            }
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                when (d.phase) {
                    AiPhase.LOADING -> repeat(3) { Box(Modifier.fillMaxWidth().height(36.dp).background(C.Raised, r(8))) }
                    AiPhase.IDENTIFY -> Identify(d, state, actions)
                    AiPhase.SEARCHING -> Searching(d)
                    AiPhase.ERROR -> Txt(d.error?.asString() ?: stringResource(R.string.ai_failed), 13, 500, maxLines = 6)
                    AiPhase.RESULTS -> Results(d, state, actions)
                }
            }
            // The manufacturer and model typed are saved with the suggestions, so they count towards Apply.
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
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Txt(label, 12, 600, C.Soft)
        PlainField(
            value, onChange, hint,
            Modifier.fillMaxWidth().heightIn(min = 44.dp).clip(r(10)).background(C.Bg).border(1.dp, C.Border2, r(10)).padding(horizontal = 10.dp, vertical = 12.dp),
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
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        LineIcon(Icons.Sparkles, 18.dp, C.Cyan, 2.2f)
        Txt(
            if (what.isNotEmpty()) stringResource(R.string.ai_searching_for, what) else stringResource(R.string.ai_searching),
            13, 600, modifier = Modifier.weight(1f), maxLines = 3,
        )
    }
    val percent = (95 * (1 - exp(-seconds / 18.0))).roundToInt()
    Box(Modifier.fillMaxWidth().height(6.dp).background(C.Raised, Capsule)) {
        Box(Modifier.fillMaxWidth(percent / 100f).height(6.dp).background(C.Cyan, Capsule))
    }
    STEPS.forEachIndexed { i, step ->
        val reached = seconds >= STEP_AT[i]
        val done = i < STEPS.lastIndex && seconds >= STEP_AT[i + 1]
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.alpha(if (reached) 1f else 0.45f)) {
            Box(
                Modifier.size(18.dp).background(if (done) C.Green else if (reached) C.Cyan.copy(alpha = 0.2f) else C.Raised, CircleShape),
                contentAlignment = Alignment.Center,
            ) { if (done) LineIcon(Icons.Check, 10.dp, C.OnYellow, 3f) }
            Txt(stringResource(step), 12, if (reached && !done) 700 else 500)
        }
    }
    Txt(stringResource(if (seconds < 30) R.string.ai_wait_short else R.string.ai_wait_long), 12, 400, C.Soft, maxLines = 3)
    Txt("${stringResource(R.string.ai_can_close)} ${seconds}s", 11, 400, C.Muted, maxLines = 3)
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
    val lead = when {
        lookup?.product != null -> stringResource(R.string.ai_found, lookup.product, lookup.message.orEmpty()).trim()
        d.picks.isEmpty() && typed.isNotEmpty() -> stringResource(R.string.ai_nothing_else)
        else -> lookup?.message.orEmpty()
    }
    if (lead.isNotEmpty()) Txt(lead, 13, 500, maxLines = 6)
    if (typed.isNotEmpty()) {
        Txt(stringResource(R.string.ai_also_saves, typed.joinToString(", ") { (f, v) -> "$f “$v”" }), 12, 400, C.Soft, maxLines = 4)
    }
    val uri = LocalUriHandler.current
    d.picks.forEach { (key, pick) ->
        val sure = lookup?.confidence?.get(key) ?: 1.0
        val source = lookup?.sources?.get(key)
        Column(
            Modifier.fillMaxWidth().testTag(AiTags.row(key)).clip(r(10)).background(if (pick.on) C.YTintBg else C.Bg)
                .border(1.dp, C.Border, r(10)).padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    Modifier.size(40.dp).clip(r(8)).clickable { actions.onToggle(key) }, contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier.size(18.dp).background(if (pick.on) C.Yellow else C.Bg, r(4)).border(1.5.dp, if (pick.on) C.Yellow else C.Border2, r(4)),
                        contentAlignment = Alignment.Center,
                    ) { if (pick.on) LineIcon(Icons.Check, 11.dp, C.OnYellow, 3.2f) }
                }
                Txt(spec.label(key).removeSuffix(" *"), 13, 700, modifier = Modifier.weight(1f), maxLines = 2)
                Txt(
                    stringResource(R.string.ai_sure, (sure * 100).roundToInt()), 10, 700, if (sure < SURE_ENOUGH) C.Orange else C.Soft,
                    Modifier.background(if (sure < SURE_ENOUGH) C.WarnRow else C.Btn, Capsule).padding(horizontal = 7.dp, vertical = 2.dp),
                )
            }
            PlainField(
                pick.value, { actions.onEdit(key, it) }, "",
                Modifier.fillMaxWidth().clip(r(8)).background(C.Surface).border(1.dp, C.Border2, r(8)).padding(horizontal = 10.dp, vertical = 10.dp),
                size = 14, singleLine = false,
            )
            val host = source?.let { runCatching { URI(it).host?.removePrefix("www.") }.getOrNull() }
            Txt(
                host ?: "—", 11, 600, if (host != null) C.Yellow else C.Muted,
                if (host != null) Modifier.clip(r(6)).clickable { runCatching { uri.openUri(source) } }.heightIn(min = 32.dp).padding(vertical = 8.dp) else Modifier,
            )
        }
    }
}

/** The dialog's buttons for its phase. */
@Composable
private fun Footer(d: AiDialogState, typedCount: Int, actions: AiDialogActions) {
    val typed = d.model.isNotBlank()
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
        when (d.phase) {
            AiPhase.LOADING -> {}
            AiPhase.IDENTIFY -> {
                Pill(stringResource(R.string.action_cancel), actions.onClose, height = 44.dp, hPad = 16.dp)
                Pill(
                    stringResource(R.string.ai_find), { if (typed) actions.onLookUp() }, Modifier.alpha(if (typed) 1f else 0.45f),
                    bg = C.Yellow, fg = C.OnYellow, height = 44.dp, hPad = 16.dp, weight = 700,
                )
            }
            AiPhase.SEARCHING -> {
                Txt(stringResource(R.string.ai_close_note), 11, 400, C.Muted, Modifier.weight(1f), maxLines = 2)
                Pill(stringResource(R.string.ai_close_keep), actions.onClose, height = 44.dp, hPad = 14.dp)
            }
            AiPhase.ERROR -> {
                Pill(stringResource(R.string.action_close), actions.onClose, height = 44.dp, hPad = 16.dp)
                if (typed) Pill(stringResource(R.string.ai_try_again), actions.onLookUp, bg = C.Yellow, fg = C.OnYellow, height = 44.dp, hPad = 16.dp, weight = 700)
            }
            AiPhase.RESULTS -> {
                val n = d.picks.count { it.value.on && it.value.value.isNotBlank() } + typedCount
                Column(Modifier.weight(1f)) {
                    Txt(if (n > 0) pluralStringResource(R.plurals.ai_will_fill, n, n) else stringResource(R.string.ai_nothing_ticked), 11, 500, C.Muted, maxLines = 2)
                }
                Txt(
                    stringResource(R.string.ai_change_model), 12, 700, C.Yellow,
                    Modifier.clip(r(6)).clickable(onClick = actions.onChangeModel).heightIn(min = 44.dp).padding(horizontal = 4.dp, vertical = 12.dp),
                )
                Txt(
                    stringResource(R.string.ai_look_again), 12, 700, C.Yellow,
                    Modifier.clip(r(6)).clickable(onClick = actions.onLookUp).heightIn(min = 44.dp).padding(horizontal = 4.dp, vertical = 12.dp),
                )
                Pill(
                    stringResource(if (d.applying) R.string.ai_saving else R.string.ai_apply), { if (n > 0 && !d.applying) actions.onApply() },
                    Modifier.testTag(AiTags.APPLY).alpha(if (n > 0 && !d.applying) 1f else 0.45f),
                    bg = C.Yellow, fg = C.OnYellow, height = 44.dp, hPad = 16.dp, weight = 700,
                )
            }
        }
    }
}
