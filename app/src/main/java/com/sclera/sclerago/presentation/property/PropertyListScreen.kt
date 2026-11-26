package com.sclera.sclerago.presentation.property

import com.sclera.sclerago.presentation.components.pressClickable
import com.sclera.sclerago.presentation.components.PRESSED_CARD
import com.sclera.sclerago.presentation.model.OnlineOnly
import com.sclera.sclerago.presentation.components.SkeletonList
import com.sclera.sclerago.presentation.components.AccessFailedBanner
import com.sclera.sclerago.presentation.components.AccessGate
import com.sclera.sclerago.presentation.components.LocalToaster
import com.sclera.sclerago.presentation.components.ToastKind
import com.sclera.sclerago.presentation.common.UiText
import com.sclera.sclerago.presentation.model.reason
import com.sclera.sclerago.presentation.components.OfflineGate
import java.util.Locale
import java.text.NumberFormat
import com.sclera.sclerago.domain.model.PropertyDownload
import com.sclera.sclerago.domain.model.Property
import com.sclera.sclerago.domain.model.inProgress
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.height
import androidx.compose.ui.semantics.stateDescription
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sclera.sclerago.R
import com.sclera.sclerago.presentation.common.asString
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.unit.IntOffset
import kotlinx.coroutines.delay
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.sclera.sclerago.presentation.components.FabAction
import com.sclera.sclerago.presentation.components.ConfirmDialog
import com.sclera.sclerago.presentation.components.FabMenu
import com.sclera.sclerago.presentation.components.FabGap
import com.sclera.sclerago.presentation.components.FabMargin
import com.sclera.sclerago.presentation.components.fabClearance
import com.sclera.sclerago.presentation.components.noRippleClick
import com.sclera.sclerago.presentation.components.PullRefresh
import com.sclera.sclerago.presentation.components.SearchFab
import com.sclera.sclerago.presentation.components.StatCard
import com.sclera.sclerago.presentation.components.StatRow
import com.sclera.sclerago.presentation.components.SubPageBar
import com.sclera.sclerago.presentation.components.pageGutter
import com.sclera.sclerago.presentation.components.r
import com.sclera.sclerago.presentation.icons.Icons
import com.sclera.sclerago.presentation.icons.LineIcon
import com.sclera.sclerago.presentation.main.AppViewModel
import com.sclera.sclerago.presentation.theme.C
import com.sclera.sclerago.presentation.theme.Txt

/** Test tags of the Property list. */
object PropertyTags {
    const val LIST = "property_list"
    const val ADD = "property_add"
    const val IMPORT = "property_import"
    const val FAB = "property_fab"
    const val SYNCED = "property_synced"
    const val EMPTY = "property_empty"
    const val LOADING = "property_loading"
    const val COUNT = "property_count"
    fun row(id: String) = "property_row_$id"
    fun info(id: String) = "property_info_$id"
    fun download(id: String) = "property_download_$id"
    fun progress(id: String) = "property_progress_$id"
    fun done(id: String) = "property_done_$id"
    fun unpacking(id: String) = "property_unpacking_$id"
}

