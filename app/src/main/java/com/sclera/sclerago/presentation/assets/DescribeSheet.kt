package com.sclera.sclerago.presentation.assets

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.sclera.sclerago.R
import com.sclera.sclerago.presentation.components.BottomSheet
import com.sclera.sclerago.presentation.components.GuardedClose
import com.sclera.sclerago.presentation.components.Pill
import com.sclera.sclerago.presentation.components.PlainField
import com.sclera.sclerago.presentation.components.SheetHeader
import com.sclera.sclerago.presentation.components.r
import com.sclera.sclerago.presentation.theme.C
import com.sclera.sclerago.presentation.theme.Txt

/** What the describe sheet does: its text changes, Fill form, close. */
class DescribeActions(
    val onText: (String) -> Unit = {},
    val onFill: () -> Unit = {},
    val onClose: () -> Unit = {},
)

/** Test tags of the describe sheet. */
object DescribeTags {
    const val SHEET = "describe-sheet"
    const val TEXT = "describe-text"
    const val FILL = "describe-fill"
}

/**
 * Describe the asset by typing (the web's "Describe the asset"), for when speaking won't do: what is typed goes to the
 * same AI fill as the voice button, and the voice fill sheet then shows the fields it found to put into the form. Opened
 * from a long press on the mic, or with "Edit text" in the voice fill sheet to correct what was heard.
 */
@Composable
fun DescribeSheet(text: String, reading: Boolean, onText: (String) -> Unit, onFill: () -> Unit, onClose: () -> Unit) {
    GuardedClose(text.isNotBlank(), onClose) { close ->
        BottomSheet(onDismiss = close) {
            Column(Modifier.testTag(DescribeTags.SHEET)) {
                SheetHeader(stringResource(R.string.describe_title), stringResource(R.string.describe_sub), close)
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    PlainField(
                        text, onText, stringResource(R.string.describe_hint),
                        Modifier.fillMaxWidth().heightIn(min = 120.dp).clip(r(12)).background(C.Bg).border(1.dp, C.Border, r(12))
                            .padding(12.dp).testTag(DescribeTags.TEXT),
                        size = 14, singleLine = false, minLines = 4,
                    )
                    Txt(stringResource(R.string.describe_count, text.length, DESCRIBE_MAX), 11, 400, C.Muted, Modifier.align(Alignment.End))
                }
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 12.dp, bottom = 14.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val ok = text.isNotBlank() && !reading
                    Pill(stringResource(R.string.action_cancel), close, Modifier.weight(1f), height = 46.dp, size = 13, weight = 700)
                    Pill(
                        stringResource(if (reading) R.string.describe_filling else R.string.describe_fill), { if (ok) onFill() },
                        Modifier.weight(1f).testTag(DescribeTags.FILL).alpha(if (ok) 1f else 0.45f),
                        bg = C.Yellow, fg = C.OnYellow, height = 46.dp, size = 13, weight = 700,
                    )
                }
            }
        }
    }
}

/** The most a typed description may hold (the web's). */
const val DESCRIBE_MAX = 4000
