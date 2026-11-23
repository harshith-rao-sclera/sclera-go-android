package com.sclera.sclerago.presentation.assets

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.sclera.sclerago.R
import com.sclera.sclerago.presentation.components.Capsule
import com.sclera.sclerago.presentation.components.RoundButton
import com.sclera.sclerago.presentation.components.SkeletonBlock
import com.sclera.sclerago.presentation.components.SkeletonLine
import com.sclera.sclerago.presentation.components.bottomBorder
import com.sclera.sclerago.presentation.components.pageGutter
import com.sclera.sclerago.presentation.components.r
import com.sclera.sclerago.presentation.icons.Icons
import com.sclera.sclerago.presentation.icons.LineIcon
import com.sclera.sclerago.presentation.theme.C

/** How many field rows the placeholder form shows. */
private const val FIELD_ROWS = 7

/** Each placeholder field's value width (part of the row), varied so the rows don't look stamped out. */
private val ValueWidths = listOf(0.6f, 0.45f, 0.7f, 0.5f, 0.38f, 0.64f, 0.55f)

/**
 * Asset details while the asset is being read from the property's data: the page's shape (top bar with a working
 * back button, the tabs, the tags and photos, the fields) in shimmering placeholders, read out as "Loading asset…".
 * Back leaves as it would from the asset.
 */
@Composable
internal fun AssetDetailSkeleton(onBack: () -> Unit, modifier: Modifier = Modifier) {
    BackHandler(onBack = onBack)
    val label = stringResource(R.string.scrt_asset_loading)
    val backLabel = stringResource(R.string.detail_back)
    Column(modifier.fillMaxSize().background(C.Bg)) {
        // The top bar: back works straight away; the name is still coming.
        Row(
            Modifier.fillMaxWidth().height(56.dp).bottomBorder(C.Border).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            RoundButton(48.dp, Color.Transparent, onBack, Modifier.semantics { contentDescription = backLabel }) {
                LineIcon(Icons.ChevronLeft, 19.dp, C.Yellow, 3f)
            }
            SkeletonLine(0.55f, 16.dp, Modifier.weight(1f).padding(end = 12.dp))
        }
        Column(Modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = label }) {
            // The tabs.
            Row(Modifier.fillMaxWidth().height(44.dp).background(C.Strip).bottomBorder(C.Border)) {
                repeat(4) {
                    Box(Modifier.weight(1f).height(44.dp), contentAlignment = Alignment.Center) {
                        SkeletonBlock(Modifier.width(46.dp).height(10.dp))
                    }
                }
            }
            Column(
                Modifier.fillMaxWidth().padding(horizontal = pageGutter()).padding(horizontal = 12.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                // The tags bar and the photos.
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    repeat(3) { SkeletonBlock(Modifier.width(76.dp).height(28.dp), Capsule) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    repeat(4) { SkeletonBlock(Modifier.size(72.dp), r(10)) }
                }
                // The fields: a label over a value, one per row, in the form's card.
                Column(
                    Modifier.fillMaxWidth().clip(r(14)).background(C.Surface).border(1.dp, C.Border, r(14)).padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    repeat(FIELD_ROWS) { i ->
                        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            SkeletonLine(0.28f, 8.dp)
                            SkeletonLine(ValueWidths[i % ValueWidths.size], 12.dp)
                        }
                    }
                }
            }
        }
    }
}
