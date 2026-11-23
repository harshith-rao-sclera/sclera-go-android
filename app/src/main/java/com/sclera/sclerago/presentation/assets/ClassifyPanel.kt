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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.sclera.sclerago.R
import com.sclera.sclerago.domain.model.SURE_ENOUGH
import com.sclera.sclerago.domain.model.SuggestionSource
import com.sclera.sclerago.domain.model.TypeSuggestion
import com.sclera.sclerago.presentation.components.OfflineBanner
import com.sclera.sclerago.presentation.model.OnlineOnly
import com.sclera.sclerago.presentation.common.asString
import com.sclera.sclerago.presentation.components.Capsule
import com.sclera.sclerago.presentation.components.r
import com.sclera.sclerago.presentation.components.topBorder
import com.sclera.sclerago.presentation.icons.Icons
import com.sclera.sclerago.presentation.icons.LineIcon
import com.sclera.sclerago.presentation.theme.C
import com.sclera.sclerago.presentation.theme.Txt
import kotlin.math.roundToInt

/** What the type suggestions under System details do when picked: each pick goes into the form at once. */
class ClassifyActions(
    val onPickType: (masterFormatId: String) -> Unit = {},
    /** Picks another of the picked type's JACS codes. */
    val onPickCode: (code: String) -> Unit = {},
)

/** Test tags of the type suggestions. */
object ClassifyTags {
    const val RESULTS = "classify-results"
    fun type(id: String) = "classify-type-$id"
    fun code(code: String) = "classify-code-$code"
}

/**
 * "Suggest type" under System details, as the iOS app: the button (a spinner and "Working out what it is…" while the
 * server reads the photos and details), then inline below it up to three of the property's types, best first, with how
 * sure, where from, how many fields the type has, and why. Picking one fills the system, asset and sub type at once,
 * and its likeliest JACS code as soon as that is known; the type's JACS codes then show below it, to pick another.
 * Nothing is saved until Save.
 */
@Composable
internal fun ClassifyPanel(
    c: ClassifyState?,
    onSuggest: () -> Unit,
    actions: ClassifyActions,
    modifier: Modifier = Modifier,
    /** Offline: a banner says suggestions need the server (the type can still be picked by hand) instead of the button. */
    offline: Boolean = false,
) {
    val loading = c?.phase == ClassifyPhase.LOADING
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (offline && !loading) OfflineBanner(OnlineOnly.SUGGEST_TYPE)
        else Row(
            Modifier.fillMaxWidth().heightIn(min = 44.dp).clip(Capsule).background(C.Surface).border(1.dp, C.Border2, Capsule)
                .clickable(enabled = !loading, onClick = onSuggest).testTag(DetailTags.SUGGEST_TYPE)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        ) {
            if (loading) CircularProgressIndicator(Modifier.size(14.dp), color = C.Cyan, strokeWidth = 2.dp)
            else LineIcon(Icons.Sparkles, 15.dp, C.Cyan, 2.2f)
            Txt(
                stringResource(
                    when {
                        loading -> R.string.classify_working
                        c == null -> R.string.classify_suggest
                        else -> R.string.classify_again
                    },
                ),
                13, 600, C.Cyan, maxLines = 1,
            )
        }
        when (c?.phase) {
            ClassifyPhase.ERROR -> Txt(c.error?.asString() ?: stringResource(R.string.classify_failed), 12, 500, C.Orange, maxLines = Int.MAX_VALUE)
            ClassifyPhase.RESULTS -> Column(Modifier.testTag(ClassifyTags.RESULTS), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Results(c, actions)
            }
            else -> {}
        }
    }
}

@Composable
private fun Results(c: ClassifyState, actions: ClassifyActions) {
    val r = c.result ?: return
    r.message?.let { Txt(it, 12, 500, C.Soft, maxLines = Int.MAX_VALUE) }
    if (r.suggestions.isEmpty()) {
        if (r.message == null) Txt(stringResource(R.string.classify_none), 12, 500, C.Soft, maxLines = Int.MAX_VALUE)
        return
    }
    Choices(r.suggestions, { it.masterFormatId }, { it.masterFormatId == c.picked }, actions.onPickType, ClassifyTags::type) { s ->
        Txt(typePath(s), 13, 700, maxLines = Int.MAX_VALUE)
        Txt(
            stringResource(
                R.string.classify_meta, (s.confidence * 100).roundToInt(), stringResource(s.source.labelRes()),
                pluralStringResource(R.plurals.classify_fields, s.fields.size, s.fields.size),
            ),
            11, 700, sureColor(s.confidence), maxLines = 1,
        )
        if (s.reason.isNotBlank()) Txt(s.reason, 12, 400, C.Soft, maxLines = Int.MAX_VALUE)
    }

    // The picked type's JACS codes, best first: its likeliest is filled with the type; another can be picked here.
    if (c.picked != null) {
        Txt(stringResource(R.string.detail_jacs_code), 12, 700, C.Muted, modifier = Modifier.padding(top = 2.dp))
        when {
            c.codesLoading -> Txt(stringResource(R.string.classify_jacs_loading), 12, 500, C.Soft, maxLines = Int.MAX_VALUE)
            c.codes.isEmpty() -> Txt(stringResource(R.string.classify_jacs_none), 12, 500, C.Soft, maxLines = Int.MAX_VALUE)
            else -> Choices(c.codes, { it.code }, { it.code == c.code }, actions.onPickCode, ClassifyTags::code) { j ->
                Txt(listOfNotNull(j.code, j.title).joinToString(" · "), 13, 700, maxLines = Int.MAX_VALUE)
                Txt(stringResource(R.string.ai_sure, (j.confidence * 100).roundToInt()), 11, 700, sureColor(j.confidence))
                j.reason?.let { Txt(it, 12, 400, C.Soft, maxLines = Int.MAX_VALUE) }
            }
        }
    }
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
                    Modifier.padding(top = 1.dp).size(18.dp).clip(CircleShape)
                        .then(if (on) Modifier.background(C.Yellow) else Modifier.border(1.5.dp, C.Border2, CircleShape)),
                    contentAlignment = Alignment.Center,
                ) { if (on) Box(Modifier.size(7.dp).clip(CircleShape).background(C.OnYellow)) }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) { content(item) }
            }
        }
    }
}

private fun sureColor(confidence: Double) = if (confidence < SURE_ENOUGH) C.Orange else C.Green

/** "HVAC › Packaged Water Chillers › Absorption Water Chillers". */
private fun typePath(s: TypeSuggestion) = listOfNotNull(s.systemType, s.assetType, s.subtype).filter { it.isNotBlank() }.joinToString(" › ")

private fun SuggestionSource.labelRes() = when (this) {
    SuggestionSource.SAME_MODEL -> R.string.classify_from_same_model
    SuggestionSource.AI -> R.string.classify_from_ai
    SuggestionSource.CHOSEN -> R.string.classify_from_chosen
}
