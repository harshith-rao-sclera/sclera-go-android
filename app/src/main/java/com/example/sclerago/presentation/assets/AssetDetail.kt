package com.example.sclerago.presentation.assets

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.sclerago.data.*
import com.example.sclerago.presentation.components.ActionCard
import com.example.sclerago.presentation.components.Capsule
import com.example.sclerago.presentation.components.CloseButton
import com.example.sclerago.presentation.components.ConfirmDialog
import com.example.sclerago.presentation.components.LocalReadOnly
import com.example.sclerago.presentation.components.MediaFull
import com.example.sclerago.presentation.components.MediaImage
import com.example.sclerago.presentation.components.NameplateLarge
import com.example.sclerago.presentation.components.Pill
import com.example.sclerago.presentation.components.PlainField
import com.example.sclerago.presentation.components.RoundButton
import com.example.sclerago.presentation.components.bottomBorder
import com.example.sclerago.presentation.components.dashedBorder
import com.example.sclerago.presentation.components.fabShadow
import com.example.sclerago.presentation.components.noRippleClick
import com.example.sclerago.presentation.components.r
import com.example.sclerago.presentation.components.topBorder
import com.example.sclerago.presentation.icons.Ico
import com.example.sclerago.presentation.icons.Icons
import com.example.sclerago.presentation.icons.LineIcon
import com.example.sclerago.presentation.theme.C
import com.example.sclerago.presentation.theme.Txt
import com.example.sclerago.presentation.validation.ValidationPanel
import com.example.sclerago.presentation.validation.validationFailCount
import com.example.sclerago.services.ai.aiAutofill
import com.example.sclerago.services.media.Media
import com.example.sclerago.services.media.describeAttachments
import com.example.sclerago.services.media.describeVisualMedia
import com.example.sclerago.services.validation.*
import com.example.sclerago.services.validation.CheckResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private data class Photo(val bg: Color, val fg: Color, val label: String, val alt: String, val plate: Boolean, val media: Media? = null)

private val CAMERA_PHOTOS = listOf(
    Photo(Color(0xFFB9BEC4), Color(0xFF1E2126), "NAMEPLATE", "Nameplate photo", true),
    Photo(Color(0xFF3B4A5A), Color(0xFFE6E6E6), "ASSET", "Asset photo", false),
    Photo(Color(0xFF2E3338), Color(0xFFE6E6E6), "LABEL", "Label photo", false),
)

