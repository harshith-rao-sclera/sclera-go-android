package com.sclera.sclerago.presentation.validation

import com.sclera.sclerago.presentation.components.FilterFab
import com.sclera.sclerago.presentation.components.fabClearance
import com.sclera.sclerago.presentation.components.pressClickable
import com.sclera.sclerago.presentation.components.PRESSED_CARD
import androidx.compose.runtime.key
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.testTag
import com.sclera.sclerago.presentation.components.SkeletonLead
import com.sclera.sclerago.presentation.components.SkeletonList
import com.sclera.sclerago.presentation.components.SkeletonCards
import com.sclera.sclerago.presentation.components.PullRefresh
import com.sclera.sclerago.presentation.components.ToastEffect
import com.sclera.sclerago.presentation.components.dashedBorder
import com.sclera.sclerago.presentation.model.OnlineOnly
import com.sclera.sclerago.presentation.components.OfflineBanner
import com.sclera.sclerago.presentation.common.ConnectivityViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sclera.sclerago.presentation.components.pageGutter
import com.sclera.sclerago.presentation.icons.assetIco
import com.sclera.sclerago.presentation.common.asString
import com.sclera.sclerago.domain.model.Asset
import com.sclera.sclerago.domain.validation.RunMode
import com.sclera.sclerago.domain.validation.RunScope
import com.sclera.sclerago.domain.validation.RunSource
import com.sclera.sclerago.domain.validation.RunStatus
import com.sclera.sclerago.domain.validation.StepGroup
import com.sclera.sclerago.domain.validation.StepSeverity
import com.sclera.sclerago.domain.validation.ValRun
import com.sclera.sclerago.domain.validation.ValStep
import com.sclera.sclerago.domain.validation.ValTemplate
import com.sclera.sclerago.domain.validation.ValidationDef
import com.sclera.sclerago.domain.validation.isServerId
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.sclera.sclerago.domain.model.AccessBlock
import com.sclera.sclerago.presentation.components.AccessGate
import com.sclera.sclerago.presentation.components.AddFab
import com.sclera.sclerago.presentation.components.BottomSheet
import com.sclera.sclerago.presentation.components.Capsule
import com.sclera.sclerago.presentation.components.CfgCard
import com.sclera.sclerago.presentation.components.CheckBox
import com.sclera.sclerago.presentation.components.CloseButton
import com.sclera.sclerago.presentation.components.ConfirmDialog
import com.sclera.sclerago.presentation.components.GuardedClose
import com.sclera.sclerago.presentation.components.EmptyNote
import com.sclera.sclerago.presentation.components.LoadFailedNote
import com.sclera.sclerago.presentation.components.LabeledInput
import com.sclera.sclerago.presentation.components.PageBar
import com.sclera.sclerago.presentation.components.SubPageBar
import com.sclera.sclerago.presentation.components.Pill
import com.sclera.sclerago.presentation.components.RadioDot
import com.sclera.sclerago.presentation.components.SearchField
import com.sclera.sclerago.presentation.components.SheetHeader
import com.sclera.sclerago.presentation.components.blockSwipe
import com.sclera.sclerago.presentation.components.fabShadow
import com.sclera.sclerago.presentation.components.noRippleClick
import com.sclera.sclerago.R
import com.sclera.sclerago.presentation.components.r
import com.sclera.sclerago.presentation.components.topBorder
import com.sclera.sclerago.presentation.icons.Ico
import com.sclera.sclerago.presentation.icons.Icons
import com.sclera.sclerago.presentation.icons.LineIcon
import com.sclera.sclerago.presentation.theme.C
import com.sclera.sclerago.presentation.theme.Txt

/** The icon of a template (the server's keys). */
private fun templateIcon(id: String): Ico = when (id) {
    "quick_check" -> Icons.Bolt
    "ai_check" -> Icons.Sparkles
    "clean_up" -> Icons.Copy
    "full_audit" -> Icons.ShieldCheck
    else -> Icons.Clipboard
}

/** The short line under a template's name (the web's), or its description for any other template. */
@Composable
private fun templateTagline(t: ValTemplate): String = when (t.key) {
    "quick_check" -> stringResource(R.string.valt_tagline_quick_check)
    "clean_up" -> stringResource(R.string.valt_tagline_clean_up)
    "ai_check" -> stringResource(R.string.valt_tagline_ai_check)
    "full_audit" -> stringResource(R.string.valt_tagline_full_audit)
    else -> t.description
}

/** "Each asset", "Across assets" or "AI and web": what a step compares. */
@Composable
private fun groupText(g: StepGroup?) = stringResource(
    when (g) {
        StepGroup.GROUP -> R.string.valt_group_across
        StepGroup.AI -> R.string.valt_group_ai
        else -> R.string.valt_group_each
    },
)

/**
 * The Validation page (side menu → Validation): templates, saved validations and recent jobs, with the
 * floating + (new validation) and ▶ (run validation). Everything runs at the server; offline the lists show
 * what was last synced. A job opens its review; an asset opened from there shows on top ([onOpenAsset]).
 */
