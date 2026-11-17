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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.sclera.sclerago.R
import com.sclera.sclerago.domain.model.PhotoReading
import com.sclera.sclerago.domain.model.SURE_ENOUGH
import com.sclera.sclerago.domain.model.SuggestionSource
import com.sclera.sclerago.domain.model.TypeSuggestion
import com.sclera.sclerago.presentation.common.asString
import com.sclera.sclerago.presentation.components.BottomSheet
import com.sclera.sclerago.presentation.components.Capsule
import com.sclera.sclerago.presentation.components.Pill
import com.sclera.sclerago.presentation.components.r
import com.sclera.sclerago.presentation.components.topBorder
import com.sclera.sclerago.presentation.theme.C
import com.sclera.sclerago.presentation.theme.Txt
import kotlin.math.roundToInt

/** What the "Suggest type" sheet's buttons do. */
class ClassifyActions(
    val onClose: () -> Unit = {},
    val onPickType: (masterFormatId: String) -> Unit = {},
    val onPickCode: (code: String) -> Unit = {},
    val onToggleReading: () -> Unit = {},
    val onApply: () -> Unit = {},
)

/** Test tags of the "Suggest type" sheet. */
object ClassifyTags {
    const val SHEET = "classify-sheet"
    const val APPLY = "classify-apply"
    fun type(id: String) = "classify-type-$id"
    fun code(code: String) = "classify-code-$code"
}

/**
 * The "Suggest type" sheet: what the server makes of the asset's photos and details. Up to three of the property's
 * types, best first, with how sure and why; the JACS codes for the type picked; and what the photos said, offered for an
 * empty manufacturer and model. Apply puts the picks into the form (which then shows the type's fields); Save keeps them.
 */
@Composable
fun ClassifySheet(c: ClassifyState, actions: ClassifyActions) {
    BottomSheet(onDismiss = actions.onClose) {
        Column(Modifier.weight(1f, fill = false).testTag(ClassifyTags.SHEET)) {
            SheetTitleRow(stringResource(R.string.classify_title), stringResource(R.string.classify_subtitle), actions.onClose)
            Column(
                Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                when (c.phase) {
                    ClassifyPhase.LOADING -> {
                        Txt(stringResource(R.string.classify_loading), 13, 500, C.Soft, maxLines = 3)
                        repeat(3) { Box(Modifier.fillMaxWidth().height(52.dp).background(C.Raised, r(10))) }
                    }
                    ClassifyPhase.ERROR -> Txt(c.error?.asString() ?: stringResource(R.string.classify_failed), 13, 500, maxLines = 6)
                    ClassifyPhase.RESULTS -> Results(c, actions)
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 10.dp, bottom = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Pill(stringResource(R.string.action_close), actions.onClose, Modifier.weight(1f), height = 46.dp, size = 13, weight = 700)
                if (c.phase == ClassifyPhase.RESULTS) {
                    val ready = c.pickedType != null
                    Pill(
                        stringResource(R.string.classify_apply), { if (ready) actions.onApply() },
                        Modifier.weight(1f).testTag(ClassifyTags.APPLY).alpha(if (ready) 1f else 0.45f),
                        bg = C.Yellow, fg = C.OnYellow, height = 46.dp, size = 13, weight = 700,
                    )
                }
            }
        }
    }
}

@Composable
private fun Results(c: ClassifyState, actions: ClassifyActions) {
    val r = c.result ?: return
    r.message?.let { Txt(it, 12, 500, C.Soft, maxLines = 5) }
    if (r.suggestions.isEmpty()) {
        if (r.message == null) Txt(stringResource(R.string.classify_none), 13, 500, C.Soft, maxLines = 4)
        return
    }
    Txt(stringResource(R.string.classify_types), 13, 700)
    Choices(r.suggestions, { it.masterFormatId }, { it.masterFormatId == c.picked }, actions.onPickType, ClassifyTags::type) { s ->
        Txt(typePath(s), 14, 700, maxLines = 3)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SureText(s.confidence)
            Txt(stringResource(s.source.labelRes()), 10, 700, C.Purple, Modifier.background(C.Purple.copy(alpha = 0.12f), Capsule).padding(horizontal = 8.dp, vertical = 2.dp))
        }
        if (s.reason.isNotBlank()) Txt(s.reason, 12, 400, C.Soft, maxLines = 4)
    }

    Txt(stringResource(R.string.detail_jacs_code), 13, 700, modifier = Modifier.padding(top = 6.dp))
    when {
        c.codesLoading -> Txt(stringResource(R.string.classify_jacs_loading), 12, 500, C.Soft, maxLines = 3)
        c.codes.isEmpty() -> Txt(stringResource(R.string.classify_jacs_none), 12, 500, C.Soft, maxLines = 3)
        else -> Choices(c.codes, { it.code }, { it.code == c.code }, actions.onPickCode, ClassifyTags::code) { j ->
            Txt(listOfNotNull(j.code, j.title).joinToString(" · "), 14, 700, maxLines = 3)
            SureText(j.confidence)
            j.reason?.let { Txt(it, 12, 400, C.Soft, maxLines = 3) }
        }
    }

    r.read?.let { read -> Reading(read, c.useReading, actions.onToggleReading) }
}