private enum class Modal { NONE, DELETE, DISCARD, SUBMIT }

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AssetDetailScreen(
    asset: Asset,
    isNew: Boolean,
    savedValues: Map<String, String>?,
    onBack: () -> Unit,
    onChangeLoc: () -> Unit,
    onCreate: (name: String, detail: String, media: List<Media>, values: Map<String, String>, submit: Boolean) -> Unit,
    onAttach: (List<Media>) -> Unit,
    onDelete: (Int) -> Unit,
    onSave: (Map<String, String>) -> Unit,
    onStatus: (Status) -> Unit,
    /** Saves the values and submits the asset; after that it can't be edited. */
    onSubmit: (Map<String, String>) -> Unit = {},
    /** Values filled in for a new asset, e.g. from the scanned device it is added from. */
    prefill: Map<String, String> = emptyMap(),
    /** Which fields show and which are required (Configuration → Asset fields). */
    fields: List<FieldCfg> = DEFAULT_FIELDS,
    /** Fewest photos a camera asset needs (sum of the photo types' minimums). */
    minPhotos: Int = 1,
) {
    fun cfg(k: String) = fields.firstOrNull { it.key == k }
    fun shown(k: String) = cfg(k)?.visible ?: true
    fun req(k: String) = cfg(k)?.let { it.required && it.visible } ?: false
    fun labelOf(k: String) = (cfg(k)?.label ?: k) + if (req(k)) " *" else ""
    fun visibleIn(sec: FieldSection) = fields.filter { it.section == sec && it.visible && it.key != "name" }
    val details = if (isNew) null else DETAILS[asset.id]
    val code = if (isNew) "New · not saved yet" else details?.code ?: "SCL-000${400 + asset.id}"
    val base = remember(asset.id, isNew, prefill) {
        val m = mutableMapOf(
            "name" to asset.name, "sys" to "Generic", "typ" to "Generic", "sub" to "Generic",
        )
        details?.fields?.let { m.putAll(it) }
        // New assets start with each field's default value (Configuration → Asset fields).
        if (isNew) fields.filter { it.visible && it.defaultValue.isNotBlank() }.forEach { f ->
            m[f.key] = when {
                f.kind == FieldKind.DATE && f.defaultValue == "Today" ->
                    java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                f.kind == FieldKind.TIME && f.defaultValue == "Now" ->
                    java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))
                else -> f.defaultValue
            }
        }
        if (isNew) m.putAll(prefill)
        m.toMap()
    }
    val saved = base + (savedValues ?: emptyMap())

    var edits by remember(asset.id, isNew) { mutableStateOf(mapOf<String, String>()) }
    var savedFlash by remember(asset.id) { mutableStateOf(false) }
    var collapsed by remember(asset.id) { mutableStateOf(setOf("cls", "tags", "notes", "more")) }
    var modal by remember(asset.id) { mutableStateOf(Modal.NONE) }
    var listening by remember(asset.id) { mutableStateOf(false) }
    // AI autofill: which fields it filled (cleared when the user edits one), and a short-lived result message.
    var aiFilled by remember(asset.id, isNew) { mutableStateOf(setOf<String>()) }
    var aiBusy by remember(asset.id) { mutableStateOf(false) }
    var aiMsg by remember(asset.id) { mutableStateOf<String?>(null) }
    var pvIndex by remember(asset.id) { mutableStateOf<Int?>(null) }

    fun v(k: String) = edits[k] ?: saved[k] ?: ""
    fun dirty(k: String) = k in edits && edits[k] != (saved[k] ?: "")
    // A submitted asset is read-only (until a recollect is requested).
    val locked = !isNew && asset.st == Status.SUBMITTED
    fun edit(k: String): (String) -> Unit = { if (!locked) { edits = edits + (k to it); savedFlash = false; aiFilled = aiFilled - k } }
    val dirtyN = edits.keys.count { dirty(it) }
    val fromCam = asset.src == Source.CAMERA
    // New assets keep their picks locally until "Add" is tapped.
    var newPicks by remember(asset.id, isNew) { mutableStateOf(listOf<Media>()) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    fun attach(list: List<Media>) {
        if (list.isEmpty()) return
        if (isNew) newPicks = newPicks + list else onAttach(list)
    }
    // "Add photo" opens the same camera as the Camera tab; its captures attach to this asset.
    var camOpen by remember(asset.id) { mutableStateOf(false) }
    var camCaps by remember(asset.id) { mutableStateOf(listOf<Media>()) }
    var camSel by remember(asset.id) { mutableStateOf(setOf<String>()) }
    var camVideo by remember(asset.id) { mutableStateOf(false) }
    var addMenu by remember(asset.id) { mutableStateOf(false) }
    // Gallery and file picks come straight back to these details, attached.
    val galleryPick = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(20)) { uris ->
        if (uris.isNotEmpty()) scope.launch { attach(describeVisualMedia(context, uris)) }
    }
    val filePick = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) scope.launch { attach(describeAttachments(context, uris)) }
    }
    val attached = asset.media + newPicks
    val photos = attached.map {
        when {
            it.isDoc -> Photo(C.Raised, C.Yellow, it.ext, it.fileName ?: "Document", false, it)
            it.video -> Photo(C.Raised, C.White, "VIDEO ${it.durationText}", "Video", false, it)
            else -> Photo(C.Raised, C.White, "", "Photo", false, it)
        }
    } + if (fromCam && asset.media.isEmpty()) CAMERA_PHOTOS else emptyList()

    // Info / Validation tabs. Checks run on open and when revalidated; results are a snapshot.
    var tab by remember(asset.id) { mutableStateOf(0) }
    val formScroll = rememberScrollState()
    // Where each Info section starts inside the form, so a fix link can scroll straight to it.
    val sectionTop = remember(asset.id) { mutableMapOf<String, Int>() }
    var pendingFix by remember(asset.id) { mutableStateOf<String?>(null) }
    LaunchedEffect(pendingFix, tab) {
        val key = pendingFix
        if (key == null) { formScroll.scrollTo(0); return@LaunchedEffect } // each tab opens at its top
        if (tab != 0) return@LaunchedEffect
        delay(60) // let the Info tab lay out first
        sectionTop[key]?.let { formScroll.animateScrollTo(it) }
        pendingFix = null
    }
    fun checkInput() = CheckInput(
        values = saved + edits, hasLocation = asset.loc != null, status = asset.st,
        fromCamera = fromCam, photoCount = photos.size,
        required = fields.filter { it.required && it.visible }.map { it.key to it.label }, minPhotos = minPhotos,
        fieldChecks = fields,
    )
    var checks by remember(asset.id) { mutableStateOf<List<CheckResult>?>(null) }
    var checkedInput by remember(asset.id) { mutableStateOf<CheckInput?>(null) }
    var checkedAt by remember(asset.id) { mutableStateOf(0L) }
    var fullCheck by remember(asset.id) { mutableStateOf(true) }
    var running by remember(asset.id) { mutableStateOf(false) }
    var runningFrom by remember(asset.id) { mutableStateOf(0) }
    LaunchedEffect(asset.id, isNew) {
        if (!isNew && checks == null) {
            val inp = checkInput()
            checks = runAllChecks(inp); checkedInput = inp; checkedAt = System.currentTimeMillis()
        }
    }
    fun runFrom(from: Int) {
        if (running) return
        running = true; runningFrom = from
        scope.launch {
            delay(900)
            val inp = checkInput()
            checks = checks.orEmpty().take(from) + (from until CHECK_NAMES.size).map { runCheck(it, inp) }
            checkedInput = inp; checkedAt = System.currentTimeMillis(); fullCheck = from == 0; running = false
        }
    }

    fun back() {
        if (isNew) modal = Modal.DISCARD else onBack()
    }

    /** New asset: create it as a draft, or submitted. All entered values are kept with it. */
    fun createNew(submit: Boolean) {
        val serial = v("serial")
        onCreate(
            v("name"), if (serial.isNotEmpty()) "S/N $serial" else v("model").ifEmpty { "Added manually" },
            newPicks, saved + edits, submit,
        )
    }

    BackHandler {
        when {
            modal != Modal.NONE -> modal = Modal.NONE
            camOpen -> camOpen = false
            pvIndex != null -> pvIndex = null
            else -> back()
        }
    }

    Box(Modifier.fillMaxSize().background(C.Bg)) {
        Column(Modifier.fillMaxSize()) {
            // Top bar
            Row(
                Modifier.fillMaxWidth().height(56.dp).background(C.Bg).bottomBorder(C.Border).padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                RoundButton(44.dp, Color.Transparent, ::back) { LineIcon(Icons.ChevronLeft, 22.dp, C.Yellow, 2.4f) }
                val headLine = when {
                    isNew -> if (v("name").isNotEmpty()) C.Border2 else C.Yellow
                    dirty("name") -> C.Yellow
                    else -> C.Border2
                }
                PlainField(
                    v("name"), edit("name"), "Asset name",
                    Modifier.weight(1f).padding(end = 6.dp).height(40.dp).bottomBorder(headLine, 1.5.dp).padding(horizontal = 2.dp),
                    size = 19, weight = 700, readOnly = locked,
                )
                if (dirtyN == 0 && savedFlash) {
                    Row(
                        Modifier.clip(Capsule).background(Color(0xFF17241D)).border(1.dp, Color(0xFF27402F), Capsule)
                            .padding(horizontal = 9.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        LineIcon(Icons.Check, 11.dp, C.Green, 3f)
                        Txt("Saved", 11, 700, C.Green)
                    }
                }
            }

            if (!isNew) {
                Row(Modifier.fillMaxWidth().height(48.dp).background(C.Strip).bottomBorder(C.Border)) {
                    DetailTab("Info", Icons.Info, tab == 0, null, Modifier.weight(1f)) { tab = 0 }
                    DetailTab("Validation", Icons.ShieldCheck, tab == 1, checks?.let { validationFailCount(it) }, Modifier.weight(1f)) { tab = 1 }
                }
            }

            CompositionLocalProvider(LocalReadOnly provides locked) {
            Column(
                // Room at the end so the last card scrolls clear of the AI and mic buttons.
                Modifier.weight(1f).verticalScroll(formScroll).padding(top = 12.dp, bottom = 160.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (tab == 1 && !isNew) {
                    val res = checks
                    if (res != null) ValidationPanel(
                        results = res, checkedAt = checkedAt, fullCheck = fullCheck,
                        running = running, runningFrom = runningFrom,
                        edited = checkedInput != null && checkedInput != checkInput(),
                        onRevalidate = ::runFrom, onRunAll = { runFrom(0) },
                        onFix = { target ->
                            if (!locked) when (target) {
                                FixTarget.LOCATION -> onChangeLoc()
                                FixTarget.PHOTOS -> camOpen = true
                                else -> target.section?.let { collapsed = collapsed - it; pendingFix = it; tab = 0 }
                            }
                        },
                        onSubmit = if (!locked) ({ modal = Modal.SUBMIT }) else null,
                    )
                } else {
                // Photos
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    photos.forEachIndexed { i, ph ->
                        Box(
                            Modifier.size(76.dp).clip(r(12)).background(ph.bg).clickable { pvIndex = i },
                        ) {
                            ph.media?.let { MediaImage(it, Modifier.fillMaxSize(), maxPx = 240) }
                            Txt(ph.label, 9, 700, ph.fg, Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp), spacing = 0.4f)
                            Box(
                                Modifier.align(Alignment.TopEnd).padding(5.dp).size(18.dp).background(Color(0x8C141518), r(5)),
                                contentAlignment = Alignment.Center,
                            ) { LineIcon(Icons.Expand, 11.dp, C.White, 2.6f) }
                        }
                    }
                    if (!locked) Box {
                        Column(
                            Modifier.size(76.dp).clip(r(12)).background(Color(0xFF1A1B1E)).dashedBorder(C.YTintBd, 1.5.dp, 12.dp)
                                .clickable { addMenu = true },
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
                        ) {
                            LineIcon(Icons.Plus, 20.dp, C.Yellow, 2.4f)
                            Txt("Add", 11, 700, C.Yellow)
                        }
                        DropdownMenu(addMenu, { addMenu = false }, containerColor = C.Raised) {
                            listOf(
                                Triple(Icons.Camera, "Take photo or video") { camOpen = true },
                                Triple(Icons.Gallery, "Upload from gallery") {
                                    galleryPick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                                },
                                Triple(Icons.FileDoc, "Upload from files") { filePick.launch(arrayOf("*/*")) },
                            ).forEach { (icon, label, go) ->
                                DropdownMenuItem(
                                    text = { Txt(label, 14, 500) },
                                    leadingIcon = { LineIcon(icon, 18.dp, C.Yellow, 2.2f) },
                                    onClick = { addMenu = false; go() },
                                )
                            }
                        }
                    }
                    if (photos.isEmpty()) {
                        Txt(
                            "No photos yet — take one, or upload from the gallery or files.", 13, 400, C.Muted,
                            Modifier.align(Alignment.CenterVertically).widthIn(max = 220.dp), maxLines = 3,
                        )
                    }
                }

                // Location
                Row(
                    Modifier.fillMaxWidth().background(C.Surface).topBorder(C.Border).bottomBorder(C.Border).padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(Modifier.size(40.dp).background(C.YTintBg, r(12)), contentAlignment = Alignment.Center) {
                        LineIcon(Icons.Pin, 20.dp, C.Yellow, 2.2f)
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Txt("LOCATION", 11, 600, C.Muted, spacing = 0.6f)
                        Txt(asset.where.ifEmpty { "No location yet" }, 15, 700, if (asset.loc != null) C.Text else C.Orange)
                    }
                    if (!locked) Pill("Change", onChangeLoc, height = 40.dp, hPad = 14.dp)
                }

                // Identification
                Section(
                    modifier = Modifier.onGloballyPositioned { sectionTop["ident"] = it.positionInParent().y.toInt() },
                    title = "Identification", note = if (fromCam) "Read from nameplate" else null, open = "ident" !in collapsed,
                    summary = listOf(v("make"), v("model")).filter { it.isNotEmpty() }.joinToString(" · ").ifEmpty { "None yet" }.let { Summary(it) },
                    onToggle = { collapsed = collapsed.toggle("ident") },
                ) {
                    if (!isNew) {
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 48.dp).topBorder(C.Border).padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Txt("Asset ID", 13, 500, C.Muted, Modifier.width(92.dp))
                            Txt(code, 14, 600, C.Soft, Modifier.weight(1f), mono = true)
                        }
                    }
                    visibleIn(FieldSection.IDENT).map { it.key to labelOf(it.key) }
                        .forEach { (k, label) ->
                            val value = v(k)
                            val edited = dirty(k)
                            val lowConf = fromCam && details?.lowConfidence == k && !edited
                            val (tag, tagC) = when {
                                k in aiFilled -> "AI" to C.Cyan
                                edited -> "EDITED" to C.Yellow
                                lowConf && value.isNotEmpty() -> "CHECK" to C.Orange
                                fromCam && value.isNotEmpty() -> "OCR" to C.Purple
                                else -> "" to C.Purple
                            }
                            FieldRow(
                                label, value, edit(k), edited, tag, tagC,
                                rowBg = if ((lowConf && value.isNotEmpty()) || (req(k) && value.isEmpty())) C.WarnRow else Color.Transparent,
                            )
                        }
                }

                // Classification
                Section(
                    modifier = Modifier.onGloballyPositioned { sectionTop["cls"] = it.positionInParent().y.toInt() },
                    title = "Classification", note = if (fromCam) "Suggested from photos" else null, open = "cls" !in collapsed,
                    summary = visibleIn(FieldSection.CLASS).firstOrNull { f ->
                        req(f.key) && (v(f.key).isEmpty() || (f.key in setOf("sys", "typ", "sub") && v(f.key) == "Generic"))
                    }?.let { Summary("${it.label} required", warn = true) }
                        ?: Summary(listOf("typ", "sys").map { v(it) }.firstOrNull { it.isNotEmpty() && it != "Generic" } ?: "Not set"),
                    onToggle = { collapsed = collapsed.toggle("cls") },
                ) {
                    visibleIn(FieldSection.CLASS).forEach { f ->
                        val k = f.key
                        val options = when (k) {
                            "sys" -> SYSTEM_TYPES.map { it to it }
                            "typ" -> ASSET_TYPES.map { it to it }
                            "sub" -> SUB_TYPES.map { it to it }
                            "jacs" -> JACS_CODES
                            else -> listOf("" to "Select") + FIELD_OPTIONS[k].orEmpty().map { it to it }
                        }
                        val x = v(k)
                        val missing = req(k) && (x.isEmpty() || (k in setOf("sys", "typ", "sub") && x == "Generic"))
                        SelectRow(
                            labelOf(k), x, options, dirty(k), edit(k),
                            required = missing, rowBg = if (missing) C.WarnRow else Color.Transparent,
                        )
                    }
                }

                // Tags & connectivity
                Section(
                    "Tags & connectivity", if (isNew && asset.src in setOf(Source.BT, Source.WIFI, Source.AP)) "From scan" else null, "tags" !in collapsed, { collapsed = collapsed.toggle("tags") },
                    summary = visibleIn(FieldSection.TAGS).map { v(it.key) }.firstOrNull { it.isNotEmpty() }
                        ?.let { Summary(it, mono = true) } ?: Summary("None yet"),
                    modifier = Modifier.onGloballyPositioned { sectionTop["tags"] = it.positionInParent().y.toInt() },
                ) {
                    visibleIn(FieldSection.TAGS).forEach { f ->
                        val k = f.key
                        FieldRow(
                            labelOf(k), v(k), edit(k), dirty(k), if (k in aiFilled) "AI" else "", C.Cyan, mono = true,
                            rowBg = if (req(k) && v(k).isEmpty()) C.WarnRow else Color.Transparent,
                        )
                    }
                }

                // More details: extra fields switched on in Configuration (condition, dates, custom fields…)
                val more = visibleIn(FieldSection.MORE)
                if (more.isNotEmpty()) Section(
                    "More details", null, "more" !in collapsed, { collapsed = collapsed.toggle("more") },
                    summary = more.count { v(it.key).isNotEmpty() }.let { n -> Summary(if (n == 0) "None yet" else "$n of ${more.size} filled") },
                ) {
                    more.forEach { f ->
                        val k = f.key
                        val missing = req(k) && v(k).isEmpty()
                        val bg = if (missing) C.WarnRow else Color.Transparent
                        val opts = when (f.kind) {
                            FieldKind.YESNO, FieldKind.CHECKBOX -> listOf("Yes", "No")
                            else -> f.options.ifEmpty { FIELD_OPTIONS[k].orEmpty() }
                        }
                        if (f.kind in setOf(FieldKind.DROPDOWN, FieldKind.MULTI, FieldKind.YESNO, FieldKind.CHECKBOX) && opts.isNotEmpty()) {
                            SelectRow(
                                labelOf(k), v(k), listOf("" to "Select") + opts.map { it to it }, dirty(k), edit(k),
                                required = missing, rowBg = bg,
                            )
                        } else {
                            FieldRow(labelOf(k), v(k), edit(k), dirty(k), if (k in aiFilled) "AI" else "", C.Cyan, rowBg = bg)
                        }
                    }
                }

                // Notes
                if (shown("note")) Section(
                    "Notes", null, "notes" !in collapsed, { collapsed = collapsed.toggle("notes") },
                    summary = Summary(v("note").replace('\n', ' ').let { if (it.length > 26) it.take(25).trimEnd() + "…" else it }.ifEmpty { "None yet" }),
                    trailing = if (locked) null else ({
                        Row(
                            Modifier.height(36.dp).clip(Capsule).background(C.Btn).clickable { listening = !listening }
                                .padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            LineIcon(Icons.Mic, 14.dp, C.Yellow, 2.2f)
                            Txt("Voice note", 12, 600)
                        }
                    }),
                ) {
                    PlainField(
                        v("note"), edit("note"), "Add a note about condition, access or anything else",
                        Modifier.padding(horizontal = 12.dp).fillMaxWidth().clip(r(10)).background(C.Bg)
                            .border(1.5.dp, if (dirty("note")) C.Yellow else C.Border, r(10)).padding(10.dp),
                        size = 14, singleLine = false, minLines = 3,
                    )
                }

                if (!isNew) {
                    FlowRow(
                        Modifier.padding(horizontal = 12.dp).padding(top = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        val st = asset.st
                        Box(
                            Modifier.clip(Capsule).background(st.chipBg).border(1.dp, st.chipBd, Capsule)
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                        ) { Txt(st.label, 12, 700, st.chipFg) }
                        InfoChip(asset.src.via)
                        InfoChip("Updated 5 Nov, 10:42")
                    }
                    // Actions for this asset, as a card at the end of the form.
                    if (locked) {
                        // Submitted: read-only. A recollect request reopens it (for now it just closes the asset).
                        SubmittedCard(onRecollect = onBack, modifier = Modifier.padding(horizontal = 12.dp).padding(top = 4.dp))
                    } else {
                        ActionCard(
                            // No button for the status the asset already has.
                            onValidate = if (asset.st != Status.VALIDATED) ({ onStatus(Status.VALIDATED) }) else null,
                            onAssign = onChangeLoc,
                            onSubmit = { modal = Modal.SUBMIT },
                            onDelete = { modal = Modal.DELETE },
                            modifier = Modifier.padding(horizontal = 12.dp).padding(top = 4.dp),
                        )
                        // Save (keeps it a draft) and Submit.
                        SaveCard(Modifier.padding(horizontal = 12.dp)) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                if (dirtyN > 0) {
                                    Txt(if (dirtyN == 1) "1 unsaved change" else "$dirtyN unsaved changes", 15, 700)
                                    Txt("Discard", 13, 700, C.Yellow, Modifier.clip(r(6)).clickable { edits = emptyMap() }.padding(vertical = 2.dp))
                                } else {
                                    Txt("No unsaved changes", 15, 700)
                                    Txt("Status: ${asset.st.label}", 13, 400, C.Muted)
                                }
                            }
                            Pill(
                                "Save", {
                                    if (dirtyN > 0) {
                                        onSave(saved + edits)
                                        edits = emptyMap()
                                        savedFlash = true
                                    }
                                },
                                Modifier.alpha(if (dirtyN > 0) 1f else 0.45f),
                                height = 44.dp, hPad = 18.dp, size = 14, weight = 700,
                            )
                            Pill("Submit", { modal = Modal.SUBMIT }, bg = C.Yellow, fg = C.OnYellow, height = 44.dp, hPad = 22.dp, size = 14, weight = 700)
                        }
                    }
                } else {
                    // New asset: save it as a draft, or submit it (then it can't be edited).
                    SaveCard(Modifier.padding(horizontal = 12.dp).padding(top = 4.dp)) {
                        Pill("Save", { createNew(false) }, Modifier.weight(1f), height = 48.dp, size = 15, weight = 700)
                        Pill(
                            "Submit", { modal = Modal.SUBMIT }, Modifier.weight(1f),
                            bg = C.Yellow, fg = C.OnYellow, height = 48.dp, size = 15, weight = 700,
                        )
                    }
                }
                }
            }
            }
        }

        if ((isNew || tab == 0) && !locked) AiFillButton(
            busy = aiBusy, message = aiMsg,
            onClick = {
                if (!aiBusy) {
                    aiBusy = true; aiMsg = null
                    scope.launch {
                        delay(1100) // "thinking"
                        val fills = aiAutofill(asset.type, saved + edits)
                        edits = edits + fills; aiFilled = aiFilled + fills.keys; savedFlash = false
                        aiBusy = false
                        aiMsg = if (fills.isEmpty()) "Nothing left to fill" else "AI filled ${plural(fills.size, "field", "fields")} · review and save"
                        delay(2600)
                        aiMsg = null
                    }
                }
            },
            modifier = Modifier.align(Alignment.BottomEnd).padding(
                end = 20.dp,
                bottom = 16.dp + 56.dp + 12.dp,
            ),
        )
        if ((isNew || tab == 0) && !locked) MicButton(
            listening, { listening = !listening },
            Modifier.align(Alignment.BottomEnd).padding(
                end = 16.dp,
                bottom = 16.dp,
            ),
        )

        if (camOpen) Box(Modifier.fillMaxSize().background(Color.Black)) {
            CameraCapture(
                active = true,
                captures = camCaps, selected = camSel,
                videoMode = camVideo, onVideoMode = { camVideo = it },
                onCapture = { m -> attach(listOf(m)); camOpen = false },
                onToggle = { id -> camSel = if (id in camSel) camSel - id else camSel + id },
                onGalleryPicked = { uris ->
                    if (uris.isNotEmpty()) scope.launch {
                        attach(describeVisualMedia(context, uris))
                        camOpen = false
                    }
                },
                onClose = { camOpen = false },
                onDone = {
                    attach(camCaps.filter { it.id in camSel })
                    camCaps = camCaps.filter { it.id !in camSel }
                    camSel = emptySet()
                    camOpen = false
                },
            )
        }

        pvIndex?.let { i ->
            PhotoViewer(photos, i, onIndex = { pvIndex = it }, onClose = { pvIndex = null })
        }

        if (modal == Modal.SUBMIT) {
            ConfirmDialog(
                icon = Icons.CheckCircle, iconBg = Status.SUBMITTED.chipBg, iconFg = C.Green,
                title = "Submit this asset?",
                body = "Once it’s submitted, its fields can’t be edited. If something needs changing later, request a recollect.",
                no = "Keep editing", yes = "Submit", yesBg = C.Yellow,
                onNo = { modal = Modal.NONE },
                onYes = {
                    modal = Modal.NONE
                    if (isNew) createNew(true) else {
                        onSubmit(saved + edits)
                        edits = emptyMap()
                    }
                },
            )
        } else if (modal != Modal.NONE) {
            val del = modal == Modal.DELETE
            ConfirmDialog(
                icon = if (del) Icons.Trash else Icons.Alert,
                iconBg = if (del) Color(0xFF2A1919) else C.WarnRow,
                iconFg = if (del) C.Red else C.Orange,
                title = if (del) "Delete this asset?" else "Discard this new asset?",
                body = if (del) "${v("name").ifEmpty { "This asset" }} will be removed. This can’t be undone."
                else "What you’ve entered won’t be saved.",
                no = if (del) "Cancel" else "Keep editing",
                yes = if (del) "Delete" else "Discard",
                onNo = { modal = Modal.NONE },
                onYes = {
                    modal = Modal.NONE
                    if (del) onDelete(asset.id) else onBack()
                },
            )
        }
    }
}

