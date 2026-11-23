package com.sclera.sclerago.presentation.property

import com.sclera.sclerago.presentation.model.OnlineOnly
import com.sclera.sclerago.presentation.components.AccessFailedBanner
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
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.sclera.sclerago.presentation.components.FabAction
import com.sclera.sclerago.presentation.components.ConfirmDialog
import com.sclera.sclerago.presentation.components.FabMenu
import com.sclera.sclerago.presentation.components.noRippleClick
import com.sclera.sclerago.presentation.components.ListLoader
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
    val canManage by vm.canManage.collectAsStateWithLifecycle()
    val suggestions by vm.suggestions.collectAsStateWithLifecycle()
    // Until a property is downloaded this is the app's first page, so Back leaves the app.
    BackHandler(enabled = app.hasProperty, onBack = app::closeAdmin)
    DisposableEffect(Unit) { onDispose(vm::reset) }
    // What Import did (or why it couldn't), flashed like the page's own messages.
    LaunchedEffect(importer) { importer.notices.collect { app.flash(it) } }
    // Import opens empty each time, as the web's.
    LaunchedEffect(state.importOpen) { if (state.importOpen) importer.reset() }
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
            currentName = app.propName.takeIf { it.isNotBlank() },
            onBack = app::openMenu, onQuery = vm::setQuery, onOpen = app::openPropertyFromList,
            onAdd = vm::openAdd, onImport = vm::openImport, onDownload = vm::download, onRefresh = vm::refresh,
            canManage = canManage, onInfo = vm::openEdit, onOffline = vm::explainOffline,
            accessFailed = app.accessFailed, onRetryAccess = app::retryAccess,
        )
        if (state.importOpen) ImportSheet(importer, vm::closeImport, onImported = vm::closeImport)
        if (state.addOpen) {
            AddPropertySheet(
                vm::closeAdd, vm::addProperty, state.addError,
                suggestions, vm::suggestAddress, vm::clearSuggestions,
            )
        }
        // The info button on a property: its details straight in the edit form, with Delete before Cancel. View only
        // offline, and for a group without Manage properties (the server refuses their changes and deletes).
        state.editing?.let { p ->
            key(p.id) {
                AddPropertySheet(
                    vm::closeEdit, vm::saveEdit, state.editError,
                    suggestions, vm::suggestAddress, vm::clearSuggestions, initial = p,
                    onDelete = if (canManage) vm::askDelete else null,
                    // Offline it shows the copy saved on the device, to look at only.
                    readOnly = !state.online || !canManage,
                    readOnlyReason = if (canManage) R.string.property_offline_edit else R.string.property_view_only,
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
    /** The property being worked on, under the title (none until one has been opened). */
    currentName: String? = null,
    onQuery: (String) -> Unit,
    onOpen: (String) -> Unit,
    onAdd: () -> Unit,
    onImport: () -> Unit,
    onDownload: (String) -> Unit = {},
    onRefresh: () -> Unit = {},
    /** Add and Import are offered only with Manage properties (as the web's "+ Property" and Import buttons). */
    canManage: Boolean = true,
    /** The info button on a property: opens it in the edit form. */
    onInfo: (Property) -> Unit = {},
    /** An online-only button was tapped offline (download, New property, Import): say why it needs the server. */
    onOffline: (OnlineOnly) -> Unit = {},
    /** The user's access couldn't be loaded: a banner says so, with Retry (properties needing a permission stay hidden). */
    accessFailed: Boolean = false,
    onRetryAccess: () -> Unit = {},
) {
    val gutter = pageGutter()
    var menuOpen by rememberSaveable { mutableStateOf(false) }
    Box(Modifier.fillMaxSize().background(C.Bg)) {
        Column(Modifier.fillMaxSize()) {
            SubPageBar(stringResource(R.string.property_list_title), property = currentName, onMenu = onBack)
            if (accessFailed) AccessFailedBanner(onRetryAccess, Modifier.padding(horizontal = gutter + 10.dp).padding(top = 10.dp))
            // The first read shows its own loader, so a pull waits until it is done.
            PullRefresh(state.refreshing, onRefresh, Modifier.weight(1f).fillMaxWidth(), enabled = !state.loading) {
                LazyColumn(
                    Modifier.fillMaxSize().testTag(PropertyTags.LIST),
                    contentPadding = PaddingValues(start = gutter + 8.dp, end = gutter + 8.dp, top = 10.dp, bottom = 160.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    item { Header(state) }
                    items(state.rows, key = { it.property.id }) { row ->
                        val id = row.property.id
                        val download = state.downloads.active[id]
                        val downloaded = id in state.downloads.downloadedIds
                        PropertyCard(
                            id, row.property.name, current = id == currentId,
                            download = download, downloaded = downloaded, online = state.online,
                            // A downloaded property opens (offline too); another one is downloaded first.
                            onClick = {
                                when {
                                    downloaded -> onOpen(id)
                                    !download.inProgress -> onDownload(id)
                                }
                            },
                            onDownload = { onDownload(id) },
                            onInfo = { onInfo(row.property) },
                            onOffline = { onOffline(OnlineOnly.DOWNLOAD_PROPERTY) },
                        )
                    }
                    when {
                        // First read from the server with nothing saved yet: a loader, not an empty list that fills suddenly.
                        state.loading -> item {
                            ListLoader(stringResource(R.string.property_loading), Modifier.testTag(PropertyTags.LOADING))
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
            Modifier.align(Alignment.BottomEnd).navigationBarsPadding().imePadding().padding(end = 14.dp, bottom = 14.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (canManage) FabMenu(
                menuOpen, { menuOpen = it },
                listOf(
                    FabAction(
                        stringResource(R.string.property_fab_import_title), Icons.Table, PropertyTags.IMPORT, onImport,
                        onBlocked = if (state.online) null else ({ onOffline(OnlineOnly.IMPORT_PROPERTIES) }),
                        subtitle = stringResource(R.string.property_fab_import_sub), iconColor = C.Green,
                    ),
                    FabAction(
                        stringResource(R.string.property_fab_new_title), Icons.Building, PropertyTags.ADD, onAdd,
                        onBlocked = if (state.online) null else ({ onOffline(OnlineOnly.NEW_PROPERTY) }),
                        subtitle = stringResource(R.string.property_fab_new_sub), iconColor = C.Text,
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
        // No counts until the properties are known, rather than a 0 that jumps.
        StatCard(
            Icons.Building, C.Yellow, if (state.loading) "–" else state.propertyTotal.toString(),
            stringResource(R.string.label_properties),
            Modifier.weight(1f).testTag(PropertyTags.COUNT), valueSize = 20, labelSize = 12, prominent = true,
        )
        StatCard(
            Icons.Download, C.Green, if (state.loading) "–" else state.syncedTotal.toString(),
            stringResource(R.string.property_synced),
            Modifier.weight(1f).testTag(PropertyTags.SYNCED), valueSize = 20, labelSize = 12, prominent = true,
            iconContent = {
                // The design's Synced badge: a green disc with a dark down arrow.
                Box(Modifier.size(24.dp).clip(CircleShape).background(C.Green), contentAlignment = Alignment.Center) {
                    LineIcon(Icons.ArrowDownShort, 24.dp, SyncedArrow, 2.4f)
                }
            },
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
    download: PropertyDownload?,
    downloaded: Boolean,
    online: Boolean,
    onClick: () -> Unit,
    onDownload: () -> Unit,
    onInfo: () -> Unit = {},
    onOffline: () -> Unit = {},
) {
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
    val fade = Modifier.alpha(if (faint) FaintAlpha else 1f)
    Box(
        Modifier.fillMaxWidth().clip(r(12)).then(surface)
            .border(if (current) 1.5.dp else 1.dp, if (faint) Color.Transparent else if (current) C.Yellow else C.Border, r(12))
            .semantics {
                selected = current
                if (current) stateDescription = currentLabel
            }.testTag(PropertyTags.row(id))
            .clickable(role = Role.Button, onClick = if (faint) onOffline else onClick),
    ) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(start = 12.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                fade.size(42.dp).clip(CircleShape).background(if (current) C.YTintBg else C.Raised)
                    .border(1.dp, if (current) C.YTintBd else C.Border, CircleShape),
                contentAlignment = Alignment.Center,
            ) { LineIcon(Icons.Building, 17.dp, if (current) C.Yellow else C.Muted, 1.9f) }
            Txt(name, 15, 600, modifier = Modifier.weight(1f).then(fade), maxLines = 2)
            // Property Info (the web's info icon on each property).
            val infoLabel = stringResource(R.string.property_info_open, name)
            Box(
                fade.size(40.dp).clip(CircleShape).semantics { contentDescription = infoLabel }
                    .testTag(PropertyTags.info(id)).clickable(role = Role.Button, onClick = onInfo),
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
                    download is PropertyDownload.Finished -> {
                        val done = stringResource(R.string.property_downloaded)
                        Box(
                            Modifier.size(48.dp, RowControl).semantics { contentDescription = done }.testTag(PropertyTags.done(id)),
                            contentAlignment = Alignment.Center,
                        ) { LineIcon(Icons.Check, 22.dp, C.Green, 2.8f) }
                    }
                    downloaded -> Box(Modifier.size(48.dp, RowControl), contentAlignment = Alignment.Center) {
                        LineIcon(Icons.ChevronRight, 13.dp, C.Dim, 2.8f)
                    }
                    else -> {
                        // Offline the download button stays, with the offline badge (the card is faded already).
                        val label = stringResource(R.string.property_download, name)
                        OfflineGate(faint, onOffline) {
                            Box(
                                Modifier.size(48.dp, RowControl).clip(CircleShape).semantics { contentDescription = label }
                                    .testTag(PropertyTags.download(id)).clickable(role = Role.Button, onClick = onDownload),
                                contentAlignment = Alignment.Center,
                            ) { LineIcon(Icons.Download, 22.dp, C.Yellow, 2.2f) }
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

/** Height of the download / progress / tick area: the icon circle's, so the row stays the design's 64dp. */
private val RowControl = 42.dp

/** The dark arrow inside the Synced tile's green disc (the design's #22332D). */
private val SyncedArrow = Color(0xFF22332D)
