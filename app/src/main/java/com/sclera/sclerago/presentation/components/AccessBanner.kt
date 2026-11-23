package com.sclera.sclerago.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.sclera.sclerago.R
import com.sclera.sclerago.presentation.icons.Icons
import com.sclera.sclerago.presentation.icons.LineIcon
import com.sclera.sclerago.presentation.theme.C
import com.sclera.sclerago.presentation.theme.Txt

/**
 * The orange banner shown while the signed-in user's access couldn't be loaded (and none was kept on the device): pages
 * and changes that need a permission stay unavailable until it is, and [onRetry] fetches it again.
 */
@Composable
fun AccessFailedBanner(onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().clip(r(10)).background(C.WarnRow).border(1.dp, C.Orange.copy(alpha = 0.35f), r(10))
            .padding(start = 12.dp, end = 6.dp, top = 6.dp, bottom = 6.dp).testTag(AccessBannerTags.FAILED),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LineIcon(Icons.Alert, 18.dp, C.Orange, 2.2f)
        Txt(stringResource(R.string.menu_access_failed), 12, 500, C.Soft, Modifier.weight(1f).padding(vertical = 4.dp), maxLines = Int.MAX_VALUE)
        Pill(
            stringResource(R.string.menu_access_retry), onRetry, Modifier.testTag(AccessBannerTags.RETRY),
            bg = C.Orange.copy(alpha = 0.18f), fg = C.Orange, height = 48.dp, hPad = 16.dp, weight = 700,
        )
    }
}

/** Test tags of the access banner. */
object AccessBannerTags {
    const val FAILED = "access-failed-banner"
    const val RETRY = "access-failed-retry"
}