private fun Set<String>.toggle(k: String) = if (k in this) this - k else this + k

/** The Save / Submit card at the end of an asset's form. */
@Composable
private fun SaveCard(modifier: Modifier, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier.fillMaxWidth().clip(r(14)).background(C.Surface).border(1.dp, C.Border2, r(14)).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        content = content,
    )
}

/** A collapsed section's one-line summary. */
private class Summary(val text: String, val warn: Boolean = false, val mono: Boolean = false)

/** End of a submitted asset's form: it's read-only now; a recollect request would reopen it. */
@Composable
private fun SubmittedCard(onRecollect: () -> Unit, modifier: Modifier) {
    Column(
        modifier.fillMaxWidth().clip(r(14)).background(Status.SUBMITTED.chipBg).border(1.dp, Status.SUBMITTED.chipBd, r(14)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(38.dp).clip(r(10)).background(Color(0xFF1D3326)), contentAlignment = Alignment.Center) {
                LineIcon(Icons.CheckCircle, 20.dp, C.Green, 2.2f)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Txt("Submitted", 15, 700, C.Green)
                Txt("Its fields can’t be edited any more. Need a change? Ask for it to be collected again.", 12, 400, C.Muted, maxLines = 3)
            }
        }
        Pill("Request recollect", onRecollect, Modifier.fillMaxWidth(), height = 44.dp, size = 14, weight = 700)
    }
}

