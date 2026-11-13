package com.sclera.sclerago.presentation.assets

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.sclera.sclerago.R
import com.sclera.sclerago.presentation.common.asString
import com.sclera.sclerago.presentation.components.CheckBox
import com.sclera.sclerago.presentation.components.Pill
import com.sclera.sclerago.presentation.components.r
import com.sclera.sclerago.presentation.components.readableWidth
import com.sclera.sclerago.presentation.components.topBorder
import com.sclera.sclerago.presentation.main.ReviewItem
import com.sclera.sclerago.presentation.theme.C
import com.sclera.sclerago.presentation.theme.Txt

/** Test tags of the Review and onboard screen. */
object ReviewTags {
    const val ADD = "review-add"
    const val DISCARD = "review-discard"
    const val GO = "review-go"
    fun item(id: String) = "review-item-$id"
    fun toggle(id: String) = "review-toggle-$id"
}

/**
 * Review and onboard: the camera shots or files picked, each becoming a draft asset. [items] with
 * `on` set are the ones that get onboarded. Content stays as wide as a list on tablets.
 */
@Composable
fun ReviewScreen(
    sub: String,
    addLabel: String,
    onAdd: () -> Unit,
    items: List<ReviewItem>,
    onToggle: (String) -> Unit,
    onDiscard: () -> Unit,
    onGo: () -> Unit,
    thumb: @Composable (ReviewItem) -> Unit,
) {
    val on = items.count { it.on }
    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "head") {
                Row(Modifier.readableWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Txt(stringResource(R.string.review_title), 18, 700)
                        Txt(sub, 12, 400, C.Muted)
                    }
                    Pill(addLabel, onAdd, Modifier.testTag(ReviewTags.ADD))
                }
            }
            items(items, key = { it.id }) { ri ->
                Row(
                    Modifier.readableWidth().testTag(ReviewTags.item(ri.id)).clip(r(14)).background(C.Surface)
                        .border(1.dp, if (ri.on) C.YTintBd else C.Border, r(14))
                        .padding(start = 10.dp, top = 10.dp, bottom = 10.dp, end = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    thumb(ri)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Txt(ri.name, 15, 600)
                        Txt(ri.sub.asString(), 12, 400, C.Muted)
                        Txt(
                            stringResource(if (ri.ready) R.string.review_ready else R.string.review_needs_input),
                            12, 600, if (ri.ready) C.Green else C.Orange,
                        )
                    }
                    val include = stringResource(R.string.review_include)
                    Box(
                        Modifier.size(48.dp).testTag(ReviewTags.toggle(ri.id)).clip(r(10))
                            .semantics { contentDescription = include }.clickable { onToggle(ri.id) },
                        contentAlignment = Alignment.Center,
                    ) { CheckBox(ri.on) }
                }
            }
        }
        Box(Modifier.fillMaxWidth().background(C.Surface).topBorder(C.Border), contentAlignment = Alignment.Center) {
            Row(
                Modifier.readableWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Pill(stringResource(R.string.action_discard), onDiscard, Modifier.testTag(ReviewTags.DISCARD), height = 48.dp, hPad = 18.dp, size = 14)
                Pill(
                    if (on > 0) pluralStringResource(R.plurals.review_onboard, on, on) else stringResource(R.string.review_select_items),
                    onGo, Modifier.weight(1f).testTag(ReviewTags.GO), bg = if (on > 0) C.Yellow else C.Raised,
                    fg = if (on > 0) C.OnYellow else C.Muted, height = 48.dp, size = 15, weight = 700,
                )
            }
        }
    }
}