/** Property list (side menu): counts, search, open a property (the synced ones first), add or import some. */
@Composable
fun PropertyListScreen(
    app: AppViewModel,
    vm: PropertyViewModel = hiltViewModel(),
    importer: PropertyImportViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    // Why Add, Import, Save and Delete are greyed out (no Manage properties, or only chosen properties); null: usable.
    val manageBlock = app.blocks.manageProperties.reason()
    val suggestions by vm.suggestions.collectAsStateWithLifecycle()
    // Until a property is downloaded this is the app's first page, so Back leaves the app.
    BackHandler(enabled = app.hasProperty, onBack = app::closeAdmin)
    DisposableEffect(Unit) { onDispose(vm::reset) }
    // What Import did (or why it couldn't), flashed like the page's own messages.
    LaunchedEffect(importer) { importer.notices.collect { app.flash(it) } }
    // Import opens empty each time, as the web's.
    LaunchedEffect(state.importOpen) { if (state.importOpen) importer.reset() }
    // A property tapped before it was downloaded opens as soon as its download finishes.
    LaunchedEffect(state.openNow) {
        state.openNow?.let { id ->
            vm.openedAfterDownload()
            app.openPropertyFromList(id)
        }
    }
    val notice = state.notice?.asString()
    LaunchedEffect(notice) {
        if (notice != null) {
            app.flash(notice)
            vm.consumeNotice()
        }
    }
    Box(Modifier.fillMaxSize()) {
        PropertyListContent(
            state, app.currentProp,
            onBack = app::openMenu, onQuery = vm::setQuery, onOpen = app::openPropertyFromList,
            onAdd = vm::openAdd, onImport = vm::openImport, onDownload = vm::download, onDownloadAndOpen = vm::downloadAndOpen,
            onRefresh = vm::refresh,
            manageBlock = manageBlock, onInfo = vm::openEdit, onOffline = vm::explainOffline,
            accessFailed = app.accessFailed, onRetryAccess = app::retryAccess,
            openBlock = app.openPropertyBlock.reason(),
        )
        if (state.importOpen) ImportSheet(importer, vm::closeImport, onImported = vm::closeImport, importReason = manageBlock)
        if (state.addOpen) {
            AddPropertySheet(
                vm::closeAdd, vm::addProperty, state.addError,
                suggestions, vm::suggestAddress, vm::clearSuggestions,
            )
        }
        // The info button on a property: its details straight in the edit form, with Delete before Cancel. View only
        // offline, and for a group without Manage properties (the server refuses their changes and deletes).
        // Its details still being read from the server: the sheet in placeholders, until they come.
        if (state.editing == null) state.openingEdit?.let { p -> PropertyFormSkeleton(p.name, vm::cancelOpenEdit) }
        state.editing?.let { p ->
            key(p.id) {
                AddPropertySheet(
                    vm::closeEdit, vm::saveEdit, state.editError,
                    suggestions, vm::suggestAddress, vm::clearSuggestions, initial = p,
                    onDelete = vm::askDelete,
                    // Offline it shows the copy saved on the device, to look at only.
                    readOnly = !state.online || manageBlock != null,
                    readOnlyReason = if (manageBlock == null) R.string.property_offline_edit else R.string.property_view_only,
                    accessBlock = manageBlock,
                )
            }
        }
        state.confirmDelete?.let { p ->
            BackHandler(onBack = vm::cancelDelete)
            ConfirmDialog(
                Icons.Trash, C.DangerBg, C.Red,
                stringResource(R.string.property_delete_title), stringResource(R.string.property_delete_body, p.name),
                stringResource(R.string.action_cancel), stringResource(R.string.action_delete),
                vm::cancelDelete, vm::deleteProperty,
            )
        }
    }
}