@Composable
private fun InfoChip(text: String) {
    Box(Modifier.clip(Capsule).background(C.Raised).padding(horizontal = 10.dp, vertical = 4.dp)) {
        Txt(text, 12, 600, C.Soft)
    }
}

@Composable
private fun Section(
    title: String,
    note: String?,
    open: Boolean,
    onToggle: () -> Unit,
    trailing: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier,
    /** Shown on the right while the section is collapsed: what's in it, or what's missing. */
    summary: Summary? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val rot by animateFloatAsState(if (open) 0f else -90f, tween(200), label = "chev")
    Column(
        modifier.fillMaxWidth().background(C.Surface).topBorder(C.Border).bottomBorder(C.Border).padding(top = 4.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(if (trailing != null && open) 4.dp else 0.dp),
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                Modifier.weight(1f).height(44.dp).clickable(onClick = onToggle),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                LineIcon(Icons.ChevronDown, 18.dp, C.Muted, 2.4f, Modifier.rotate(rot))
                Txt(title, 15, 700, modifier = Modifier.weight(1f))
                if (!open && summary != null) {
                    Txt(summary.text, 12, if (summary.warn) 700 else 500, if (summary.warn) C.Orange else C.Muted, mono = summary.mono)
                } else note?.let { Txt(it, 11, 500, C.Muted) }
            }
            if (open) trailing?.invoke()
        }
        if (open) content()
    }
}

