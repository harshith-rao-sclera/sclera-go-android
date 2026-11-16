package com.sclera.sclerago.presentation.property

import java.util.Locale
import java.text.NumberFormat
import kotlinx.coroutines.delay
import com.sclera.sclerago.domain.model.PropertyDownload
import com.sclera.sclerago.domain.model.inProgress
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.height
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import com.sclera.sclerago.presentation.components.CircleFab
import com.sclera.sclerago.presentation.components.ImportStrip
import com.sclera.sclerago.presentation.components.SearchFab
import com.sclera.sclerago.presentation.components.StatCard
import com.sclera.sclerago.presentation.components.StatRow
import com.sclera.sclerago.presentation.components.SubPageBar
import com.sclera.sclerago.presentation.components.Toast
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
    const val EMPTY = "property_empty"
    const val LOADING = "property_loading"
    const val COUNT = "property_count"
    fun row(id: String) = "property_row_$id"
    fun download(id: String) = "property_download_$id"
    fun progress(id: String) = "property_progress_$id"
    fun done(id: String) = "property_done_$id"
    fun unpacking(id: String) = "property_unpacking_$id"
}

/** Property list (side menu): counts, import, search, open a property, add one. */
@Composable
fun PropertyListScreen(app: AppViewModel, vm: PropertyViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    // Until a property is downloaded this is the app's first page, so Back leaves the app.
    BackHandler(enabled = app.hasProperty, onBack = app::closeAdmin)
    DisposableEffect(Unit) { onDispose(vm::reset) }
    // Offline, properties that aren't downloaded fade out; check the connection while the page is shown.
    LaunchedEffect(Unit) {
        while (true) {
            vm.checkConnection()
            delay(CONNECTION_CHECK_MS)
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
            onAdd = vm::openAdd, onImport = vm::openImport, onDownload = vm::download,
        )
        AnimatedVisibility(
            app.state.toastOn && !state.addOpen,
            Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 72.dp),
            enter = fadeIn(), exit = fadeOut(),
        ) { Toast(app.state.toast.asString()) }
        if (state.importOpen) ImportSheet(vm::closeImport, vm::importFile, vm::importProblem, vm::writeSample)
        if (state.addOpen) AddPropertySheet(state.properties, vm::closeAdd, vm::addProperty)
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
) {
    val gutter = pageGutter()
    Box(Modifier.fillMaxSize().background(C.Bg)) {
        Column(Modifier.fillMaxSize()) {
            SubPageBar(stringResource(R.string.property_list_title), property = null, onMenu = onBack)
            LazyColumn(
                Modifier.weight(1f).fillMaxWidth().testTag(PropertyTags.LIST),
                contentPadding = PaddingValues(start = gutter + 10.dp, end = gutter + 10.dp, top = 10.dp, bottom = 160.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                item { Header(state, onImport) }
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
                    )
                }
                when {
                    // First read from the server with nothing saved yet: a loader, not an empty list that fills suddenly.
                    state.loading -> item { ListLoader() }
                    state.rows.isEmpty() -> item {
                        val text = if (state.query.isBlank()) stringResource(R.string.property_none)
                        else stringResource(R.string.property_none_match, state.query.trim())
                        Txt(
                            text, 13, 400, C.Muted,
                            Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 30.dp).testTag(PropertyTags.EMPTY),
                            maxLines = 3, align = TextAlign.Center,
                        )
                    }
                }
            }
        }
        Column(
            Modifier.align(Alignment.BottomEnd).navigationBarsPadding().imePadding().padding(end = 14.dp, bottom = 14.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            CircleFab(Icons.Plus, stringResource(R.string.property_add_fab), onAdd, Modifier.testTag(PropertyTags.ADD))
            SearchFab(
                state.query, onQuery, stringResource(R.string.property_list_search_hint),
                stringResource(R.string.action_search), stringResource(R.string.action_close_search),
            )
        }
    }
}

/** Spinner and "Loading properties…" while the first server read runs (read out as one line). */
@Composable
private fun ListLoader() {
    Column(
        Modifier.fillMaxWidth().padding(top = 40.dp).semantics(mergeDescendants = true) {}.testTag(PropertyTags.LOADING),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CircularProgressIndicator(Modifier.size(28.dp), color = C.Yellow, strokeWidth = 2.5.dp)
        Txt(stringResource(R.string.property_loading), 13, 400, C.Muted, maxLines = 2, align = TextAlign.Center)
    }
}