@Composable
fun ValidationScreen(
    propertyId: String,
    propName: String,
    assets: List<Asset>,
    onOpenAsset: (String) -> Unit,
    onClose: () -> Unit,
    /** The header's menu button: opens the side menu. */
    onMenu: () -> Unit = {},
    /** A run to open as the page opens ("Open in Validation" from an asset); [onJobOpened] once it has. */
    openJobId: String? = null,
    /** With [openJobId]: the asset whose result opens on that run. */
    openAssetId: String? = null,
    onJobOpened: () -> Unit = {},
    vm: ValidationViewModel = hiltViewModel(),
    connectivity: ConnectivityViewModel = hiltViewModel(),
) {
    LaunchedEffect(propertyId) { vm.bind(propertyId) }
    LaunchedEffect(openJobId) {
        openJobId?.let {
            vm.openReview(it, openAssetId)
            onJobOpened()
        }
    }
    val s = vm.state
    // Validations are run, kept and changed on the server: offline (or when the last read found no connection) the page
    // shows what was last synced under a banner, and its run and change buttons are left out.
    val online by connectivity.online.collectAsStateWithLifecycle()
    val offline = s.offline || !online
    LaunchedEffect(online) { if (online && s.offline) vm.reload() }
    val close = {
        vm.closeOverlays()
        onClose()
    }
    BackHandler(onBack = close)
    Box(Modifier.fillMaxSize().background(C.Bg)) {
        Column(Modifier.fillMaxSize()) {
            SubPageBar(stringResource(R.string.menu_validation), propName, onMenu)
            // Pull down to read the saved validations and runs again, as on the other pages (with a connection: also
            // after a read that found none, to try again).
            PullRefresh(s.refreshing, vm::refresh, Modifier.weight(1f).fillMaxWidth(), enabled = online) {
                // Room for the floating buttons, so the last job scrolls clear of them: the filter (once there are runs),
                // over the + and the run button (online only).
                val fabs = (if (s.runs.isNotEmpty()) 1 else 0) + (if (offline) 0 else 2)
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = pageGutter(), end = pageGutter(), bottom = fabClearance(fabs, navigationBars = true))) {
                    item(key = "top") { Spacer(Modifier.height(4.dp)) }
                    if (offline) item(key = "offline") { OfflineBanner(OnlineOnly.VALIDATION, Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) }
                    item(key = "templates") {
                        SectionTitle(stringResource(R.string.valt_templates), null) {
                            if (s.catalogLoaded) OrderButton(vm::openOrder)
                        }
                        if (s.templates.isEmpty()) {
                            if (offline) EmptyNote(stringResource(R.string.valt_templates_offline))
                            else SkeletonCards(stringResource(R.string.valt_templates_loading), Modifier.padding(horizontal = 12.dp))
                        } else Row(
                            // Every card as tall as the tallest (a handful of templates, so not lazy), buttons at the bottom.
                            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).height(IntrinsicSize.Max)
                                .padding(horizontal = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            s.templates.forEach { t ->
                                key(t.key) {
                                    TemplateCard(
                                        t, ai = t.steps.any { s.stepsByKey[it]?.usesAi == true }, online = !offline, runBlock = s.checkBlock,
                                        aiOn = s.aiOn, runs = !s.aiOnly(t.steps),
                                        onRun = { vm.runOnAll(RunSource.Template(t.key)) },
                                        onChoose = { vm.openRunSheet(RunSource.Template(t.key), pick = true) },
                                        onChecks = { vm.showTemplate(t.key) },
                                        modifier = Modifier.fillMaxHeight(),
                                    )
                                }
                            }
                        }
                    }
                    // A read that failed isn't "nothing saved yet": no counts, and one note that says so with Try again.
                    item(key = "saved-title") {
                        SectionTitle(stringResource(R.string.valt_saved_validations), s.saved.size.takeUnless { s.loadFailed })
                    }
                    if (s.saved.isEmpty()) item(key = "saved-empty") {
                        when {
                            s.loading -> SkeletonList(stringResource(R.string.val_loading), Modifier.padding(horizontal = 12.dp, vertical = 4.dp), count = 2)
                            s.loadFailed -> LoadFailedNote(stringResource(R.string.val_load_failed_note), vm::reload)
                            else -> EmptyNote(stringResource(if (s.canValidate) R.string.val_saved_empty_reviewer else R.string.val_saved_empty_checker))
                        }
                    }
                    if (s.saved.isNotEmpty()) item(key = "saved") {
                        Row(
                            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).height(IntrinsicSize.Max)
                                .padding(horizontal = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            s.saved.forEach { sv ->
                                key(sv.id) {
                                    SavedCard(
                                        sv, s.stepsByKey,
                                        onRun = if (!offline) ({ vm.openRunSheet(RunSource.Saved(sv.id)) }) else null,
                                        // Only reviewers (validate) keep saved validations; anyone with check may run them
                                        // (the others see the buttons greyed out, saying why). One that has run can't
                                        // change any more (the server answers 409): as the web, it offers Edit as new.
                                        onEdit = if (!offline) ({ if (sv.locked) vm.editAsNew(sv) else vm.editValidation(sv) }) else null,
                                        onDelete = if (!offline) ({ vm.askDelete(sv) }) else null,
                                        runBlock = s.checkBlock, keepBlock = s.validateBlock,
                                        modifier = Modifier.fillMaxHeight(),
                                    )
                                }
                            }
                        }
                    }
                    // Recent jobs: the newest 100 runs, narrowed by who ran them, status and check (the web's filters).
                    item(key = "jobs-title") { SectionTitle(stringResource(R.string.valt_recent_jobs), s.matchingRuns.size.takeUnless { s.loadFailed }) }
                    // After a failed read the note (with Try again) above already says so; nothing more here.
                    if (s.runs.isEmpty() && !s.loadFailed) item(key = "jobs-empty") {
                        if (s.loading) {
                            SkeletonList(stringResource(R.string.val_loading), Modifier.padding(horizontal = 12.dp, vertical = 4.dp), count = 3, lead = SkeletonLead.NONE, lines = 3)
                        } else EmptyNote(stringResource(R.string.val_jobs_empty))
                    }
                    else {
                        if (s.matchingRuns.isEmpty()) item(key = "jobs-none") {
                            EmptyNote(stringResource(R.string.valt_dot_pair, stringResource(R.string.valt_jobs_no_match), stringResource(R.string.valt_jobs_no_match_hint)))
                        }
                    }
                    // Every job in one card, one row each with a line between, rather than a card per job.
                    if (s.shownRuns.isNotEmpty()) item(key = "jobs-list") {
                        Column(
                            Modifier.padding(horizontal = 12.dp).fillMaxWidth().clip(r(14)).background(C.Surface)
                                .border(1.dp, C.Border, r(14)).testTag("val_jobs_list"),
                        ) {
                            s.shownRuns.forEachIndexed { i, j ->
                                key(j.id) {
                                    JobRow(
                                        j, busy = j.id in s.busyRuns, onReview = { vm.openReview(j.id) },
                                        onStart = if (!offline) ({ vm.startReady(j) }) else null,
                                        onDiscard = if (!offline) ({ vm.discard(j) }) else null,
                                        startBlock = s.checkBlock, discardBlock = s.validateBlock,
                                        modifier = if (i > 0) Modifier.topBorder(C.Border) else Modifier,
                                    )
                                }
                            }
                        }
                    }
                    // 20 rows at first; "Show more" adds as many again.
                    val left = s.matchingRuns.size - s.shownRuns.size
                    if (left > 0) item(key = "jobs-more") {
                        Box(Modifier.fillMaxWidth().padding(top = 6.dp), contentAlignment = Alignment.Center) {
                            TextAction(stringResource(R.string.valt_jobs_show_more, left), vm::showMoreJobs, color = C.Soft)
                        }
                    }
                }
            }
        }
        // Filter jobs, floating above the + and the run button (it narrows the runs already listed, so offline too).
        if (s.reviewJobId == null && s.runs.isNotEmpty()) {
            val bottom = if (offline) 14.dp else 146.dp
            val on = listOf(s.filters.by, s.filters.status, s.filters.check).count { it != null }
            val description = if (on > 0) pluralStringResource(R.plurals.valt_jobs_filter_active, on, on) else stringResource(R.string.valt_jobs_filter_title)
            FilterFab(
                on, description, vm::openJobFilter,
                modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(end = 14.dp, bottom = bottom),
                buttonModifier = Modifier.testTag("val_job_filter_fab"),
            )
        }
        // Create a validation (+), in the corner like the main screen's buttons. Runs start from a template or a saved
        // validation's Run.
        // Either is greyed out, saying why, for someone whose group can't (+ needs Validate, the run button Check).
        if (s.reviewJobId == null && !offline) {
            AccessGate(s.validateBlock, Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(end = 4.dp, bottom = 72.dp)) {
                AddFab { vm.newValidation() }
            }
            // Run validation: the floating ▶ under the +, once the templates are read.
            if (s.catalogLoaded) AccessGate(s.checkBlock, Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(end = 14.dp, bottom = 14.dp)) {
                Box(
                    Modifier.size(54.dp).fabShadow().pressClickable(shape = CircleShape) { vm.openRunSheet() }.background(C.Btn),
                    contentAlignment = Alignment.Center,
                ) { LineIcon(Icons.Play, 20.dp, C.Text, 2.2f) }
            }
        }

        // Layers on top of the page.
        s.reviewJobId?.let { id ->
            JobReview(
                s.propertyId, id, s.stepsByKey, assets, propName, onOpenAsset, onBack = { vm.openReview(null) }, onRunAgain = vm::runAgain,
                openAssetId = s.reviewAssetId, onAssetOpened = vm::reviewAssetShown,
            )
        }
        s.templateDetails?.let { key -> s.templates.firstOrNull { it.key == key } }?.let { t ->
            ChecksSheet(
                t.name, t.steps.mapNotNull { s.stepsByKey[it] }, { vm.showTemplate(null) },
                aiOn = s.aiOn, runs = !s.aiOnly(t.steps),
                onCustomize = { vm.newValidation(t) },
                onRun = if (!offline) ({ vm.showTemplate(null); vm.runOnAll(RunSource.Template(t.key)) }) else null,
                onChoose = if (!offline) ({ vm.showTemplate(null); vm.openRunSheet(RunSource.Template(t.key), pick = true) }) else null,
                runBlock = s.checkBlock, customizeBlock = s.validateBlock,
            )
        }
        // A run sheet opened from Data Collection shows there ([QuickValidate]), not here.
        s.runSheet?.takeIf { !it.quick }?.let { RunSheetView(vm, s, it, assets) }
        s.jobDraft?.let { draft ->
            JobFilterSheet(draft, s.jobOptions, s::jobCount, vm::editJobDraft, vm::resetJobDraft, vm::closeJobFilter)
        }
        s.runSheet?.takeIf { it.picking && !it.quick }?.let { AssetPicker(vm, it, assets) }
        s.orderSheet?.let { OrderSheet(vm, s, it) }
        s.editor?.let { EditorPage(vm, s, it) }
        s.confirmDelete?.let { v ->
            ConfirmDialog(
                icon = Icons.Trash, iconBg = Color(0xFF2A1919), iconFg = C.Red,
                title = stringResource(R.string.val_delete_title, v.name),
                body = stringResource(R.string.val_delete_body),
                no = stringResource(R.string.val_cancel), yes = stringResource(R.string.action_delete), onNo = { vm.askDelete(null) }, onYes = vm::delete,
            )
        }
        ToastEffect(s.message)
        // A run started here (or saved for later) is said with a button that opens it; Data Collection's own show there.
        JobBannerEffect(s.banner?.takeIf { !it.quick }, onOpen = { id -> id?.let(vm::openReview) }, onShown = vm::bannerShown)
    }
}