@Composable
private fun FieldRow(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    edited: Boolean,
    tag: String,
    tagC: Color,
    rowBg: Color = Color.Transparent,
    mono: Boolean = false,
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).topBorder(C.Border).background(rowBg).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Txt(label, 13, 500, C.Muted, Modifier.width(96.dp), maxLines = 2)
        PlainField(
            value, onChange, "Add ${label.lowercase()}",
            Modifier.weight(1f).bottomBorder(if (edited) C.Yellow else Color.Transparent, 1.5.dp).padding(vertical = 6.dp),
            size = if (mono) 14 else 15, weight = 600, mono = mono,
        )
        if (tag.isNotEmpty()) Txt(tag, 10, 700, tagC, spacing = 0.3f)
    }
}

@Composable
private fun SelectRow(
    label: String,
    value: String,
    options: List<Pair<String, String>>,
    edited: Boolean,
    onChange: (String) -> Unit,
    required: Boolean = false,
    rowBg: Color = Color.Transparent,
) {
    var open by remember { mutableStateOf(false) }
    val readOnly = LocalReadOnly.current
    val shown = options.firstOrNull { it.first == value }?.second ?: value
    Box {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp).topBorder(C.Border).background(rowBg).clickable(enabled = !readOnly) { open = true }
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Txt(label, 13, 500, C.Muted, Modifier.width(96.dp), maxLines = 2)
            Txt(
                shown, 15, 600, if (value.isEmpty()) C.Muted else C.Text,
                Modifier.weight(1f).bottomBorder(if (edited) C.Yellow else Color.Transparent, 1.5.dp).padding(vertical = 6.dp),
            )
            if (required) Txt("REQUIRED", 10, 700, C.Orange)
            if (!readOnly) LineIcon(Icons.ChevronDown, 16.dp, C.Muted, 2.4f)
        }
        DropdownMenu(open, { open = false }, containerColor = C.Surface) {
            options.forEach { (k, text) ->
                DropdownMenuItem(
                    text = { Txt(text, 15, if (k == value) 700 else 500, if (k == value) C.Yellow else C.Text) },
                    onClick = { onChange(k); open = false },
                )
            }
        }
    }
}

