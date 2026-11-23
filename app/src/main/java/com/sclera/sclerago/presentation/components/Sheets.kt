package com.sclera.sclerago.presentation.components

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import android.widget.VideoView
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.sclera.sclerago.R
import com.sclera.sclerago.domain.model.LocNode
import com.sclera.sclerago.domain.model.LocPick
import com.sclera.sclerago.domain.model.Media
import com.sclera.sclerago.domain.model.NodeKind
import com.sclera.sclerago.presentation.icons.Icons
import com.sclera.sclerago.presentation.icons.LineIcon
import com.sclera.sclerago.presentation.main.Preview
import com.sclera.sclerago.presentation.theme.C
import com.sclera.sclerago.presentation.theme.Txt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val SheetShape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)

// ---------------------------------------------------------------- Location picker

private sealed interface TreeRow {
    data class Node(val node: LocNode, val depth: Int) : TreeRow
    data class Empty(val text: String, val depth: Int) : TreeRow
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LocationPicker(
    propId: String,
    propName: String,
    startSel: String,
    onClose: () -> Unit,
    onPick: (LocPick) -> Unit,
    /** The property's buildings → floors → locations (edited in Configuration → Building info). */
    tree: List<LocNode>,
) {
    val byId = remember(tree) { mutableMapOf<String, LocNode>().also { m -> fun w(ns: List<LocNode>) { ns.forEach { m[it.id] = it; it.kids?.let(::w) } }; w(tree) } }
    val parent = remember(tree) { mutableMapOf<String, String?>().also { m -> fun w(ns: List<LocNode>, p: String?) { ns.forEach { m[it.id] = p; it.kids?.let { k -> w(k, it.id) } } }; w(tree, null) } }
    fun chain(id: String): List<LocNode> {
        val c = ArrayList<LocNode>()
        var x: String? = id
        while (x != null) { byId[x]?.let { c.add(0, it) }; x = parent[x] }
        return c
    }
    fun leaves(n: LocNode): List<String> = if (n.kind == NodeKind.LOCATION) listOf(n.name) else n.kids.orEmpty().flatMap { leaves(it) }

    // What it opened on: another place picked but not confirmed makes closing ask before discarding it.
    val startPick = if (byId.containsKey(startSel)) startSel else null
    var sel by remember { mutableStateOf(startPick) }
    var open by remember { mutableStateOf(sel?.let { s -> chain(s).filter { it.kids != null }.map { it.id }.toSet() } ?: emptySet()) }
    var q by remember { mutableStateOf("") }
    val ql = q.trim().lowercase()

    fun pick(id: String) {
        sel = id
        open = open + chain(id).filter { it.kids != null }.map { it.id }
        q = ""
    }

    val selChain = sel?.let { chain(it) } ?: emptyList()
    val onPath = selChain.map { it.id }.toSet()
    val node = sel?.let { byId[it] }
    val names = selChain.map { it.name }

    val noFloors = stringResource(R.string.scrt_loc_no_floors_yet)
    val noLocations = stringResource(R.string.scrt_loc_no_locations_yet)
    val rows = buildList {
        fun walk(ns: List<LocNode>, d: Int) {
            ns.forEach { n ->
                add(TreeRow.Node(n, d))
                if (n.kids != null && n.id in open) {
                    if (n.kids.isEmpty()) add(TreeRow.Empty(if (n.kind == NodeKind.BUILDING) noFloors else noLocations, d + 1))
                    else walk(n.kids, d + 1)
                }
            }
        }
        walk(tree, 0)
    }
    val results = if (ql.isEmpty()) emptyList() else byId.values.filter { it.name.lowercase().contains(ql) }
        .sortedBy { if (it.kind == NodeKind.LOCATION) 0 else 1 }
    val emptyText = when {
        tree.isEmpty() -> stringResource(R.string.scrt_loc_no_buildings, propName)
        ql.isNotEmpty() && results.isEmpty() -> stringResource(R.string.scrt_loc_no_match, q.trim())
        else -> ""
    }

    GuardedClose(sel != startPick, onClose) { close ->
        BackHandler(onBack = close)
        val shown = rememberAppear()
        val tablet = isTablet()
        Box(Modifier.fillMaxSize().scrim(shown).noRippleClick(close).statusBarsPadding()) {
            Column(
                Modifier.sheetIn(shown, tablet).swipeDownToClose(close).then(if (isTablet()) sheetFrame(C.Surface, heightFraction = 0.9f, fill = true)
                else Modifier.padding(top = 40.dp).fillMaxSize().clip(SheetShape).background(C.Surface).topBorder(C.Border)
                    .noRippleClick { }.navigationBarsPadding()).imePadding(),
            ) {
                Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (!isTablet()) SheetHandle(Modifier.align(Alignment.CenterHorizontally))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Txt(stringResource(R.string.scrt_loc_select_title), 17, 700)
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                LineIcon(Icons.Property, 11.dp, C.Muted, 2.2f)
                                Txt(propName, 11, 500, C.Muted)
                            }
                        }
                        CloseButton(close)
                    }
                    if (tree.isNotEmpty()) {
                        SearchField(q, { q = it }, stringResource(R.string.scrt_loc_search_hint))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            itemVerticalAlignment = Alignment.CenterVertically,
                        ) {
                            selChain.forEachIndexed { i, n ->
                                if (i > 0) LineIcon(Icons.ChevronRight, 12.dp, C.Dim, 2.6f)
                                val last = i == selChain.size - 1
                                Box(
                                    Modifier.heightIn(min = 32.dp).pressClickable(shape = Capsule) { pick(n.id) }.background(if (last) C.YTintBg else C.Raised)
                                        .border(1.dp, if (last) C.YTintBd else C.ChipBd, Capsule)
                                        .padding(horizontal = 10.dp),
                                    contentAlignment = Alignment.Center,
                                ) { Txt(n.name, 11, 600, if (last) C.Yellow else C.Text) }
                            }
                        }
                    }
                }

                LazyColumn(
                    Modifier.weight(1f),
                    contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 10.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(if (ql.isNotEmpty()) 4.dp else 2.dp),
                ) {
                    if (ql.isNotEmpty()) {
                        items(results, key = { "r-" + it.id }) { n ->
                            val c = chain(n.id)
                            Row(
                                Modifier.fillMaxWidth().heightIn(min = 56.dp).pressClickable(shape = r(12), pressed = PRESSED_CARD) { pick(n.id) }
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                KindIcon(n.kind, C.Yellow)
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Txt(n.name, 14, 600)
                                    Txt(if (c.size > 1) c.dropLast(1).joinToString(" › ") { it.name } else stringResource(R.string.detail_building), 11, 400, C.Muted)
                                }
                            }
                        }
                    } else {
                        items(rows, key = { if (it is TreeRow.Node) it.node.id else "e-" + (it as TreeRow.Empty).depth + it.text }) { row ->
                            when (row) {
                                is TreeRow.Empty -> Txt(
                                    row.text, 12, 400, Color(0xFF8B8F97),
                                    Modifier.heightIn(min = 40.dp).padding(start = (4 + row.depth * 20 + 36).dp).wrapContentHeight(),
                                    maxLines = Int.MAX_VALUE,
                                )
                                is TreeRow.Node -> TreeNodeRow(
                                    row.node, row.depth, row.node.id == sel, row.node.id in onPath, row.node.id in open,
                                    // Its own count (by its building, floor or location id, assets on the building or floor itself included):
                                    // what the list shows when it is picked.
                                    subFor(row.node), row.node.count,
                                    onToggle = { open = open.toggle(row.node.id) },
                                    onPick = { pick(row.node.id) },
                                )
                            }
                        }
                    }
                    if (emptyText.isNotEmpty()) item { Txt(emptyText, 13, 400, C.Muted, Modifier.padding(10.dp, 16.dp), maxLines = Int.MAX_VALUE) }
                }

                Row(
                    Modifier.fillMaxWidth().topBorder(C.Border).padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Txt(stringResource(R.string.scrt_loc_selected_caps), 10, 500, C.Muted, spacing = 0.6f)
                        Txt(node?.name ?: stringResource(R.string.scrt_loc_nothing_selected), 15, 700)
                        Txt(
                            if (node == null) stringResource(R.string.scrt_loc_pick_hint)
                            else if (selChain.size > 1) names.dropLast(1).joinToString(" › ") else stringResource(R.string.scrt_loc_whole_building),
                            12, 400, C.Muted, maxLines = Int.MAX_VALUE,
                        )
                    }
                    Pill(
                        stringResource(R.string.scrt_loc_confirm),
                        {
                            node?.let {
                                onPick(LocPick(it.id, it.name, leaves(it), names.reversed().take(2).joinToString(" · "), names, it.kind))
                            }
                        },
                        bg = if (node != null) C.Yellow else C.Raised, fg = if (node != null) C.OnYellow else C.Muted,
                        height = 48.dp, hPad = 22.dp, size = 14, weight = 700,
                    )
                }
            }
        }
    }
}