/** The page itself, stateless: [state] in, taps out. */
@Composable
internal fun PropertyListContent(
    state: PropertyListState,
    currentId: String,
    onBack: () -> Unit,
    onQuery: (String) -> Unit,
    onOpen: (String) -> Unit,
    onAdd: () -> Unit,
    onImport: () -> Unit,
    onDownload: (String) -> Unit = {},
    /** A tap on a property that isn't downloaded: downloads it, then opens it. */
    onDownloadAndOpen: (String) -> Unit = onDownload,
    onRefresh: () -> Unit = {},
    /** Why Add and Import are greyed out (the web's "+ Property" and Import need Manage properties); null: usable. */
    manageBlock: UiText? = null,
    /** The info button on a property: opens it in the edit form. */
    onInfo: (Property) -> Unit = {},
    /** An online-only button was tapped offline (download, New property, Import): say why it needs the server. */
    onOffline: (OnlineOnly) -> Unit = {},
    /** The user's access couldn't be loaded: a banner says so, with Retry (properties needing a permission stay hidden). */
    accessFailed: Boolean = false,
    onRetryAccess: () -> Unit = {},
    /** Why a property can't be opened or downloaded (a group that can't look inside one); null: it can. */
    openBlock: UiText? = null,
) {
    val gutter = pageGutter()
    var menuOpen by rememberSaveable { mutableStateOf(false) }
    val listState = rememberLazyListState()
    FollowFinishedDownload(state, listState)
    Box(Modifier.fillMaxSize().background(C.Bg)) {
        Column(Modifier.fillMaxSize()) {
            // Just "Properties": the page lists every property, so no current property under the title.
            SubPageBar(stringResource(R.string.label_properties), property = null, onMenu = onBack)
            if (accessFailed) AccessFailedBanner(onRetryAccess, Modifier.padding(horizontal = gutter + 10.dp).padding(top = 10.dp))
            // The first read shows its own loader, so a pull waits until it is done.
            PullRefresh(state.refreshing, onRefresh, Modifier.weight(1f).fillMaxWidth(), enabled = !state.loading) {
                LazyColumn(
                    Modifier.fillMaxSize().testTag(PropertyTags.LIST),
                    state = listState,
                    // Room for the + menu and search buttons, so the last property scrolls clear of them.
                    contentPadding = PaddingValues(start = gutter + 8.dp, end = gutter + 8.dp, top = 10.dp, bottom = fabClearance(2, navigationBars = true)),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    item { Header(state) }
                    items(state.rows, key = { it.property.id }) { row ->
                        val id = row.property.id
                        val download = state.downloads.active[id]
                        val downloaded = id in state.downloads.downloadedIds
                        PropertyCard(
                            // A property that finishes downloading slides up into the downloaded ones (listed first).
                            modifier = Modifier.animateItem(fadeInSpec = RowFade, placementSpec = RowMove, fadeOutSpec = RowFade),
                            id = id, name = row.property.name, current = id == currentId,
                            download = download, downloaded = downloaded, online = state.online,
                            // A downloaded property opens (offline too); another one is downloaded first, then opens.
                            onClick = {
                                when {
                                    downloaded -> onOpen(id)
                                    !download.inProgress -> onDownloadAndOpen(id)
                                }
                            },
                            onDownload = { onDownload(id) },
                            onInfo = { onInfo(row.property) },
                            onOffline = { onOffline(OnlineOnly.DOWNLOAD_PROPERTY) },
                            openBlock = openBlock,
                        )
                    }
                    when {
                        // First read from the server with nothing saved yet: a loader, not an empty list that fills suddenly.
                        state.loading -> item {
                            SkeletonList(stringResource(R.string.property_loading), Modifier.testTag(PropertyTags.LOADING), count = 4, lines = 3)
                        }
                        state.rows.isEmpty() -> item {
                            val text = if (state.query.isBlank()) stringResource(R.string.property_none)
                            else stringResource(R.string.property_none_match, state.query.trim())
                            Txt(
                                text, 13, 400, C.Muted,
                                Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 30.dp).testTag(PropertyTags.EMPTY),
                                maxLines = Int.MAX_VALUE, align = TextAlign.Center,
                            )
                        }
                    }
                }
            }
        }
        // The page dims behind the open menu; a tap on it closes the menu.
        if (menuOpen) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)).noRippleClick { menuOpen = false })
        Column(
            Modifier.align(Alignment.BottomEnd).navigationBarsPadding().imePadding().padding(end = FabMargin, bottom = FabMargin),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(FabGap),
        ) {
            FabMenu(
                menuOpen, { menuOpen = it },
                listOf(
                    FabAction(
                        stringResource(R.string.property_fab_import_title), Icons.Table, PropertyTags.IMPORT, onImport,
                        onBlocked = if (state.online) null else ({ onOffline(OnlineOnly.IMPORT_PROPERTIES) }),
                        subtitle = stringResource(R.string.property_fab_import_sub), iconColor = C.Green, accessBlock = manageBlock,
                    ),
                    FabAction(
                        stringResource(R.string.property_fab_new_title), Icons.Building, PropertyTags.ADD, onAdd,
                        onBlocked = if (state.online) null else ({ onOffline(OnlineOnly.NEW_PROPERTY) }),
                        subtitle = stringResource(R.string.property_fab_new_sub), iconColor = C.Text, accessBlock = manageBlock,
                    ),
                ),
                stringResource(R.string.property_add_fab), stringResource(R.string.action_close),
                Modifier.testTag(PropertyTags.FAB),
            )
            SearchFab(
                state.query, onQuery, stringResource(R.string.property_list_search_hint),
                stringResource(R.string.action_search), stringResource(R.string.action_close_search),
            )
        }
    }
}