// ---------------------------------------------------------------- Page pieces

@Composable
internal fun SectionTitle(title: String, count: Int?, trailing: (@Composable () -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp, top = 18.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Txt(title, 15, 700)
        count?.let {
            Box(
                Modifier.height(22.dp).widthIn(min = 22.dp).clip(Capsule).background(C.Raised).padding(horizontal = 6.dp),
                contentAlignment = Alignment.Center,
            ) { Txt(it.toString(), 10, 700, C.Soft) }
        }
        Spacer(Modifier.weight(1f))
        trailing?.invoke()
    }
}

/** The small grey count after a section's title (min 22 x 20dp). */
@Composable
internal fun CountPill(n: Int) {
    Box(Modifier.heightIn(min = 20.dp).widthIn(min = 22.dp).clip(Capsule).background(C.Raised).padding(horizontal = 7.dp), contentAlignment = Alignment.Center) {
        Txt(n.toString(), 11, 700, C.Muted)
    }
}

/** The yellow action button; [pill] makes it a full capsule (the Run sheet's), [play] shows the play icon. */
@Composable
internal fun YellowButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    height: Int = 44,
    textSize: Int = 13,
    pill: Boolean = false,
    play: Boolean = true,
) {
    val shape = if (pill) Capsule else r(9)
    Row(
        modifier.height(height.dp).pressClickable(shape = shape, enabled = enabled, onClick = onClick).background(if (enabled) C.Yellow else C.Raised)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        val fg = if (enabled) C.OnYellow else C.Dim
        if (play) LineIcon(Icons.Play, 13.dp, fg)
        Txt(text, textSize, 700, fg)
    }
}

/** "AI": it uses AI and the web; [off] (AI is off for the property) "AI off", greyed, with why for screen readers. */
@Composable
internal fun AiBadge(off: Boolean = false) {
    val why = stringResource(R.string.ai_off)
    Row(
        Modifier.clip(Capsule).background(if (off) C.Raised else Color(0xFF26233A)).padding(horizontal = 6.dp, vertical = 2.dp)
            .then(if (off) Modifier.semantics { contentDescription = why } else Modifier),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        val fg = if (off) C.Muted else C.Purple
        LineIcon(Icons.Sparkles, 10.dp, fg)
        Txt(stringResource(if (off) R.string.valt_ai_off_badge else R.string.valt_ai_badge), 10, 700, fg)
    }
}

/** Why a run button is off: AI is off for the property and everything it would run is an AI check. */
@Composable
private fun AiOffNote(modifier: Modifier = Modifier) {
    Txt(stringResource(R.string.ai_off), 11, 400, C.Muted, modifier, maxLines = Int.MAX_VALUE)
}

/**
 * A template: its icon, name and AI badge ("AI off" when [aiOn] is false), its tagline, and Run on all assets / Choose
 * assets ([online] only; greyed out, saying why, while [runBlock] holds the user back); with AI off a template of nothing
 * but AI checks ([runs] false) can't run, and says why.
 */
@Composable
private fun TemplateCard(
    t: ValTemplate,
    ai: Boolean,
    online: Boolean,
    runBlock: AccessBlock?,
    onRun: () -> Unit,
    onChoose: () -> Unit,
    onChecks: () -> Unit,
    modifier: Modifier = Modifier,
    aiOn: Boolean = true,
    runs: Boolean = true,
) {
    Column(
        modifier.width(CARD_WIDTH).clip(r(14)).background(C.Surface).border(1.dp, C.Border, r(14)).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(34.dp).clip(r(9)).background(C.YTintBg), contentAlignment = Alignment.Center) {
                LineIcon(templateIcon(t.key), 16.dp, C.Yellow, 2f)
            }
            Txt(t.name, 14, 700, modifier = Modifier.weight(1f, fill = false))
            if (ai) AiBadge(off = !aiOn)
        }
        Txt(templateTagline(t), 12, 400, C.Muted, maxLines = Int.MAX_VALUE)
        // Fills the room a taller card's text takes, so every card's buttons line up at the bottom.
        Spacer(Modifier.weight(1f))
        if (online && runBlock == null && !runs) AiOffNote()
        if (online) AccessGate(runBlock, Modifier.fillMaxWidth()) {
            YellowButton(stringResource(R.string.valt_run_on_all), onRun, Modifier.fillMaxWidth(), enabled = runs, height = 40)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (online) AccessGate(runBlock) {
                Txt(
                    stringResource(R.string.valt_choose_assets), 11, 600, if (runs) C.Soft else C.Dim,
                    Modifier.pressClickable(shape = r(6), enabled = runs, onClick = onChoose).padding(vertical = 4.dp),
                )
            }
            Spacer(Modifier.weight(1f))
            Txt(pluralStringResource(R.plurals.valt_see_steps, t.steps.size, t.steps.size), 11, 600, C.Yellow, Modifier.pressClickable(shape = r(6), onClick = onChecks).padding(vertical = 4.dp))
        }
    }
}

/** The width of a template card and a saved validation's card, so the two rows match. */
private val CARD_WIDTH = 272.dp

/** The least height of a saved validation's card, so a short one isn't cramped. */
private val SAVED_MIN_HEIGHT = 190.dp

/** The least width of a saved validation's Edit button (about Run's), so a short label doesn't make it a small box. */
private val EDIT_MIN_WIDTH = 88.dp

/** Default order (Templates' head): an outlined capsule with the sort arrows; opens the templates' order. */
@Composable
private fun OrderButton(onClick: () -> Unit) {
    Row(
        Modifier.heightIn(min = 40.dp).pressClickable(shape = Capsule, onClick = onClick).border(1.dp, C.Border, Capsule)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        LineIcon(Icons.SortArrows, 16.dp, C.Text, 2f)
        Txt(stringResource(R.string.valt_default_order), 13, 600)
    }
}

/**
 * A saved validation, a card in a row that scrolls sideways (as the templates): its name with a lock once it has run,
 * its steps, when it last ran, then Run (greyed out, saying why, while [runBlock] holds the user back), Edit (Edit as new
 * once it has run) and Delete ([keepBlock]). A button whose action is null (offline) is left out.
 */
