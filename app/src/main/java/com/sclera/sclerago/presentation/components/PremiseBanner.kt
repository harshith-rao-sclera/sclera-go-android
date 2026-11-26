package com.sclera.sclerago.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sclera.sclerago.R
import com.sclera.sclerago.presentation.icons.Icons
import com.sclera.sclerago.presentation.icons.LineIcon
import com.sclera.sclerago.presentation.theme.C
import com.sclera.sclerago.presentation.theme.Txt

/** Why the location is wanted, just before Android's own prompt (the geofence check; nothing is kept or sent). */
@Composable
fun LocationPermissionSheet(onContinue: () -> Unit, onNotNow: () -> Unit) {
    BottomSheet(onNotNow) {
        Column(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 16.dp).testTag(PremiseTags.SHEET),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier.size(64.dp).clip(r(18)).background(C.YTintBg).border(1.dp, C.YTintBd, r(18)),
                contentAlignment = Alignment.Center,
            ) { LineIcon(Icons.Fence, 32.dp, C.Yellow, 2f) }
            Spacer(Modifier.height(14.dp))
            Txt(stringResource(R.string.premise_perm_title), 17, 700, maxLines = 2, align = TextAlign.Center)
            Spacer(Modifier.height(6.dp))
            Txt(stringResource(R.string.premise_perm_body), 13, 400, C.Muted, maxLines = Int.MAX_VALUE, align = TextAlign.Center)
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Pill(stringResource(R.string.scrt_perm_not_now), onNotNow, Modifier.weight(1f), height = 48.dp, size = 14)
                Pill(
                    stringResource(R.string.scrt_perm_allow), onContinue, Modifier.weight(1.4f).testTag(PremiseTags.CONTINUE),
                    bg = C.Yellow, fg = C.OnYellow, height = 48.dp, size = 14, weight = 700,
                )
            }
        }
    }
}

/** Test tags of the location sheet. */
object PremiseTags {
    const val SHEET = "premise-sheet"
    const val CONTINUE = "premise-continue"
}