private fun Set<String>.toggle(k: String) = if (k in this) this - k else this + k

@Composable
private fun subFor(n: LocNode): String {
    val kind = stringResource(
        when (n.kind) {
            NodeKind.BUILDING -> R.string.detail_building
            NodeKind.FLOOR -> R.string.detail_floor
            NodeKind.LOCATION -> R.string.detail_location
        },
    )
    val kids = n.kids ?: return kind
    val building = n.kind == NodeKind.BUILDING
    return when {
        kids.isEmpty() -> stringResource(if (building) R.string.scrt_loc_sub_no_floors else R.string.scrt_loc_sub_no_locations, kind)
        else -> stringResource(
            R.string.scrt_loc_sub_count, kind,
            pluralStringResource(if (building) R.plurals.pi_count_floors else R.plurals.pi_count_locations, kids.size, kids.size),
        )
    }
}

@Composable
private fun KindIcon(kind: NodeKind, color: Color) {
    Box(Modifier.size(32.dp).background(C.Raised, r(9)), contentAlignment = Alignment.Center) {
        LineIcon(
            when (kind) {
                NodeKind.BUILDING -> Icons.Building
                NodeKind.FLOOR -> Icons.Floor
                NodeKind.LOCATION -> Icons.Pin
            },
            18.dp, color,
        )
    }
}