/** The Properties and Synced counts (the app bar already names the page). */
@Composable
private fun Header(state: PropertyListState) {
    StatRow(Modifier.padding(bottom = 10.dp)) {
        // No counts until the properties are known (placeholders), rather than a 0 that jumps.
        StatCard(
            Icons.Building, C.Yellow, state.propertyTotal.toString(),
            stringResource(R.string.label_properties),
            Modifier.weight(1f).testTag(PropertyTags.COUNT), valueSize = 24, labelSize = 12, prominent = true, loading = state.loading,
        )
        StatCard(
            Icons.Download, C.Green, state.syncedTotal.toString(),
            stringResource(R.string.property_synced),
            Modifier.weight(1f).testTag(PropertyTags.SYNCED), valueSize = 24, labelSize = 12, prominent = true, loading = state.loading,
        )
    }
}

/**
 * A property: round building icon and name, then where its offline data is: a download button, the download's
 * progress (percent, MB and a yellow edge along the bottom), a tick when it has just finished, or the chevron once
 * it is downloaded. Offline, a property that isn't downloaded is faded and can't be opened: its download button carries
 * the offline badge, and a tap on it or the card says why ([onOffline]). The current property
 * is washed with yellow from its start side (fading out across the card), with a faint yellow border and a yellow building, and is
 * read out as the current property.
 */