@Composable
private fun SavedCard(
    sv: ValidationDef,
    steps: Map<String, ValStep>,
    onRun: (() -> Unit)?,
    onEdit: (() -> Unit)?,
    onDelete: (() -> Unit)?,
    runBlock: AccessBlock?,
    keepBlock: AccessBlock?,
    modifier: Modifier = Modifier,
) {
    Column(
        // Roomier than a template card (same width): its name, steps and last run each get their own breathing space.
        modifier.width(CARD_WIDTH).heightIn(min = SAVED_MIN_HEIGHT).clip(r(14)).background(C.Surface).border(1.dp, C.Border, r(14))
            .padding(horizontal = 12.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        val last = sv.lastRun
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            // The name, with a lock at the end once it has run: its runs keep the steps they ran with, so it can't change.
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Txt(sv.name, 15, 700, modifier = Modifier.weight(1f), maxLines = 2)
                if (sv.locked) {
                    val why = stringResource(R.string.valt_ran_note)
                    Box(Modifier.padding(top = 2.dp).semantics { contentDescription = why }) { LineIcon(Icons.Lock, 14.dp, C.Soft, 2.2f) }
                }
            }
            Txt(sv.steps.joinToString(" · ") { stepName(steps, it) }, 12, 400, C.Soft, maxLines = 2)
            Txt(
                when {
                    last == null -> stringResource(R.string.valt_not_run_yet)
                    last.status.active -> stringResource(R.string.valt_last_run_running, relativeTime(last.createdAt ?: 0L))
                    else -> stringResource(R.string.valt_last_run_review, relativeTime(last.createdAt ?: 0L), last.openCount)
                },
                11, 400, C.Muted, maxLines = 2,
            )
        }
        // Fills the room a taller card's text takes, so every card's buttons line up at the bottom.
        Spacer(Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            onRun?.let { AccessGate(runBlock) { YellowButton(stringResource(R.string.valt_run), it, height = 40) } }
            onEdit?.let {
                // One that has run: Edit as new, with the copy icon (the web's), else Edit with a pencil; an outlined
                // button at least as wide as Run, so a short "Edit" isn't a small box.
                AccessGate(keepBlock) {
                    Row(
                        Modifier.height(40.dp).widthIn(min = EDIT_MIN_WIDTH).pressClickable(shape = r(9), onClick = it)
                            .border(1.dp, C.Border, r(9)).padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                    ) {
                        LineIcon(if (sv.locked) Icons.Clone else Icons.Pencil, 14.dp, C.Text, 2.2f)
                        Txt(stringResource(if (sv.locked) R.string.valt_edit_as_new else R.string.action_edit), 13, 600)
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            onDelete?.let {
                val label = stringResource(R.string.action_delete)
                AccessGate(keepBlock) {
                    Box(
                        Modifier.size(40.dp).pressClickable(shape = r(9), onClick = it).semantics { contentDescription = label },
                        contentAlignment = Alignment.Center,
                    ) { LineIcon(Icons.Trash, 19.dp, C.Red, 2f) }
                }
            }
        }
    }
}

/** Red / orange / green bar for failed / to look at / passed. */
@Composable
internal fun ResultBar(failed: Int, look: Int, passed: Int, modifier: Modifier = Modifier, barHeight: Dp = 6.dp) {
    val total = (failed + look + passed).coerceAtLeast(1)
    Row(modifier.fillMaxWidth().height(barHeight).clip(Capsule).background(C.Raised)) {
        if (failed > 0) Box(Modifier.weight(failed.toFloat() / total).fillMaxHeight().background(C.Red))
        if (look > 0) Box(Modifier.weight(look.toFloat() / total).fillMaxHeight().background(C.Orange))
        if (passed > 0) Box(Modifier.weight(passed.toFloat() / total).fillMaxHeight().background(C.Green))
    }
}

@Composable
internal fun Counts(failed: Int, look: Int, passed: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        listOf(
            Triple(failed, stringResource(R.string.valt_count_failed), C.Red),
            Triple(look, stringResource(R.string.valt_count_need_look), C.Orange),
            Triple(passed, stringResource(R.string.valt_count_passed), C.Green),
        ).forEach { (n, l, c) ->
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                Txt(n.toString(), 11, 700, c)
                Txt(l, 11, 400, C.Muted)
            }
        }
    }
}

/** Running (spinning), Queued, Ready, Failed or Completed. */
@Composable
internal fun StatusChip(status: RunStatus) {
    val (bg, fg, label) = when (status) {
        RunStatus.RUNNING -> Triple(C.YTintBg, C.Yellow, stringResource(R.string.valt_status_running))
        RunStatus.QUEUED -> Triple(C.YTintBg, C.Yellow, stringResource(R.string.valt_status_queued))
        RunStatus.READY -> Triple(C.Raised, C.Blue, stringResource(R.string.valt_status_ready))
        RunStatus.FAILED -> Triple(Color(0xFF2A1919), C.Red, stringResource(R.string.rev_stat_failed))
        RunStatus.COMPLETED -> Triple(Color(0xFF17241D), C.Green, stringResource(R.string.valt_status_completed))
    }
    Row(
        Modifier.height(26.dp).clip(Capsule).background(bg).padding(horizontal = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        if (status.active) {
            val spin by rememberInfiniteTransition(label = "job").animateFloat(
                0f, 360f, infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart), label = "spin",
            )
            Box(Modifier.size(9.dp).rotate(spin).border(1.5.dp, fg, CircleShape).padding(2.dp))
        } else {
            Box(Modifier.size(7.dp).clip(CircleShape).background(fg))
        }
        Txt(label, 10, 600, fg)
    }
}

/** The bar of a running job: how far it has got (or a moving bar while it's queued). */
@Composable
internal fun JobProgress(j: ValRun, barHeight: Dp = 6.dp) {
    if (j.status == RunStatus.RUNNING && j.total > 0) {
        Box(Modifier.fillMaxWidth().height(barHeight).clip(Capsule).background(C.Raised)) {
            Box(Modifier.fillMaxWidth((j.processed.toFloat() / j.total).coerceIn(0.08f, 1f)).fillMaxHeight().background(C.Yellow.copy(alpha = 0.7f)))
        }
    } else {
        val p by rememberInfiniteTransition(label = "bar").animateFloat(
            0.1f, 0.9f, infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Reverse), label = "p",
        )
        Box(Modifier.fillMaxWidth().height(barHeight).clip(Capsule).background(C.Raised)) {
            Box(Modifier.fillMaxWidth(p).fillMaxHeight().background(C.Yellow.copy(alpha = 0.7f)))
        }
    }
    val phase = j.phase ?: pluralStringResource(R.plurals.valt_checking_assets, j.total, j.total)
    Txt(
        if (j.total > 0) stringResource(R.string.valt_progress_count, phase, j.processed, j.total)
        else stringResource(R.string.valt_progress_pending, phase),
        11, 400, C.Muted, maxLines = Int.MAX_VALUE,
    )
}

/** "All assets (12)", "3 chosen assets"… what a job covered. */
@Composable
internal fun scopeText(j: ValRun): String = ValidationViewModel.scopeText(j).asString()

@Composable
private fun JobRow(
    j: ValRun,
    busy: Boolean,
    onReview: () -> Unit,
    onStart: (() -> Unit)?,
    onDiscard: (() -> Unit)?,
    /** Why the user can't start a ready run (Check) or discard one (Validate): greyed out, saying why; null when they can. */
    startBlock: AccessBlock?,
    discardBlock: AccessBlock?,
    modifier: Modifier = Modifier,
) {
    val ready = j.status == RunStatus.READY
    // A row of the Recent jobs card (the card draws the frame; rows after the first draw the line above them).
    Column(
        modifier.pressClickable(pressed = PRESSED_CARD, enabled = !ready, onClick = onReview).fillMaxWidth().padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatusChip(j.status)
            // When it was started (or set up), with who started it on the line below, as the web's: both against the end.
            val ago = relativeTime(j.startedAt ?: j.createdAt ?: 0L)
            val whenText = if (ready) stringResource(R.string.valt_set_up_ago, ago) else ago
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Txt(whenText, 11, 400, C.Muted, align = TextAlign.End)
                j.startedBy?.let { Txt(it, 11, 400, C.Muted, align = TextAlign.End) }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Txt(j.name, 14, 700)
                Txt(stringResource(R.string.valt_dot_pair, scopeText(j), pluralStringResource(R.plurals.rev_steps, j.steps.size, j.steps.size)), 11, 400, C.Muted)
            }
            // Review (View while it runs): no fill, a white outline with small corners.
            if (!ready) {
                Box(
                    Modifier.height(28.dp).clip(r(6)).border(1.dp, C.Text, r(6)).pressClickable(shape = r(6), role = Role.Button, onClick = onReview)
                        .padding(horizontal = 10.dp),
                    contentAlignment = Alignment.Center,
                ) { Txt(stringResource(if (j.status.active) R.string.valt_view else R.string.rev_title), 11, 600, C.Text) }
            }
        }
        when {
            ready -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Txt(
                    stringResource(if (j.scope == RunScope.CHANGED) R.string.valt_ready_changed else R.string.valt_ready_not_started),
                    11, 400, C.Muted, Modifier.weight(1f), maxLines = Int.MAX_VALUE,
                )
                onDiscard?.let {
                    AccessGate(discardBlock) {
                        Txt(stringResource(R.string.action_discard), 12, 700, C.Red, Modifier.pressClickable(shape = r(8), enabled = !busy, onClick = it).padding(horizontal = 8.dp, vertical = 8.dp))
                    }
                }
                onStart?.let { AccessGate(startBlock) { YellowButton(stringResource(if (busy) R.string.valt_starting else R.string.valt_run), it, enabled = !busy, height = 36) } }
            }
            j.status.active -> JobProgress(j)
            j.status == RunStatus.FAILED && j.processed == 0 -> Txt(j.statusDetail ?: stringResource(R.string.rev_stat_failed), 11, 400, C.Red, maxLines = Int.MAX_VALUE)
            else -> {
                ResultBar(j.failedCount, j.warningCount, j.passedCount)
                Counts(j.failedCount, j.warningCount, j.passedCount)
            }
        }
    }
}

