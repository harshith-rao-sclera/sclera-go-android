package com.sclera.sclerago.presentation.integration

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import com.sclera.sclerago.domain.model.ManagedCounts
import com.sclera.sclerago.presentation.components.BottomSheet
import com.sclera.sclerago.presentation.components.SheetHeader
import com.sclera.sclerago.presentation.components.r
import com.sclera.sclerago.presentation.theme.C
import com.sclera.sclerago.presentation.theme.Txt

/** Managed Assets Summary, as the web's info popup: moved to Managed Assets, and returned from it. */
@Composable
fun ManagedSummarySheet(counts: ManagedCounts, onClose: () -> Unit) {
    BottomSheet(onClose) {
        SheetHeader(stringResource(R.string.ms_title), null, onClose)
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 20.dp).testTag("managed_summary"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            CountRow(stringResource(R.string.ms_moved), stringResource(R.string.ms_moved_sub), counts.moved)
            CountRow(stringResource(R.string.ms_returned), stringResource(R.string.ms_returned_sub), counts.returned)
        }
    }
}

@Composable
private fun CountRow(heading: String, sub: String, count: Int) {
    Row(
        Modifier.fillMaxWidth().clip(r(12)).background(C.Bg).border(1.dp, C.Border, r(12)).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Txt(heading, 14, 700, maxLines = 2)
            Txt(sub, 12, 400, C.Muted, maxLines = 4)
        }
        Txt(count.toString(), 20, 800, C.Yellow)
    }
}