@Composable
private fun PropertyCard(
    id: String,
    name: String,
    current: Boolean,
    modifier: Modifier = Modifier,
    download: PropertyDownload?,
    downloaded: Boolean,
    online: Boolean,
    onClick: () -> Unit,
    onDownload: () -> Unit,
    onInfo: () -> Unit = {},
    onOffline: () -> Unit = {},
    /** Why the property can't be opened or downloaded (the user's group can't look inside one): dimmed, a tap says why. */
    openBlock: UiText? = null,
) {
    val toaster = LocalToaster.current
    val running = download as? PropertyDownload.Running
    val unpacking = download is PropertyDownload.Unpacking
    val faint = !online && !downloaded && !download.inProgress
    val currentLabel = stringResource(R.string.menu_current_property)
    // The yellow wash starts on the icon's side, which is the right in right-to-left languages.
    val wash = listOf(C.Yellow.copy(alpha = 0.32f), C.YTintBg)
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val surface = when {
        current -> Modifier.background(Brush.horizontalGradient(if (rtl) wash.reversed() else wash))
        // Faded, but not as a whole: the download button's offline badge stays clear.
        faint -> Modifier.background(C.Surface.copy(alpha = FaintAlpha))
        else -> Modifier.background(C.Surface)
    }
    val fade = Modifier.alpha(if (faint) FaintAlpha else if (openBlock != null) BlockedAlpha else 1f)
    val edge = if (faint) Color.Transparent else if (current) C.Yellow.copy(alpha = 0.3f) else C.Border
    // Just downloaded (the tick shows): a yellow ring, which fades out with the tick, in step ([DONE_FADE_MS]).
    val finished = download is PropertyDownload.Finished
    val ring by animateColorAsState(if (finished) C.Yellow else edge, tween(DONE_FADE_MS), label = "done-ring")
    val ringWidth by animateDpAsState(if (finished) 1.5.dp else 1.dp, tween(DONE_FADE_MS), label = "done-ring-width")
    Box(
        modifier.fillMaxWidth().pressClickable(
            shape = r(12), pressed = PRESSED_CARD, role = Role.Button,
            onClick = when {
                openBlock != null -> ({ toaster.show(openBlock, ToastKind.INFO) })
                faint -> onOffline
                else -> onClick
            },
        ).then(surface)
            .border(ringWidth, ring, r(12))
            .semantics {
                selected = current
                if (current) stateDescription = currentLabel
            }.testTag(PropertyTags.row(id))
            ,
    ) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(start = 10.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                fade.size(40.dp).clip(CircleShape).background(if (current) C.YTintBg else C.Raised)
                    .border(1.dp, if (current) C.YTintBd else C.Border, CircleShape),
                contentAlignment = Alignment.Center,
            ) { LineIcon(Icons.Building, 17.dp, if (current) C.Yellow else C.Muted, 1.9f) }
            Txt(name, 14, 600, modifier = Modifier.weight(1f).then(fade), maxLines = 2)
            // Property Info (the web's info icon on each property).
            val infoLabel = stringResource(R.string.property_info_open, name)
            Box(
                fade.size(40.dp).pressClickable(shape = CircleShape, role = Role.Button, onClick = onInfo).semantics { contentDescription = infoLabel }
                    .testTag(PropertyTags.info(id)),
                contentAlignment = Alignment.Center,
            ) { LineIcon(Icons.Info, 18.dp, C.Muted, 2f) }
            // Every state keeps the height of the icon circle (the design's 64dp row, 48dp wide for the tap), so the card doesn't change size as a download runs.
            Box(Modifier.heightIn(min = RowControl), contentAlignment = Alignment.Center) {
                when {
                    running != null -> DownloadProgress(name, running, Modifier.testTag(PropertyTags.progress(id)))
                    unpacking -> {
                        val label = stringResource(R.string.property_unpacking, name)
                        Box(
                            Modifier.size(48.dp, RowControl).semantics { contentDescription = label }.testTag(PropertyTags.unpacking(id)),
                            contentAlignment = Alignment.Center,
                        ) { CircularProgressIndicator(Modifier.size(22.dp), color = C.Yellow, strokeWidth = 2.5.dp) }
                    }
                    // The tick, then the arrow of a downloaded property: the tick fades into the arrow as the ring fades.
                    finished || downloaded -> {
                        val tick by animateFloatAsState(if (finished) 1f else 0f, tween(DONE_FADE_MS), label = "done-tick")
                        val done = stringResource(R.string.property_downloaded)
                        Box(
                            Modifier.size(48.dp, RowControl).then(
                                if (finished) Modifier.semantics { contentDescription = done }.testTag(PropertyTags.done(id)) else Modifier,
                            ),
                            contentAlignment = Alignment.Center,
                        ) {
                            LineIcon(Icons.ChevronRight, 13.dp, C.Dim, 2.8f, Modifier.alpha(1f - tick))
                            LineIcon(Icons.Check, 22.dp, C.Green, 2.8f, Modifier.alpha(tick))
                        }
                    }
                    else -> {
                        // Offline the download button stays, with the offline badge (the card is faded already).
                        val label = stringResource(R.string.property_download, name)
                        AccessGate(openBlock) {
                            OfflineGate(faint && openBlock == null, onOffline) {
                                Box(
                                    Modifier.size(48.dp).pressClickable(shape = CircleShape, role = Role.Button, onClick = onDownload).semantics { contentDescription = label }
                                        .testTag(PropertyTags.download(id)),
                                    contentAlignment = Alignment.Center,
                                ) { LineIcon(Icons.Download, 22.dp, C.Yellow, 2.2f) }
                            }
                        }
                    }
                }
            }
        }
        // How far the download has got, as a yellow edge along the bottom of the card.
        if (running != null) {
            Box(Modifier.align(Alignment.BottomStart).fillMaxWidth(running.fraction).height(3.dp).background(C.Yellow))
        }
    }
}