/** A list of choices, one picked (a radio dot), each a 48dp-tall row. */
@Composable
private fun <T> Choices(
    items: List<T>,
    id: (T) -> String,
    picked: (T) -> Boolean,
    onPick: (String) -> Unit,
    tag: (String) -> String,
    content: @Composable (T) -> Unit,
) {
    Column(Modifier.fillMaxWidth().clip(r(12)).border(1.dp, C.Border, r(12))) {
        items.forEachIndexed { n, item ->
            val on = picked(item)
            Row(
                Modifier.fillMaxWidth().testTag(tag(id(item))).then(if (n > 0) Modifier.topBorder(C.Border) else Modifier)
                    .background(if (on) C.YTintBg else C.Surface).semantics { selected = on; role = Role.RadioButton }
                    .clickable { onPick(id(item)) }.heightIn(min = 48.dp).padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    Modifier.padding(top = 2.dp).size(18.dp).clip(CircleShape).border(1.5.dp, if (on) C.Yellow else C.Border2, CircleShape),
                    contentAlignment = Alignment.Center,
                ) { if (on) Box(Modifier.size(9.dp).clip(CircleShape).background(C.Yellow)) }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) { content(item) }
            }
        }
    }
}

/** What the photos said, ticked to fill an empty manufacturer and model. */
@Composable
private fun Reading(read: PhotoReading, on: Boolean, onToggle: () -> Unit) {
    val said = listOfNotNull(read.manufacturer, read.model, read.kind).joinToString(" · ")
    if (said.isEmpty()) return
    Row(
        Modifier.fillMaxWidth().padding(top = 4.dp).clip(r(12)).border(1.dp, C.Border, r(12)).padding(end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TickBox(on, onToggle)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Txt(stringResource(R.string.classify_read_title), 13, 700)
            Txt(stringResource(R.string.classify_read_body, said), 12, 400, C.Soft, maxLines = 4)
        }
    }
}

@Composable
private fun SureText(confidence: Double) {
    Txt(stringResource(R.string.ai_sure, (confidence * 100).roundToInt()), 11, 700, if (confidence < SURE_ENOUGH) C.Orange else C.Green)
}

/** "HVAC › Packaged Water Chillers › Absorption Water Chillers". */
private fun typePath(s: TypeSuggestion) = listOfNotNull(s.systemType, s.assetType, s.subtype).filter { it.isNotBlank() }.joinToString(" › ")

private fun SuggestionSource.labelRes() = when (this) {
    SuggestionSource.SAME_MODEL -> R.string.classify_from_same_model
    SuggestionSource.AI -> R.string.classify_from_ai
    SuggestionSource.CHOSEN -> R.string.classify_from_chosen
}