/** Small floating button above the mic: fills empty fields with AI suggestions. */
@Composable
private fun AiFillButton(busy: Boolean, message: String?, onClick: () -> Unit, modifier: Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        val tip = if (busy) "AI is filling empty fields…" else message
        if (tip != null) {
            Txt(
                tip, 12, 500, C.Soft,
                Modifier.widthIn(max = 210.dp).fabShadow(r(12)).clip(r(12)).background(C.Surface)
                    .border(1.dp, C.Border2, r(12)).padding(horizontal = 12.dp, vertical = 8.dp),
                maxLines = 2,
            )
        }
        val inf = rememberInfiniteTransition(label = "ai")
        val spin by inf.animateFloat(0f, 360f, infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Restart), label = "spin")
        Box(
            Modifier.size(48.dp).fabShadow().clip(CircleShape).background(C.Surface)
                .border(1.dp, C.Border2, CircleShape).clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            LineIcon(Icons.Sparkles, 27.dp, C.Cyan, 2.2f, if (busy) Modifier.rotate(spin) else Modifier)
        }
    }
}

@Composable
private fun MicButton(listening: Boolean, onClick: () -> Unit, modifier: Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (listening) {
            Txt(
                "Listening… try “Serial number 24A-118903”", 12, 500, C.Soft,
                Modifier.widthIn(max = 210.dp).fabShadow(r(12)).clip(r(12)).background(C.Surface)
                    .border(1.dp, C.Border2, r(12)).padding(horizontal = 12.dp, vertical = 8.dp),
                maxLines = 3,
            )
        }
        Box(contentAlignment = Alignment.Center) {
            if (listening) {
                val inf = rememberInfiniteTransition(label = "mic")
                val p by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart), label = "p")
                Canvas(Modifier.size(56.dp)) {
                    listOf(p, (p + 0.5f) % 1f).forEach { t ->
                        drawCircle(C.Yellow.copy(alpha = 0.8f * (1f - t)), (28f + 18f * t).dp.toPx(), style = Stroke(2.dp.toPx()))
                    }
                }
            }
            Box(
                Modifier.size(56.dp).fabShadow().clip(CircleShape).background(C.Yellow).clickable(onClick = onClick),
                contentAlignment = Alignment.Center,
            ) { LineIcon(Icons.Mic, 24.dp, C.OnYellow, 2.2f) }
        }
    }
}