@Composable
private fun TreeNodeRow(
    n: LocNode,
    depth: Int,
    selected: Boolean,
    onPath: Boolean,
    expanded: Boolean,
    sub: String,
    count: Int,
    onToggle: () -> Unit,
    onPick: () -> Unit,
) {
    val rot by animateFloatAsState(if (expanded) 90f else 0f, tween(200), label = "tree")
    Row(
        Modifier.fillMaxWidth().heightIn(min = 54.dp).height(IntrinsicSize.Min).clip(r(12))
            .background(if (selected) C.YTintBg else Color.Transparent).padding(end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width((4 + depth * 20).dp).fillMaxHeight(), contentAlignment = Alignment.CenterEnd) {
            if (depth > 0) Box(Modifier.padding(end = 1.dp).width(1.dp).fillMaxHeight().background(C.ChipBd))
        }
        if (n.kids != null) {
            Box(Modifier.size(36.dp, 44.dp).pressClickable(shape = r(8), onClick = onToggle), contentAlignment = Alignment.Center) {
                LineIcon(Icons.ChevronRight, 16.dp, C.Muted, 2.6f, Modifier.rotate(rot))
            }
        } else Spacer(Modifier.width(36.dp))
        Row(
            Modifier.pressClickable(pressed = PRESSED_CARD, onClick = onPick).weight(1f).heightIn(min = 54.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            KindIcon(n.kind, if (selected || onPath) C.Yellow else C.Muted)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Txt(n.name, 15, if (selected || onPath) 700 else 500, if (selected) C.Yellow else C.Text)
                Txt(sub, 11, 400, C.Muted)
            }
            if (count > 0) {
                Box(
                    Modifier.widthIn(min = 24.dp).clip(Capsule).background(C.Raised).padding(horizontal = 7.dp, vertical = 2.dp),
                    contentAlignment = Alignment.Center,
                ) { Txt(count.toString(), 10, 700, C.Muted) }
            }
            RadioDot(selected)
        }
    }
}

// ---------------------------------------------------------------- Capture preview

@Composable
fun CapturePreview(p: Preview, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    Column(Modifier.fillMaxSize().background(Color(0xF2060708)).noRippleClick { }.systemBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp, top = 14.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Txt(p.name, 16, 700)
                Txt(p.sub, 12, 400, C.Muted)
            }
            CloseButton(onClose)
        }
        Box(
            Modifier.weight(1f).fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 40.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier.fillMaxWidth().heightIn(max = 480.dp).fillMaxHeight().clip(r(14)).background(p.bg),
                contentAlignment = Alignment.Center,
            ) {
                if (p.media != null) MediaFull(p.media)
                if (p.plate) NameplateLarge()
                if (p.video && p.media == null) {
                    Box(Modifier.size(72.dp).background(Color(0x99141518), CircleShape), contentAlignment = Alignment.Center) {
                        LineIcon(Icons.Play, 30.dp, C.White)
                    }
                }
            }
        }
    }
}

