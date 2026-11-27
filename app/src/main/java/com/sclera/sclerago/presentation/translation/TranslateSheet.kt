package com.sclera.sclerago.presentation.translation

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.sclera.sclerago.R
import com.sclera.sclerago.domain.model.TranslationLanguages
import com.sclera.sclerago.presentation.components.BottomSheet
import com.sclera.sclerago.presentation.components.CloseButton
import com.sclera.sclerago.presentation.components.PRESSED_CARD
import com.sclera.sclerago.presentation.components.SearchField
import com.sclera.sclerago.presentation.components.pressClickable
import com.sclera.sclerago.presentation.components.r
import com.sclera.sclerago.presentation.icons.Icons
import com.sclera.sclerago.presentation.icons.LineIcon
import com.sclera.sclerago.presentation.theme.C
import com.sclera.sclerago.presentation.theme.Txt
import java.util.Locale

/** Test tags of the Translate to sheet. */
object TranslateTags {
    const val SHEET = "translate_sheet"
    const val SEARCH = "translate_search"
    const val CLOSE = "translate_close"
    const val FAB = "translate_fab"
    const val NONE = "translate_option_none"
    const val NO_MATCH = "translate_no_match"
    fun option(code: String) = "translate_option_$code"
    fun retry(code: String) = "translate_retry_$code"
}

/** A language in the list: its own name, and its name in English. */
internal data class LanguageOption(val code: String, val nativeName: String, val englishName: String)

private fun tagOf(code: String) = when (code) { "iw" -> "he"; "in" -> "id"; "zh-CN" -> "zh-Hans"; "zh-TW" -> "zh-Hant"; else -> code }

/** A language in its own name ("dansk"). */
internal fun languageName(code: String): String {
    val locale = Locale.forLanguageTag(tagOf(code))
    return locale.getDisplayName(locale).replaceFirstChar { it.titlecase(locale) }
}

/** The languages offered, by English name. */
internal fun languageOptions(codes: List<String> = TranslationLanguages.supported): List<LanguageOption> =
    codes.map { code ->
        val english = Locale.forLanguageTag(tagOf(code)).getDisplayName(Locale.ENGLISH).replace(" Han", "")
        LanguageOption(code, languageName(code), english)
    }.sortedBy { it.englishName }

/** Languages whose own or English name contains [query]. */
internal fun List<LanguageOption>.matching(query: String): List<LanguageOption> {
    val q = query.trim()
    return if (q.isEmpty()) this else filter { it.nativeName.contains(q, ignoreCase = true) || it.englishName.contains(q, ignoreCase = true) }
}

private const val MEDIUM = 0.54f
private const val LARGE = 0.92f
private const val DRAG_TO_LARGE_PX = 40f

/**
 * The "Translate to" sheet: opens at medium height and drags up to large; Don't translate first, then the languages by
 * English name. A tap picks a language (app text switches at once); the tapped row then shows its language being made
 * ready for names and comments ([download]), while the other rows dim.
 */