@Composable
private fun PhotoViewer(photos: List<Photo>, index: Int, onIndex: (Int) -> Unit, onClose: () -> Unit) {
    val ph = photos[index]
    Column(Modifier.fillMaxSize().background(Color(0xF5060708)).noRippleClick { }) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 10.dp, top = 10.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Txt(ph.alt, 16, 700)
                Txt("${index + 1} of ${photos.size}", 12, 400, C.Muted)
                ph.media?.credit?.let { Txt(it, 11, 400, C.Muted, maxLines = 2) }
            }
            CloseButton(onClose)
        }
        Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), contentAlignment = Alignment.Center) {
            Box(
                Modifier.fillMaxWidth().heightIn(max = 440.dp).fillMaxHeight().clip(r(14)).background(ph.bg),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    ph.media != null -> MediaFull(ph.media)
                    ph.plate -> NameplateLarge()
                    else -> Txt(ph.label, 22, 700, ph.fg, spacing = 1f)
                }
            }
            NavArrow(Icons.ChevronLeft, index > 0, Modifier.align(Alignment.CenterStart).padding(start = 6.dp)) {
                if (index > 0) onIndex(index - 1)
            }
            NavArrow(Icons.ChevronRight, index < photos.size - 1, Modifier.align(Alignment.CenterEnd).padding(end = 6.dp)) {
                if (index < photos.size - 1) onIndex(index + 1)
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        ) {
            photos.indices.forEach { i ->
                Box(Modifier.size(if (i == index) 18.dp else 6.dp, 6.dp).clip(r(3)).background(if (i == index) C.Yellow else C.RadioOff))
            }
        }
    }
}