// ---------------------------------------------------------------- Sheets

@Composable
internal fun OptionCard(
    title: String,
    body: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    badge: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier.pressClickable(shape = r(12), onClick = onClick).background(if (selected) C.YTintBg else C.Bg)
            .border(if (selected) 1.5.dp else 1.dp, if (selected) C.Yellow else C.Border2, r(12))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Txt(title, 13, 700, modifier = Modifier.weight(1f, fill = false), maxLines = 2)
            badge?.invoke()
        }
        Txt(body, 11, 400, if (selected) C.Soft else C.Muted, maxLines = Int.MAX_VALUE)
    }
}

@Composable
private fun RuleChip(text: String, ai: Boolean, aiOn: Boolean = true) {
    // An AI check with AI off is skipped: greyed.
    val skipped = ai && !aiOn
    Row(
        Modifier.clip(Capsule).background(C.Raised).alpha(if (skipped) 0.5f else 1f).padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (ai) LineIcon(Icons.Sparkles, 10.dp, if (skipped) C.Muted else C.Purple)
        Txt(text, 11, 500, C.Soft)
    }
}

private class RunOption(val source: RunSource, val icon: String, val name: String, val tagline: String, val ai: Boolean, val saved: Boolean, val steps: List<String>)

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
internal fun RunSheetView(vm: ValidationViewModel, s: ValidationUiState, sh: RunSheetState, assets: List<Asset>) {
    val steps = s.stepsByKey
    // The templates, then the user's saved validations (and the steps of a job being run again).
    val options = s.templates.map { t -> RunOption(RunSource.Template(t.key), t.key, t.name, templateTagline(t), t.steps.any { steps[it]?.usesAi == true }, false, t.steps) } +
        s.saved.map { sv -> RunOption(RunSource.Saved(sv.id), "", sv.name, sv.description?.takeIf { it.isNotBlank() } ?: stringResource(R.string.valt_saved_validation), sv.steps.any { steps[it]?.usesAi == true }, true, sv.steps) } +
        listOfNotNull((sh.source as? RunSource.Steps)?.let { src -> RunOption(src, "", stringResource(R.string.valt_these_steps), stringResource(R.string.valt_these_steps_sub), src.keys.any { steps[it]?.usesAi == true }, false, src.keys) })
    val chosen = options.firstOrNull { it.source == sh.source }
    val count = when (sh.mode) {
        RunMode.ALL -> assets.count { isServerId(it.id) }
        RunMode.CHOSEN -> sh.chosen.size
        RunMode.CHANGED -> -1
    }
    GuardedClose(sh.changed, vm::closeRunSheet) { close ->
        BottomSheet(close) {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 10.dp, top = 4.dp, bottom = 8.dp), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Txt(stringResource(R.string.valt_run_validation), 16, 700)
                    Txt(stringResource(R.string.val_run_sub), 13, 400, C.Muted, maxLines = Int.MAX_VALUE)
                }
                CloseButton(close, size = 36.dp, iconSize = 14.dp)
            }
            Column(
                Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Txt(stringResource(R.string.valt_what_to_check), 12, 600)
                options.forEach { o ->
                    val on = sh.source == o.source
                    Column(
                        Modifier.fillMaxWidth().pressClickable(shape = r(12), pressed = PRESSED_CARD) { vm.setRunSource(o.source) }.background(if (on) C.YTintBg else C.Surface)
                            .border(if (on) 1.5.dp else 1.dp, if (on) C.Yellow else C.Border, r(12))
                            .padding(10.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Box(
                                Modifier.size(34.dp).clip(r(9)).background(if (on) C.Yellow.copy(alpha = 0.18f) else C.Raised),
                                contentAlignment = Alignment.Center,
                            ) {
                                LineIcon(templateIcon(o.icon), 16.dp, C.Yellow, 2f)
                            }
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Txt(o.name, 13, 700, modifier = Modifier.weight(1f, fill = false))
                                    if (o.ai) AiBadge(off = !s.aiOn)
                                    if (o.saved) Txt(stringResource(R.string.valt_saved_badge), 10, 600, C.Muted)
                                }
                                Txt(stringResource(R.string.valt_dot_pair, o.tagline, pluralStringResource(R.plurals.rev_steps, o.steps.size, o.steps.size)), 12, 400, C.Muted)
                            }
                            RadioDot(on)
                        }
                        // The chosen one shows what it checks.
                        if (on && o.steps.isNotEmpty()) FlowRow(
                            Modifier.padding(start = 40.dp),
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                            verticalArrangement = Arrangement.spacedBy(5.dp),
                        ) {
                            o.steps.forEach { RuleChip(stepName(steps, it), steps[it]?.usesAi == true, s.aiOn) }
                        }
                        // AI off for the property: its AI checks are skipped (and one of nothing but AI checks can't run).
                        if (on && o.ai && !s.aiOn) Txt(
                            stringResource(R.string.valt_ai_off_skipped), 11, 400, C.Muted, Modifier.padding(start = 40.dp, top = 6.dp),
                            maxLines = Int.MAX_VALUE,
                        )
                    }
                }
                // The templates or the saved validations are still being read: placeholder choices where they will go.
                if ((!s.catalogLoaded && !s.catalogFailed) || s.loading) {
                    SkeletonList(stringResource(R.string.val_loading), count = if (options.isEmpty()) 3 else 1, gap = 6.dp)
                }

                Txt(stringResource(R.string.valt_which_assets), 12, 600, modifier = Modifier.padding(top = 10.dp))
                val modes = listOf(RunMode.ALL, RunMode.CHOSEN, RunMode.CHANGED)
                ScopeTabs(
                    listOf(
                        stringResource(R.string.valt_mode_all, assets.count { isServerId(it.id) }),
                        stringResource(R.string.valt_mode_choose),
                        stringResource(R.string.valt_mode_changed),
                    ),
                    modes.indexOf(sh.mode),
                ) { i ->
                    val m = modes[i]
                    if (m == RunMode.CHOSEN) vm.openPicker() else vm.setRunMode(m)
                }
                when (sh.mode) {
                    RunMode.CHOSEN -> pluralStringResource(R.plurals.valt_ticked_choose, sh.chosen.size, sh.chosen.size)
                    RunMode.CHANGED -> stringResource(R.string.valt_changed_hint)
                    RunMode.ALL -> null
                }?.let { Txt(it, 11, 400, C.Muted, Modifier.padding(top = 2.dp), maxLines = Int.MAX_VALUE) }
                Spacer(Modifier.height(4.dp))
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Just the two actions; what will be checked, and on which assets, shows above them. AI-only steps can't run while
                // the property's AI is off.
                val ok = chosen != null && count != 0 && !sh.busy && !s.aiOnly(chosen.steps)
                val name = chosen?.name ?: stringResource(R.string.valt_run_name_fallback)
                val label = when {
                    count == 0 -> stringResource(if (sh.mode == RunMode.CHOSEN) R.string.valt_tick_first else R.string.valt_no_assets_to_check)
                    count < 0 -> stringResource(R.string.valt_run_on_changed, name)
                    else -> pluralStringResource(R.plurals.valt_run_on_n, count, count, name)
                }
                // Greyed out, saying why, for someone whose group can't run validations (Check).
                AccessGate(s.checkBlock) {
                    Pill(stringResource(R.string.valt_save_as_ready), { if (ok) vm.submitRunSheet(now = false) }, Modifier.alpha(if (ok) 1f else 0.5f), height = 44.dp, hPad = 14.dp, size = 13)
                }
                AccessGate(s.checkBlock, Modifier.weight(1f)) {
                    YellowButton(label, { vm.submitRunSheet(now = true) }, Modifier.fillMaxWidth(), enabled = ok, height = 44)
                }
            }
        }
    }
}