/** A photo at full size, a video that plays (looping, tap to pause), a voice memo to play, or a document to open. */
@Composable
fun MediaFull(media: Media) {
    // A voice memo keeps its file name, so it is checked before documents.
    if (media.audio) {
        AudioFull(media)
        return
    }
    if (media.isDoc) {
        DocFull(media)
        return
    }
    if (!media.video) {
        MediaImage(media, Modifier.fillMaxSize(), maxPx = 1600, contentScale = ContentScale.Fit, placeholder = Color.Transparent)
        return
    }
    AndroidView(
        factory = { ctx ->
            VideoView(ctx).apply {
                setVideoURI(Uri.parse(media.uri))
                setOnPreparedListener { it.isLooping = true; start() }
                setOnClickListener { if (isPlaying) pause() else start() }
            }
        },
        onRelease = { it.stopPlayback() },
        modifier = Modifier.fillMaxSize().wrapContentSize(),
    )
}

/** A voice memo: its name and length, with Play / Pause. One that isn't on this device can't be played offline. */
@Composable
private fun AudioFull(media: Media) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var playing by remember(media.uri) { mutableStateOf(false) }
    var loading by remember(media.uri) { mutableStateOf(true) }
    // Opened off the main thread (a memo kept in the offline database is copied out first), and released when this
    // memo goes away; null when it isn't on this device.
    val player by produceState<MediaPlayer?>(null, media.uri) {
        val uri = Uri.parse(media.uri)
        val opened = if (uri.scheme == "file" || uri.scheme == "content") openPlayer(context, uri) else null
        opened?.setOnCompletionListener { playing = false }
        value = opened
        loading = false
        awaitDispose { opened?.release() }
    }
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    ) {
        Box(Modifier.size(84.dp).clip(CircleShape).background(C.YTintBg), contentAlignment = Alignment.Center) {
            LineIcon(Icons.Mic, 34.dp, C.Yellow, 2.2f)
        }
        Txt(media.fileName ?: stringResource(R.string.media_tag_audio), 15, 600, maxLines = 3)
        if (media.durationMs > 0) Txt(media.durationText, 12, 400, C.Muted)
        val ready = player
        when {
            loading -> Unit
            ready == null -> Txt(stringResource(R.string.memo_not_on_device), 12, 400, C.Muted, maxLines = Int.MAX_VALUE)
            else -> Pill(
                stringResource(if (playing) R.string.memo_pause else R.string.memo_play),
                {
                    if (playing) ready.pause() else ready.start()
                    playing = !playing
                },
                Modifier.testTag("memo-play"), bg = C.Yellow, fg = C.OnYellow, height = 44.dp, hPad = 24.dp, size = 14, weight = 700,
            )
        }
    }
}

/**
 * A player prepared for [uri] off the main thread, or null when the memo can't be opened (not on this device, or not
 * playable). A player that doesn't open, or whose screen closes while it opens, is released here so none is left behind.
 */
private suspend fun openPlayer(context: Context, uri: Uri): MediaPlayer? {
    val player = MediaPlayer()
    return try {
        withContext(Dispatchers.IO) {
            player.setDataSource(context, uri)
            player.prepare()
        }
        player
    } catch (e: Exception) {
        player.release()
        if (e is CancellationException) throw e
        null
    }
}

/** An uploaded document: its type and name, with a button to open it in another app. */
@Composable
private fun DocFull(media: Media) {
    val context = androidx.compose.ui.platform.LocalContext.current
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    ) {
        Box(Modifier.size(84.dp).clip(r(16)).background(C.Raised), contentAlignment = Alignment.Center) {
            Txt(media.ext, 18, 800, C.Yellow, spacing = 0.6f)
        }
        Txt(media.fileName ?: stringResource(R.string.detail_document), 15, 600, maxLines = 3)
        val openWith = stringResource(R.string.doc_open_with)
        Pill(stringResource(R.string.scrt_doc_open), {
            val i = android.content.Intent(android.content.Intent.ACTION_VIEW)
                .setData(Uri.parse(media.uri))
                .addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            runCatching { context.startActivity(android.content.Intent.createChooser(i, openWith)) }
        }, bg = C.Yellow, fg = C.OnYellow, height = 40.dp, hPad = 24.dp, size = 14, weight = 700)
    }
}
