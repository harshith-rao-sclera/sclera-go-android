package com.sclera.sclerago.presentation.translation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.sclera.sclerago.R
import com.sclera.sclerago.domain.model.TranslationResult
import com.sclera.sclerago.presentation.components.pressClickable
import com.sclera.sclerago.presentation.components.r
import com.sclera.sclerago.presentation.icons.Icons
import com.sclera.sclerago.presentation.icons.LineIcon
import com.sclera.sclerago.presentation.theme.C
import com.sclera.sclerago.presentation.theme.Txt

object TranslatedCommentTags {
    const val TRANSLATED = "comment_translated"
    const val TRANSLATING = "comment_translating"
    const val FAILED = "comment_failed"
}

/**
 * A comment's text, selectable, with its translation under it: "Translating…" while it runs, a tinted box
 * "Translated to <language>" when it is done, and "Couldn't translate this · Try again" when it failed. With no language
 * chosen, in that language already, or the language not on the phone, only the original shows.
 */
@Composable
fun TranslatedComment(text: String, modifier: Modifier = Modifier) {
    val translation = LocalTextTranslation.current
    val target = translation.target
    var attempt by remember { mutableIntStateOf(0) }
    var result by remember(text, target) { mutableStateOf<TranslationResult?>(null) }
    var running by remember(text, target) { mutableStateOf(false) }
    LaunchedEffect(text, target, attempt) {
        if (target == null || text.isBlank()) return@LaunchedEffect
        running = true
        result = translation.translate(text, target)
        running = false
    }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        androidx.compose.foundation.text.selection.SelectionContainer { Txt(text, 14, 400, maxLines = Int.MAX_VALUE) }
        when (val r = result) {
            is TranslationResult.Done -> Column(
                Modifier.fillMaxWidth().background(C.YTintBg, r(10)).border(1.dp, C.YTintBd, r(10)).padding(10.dp).testTag(TranslatedCommentTags.TRANSLATED),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Txt(stringResource(R.string.translate_comment_label, languageName(target.orEmpty())), 11, 600, C.Yellow, maxLines = 2)
                androidx.compose.foundation.text.selection.SelectionContainer { Txt(r.text, 14, 400, maxLines = Int.MAX_VALUE) }
            }
            TranslationResult.Failed -> Row(
                Modifier.testTag(TranslatedCommentTags.FAILED), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                LineIcon(Icons.Warning, 14.dp, C.Orange, 2.2f)
                Txt(stringResource(R.string.translate_comment_failed), 12, 400, C.Soft, Modifier.weight(1f, fill = false), maxLines = 2)
                Txt(
                    stringResource(R.string.translate_try_again), 12, 600, C.Yellow,
                    Modifier.pressClickable(role = Role.Button) { result = null; attempt++ }.padding(vertical = 8.dp),
                )
            }
            else -> if (running) Row(
                Modifier.testTag(TranslatedCommentTags.TRANSLATING), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CircularProgressIndicator(Modifier.size(14.dp), color = C.Yellow, trackColor = C.Border, strokeWidth = 2.dp)
                Txt(stringResource(R.string.translate_comment_translating), 12, 400, C.Soft)
            }
        }
    }
}