/** "42%" over "5.2 / 12.4 MB" (the size once the server has sent it), read out as "Downloading <name>". */
@Composable
private fun DownloadProgress(name: String, d: PropertyDownload.Running, modifier: Modifier) {
    val label = stringResource(R.string.property_downloading, name)
    val locale = Locale.getDefault()
    Column(
        modifier.widthIn(min = 78.dp).padding(end = 6.dp).semantics(mergeDescendants = true) {
            contentDescription = label
            progressBarRangeInfo = ProgressBarRangeInfo(d.fraction, 0f..1f)
        },
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        Txt(NumberFormat.getPercentInstance(locale).format(d.fraction.toDouble()), 13, 700, C.Yellow)
        if (d.totalMb > 0) {
            val done = String.format(locale, "%.1f", d.downloadedMb)
            val total = String.format(locale, "%.1f", d.totalMb)
            Txt(stringResource(R.string.property_download_size, done, total), 11, 400, C.Muted)
        }
    }
}

/** How faded a property that can't be used offline looks (as in the design). */
private const val FaintAlpha = 0.26f

/** How a property row moves to its new place (a download finishing moves it up) and fades in or out. */
// A set time with an ease (not a spring), so the row stops cleanly where it lands instead of settling in small steps.
private const val MOVE_MS = 450
private val RowMove = tween<IntOffset>(MOVE_MS, easing = FastOutSlowInEasing)
private val RowFade = tween<Float>(220)

/**
 * How long a just-downloaded property's tick and yellow ring take to fade out, together. They show for as long as the
 * download says it has finished ([PropertyDownload.Finished]), then fade out in step.
 */
private const val DONE_FADE_MS = 450

/**
 * A property whose download (started from this list) has just finished moves up into the downloaded properties; should
 * it land above the screen, the list scrolls up to it.
 */
@Composable
private fun FollowFinishedDownload(state: PropertyListState, list: LazyListState) {
    var landed by remember { mutableStateOf<String?>(null) }
    // Downloads seen running here: only those count as landing (not properties already downloaded when the list opened).
    val running = remember { mutableSetOf<String>() }
    LaunchedEffect(state.downloads) {
        running += state.downloads.active.filterValues { it.inProgress }.keys
        val done = running.firstOrNull { it in state.downloads.downloadedIds } ?: return@LaunchedEffect
        running -= done
        landed = done
    }
    // Apart from the download changes above (which come often), so the moment isn't cut short by the next one.
    LaunchedEffect(landed) {
        val done = landed ?: return@LaunchedEffect
        // The list's first item is its header, so a property's place in the list is one after its row.
        val at = state.rows.indexOfFirst { it.property.id == done } + 1
        // After the move: scrolling while the rows still slide makes the two motions fight (a jitter where it lands).
        delay(MOVE_MS.toLong())
        if (at > 0 && at <= list.firstVisibleItemIndex) {
            // One eased scroll up by the rows in between (they are all about one height). animateScrollToItem springs and
            // then corrects itself once the row is laid out, which shows as a jitter as it stops.
            val rows = list.layoutInfo.visibleItemsInfo.filter { it.index > 0 }
            val rowStep = rows.takeIf { it.isNotEmpty() }?.let { r -> r.sumOf { it.size } / r.size + list.layoutInfo.mainAxisItemSpacing } ?: 0
            val distance = (list.firstVisibleItemIndex - (at - 1)) * rowStep + list.firstVisibleItemScrollOffset
            if (distance > 0) list.animateScrollBy(-distance.toFloat(), tween(MOVE_MS, easing = FastOutSlowInEasing))
        }
        landed = null
    }
}

/** How faded the name and icon of a property the user's group can't open are (as a greyed-out button). */
private const val BlockedAlpha = 0.45f

/** Height of the download / progress / tick area: the icon circle's, so the row stays the design's 64dp. */
private val RowControl = 42.dp