@Composable
private fun NavArrow(icon: Ico, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    RoundButton(44.dp, Color(0xB3141518), onClick, modifier.alpha(if (enabled) 1f else 0.3f)) {
        LineIcon(icon, 20.dp, C.Text, 2.6f)
    }
}

@Composable
private fun DetailTab(label: String, icon: Ico, selected: Boolean, fails: Int?, modifier: Modifier, onClick: () -> Unit) {
    val fg = if (selected) C.Yellow else C.Muted
    Box(modifier.fillMaxHeight().clickable(onClick = onClick)) {
        Row(
            Modifier.align(Alignment.Center),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            LineIcon(icon, 18.dp, fg, 2.1f)
            Txt(label, 15, if (selected) 700 else 500, fg)
            when {
                fails == null -> {}
                fails > 0 -> Box(
                    Modifier.heightIn(min = 18.dp).widthIn(min = 18.dp).background(C.Red, Capsule).padding(horizontal = 5.dp),
                    contentAlignment = Alignment.Center,
                ) { Txt(fails.toString(), 11, 800, C.OnYellow) }
                else -> Box(Modifier.size(18.dp).background(C.Green, CircleShape), contentAlignment = Alignment.Center) {
                    LineIcon(Icons.Check, 11.dp, C.OnYellow, 3.6f)
                }
            }
        }
        if (selected) {
            Box(
                Modifier.align(Alignment.BottomCenter).padding(horizontal = 24.dp).fillMaxWidth().height(3.dp)
                    .background(C.Yellow, androidx.compose.foundation.shape.RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp)),
            )
        }
    }
}