@Composable
fun TranslateSheet(
    selected: String?,
    download: LanguageDownload,
    installed: Set<String>,
    online: Boolean,
    onPick: (String?) -> Unit,
    onRetry: (String) -> Unit,
    onClose: () -> Unit,
) {
    val all = remember { languageOptions() }
    var query by remember { mutableStateOf("") }
    var large by remember { mutableStateOf(false) }
    // Languages on the phone come first, online or not; the rest follow by English name.
    val shown = all.matching(query).sortedByDescending { it.code in installed }
    val fraction by animateFloatAsState(if (large || query.isNotBlank()) LARGE else MEDIUM, label = "sheet-height")
    val busy = download is LanguageDownload.Working || download is LanguageDownload.Ready
    BottomSheet(onDismiss = onClose, maxHeightFraction = fraction, background = C.Surface) {
        Column(Modifier.weight(1f).testTag(TranslateTags.SHEET).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                Modifier.fillMaxWidth().pointerInput(Unit) {
                    var dragged = 0f
                    detectVerticalDragGestures(onDragStart = { dragged = 0f }, onVerticalDrag = { _, dy ->
                        dragged += dy
                        if (dragged < -DRAG_TO_LARGE_PX) large = true
                    })
                }.padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Txt(stringResource(R.string.translate_title), 22, 700, modifier = Modifier.weight(1f))
                Box(Modifier.testTag(TranslateTags.CLOSE)) { CloseButton(onClose, size = 44.dp, iconSize = 16.dp) }
            }
            SearchField(
                query, { query = it }, stringResource(R.string.translate_search),
                Modifier.testTag(TranslateTags.SEARCH), height = 44.dp, bg = C.Bg,
            )
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (query.isBlank()) item(key = "none") {
                    OptionRow(
                        stringResource(R.string.translate_none), stringResource(R.string.translate_none_sub), selected == null,
                        Modifier.testTag(TranslateTags.NONE), enabled = !busy,
                    ) { onPick(null) }
                }
                items(shown, key = { it.code }) { o ->
                    val mine = download.codeOrNull() == o.code
                    val failed = mine && download is LanguageDownload.Failed
                    // Offline, a language that isn't on the phone can't be fetched, so it can't be picked.
                    val reachable = o.code in installed || online || o.code == selected
                    OptionRow(
                        o.nativeName,
                        when {
                            mine && download is LanguageDownload.Working -> stringResource(R.string.translate_row_downloading, o.englishName)
                            mine && download is LanguageDownload.Ready -> stringResource(R.string.translate_row_ready, o.englishName)
                            failed -> stringResource(R.string.translate_row_failed)
                            else -> o.englishName
                        },
                        o.code == selected,
                        Modifier.testTag(TranslateTags.option(o.code)),
                        enabled = !busy && reachable,
                        dimmed = (busy && !mine) || !reachable,
                        titleHidden = failed,
                        status = if (mine) download else null,
                        onRetry = if (failed) ({ onRetry(o.code) }) else null,
                        retryTag = TranslateTags.retry(o.code),
                    ) { onPick(o.code) }
                }
                if (shown.isEmpty()) item(key = "none-match") {
                    Txt(stringResource(R.string.translate_no_match), 14, 400, C.Muted, Modifier.padding(vertical = 20.dp).testTag(TranslateTags.NO_MATCH), maxLines = Int.MAX_VALUE)
                }
            }
        }
    }
}

private fun LanguageDownload.codeOrNull(): String? = when (this) {
    is LanguageDownload.Working -> code
    is LanguageDownload.Ready -> code
    is LanguageDownload.Failed -> code
    LanguageDownload.Idle -> null
}

@Composable
private fun OptionRow(
    title: String,
    sub: String,
    on: Boolean,
    modifier: Modifier,
    enabled: Boolean = true,
    dimmed: Boolean = false,
    titleHidden: Boolean = false,
    status: LanguageDownload? = null,
    onRetry: (() -> Unit)? = null,
    retryTag: String = "",
    onClick: () -> Unit,
) {
    Row(
        modifier.fillMaxWidth().alpha(if (dimmed) 0.45f else 1f).clip(r(12)).then(if (on) Modifier.background(C.YTintBg) else Modifier)
            .pressClickable(shape = r(12), pressed = PRESSED_CARD, enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .semantics { selected = on; role = Role.RadioButton }.heightIn(min = 52.dp).padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (!titleHidden) Txt(title, 15, if (on) 700 else 500, if (on) C.Yellow else C.Text, maxLines = 2)
            else Txt(title, 15, 700, C.Yellow, maxLines = 1)
            Txt(sub, 12, 400, C.Muted, maxLines = 3)
            if (onRetry != null) Txt(
                stringResource(R.string.translate_try_again), 12, 600, C.Yellow,
                Modifier.pressClickable(role = Role.Button, onClick = onRetry).padding(vertical = 8.dp).testTag(retryTag),
            )
        }
        when (status) {
            is LanguageDownload.Working -> CircularProgressIndicator(Modifier.size(22.dp), color = C.Yellow, trackColor = C.Border, strokeWidth = 2.dp)
            is LanguageDownload.Ready -> Box(Modifier.size(22.dp).background(C.Green, CircleShape), contentAlignment = Alignment.Center) {
                LineIcon(Icons.Check, 13.dp, C.OnYellow, 3f)
            }
            else -> Box(Modifier.size(22.dp).border(2.dp, if (on) C.Yellow else C.RadioOff, CircleShape), contentAlignment = Alignment.Center) {
                if (on) Box(Modifier.size(10.dp).background(C.Yellow, CircleShape))
            }
        }
    }
}
