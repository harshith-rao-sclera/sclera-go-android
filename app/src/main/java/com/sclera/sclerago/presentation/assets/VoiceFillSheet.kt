package com.sclera.sclerago.presentation.assets

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.sclera.sclerago.R
import com.sclera.sclerago.domain.model.CHECKED
import com.sclera.sclerago.domain.model.FieldKind
import com.sclera.sclerago.domain.model.VOICE_SURE_ENOUGH
import com.sclera.sclerago.domain.model.UNCHECKED
import com.sclera.sclerago.presentation.components.BottomSheet
import com.sclera.sclerago.presentation.components.Pill
import com.sclera.sclerago.presentation.components.r
import com.sclera.sclerago.presentation.components.topBorder
import com.sclera.sclerago.presentation.theme.C
import com.sclera.sclerago.presentation.theme.Txt
import kotlin.math.roundToInt

/** What the voice fill sheet's buttons do. */
class VoiceFillActions(
    val onToggle: (key: String) -> Unit = {},
    val onApply: () -> Unit = {},
    val onClose: () -> Unit = {},
    /** Close the sheet and listen again. */
    val onSpeakAgain: () -> Unit = {},
)

/** Test tags of the voice fill sheet. */
object VoiceTags {
    const val SHEET = "voice-sheet"
    const val APPLY = "voice-apply"
    fun row(key: String) = "voice-row-$key"
}

/**
 * The voice fill sheet, shown after speaking: how many fields were filled and how many would change what the form has,
 * what was said, then one row per field with a tick, its name, the value, what the form has now (when that differs)
 * and how sure the AI was (values it was unsure of start unticked), then what was said that no field took. Apply puts
 * the ticked values into the form to save.
 */
@Composable
fun VoiceFillSheet(v: VoiceFillState, state: AssetDetailState, actions: VoiceFillActions) {
    val spec = FieldSpec(state.fields)
    val differs = v.picks.count { (key, pick) -> state.value(key).let { it.isNotEmpty() && it != pick.value } }
    BottomSheet(onDismiss = actions.onClose) {
        Column(Modifier.weight(1f, fill = false).testTag(VoiceTags.SHEET)) {
            // Nothing found: the AI found no field for it; the phone's own reading (offline) needs the field names said.
            val summary = if (v.picks.isEmpty()) stringResource(if (v.byAi) R.string.voice_nothing_matched else R.string.voice_none)
            else pluralStringResource(R.plurals.voice_filled, v.picks.size, v.picks.size) +
                if (differs > 0) " " + pluralStringResource(R.plurals.voice_differs, differs, differs) else ""
            SheetTitleRow(stringResource(R.string.voice_title), summary, actions.onClose)
            Column(
                Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Txt(stringResource(R.string.voice_you_said), 11, 500, C.Soft)
                    Txt("“${v.said}”", 14, 700, maxLines = 6)
                }
                if (v.picks.isNotEmpty()) {
                    Column(Modifier.fillMaxWidth().clip(r(12)).border(1.dp, C.Border, r(12))) {
                        v.picks.entries.forEachIndexed { n, (key, pick) ->
                            val kind = spec.cfg(key)?.kind
                            val now = state.value(key)
                            Row(
                                Modifier.fillMaxWidth().testTag(VoiceTags.row(key)).then(if (n > 0) Modifier.topBorder(C.Border) else Modifier)
                                    .padding(10.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                TickBox(pick.on) { actions.onToggle(key) }
                                Column(Modifier.weight(1f).padding(top = 2.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Txt(spec.label(key).removeSuffix(" *"), 12, 600, C.Soft, maxLines = 2)
                                    Txt(shown(kind, pick.value), 14, 800, maxLines = 6)
                                    if (now.isNotEmpty() && now != pick.value) {
                                        Txt(stringResource(R.string.voice_now, shown(kind, now)), 12, 600, C.Orange, maxLines = 4)
                                    }
                                    v.sure[key]?.let { sure ->
                                        Txt(stringResource(R.string.ai_sure, (sure * 100).roundToInt()), 11, 700, if (sure < VOICE_SURE_ENOUGH) C.Orange else C.Green)
                                    }
                                }
                            }
                        }
                    }
                }
                // What was said that no field took (shown, never saved), as the server lists it.
                if (v.unmatched.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Txt(stringResource(R.string.voice_not_used), 11, 500, C.Soft)
                        v.unmatched.forEach { u ->
                            Txt("“${u.text}”", 12, 500, C.Muted, maxLines = 3)
                            u.reason?.let { Txt(it, 11, 400, C.Soft, Modifier.padding(start = 8.dp), maxLines = 3) }
                        }
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 12.dp, bottom = 14.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val n = v.picks.count { it.value.on }
                Pill(stringResource(R.string.voice_speak_again), actions.onSpeakAgain, Modifier.weight(1f), height = 46.dp, size = 13, weight = 700)
                Pill(
                    stringResource(R.string.voice_apply), { if (n > 0) actions.onApply() },
                    Modifier.weight(1f).testTag(VoiceTags.APPLY).alpha(if (n > 0) 1f else 0.45f),
                    bg = C.Yellow, fg = C.OnYellow, height = 46.dp, size = 13, weight = 700,
                )
            }
        }
    }
}

/** A value as the form shows it: checkbox answers as yes / no. */
@Composable
private fun shown(kind: FieldKind?, value: String): String = when {
    kind == FieldKind.CHECKBOX && value == CHECKED -> stringResource(R.string.detail_yes)
    kind == FieldKind.CHECKBOX && value == UNCHECKED -> stringResource(R.string.detail_no)
    else -> value
}
