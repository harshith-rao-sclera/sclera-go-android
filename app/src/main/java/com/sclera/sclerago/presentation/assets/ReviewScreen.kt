package com.sclera.sclerago.presentation.assets

import com.sclera.sclerago.presentation.components.pressClickable
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.sclera.sclerago.R
import com.sclera.sclerago.presentation.common.UiText
import com.sclera.sclerago.presentation.components.AccessGate
import com.sclera.sclerago.presentation.components.OfflineBanner
import com.sclera.sclerago.presentation.components.OfflineGate
import com.sclera.sclerago.presentation.model.OnlineOnly
import com.sclera.sclerago.presentation.common.asString
import com.sclera.sclerago.presentation.components.CheckBox
import com.sclera.sclerago.presentation.components.IconMenu
import com.sclera.sclerago.presentation.components.MenuAct
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
 * Review and onboard: the camera shots taken, or the files picked and the assets read from them. [items] with
 * `on` set are the ones that get onboarded. Content stays as wide as a list on tablets.
 * [onAdd] null hides the add button; [goLabel] replaces the onboard button's text; while [busy] it does nothing;
 * [footer] shows above the buttons; with [onOpen], tapping an item's text opens it (by id). Holding an item (its
 * picture, which [thumb] is told how, or its text) lists what can be done with it ([menu]).
 */
@Composable
internal fun ReviewScreen(
    sub: String,
    addLabel: String,
    onAdd: (() -> Unit)?,
    items: List<ReviewItem>,
    onToggle: (String) -> Unit,
    onDiscard: () -> Unit,
    onGo: () -> Unit,
    goLabel: String? = null,
    busy: Boolean = false,
    footer: (@Composable () -> Unit)? = null,
    onOpen: ((String) -> Unit)? = null,
    /** Offline, for a review the server does: [offlineBanner] says why at the top of the list. */
    offlineBanner: OnlineOnly? = null,
    /** Offline, when the go button needs the server: it is dimmed with a badge and a tap says this why ([onOffline]). */
    offlineGo: OnlineOnly? = null,
    /**
     * The signed-in user may not add assets (Collect): the go button, Discard and the add button are greyed out and a tap
     * says this why instead (it comes before [offlineGo]).
     */
    goBlock: UiText? = null,
    onOffline: (OnlineOnly) -> Unit = {},
    /** What holding an item lists; none (the default) and holding does nothing more than a tap. */
    menu: @Composable (ReviewItem) -> List<MenuAct> = { emptyList() },
    /** An item's picture; the second argument opens its menu (for the picture's own hold). */
    thumb: @Composable (ReviewItem, onHold: () -> Unit) -> Unit,
) {
    val actionsLabel = stringResource(R.string.media_actions)
    val on = items.count { it.on }
    val canGo = on > 0 && !busy
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
                        Txt(stringResource(R.string.review_title), 16, 700)
                        Txt(sub, 11, 400, C.Muted, maxLines = Int.MAX_VALUE)
                    }
                    if (onAdd != null) AccessGate(goBlock) { Pill(addLabel, onAdd, Modifier.testTag(ReviewTags.ADD)) }
                }
            }
            if (offlineBanner != null) item(key = "offline") { OfflineBanner(offlineBanner, Modifier.readableWidth()) }
            items(items, key = { it.id }) { ri ->
                val acts = menu(ri)
                var menuOpen by remember(ri.id) { mutableStateOf(false) }
                val hold = if (acts.isEmpty()) null else ({ menuOpen = true })
                Row(
                    Modifier.readableWidth().testTag(ReviewTags.item(ri.id)).clip(r(14)).background(C.Surface)
                        .border(1.dp, if (ri.on) C.YTintBd else C.Border, r(14))
                        .padding(start = 10.dp, top = 10.dp, bottom = 10.dp, end = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    thumb(ri) { hold?.invoke() }
                    Column(
                        Modifier.weight(1f).then(
                            if (onOpen != null || hold != null) {
                                Modifier.pressClickable(shape = r(8), onLongClick = hold, onLongClickLabel = actionsLabel) { onOpen?.invoke(ri.id) }
                            } else Modifier,
                        ),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Txt(ri.name, 14, 600)
                        Txt(ri.sub.asString(), 11, 400, C.Muted, maxLines = Int.MAX_VALUE)
                        if (ri.note != null) {
                            Txt(ri.note.asString(), 11, 600, ri.noteColor.takeOrElse { C.Muted }, maxLines = Int.MAX_VALUE)
                        } else {
                            Txt(
                                stringResource(if (ri.ready) R.string.review_ready else R.string.review_needs_input),
                                12, 600, if (ri.ready) C.Green else C.Orange,
                            )
                        }
                    }
                    IconMenu(menuOpen, { menuOpen = false }, acts)
                    val include = stringResource(R.string.review_include)
                    Box(
                        Modifier.size(48.dp).testTag(ReviewTags.toggle(ri.id)).pressClickable(shape = r(10)) { onToggle(ri.id) }
                            .semantics { contentDescription = include },
                        contentAlignment = Alignment.Center,
                    ) { CheckBox(ri.on) }
                }
            }
        }
        Box(Modifier.fillMaxWidth().background(C.Surface).topBorder(C.Border), contentAlignment = Alignment.Center) {
            Column(Modifier.readableWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                footer?.invoke()
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    AccessGate(goBlock) {
                        Pill(stringResource(R.string.action_discard), onDiscard, Modifier.testTag(ReviewTags.DISCARD), height = 48.dp, hPad = 18.dp, size = 14)
                    }
                    // Not this user's to add (going online wouldn't change that): greyed out, saying why.
                    AccessGate(goBlock, Modifier.weight(1f)) {
                        OfflineGate(offlineGo != null && goBlock == null, { offlineGo?.let(onOffline) }) {
                            Pill(
                                goLabel ?: if (on > 0) pluralStringResource(R.plurals.review_onboard, on, on) else stringResource(R.string.review_select_items),
                                { if (!busy) onGo() }, Modifier.fillMaxWidth().testTag(ReviewTags.GO), bg = if (canGo) C.Yellow else C.Raised,
                                fg = if (canGo) C.OnYellow else C.Muted, height = 48.dp, size = 14, weight = 700,
                            )
                        }
                    }
                }
            }
        }
    }
}