/** Full-width segmented control (the asset scope in Run validation). */
@Composable
private fun ScopeTabs(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(r(12)).background(C.Raised).padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        labels.forEachIndexed { i, l ->
            val on = i == selected
            Box(
                Modifier.weight(1f).height(32.dp).pressClickable(shape = r(8)) { onSelect(i) }.background(if (on) C.Yellow else Color.Transparent),
                contentAlignment = Alignment.Center,
            ) { Txt(l, 13, if (on) 700 else 600, if (on) C.OnYellow else C.Muted, maxLines = 1) }
        }
    }
}

/** Choose assets: only synced ones (with a server id) can be validated. */
@Composable
internal fun AssetPicker(vm: ValidationViewModel, sh: RunSheetState, assets: List<Asset>) {
    var query by remember { mutableStateOf("") }
    val synced = assets.filter { isServerId(it.id) }
    val q = query.trim().lowercase()
    val list = if (q.isEmpty()) synced else synced.filter { q in "${it.name} ${it.detail} ${it.where}".lowercase() }
    BottomSheet(vm::closePicker, maxHeightFraction = 0.85f) {
        SheetHeader(stringResource(R.string.valt_choose_assets), pluralStringResource(R.plurals.valt_n_ticked, sh.chosen.size, sh.chosen.size), vm::closePicker)
        Box(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
            SearchField(query, { query = it }, stringResource(R.string.val_search_assets))
        }
        val all = list.isNotEmpty() && list.all { it.id in sh.chosen }
        if (list.isNotEmpty()) Row(
            Modifier.pressClickable(pressed = PRESSED_CARD) { vm.setChosen(list.map { it.id }, !all) }.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CheckBox(all)
            Txt(stringResource(if (all) R.string.val_clear_shown else R.string.val_select_shown, list.size), 13, 600)
        }
        if (list.isEmpty()) Txt(stringResource(R.string.val_no_asset_match), 12, 400, C.Muted, Modifier.padding(horizontal = 16.dp, vertical = 12.dp), maxLines = Int.MAX_VALUE)
        LazyColumn(Modifier.weight(1f, fill = false)) {
            items(list, key = { it.id }) { a ->
                Row(
                    Modifier.pressClickable(pressed = PRESSED_CARD) { vm.toggleChosen(a.id) }.fillMaxWidth().topBorder(C.Border)
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    CheckBox(a.id in sh.chosen)
                    Box(Modifier.size(34.dp).clip(r(9)).background(C.Raised), contentAlignment = Alignment.Center) {
                        LineIcon(assetIco(a), 17.dp, C.Yellow, 1.9f)
                    }
                    Column(Modifier.weight(1f)) {
                        Txt(a.name.ifBlank { stringResource(R.string.val_unnamed) }, 13, 600)
                        Txt(listOf(a.detail, a.where).filter { it.isNotBlank() }.joinToString(" · "), 11, 400, C.Muted, maxLines = Int.MAX_VALUE)
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().topBorder(C.Border).padding(horizontal = 16.dp, vertical = 12.dp)) {
            Pill(
                stringResource(R.string.action_done), vm::closePicker, Modifier.fillMaxWidth(), bg = C.Yellow, fg = C.OnYellow, height = 44.dp, size = 14, weight = 700,
            )
        }
    }
}

/** A template's steps, each with what it checks. */
@Composable
private fun ChecksSheet(
    name: String,
    steps: List<ValStep>,
    onClose: () -> Unit,
    onCustomize: (() -> Unit)?,
    onRun: (() -> Unit)? = null,
    onChoose: (() -> Unit)? = null,
    /** AI is on in the property; off, its AI steps are skipped. */
    aiOn: Boolean = true,
    /** It has something to run (with AI off, not when every step is an AI one). */
    runs: Boolean = true,
    /** Why the user can't run it (Check) or customise it into a saved validation (Validate): greyed out, saying why. */
    runBlock: AccessBlock? = null,
    customizeBlock: AccessBlock? = null,
) {
    BottomSheet(onClose) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 8.dp, bottom = 14.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Txt(name, 22, 700, maxLines = 2)
                Txt(
                    pluralStringResource(
                        when {
                            steps.none { it.usesAi } -> R.plurals.rev_steps
                            aiOn -> R.plurals.valt_steps_some_ai
                            else -> R.plurals.valt_steps_ai_off
                        },
                        steps.size, steps.size,
                    ),
                    13, 400, C.Muted, maxLines = Int.MAX_VALUE,
                )
            }
            CloseButton(onClose, size = 40.dp, iconSize = 16.dp)
        }
        Column(
            Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            steps.forEach { r ->
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    LineIcon(Icons.CheckCircle, 22.dp, C.Yellow, 2f, Modifier.padding(top = 1.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Txt(r.name, 15, 600, modifier = Modifier.weight(1f, fill = false), maxLines = 2)
                            if (r.usesAi) AiBadge(off = !aiOn)
                        }
                        Txt(r.description, 12, 400, C.Muted, maxLines = Int.MAX_VALUE)
                        Txt(
                            pluralStringResource(R.plurals.valt_checks_list, r.checks.size, r.checks.size, r.checks.joinToString(" · ") { it.short }),
                            11, 400, C.Soft, maxLines = Int.MAX_VALUE,
                        )
                        Txt(stringResource(stepKind(r.group)), 11, 500, C.Muted)
                    }
                }
            }
            Txt(stringResource(R.string.val_steps_in_order), 12, 400, C.Muted, maxLines = Int.MAX_VALUE)
            if (!runs && runBlock == null && (onRun != null || onChoose != null)) AiOffNote()
            onRun?.let { AccessGate(runBlock, Modifier.fillMaxWidth()) { YellowButton(stringResource(R.string.valt_run_on_all), it, Modifier.fillMaxWidth(), enabled = runs, height = 44) } }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                onChoose?.let { choose ->
                    AccessGate(runBlock, Modifier.weight(1f)) {
                        Pill(stringResource(R.string.valt_choose_assets), { if (runs) choose() }, Modifier.fillMaxWidth().alpha(if (runs) 1f else 0.4f), height = 40.dp, size = 13)
                    }
                }
                onCustomize?.let { AccessGate(customizeBlock, Modifier.weight(1f)) { Pill(stringResource(R.string.valt_customize), it, Modifier.fillMaxWidth(), height = 40.dp, size = 13) } }
            }
        }
    }
}

/** What a step compares, as the web's step details say it. */
private fun stepKind(g: StepGroup) = when (g) {
    StepGroup.GROUP -> R.string.val_kind_group
    StepGroup.AI -> R.string.val_kind_ai
    StepGroup.FIELD -> R.string.val_kind_field
}

