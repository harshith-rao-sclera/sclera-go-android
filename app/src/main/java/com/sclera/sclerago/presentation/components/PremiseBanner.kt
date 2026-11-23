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
import com.sclera.sclerago.domain.model.PremiseNotice
import com.sclera.sclerago.presentation.icons.Icons
import com.sclera.sclerago.presentation.icons.LineIcon
import com.sclera.sclerago.presentation.theme.C
import com.sclera.sclerago.presentation.theme.Txt

/**
 * The orange banner shown to a collector who is view only because of the property's geofence: outside its fences, the
 * location permission missing, or the location unknown. [onAllow] (the permission case only) asks for the permission.
 */
@Composable
fun PremiseBanner(notice: PremiseNotice, onAllow: () -> Unit, modifier: Modifier = Modifier) {
    val message = when (notice) {
        PremiseNotice.NONE -> return
        PremiseNotice.OFF_PREMISE -> R.string.premise_banner_off
        PremiseNotice.LOCATION_NEEDED -> R.string.premise_banner_location_needed
        PremiseNotice.LOCATION_UNKNOWN -> R.string.premise_banner_location_unknown
    }
    Row(
        modifier.fillMaxWidth().clip(r(10)).background(C.WarnRow).border(1.dp, C.Orange.copy(alpha = 0.35f), r(10))
            .padding(start = 12.dp, end = 6.dp, top = 6.dp, bottom = 6.dp).testTag(PremiseTags.BANNER),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LineIcon(Icons.Fence, 18.dp, C.Orange, 2.2f)
        Txt(stringResource(message), 12, 500, C.Soft, Modifier.weight(1f).padding(vertical = 4.dp), maxLines = Int.MAX_VALUE)
        if (notice == PremiseNotice.LOCATION_NEEDED) {
            Pill(
                stringResource(R.string.scrt_perm_allow), onAllow, Modifier.testTag(PremiseTags.ALLOW),
                bg = C.Orange.copy(alpha = 0.18f), fg = C.Orange, height = 48.dp, hPad = 16.dp, weight = 700,
            )
        }
    }
}

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

/** Test tags of the geofence banner and location sheet. */
object PremiseTags {
    const val BANNER = "premise-banner"
    const val ALLOW = "premise-allow"
    const val SHEET = "premise-sheet"
    const val CONTINUE = "premise-continue"
}
