package com.sclera.sclerago.presentation.validation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.sclera.sclerago.R
import com.sclera.sclerago.presentation.components.Capsule
import com.sclera.sclerago.presentation.components.SkeletonBlock
import com.sclera.sclerago.presentation.components.SkeletonLine
import com.sclera.sclerago.presentation.components.SkeletonList
import com.sclera.sclerago.presentation.components.pageGutter
import com.sclera.sclerago.presentation.components.r
import com.sclera.sclerago.presentation.theme.C

/**
 * A run's review while the run is being read: its shape in shimmering placeholders (the name and status, what it
 * covered, the actions, the summary card with its four counts, the steps and the first assets), read out as
 * "Loading…".
 */
@Composable
internal fun ReviewSkeleton(modifier: Modifier = Modifier) {
    val label = stringResource(R.string.val_loading)
    Column(
        modifier.fillMaxWidth().padding(horizontal = pageGutter()).semantics(mergeDescendants = true) { contentDescription = label },
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // The name with its status chip, and the line under it.
        Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SkeletonLine(0.55f, 20.dp, Modifier.weight(1f, fill = false))
                SkeletonBlock(Modifier.width(70.dp).height(22.dp), Capsule)
            }
            SkeletonLine(0.8f, 9.dp)
        }
        // The actions.
        Row(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SkeletonBlock(Modifier.width(120.dp).height(34.dp), Capsule)
            SkeletonBlock(Modifier.width(96.dp).height(34.dp), Capsule)
        }
        // The summary card: quality and steps, how many were checked, the bar and the four counts.
        Column(
            Modifier.padding(horizontal = 12.dp).fillMaxWidth().clip(r(14)).background(C.Surface).border(1.dp, C.Border, r(14)).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SkeletonBlock(Modifier.width(90.dp).height(22.dp), r(8))
                Spacer(Modifier.weight(1f))
                SkeletonLine(0.25f, 10.dp)
            }
            SkeletonLine(0.5f, 18.dp)
            SkeletonLine(0.35f, 9.dp)
            SkeletonBlock(Modifier.fillMaxWidth().height(8.dp), Capsule)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(4) { SkeletonBlock(Modifier.weight(1f).height(48.dp), r(10)) }
            }
        }
        // The steps.
        Row(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(3) { SkeletonBlock(Modifier.width(92.dp).height(30.dp), Capsule) }
        }
        // The first assets.
        SkeletonList(label, Modifier.padding(horizontal = 12.dp), count = 4, trailing = true)
    }
}