/** The order templates and custom runs check steps in (the user's own), with the step list's look. */
@Composable
private fun OrderSheet(vm: ValidationViewModel, s: ValidationUiState, o: OrderSheetState) {
    GuardedClose(o.changed, vm::closeOrder) { close ->
        BottomSheet(close) {
            SheetHeader(stringResource(R.string.valt_default_order), stringResource(R.string.val_order_sub), close)
            Column(
                Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (o.loading && o.steps.isEmpty()) SkeletonList(stringResource(R.string.val_loading), count = 4, lead = SkeletonLead.NONE, lines = 1)
                ReorderableSteps(o.steps, { id, by -> vm.moveOrderStep(o.steps.indexOf(id), by) }) { i, key, modifier, handle ->
                    StepRow(
                        i, stepName(s.stepsByKey, key), groupText(s.stepsByKey[key]?.group), first = i == 0, last = i == o.steps.size - 1,
                        onMove = { vm.moveOrderStep(i, it) }, onInfo = null, onRemove = null, modifier = modifier, handle = handle,
                    )
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (o.custom) Pill(stringResource(R.string.valt_reset_default), { if (!o.saving) vm.resetOrder() }, height = 44.dp, hPad = 14.dp, size = 13)
                Pill(
                    stringResource(R.string.valt_save_order), { if (!o.loading && !o.saving) vm.saveOrder() }, Modifier.weight(1f), bg = C.Yellow, fg = C.OnYellow,
                    height = 44.dp, size = 13, weight = 700,
                )
            }
        }
    }
}

/**
 * New validation (full page): name and description, its steps (checked on every asset, in order: start from a
 * template, reorder with the handle or arrows; the gear shows a step and what it looks at) and the steps to add.
 */
@Composable
private fun EditorPage(vm: ValidationViewModel, s: ValidationUiState, e: EditorState) {
    // Leaving with anything changed asks first.
    Box(Modifier.fillMaxSize()) { GuardedClose(e.dirty, vm::closeEditor) { close -> EditorPageBody(vm, s, e, close) } }
}

@Composable
private fun EditorPageBody(vm: ValidationViewModel, s: ValidationUiState, e: EditorState, close: () -> Unit) {
    BackHandler(onBack = close)
    var q by remember { mutableStateOf("") }
    var infoFor by remember { mutableStateOf<String?>(null) }
    val ok = e.complete && !e.saving
    val steps = s.stepsByKey
    Column(Modifier.fillMaxSize().background(C.Bg).noRippleClick { }.blockSwipe()) {
        PageBar(
            stringResource(
                when {
                    e.id != null -> R.string.val_editor_edit
                    e.copyOf != null -> R.string.val_editor_copy
                    else -> R.string.val_editor_new
                },
            ),
            null, close,
        )
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = pageGutter()).navigationBarsPadding().imePadding().padding(horizontal = 6.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // A copy of one that has already run: said up front, so nobody edits it expecting the original to change.
            e.copyOf?.let { CopyNote(it) }
            // Name, description, save
            CfgCard {
                Column(Modifier.padding(EditorCardPadding), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    LabeledInput(
                        stringResource(R.string.val_editor_name), e.name, vm::setEditorName,
                        stringResource(R.string.val_editor_name_hint), compact = true,
                    )
                    LabeledInput(
                        stringResource(R.string.val_editor_description), e.description, vm::setEditorDescription,
                        stringResource(R.string.val_editor_description_hint), compact = true,
                    )
                    Row(Modifier.padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Pill(stringResource(R.string.val_cancel), close, height = 42.dp, hPad = 14.dp, size = 13)
                        // Saving needs Validate, Save and run Check too: greyed out, saying why, for whoever lacks them.
                        AccessGate(s.validateBlock) {
                            Pill(
                                stringResource(R.string.val_save), { if (ok) vm.saveEditor(andRun = false) }, Modifier.alpha(if (ok) 1f else 0.5f),
                                height = 42.dp, hPad = 16.dp, size = 13,
                            )
                        }
                        AccessGate(s.validateBlock ?: s.checkBlock, Modifier.weight(1f)) {
                            Pill(
                                stringResource(R.string.val_editor_save_and_run), { if (ok) vm.saveEditor(andRun = true) },
                                Modifier.fillMaxWidth().alpha(if (ok) 1f else 0.5f),
                                bg = if (ok) C.Yellow else C.Btn, fg = if (ok) C.OnYellow else C.Text, height = 42.dp, hPad = 18.dp, size = 13, weight = 700,
                            )
                        }
                    }
                }
            }

            // Steps
            CfgCard {
                Column(Modifier.padding(EditorCardPadding), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Txt(stringResource(R.string.val_editor_steps), 15, 700)
                        CountPill(e.steps.size)
                        Spacer(Modifier.weight(1f))
                        if (e.steps.isNotEmpty()) {
                            Txt(
                                stringResource(R.string.val_editor_clear_all), 11, 600, C.Soft,
                                Modifier.pressClickable(shape = r(8), onClick = vm::clearSteps).padding(4.dp),
                            )
                        }
                    }
                    Txt(stringResource(R.string.val_editor_steps_hint), 11, 400, C.Muted, maxLines = Int.MAX_VALUE)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Txt(stringResource(R.string.val_editor_start_from), 11, 500, C.Muted)
                        Spacer(Modifier.width(6.dp))
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            s.templates.forEach { t ->
                                // The template the steps came from stands out, as on the web.
                                val on = e.templateKey == t.key
                                Box(
                                    Modifier.heightIn(min = 34.dp).pressClickable(shape = Capsule) { vm.startFrom(t) }.background(if (on) C.YTintBg else Color.Transparent)
                                        .border(1.dp, if (on) C.Yellow else C.Border2, Capsule)
                                        .padding(horizontal = 12.dp, vertical = 7.dp),
                                    contentAlignment = Alignment.Center,
                                ) { Txt(t.name, 11, 600, if (on) C.Yellow else C.Text) }
                            }
                            if (e.undo != null) {
                                Txt(
                                    stringResource(R.string.val_undo), 11, 700, C.Yellow,
                                    Modifier.pressClickable(shape = r(8), onClick = vm::undoSteps).padding(horizontal = 6.dp, vertical = 6.dp),
                                )
                            }
                        }
                    }
                    if (e.steps.isEmpty()) {
                        Txt(
                            stringResource(R.string.val_editor_no_steps), 11, 400, C.Soft,
                            Modifier.padding(top = 2.dp).fillMaxWidth().dashedBorder(C.Border2, 1.dp, 12.dp).padding(horizontal = 14.dp, vertical = 16.dp),
                            maxLines = Int.MAX_VALUE,
                        )
                    }
                    // Drag the handle to reorder (or use the arrows).
                    ReorderableSteps(e.steps, vm::moveStep) { i, id, modifier, handle ->
                        StepRow(
                            i, stepName(steps, id), stepSetting(e, id, steps[id]?.group), first = i == 0, last = i == e.steps.size - 1,
                            onMove = { vm.moveStep(id, it) }, onInfo = { infoFor = id }, onRemove = { vm.removeStep(id) },
                            modifier = modifier, handle = handle,
                        )
                    }
                }
            }

            // Steps to add
            CfgCard {
                Column(Modifier.padding(EditorCardPadding), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Txt(stringResource(R.string.val_editor_library), 15, 700)
                    Txt(stringResource(R.string.val_editor_library_hint), 11, 400, C.Muted, maxLines = Int.MAX_VALUE)
                    SearchField(q, { q = it }, stringResource(R.string.val_editor_search))
                    val query = q.trim()
                    val all = s.steps.filter { r ->
                        query.isEmpty() || r.name.contains(query, true) || r.description.contains(query, true) ||
                            r.checks.any { it.name.contains(query, true) || it.short.contains(query, true) }
                    }
                    listOf(StepGroup.FIELD, StepGroup.GROUP, StepGroup.AI).map { g -> groupText(g) to all.filter { it.group == g } }
                        .filter { it.second.isNotEmpty() }
                        .forEach { (title, list) ->
                            Txt(title.uppercase(), 10, 600, C.Muted, Modifier.padding(top = 6.dp, bottom = 2.dp), spacing = 0.6f)
                            list.forEach { r -> CheckRow(r, r.key in e.steps) { vm.addStep(r.key) } }
                        }
                    if (s.steps.isEmpty()) Txt(stringResource(R.string.val_editor_library_offline), 11, 400, C.Muted, maxLines = Int.MAX_VALUE)
                    else if (all.isEmpty()) Txt(stringResource(R.string.val_editor_no_match, query), 11, 400, C.Muted, maxLines = Int.MAX_VALUE)
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
    infoFor?.let { id -> steps[id] }?.let { r -> StepInfoSheet(vm, s, e, r) { infoFor = null } }
}

/** The editor opened on a copy of [original], a saved validation that has already run (the web's note). */
@Composable
private fun CopyNote(original: String) {
    Row(
        Modifier.fillMaxWidth().clip(r(12)).background(C.YTintBg).border(1.dp, C.YTintBd, r(12)).padding(12.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        LineIcon(Icons.Lock, 16.dp, C.Yellow, 2.2f, Modifier.padding(top = 1.dp))
        Txt(stringResource(R.string.val_editor_copy_note, original), 12, 500, C.Soft, Modifier.weight(1f), maxLines = Int.MAX_VALUE)
    }
}

/** The inside margin of the editor's cards (name, steps, steps library). */
private val EditorCardPadding = PaddingValues(14.dp)

/** What a step in the editor is set to: its kind, plus its fields and severity when set. */
@Composable
private fun stepSetting(e: EditorState, key: String, group: StepGroup?): String {
    val parts = mutableListOf(groupText(group))
    e.stepFields[key]?.takeIf { it.isNotEmpty() }?.let {
        parts += pluralStringResource(if (key == "unique") R.plurals.valt_match_on_fields else R.plurals.ai_auto_fields, it.size, it.size)
    }
    e.stepSeverity[key]?.let { parts += stringResource(if (it == StepSeverity.ERROR) R.string.valt_as_errors else R.string.valt_as_warnings) }
    return parts.joinToString(" · ")
}

/** The gear of a step: what it does, and which fields it looks at and how it reports what it finds. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun StepInfoSheet(vm: ValidationViewModel, s: ValidationUiState, e: EditorState, r: ValStep, onClose: () -> Unit) {
    val fields = e.stepFields[r.key].orEmpty()
    val severity = e.stepSeverity[r.key]
    val unique = r.key == "unique"
    BottomSheet(onClose) {
        SheetHeader(r.name, groupText(r.group), onClose)
        Column(
            Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Txt(r.description, 14, 400, C.Soft, maxLines = Int.MAX_VALUE)
            r.checks.forEach { c ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LineIcon(Icons.CheckCircle, 16.dp, C.Yellow, 2f)
                    Txt(c.name, 12, 500, C.Soft, Modifier.weight(1f, fill = false), maxLines = 2)
                    if (c.usesAi) AiBadge()
                }
            }
            if (e.steps.contains(r.key)) {
                Txt(stringResource(if (unique) R.string.valt_match_on else R.string.valt_fields), 12, 600, C.Soft, Modifier.padding(top = 6.dp))
                Txt(stringResource(if (unique) R.string.valt_match_on_hint else R.string.valt_fields_hint), 11, 400, C.Muted, maxLines = Int.MAX_VALUE)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SettingChip(stringResource(R.string.valt_all_fields), fields.isEmpty()) { vm.setStepFields(r.key, emptyList()) }
                    s.fields.forEach { f ->
                        val on = f.id in fields
                        SettingChip(f.name, on) { vm.setStepFields(r.key, if (on) fields - f.id else fields + f.id) }
                    }
                }
                Txt(stringResource(R.string.valt_report_as), 12, 600, C.Soft, Modifier.padding(top = 6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SettingChip(stringResource(R.string.valt_sev_each), severity == null) { vm.setStepSeverity(r.key, null) }
                    SettingChip(stringResource(R.string.valt_sev_warnings), severity == StepSeverity.WARNING) { vm.setStepSeverity(r.key, StepSeverity.WARNING) }
                    SettingChip(stringResource(R.string.valt_sev_errors), severity == StepSeverity.ERROR) { vm.setStepSeverity(r.key, StepSeverity.ERROR) }
                }
            }
        }
    }
}

/** A chip like the editor's "Start from" ones; yellow when picked. */
@Composable
internal fun SettingChip(text: String, on: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.pressClickable(shape = Capsule, onClick = onClick).background(if (on) C.YTintBg else Color.Transparent).border(1.dp, if (on) C.Yellow else C.Border2, Capsule)
            .padding(horizontal = 11.dp, vertical = 6.dp),
    ) { Txt(text, 11, 600, if (on) C.Yellow else C.Text) }
}

/** One step: drag handle, number, step name and what it is set to, then settings, up, down and remove. */
@Composable
private fun StepRow(
    i: Int,
    name: String,
    sub: String,
    first: Boolean,
    last: Boolean,
    onMove: (Int) -> Unit,
    onInfo: (() -> Unit)?,
    onRemove: (() -> Unit)?,
    modifier: Modifier = Modifier,
    handle: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth().clip(r(10)).background(C.Bg).border(1.dp, C.Border, r(10)).padding(start = 2.dp, end = 2.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The drag handle (dragging is handled by the list, which moves the row).
        Box(Modifier.size(30.dp, 40.dp).then(handle), contentAlignment = Alignment.Center) { LineIcon(Icons.Menu, 16.dp, C.Muted, 2.2f) }
        Box(Modifier.size(24.dp).clip(CircleShape).background(C.YTintBg), contentAlignment = Alignment.Center) {
            Txt("${i + 1}", 11, 700, C.Yellow)
        }
        Column(Modifier.weight(1f).padding(horizontal = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Txt(name, 12, 600, maxLines = 2)
            Txt(sub, 10, 400, C.Muted, maxLines = 2)
        }
        onInfo?.let { StepIcon(Icons.Gear, true, it) }
        StepIcon(Icons.ChevronDown, !first, { onMove(-1) }, flip = true)
        StepIcon(Icons.ChevronDown, !last, { onMove(1) })
        onRemove?.let { StepIcon(Icons.Close, true, it) }
    }
}

@Composable
private fun StepIcon(icon: Ico, enabled: Boolean, onClick: () -> Unit, flip: Boolean = false) {
    Box(Modifier.size(30.dp).pressClickable(shape = CircleShape, enabled = enabled, onClick = onClick), contentAlignment = Alignment.Center) {
        LineIcon(icon, 15.dp, if (enabled) C.Text else C.Raised, 2.2f, if (flip) Modifier.rotate(180f) else Modifier)
    }
}

/**
 * A step in the Steps library: its name (and AI badge), what it covers, then the checks it runs, with "+ Add" at the
 * top end; greyed "Added" once it's one of the steps.
 */
@Composable
private fun CheckRow(rule: ValStep, added: Boolean, onAdd: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(r(10)).background(C.Bg).border(1.dp, C.Border, r(10)).padding(10.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Txt(rule.name, 13, 600, modifier = Modifier.weight(1f, fill = false), maxLines = 2)
                if (rule.usesAi) AiBadge()
            }
            Txt(rule.description, 11, 400, C.Muted, maxLines = 2)
        }
        if (added) {
            Row(
                Modifier.height(30.dp).clip(Capsule).border(1.dp, C.Border, Capsule).padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                LineIcon(Icons.Check, 12.dp, C.Dim, 2.4f)
                Txt(stringResource(R.string.val_editor_added), 11, 700, C.Dim)
            }
        } else {
            Row(
                Modifier.pressClickable(shape = Capsule, onClick = onAdd).border(1.dp, C.Soft.copy(alpha = 0.6f), Capsule).padding(horizontal = 11.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                LineIcon(Icons.PlusSmall, 12.dp, C.Text, 2.6f)
                Txt(stringResource(R.string.action_add), 11, 700)
            }
        }
    }
}