/** The Properties / Assets tiles and the import strip (the app bar already names the page). */
@Composable
private fun Header(state: PropertyListState, onImport: () -> Unit) {
    Column(Modifier.padding(bottom = 7.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        StatRow {
            StatCard(
                // No count until the properties are known, rather than a 0 that jumps.
                Icons.Building, C.Yellow, if (state.loading) "–" else state.propertyTotal.toString(),
                stringResource(R.string.label_properties),
                Modifier.weight(1f).testTag(PropertyTags.COUNT), valueSize = 17, labelSize = 11,
            )
        }
        ImportStrip(
            stringResource(R.string.property_import_strip), stringResource(R.string.property_import_format), onImport,
            Modifier.testTag(PropertyTags.IMPORT), labelSize = 13, formatSize = 11,
        )
    }
}

/**
 * A property: round building icon and name, then where its offline data is: a download button, the download's
 * progress (percent, MB and a yellow edge along the bottom), a tick when it has just finished, or the chevron once
 * it is downloaded. Offline, a property that isn't downloaded is faded and can't be opened. The current property
 * is marked for accessibility only.
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
) {
    val running = download as? PropertyDownload.Running
    val unpacking = download is PropertyDownload.Unpacking
    val faint = !online && !downloaded && !download.inProgress
    Box(
        Modifier.fillMaxWidth().alpha(if (faint) FaintAlpha else 1f).clip(r(12)).background(C.Surface)
            .border(1.dp, if (faint) Color.Transparent else C.Border, r(12))
            .semantics { selected = current }.testTag(PropertyTags.row(id))
            .clickable(enabled = !faint, role = Role.Button, onClick = onClick),
    ) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 60.dp).padding(start = 12.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(C.Raised).border(1.dp, C.Border, CircleShape),
                contentAlignment = Alignment.Center,
            ) { LineIcon(Icons.Building, 17.dp, C.Muted, 1.9f) }
            Txt(name, 14, 600, modifier = Modifier.weight(1f), maxLines = 2)
            when {
                running != null -> DownloadProgress(name, running, Modifier.testTag(PropertyTags.progress(id)))
                unpacking -> {
                    val label = stringResource(R.string.property_unpacking, name)
                    Box(
                        Modifier.size(48.dp).semantics { contentDescription = label }.testTag(PropertyTags.unpacking(id)),
                        contentAlignment = Alignment.Center,
                    ) { LineIcon(Icons.Unzip, 22.dp, C.Yellow, 2f) }
                }
                download is PropertyDownload.Finished -> {
                    val done = stringResource(R.string.property_downloaded)
                    Box(
                        Modifier.size(48.dp).semantics { contentDescription = done }.testTag(PropertyTags.done(id)),
                        contentAlignment = Alignment.Center,
                    ) { LineIcon(Icons.Check, 22.dp, C.Green, 2.8f) }
                }
                downloaded -> Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                    LineIcon(Icons.ChevronRight, 13.dp, C.Dim, 2.8f)
                }
                faint -> Unit
                else -> {
                    val label = stringResource(R.string.property_download, name)
                    Box(
                        Modifier.size(48.dp).clip(CircleShape).semantics { contentDescription = label }
                            .testTag(PropertyTags.download(id)).clickable(role = Role.Button, onClick = onDownload),
                        contentAlignment = Alignment.Center,
                    ) { LineIcon(Icons.Download, 22.dp, C.Yellow, 2.2f) }
                }
            }
        }
        // How far the download has got, as a yellow edge along the bottom of the card.
        if (running != null) {
            Box(Modifier.align(Alignment.BottomStart).fillMaxWidth(running.fraction).height(3.dp).background(C.Yellow))
        }
        // Unpacking takes an unknown time: a yellow piece slides along the same bottom edge until it is done.
        if (unpacking) EndlessEdge(Modifier.align(Alignment.BottomStart))
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

/** A 3dp edge with a yellow piece sliding across it again and again (progress of unknown length). */
@Composable
private fun EndlessEdge(modifier: Modifier) {
    val slide by rememberInfiniteTransition(label = "unpacking").animateFloat(
        initialValue = -EdgePiece, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(EDGE_SWEEP_MS, easing = LinearEasing), RepeatMode.Restart),
        label = "unpacking-edge",
    )
    BoxWithConstraints(modifier.fillMaxWidth().height(3.dp)) {
        Box(Modifier.offset(x = maxWidth * slide).width(maxWidth * EdgePiece).height(3.dp).background(C.Yellow))
    }
}

/** Share of the card's width the sliding piece takes, and how long one sweep lasts. */
private const val EdgePiece = 0.35f
private const val EDGE_SWEEP_MS = 1_100

/** How faded a property that can't be used offline looks (as in the design). */
private const val FaintAlpha = 0.26f

/** How often the page re-checks the connection while it is shown. */
private const val CONNECTION_CHECK_MS = 3_000L
