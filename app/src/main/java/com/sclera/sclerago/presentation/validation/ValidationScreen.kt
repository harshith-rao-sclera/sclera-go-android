package com.sclera.sclerago.presentation.validation

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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.sclera.sclerago.presentation.components.AddFab
import com.sclera.sclerago.presentation.components.BottomSheet
import com.sclera.sclerago.presentation.components.Capsule
import com.sclera.sclerago.presentation.components.CfgCard
import com.sclera.sclerago.presentation.components.CheckBox
import com.sclera.sclerago.presentation.components.CloseButton
import com.sclera.sclerago.presentation.components.ConfirmDialog
import com.sclera.sclerago.presentation.components.GuardedClose
import com.sclera.sclerago.presentation.components.EmptyNote
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
    onJobOpened: () -> Unit = {},
    vm: ValidationViewModel = hiltViewModel(),
    connectivity: ConnectivityViewModel = hiltViewModel(),
) {
    LaunchedEffect(propertyId) { vm.bind(propertyId) }
    LaunchedEffect(openJobId) {
        openJobId?.let {
            vm.openReview(it)
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
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(start = pageGutter(), end = pageGutter(), bottom = 160.dp)) {
                item(key = "top") { Spacer(Modifier.height(4.dp)) }
                if (offline) item(key = "offline") { OfflineBanner(OnlineOnly.VALIDATION, Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) }
                item(key = "lead") {
                    Txt(stringResource(R.string.val_lead), 12, 400, C.Muted, Modifier.padding(horizontal = 16.dp, vertical = 4.dp), maxLines = Int.MAX_VALUE)
                }
                item(key = "templates") {
                    SectionTitle(stringResource(R.string.valt_templates), null) {
                        if (s.catalogLoaded) Txt(stringResource(R.string.valt_default_order), 11, 600, C.Yellow, Modifier.clip(r(6)).clickable(onClick = vm::openOrder).padding(4.dp))
                    }
                    if (s.templates.isEmpty()) {
                        EmptyNote(stringResource(if (offline) R.string.valt_templates_offline else R.string.valt_templates_loading))
                    } else LazyRow(
                        contentPadding = PaddingValues(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(s.templates, key = { it.key }) { t ->
                            TemplateCard(
                                t, ai = t.steps.any { s.stepsByKey[it]?.usesAi == true }, canRun = s.canCheck && !offline,
                                onRun = { vm.runOnAll(RunSource.Template(t.key)) },
                                onChoose = { vm.openRunSheet(RunSource.Template(t.key), pick = true) },
                                onChecks = { vm.showTemplate(t.key) },
                            )
                        }
                    }
                }
                item(key = "saved-title") {
                    SectionTitle(stringResource(R.string.valt_saved_validations), s.saved.size)
                }
                if (s.saved.isEmpty()) item(key = "saved-empty") {
                    EmptyNote(
                        when {
                            s.loading -> stringResource(R.string.val_loading)
                            s.canValidate -> stringResource(R.string.val_saved_empty_reviewer)
                            else -> stringResource(R.string.val_saved_empty_checker)
                        },
                    )
                }
                items(s.saved, key = { it.id }) { sv ->
                    val keeps = s.canValidate && !offline
                    SavedCard(
                        sv, s.stepsByKey,
                        onRun = if (s.canCheck && !offline) ({ vm.openRunSheet(RunSource.Saved(sv.id)) }) else null,
                        // Only reviewers (validate) keep saved validations; anyone with check may run them. One that has
                        // run can't be edited any more (the server answers 409), so it says why instead.
                        onEdit = if (keeps && !sv.locked) ({ vm.editValidation(sv) }) else null,
                        onDelete = if (keeps) ({ vm.askDelete(sv) }) else null,
                        showLocked = keeps && sv.locked,
                    )
                }
                item(key = "jobs-title") { SectionTitle(stringResource(R.string.valt_recent_jobs), s.runs.size) }
                if (s.runs.isEmpty()) item(key = "jobs-empty") { EmptyNote(stringResource(if (s.loading) R.string.val_loading else R.string.val_jobs_empty)) }
                items(s.runs, key = { "job-${it.id}" }) { j ->
                    JobRow(
                        j, busy = j.id in s.busyRuns, onReview = { vm.openReview(j.id) },
                        onStart = if (s.canCheck && !offline) ({ vm.startReady(j) }) else null,
                        onDiscard = if (s.canValidate && !offline) ({ vm.discard(j) }) else null,
                    )
                }
            }
        }
        // Create a validation (+, above) and run one (below), floating like the main screen's buttons.
        if (s.reviewJobId == null && !offline) {
            if (s.canValidate) AddFab(Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(bottom = 68.dp)) { vm.newValidation() }
            if (s.canCheck && s.catalogLoaded) Box(
                Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(end = 14.dp, bottom = 14.dp).size(54.dp).fabShadow()
                    .clip(CircleShape).background(C.Btn).clickable { vm.openRunSheet() },
                contentAlignment = Alignment.Center,
            ) { LineIcon(Icons.Play, 20.dp, C.Text, 2.2f) }
        }

        // Layers on top of the page.
        s.reviewJobId?.let { id ->
            JobReview(s.propertyId, id, s.stepsByKey, assets, propName, onOpenAsset, onBack = { vm.openReview(null) }, onRunAgain = vm::runAgain)
        }
        s.templateDetails?.let { key -> s.templates.firstOrNull { it.key == key } }?.let { t ->
            val canRun = s.canCheck && !offline
            ChecksSheet(
                t.name, t.steps.mapNotNull { s.stepsByKey[it] }, { vm.showTemplate(null) },
                onCustomize = if (s.canValidate) ({ vm.newValidation(t) }) else null,
                onRun = if (canRun) ({ vm.showTemplate(null); vm.runOnAll(RunSource.Template(t.key)) }) else null,
                onChoose = if (canRun) ({ vm.showTemplate(null); vm.openRunSheet(RunSource.Template(t.key), pick = true) }) else null,
            )
        }
        // A run sheet opened from Data Collection shows there ([QuickValidate]), not here.
        s.runSheet?.takeIf { !it.quick }?.let { RunSheetView(vm, s, it, assets) }
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
            Box(Modifier.size(22.dp).clip(CircleShape).background(C.Raised), contentAlignment = Alignment.Center) {
                Txt(it.toString(), 10, 700, C.Soft)
            }
        }
        Spacer(Modifier.weight(1f))
        trailing?.invoke()
    }
}

@Composable
internal fun YellowButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, height: Int = 44) {
    Row(
        modifier.height(height.dp).clip(r(10)).background(if (enabled) C.Yellow else C.Raised)
            .clickable(enabled = enabled, onClick = onClick).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        val fg = if (enabled) C.OnYellow else C.Dim
        LineIcon(Icons.Play, 13.dp, fg)
        Txt(text, 13, 700, fg)
    }
}

@Composable
internal fun AiBadge() {
    Row(
        Modifier.clip(Capsule).background(Color(0xFF26233A)).padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        LineIcon(Icons.Sparkles, 10.dp, C.Purple)
        Txt(stringResource(R.string.valt_ai_badge), 10, 700, C.Purple)
    }
}

@Composable
private fun TemplateCard(t: ValTemplate, ai: Boolean, canRun: Boolean, onRun: () -> Unit, onChoose: () -> Unit, onChecks: () -> Unit) {
    Column(
        Modifier.width(272.dp).clip(r(14)).background(C.Surface).border(1.dp, C.Border, r(14)).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(36.dp).clip(r(9)).background(C.YTintBg), contentAlignment = Alignment.Center) {
                LineIcon(templateIcon(t.key), 18.dp, C.Yellow, 2.2f)
            }
            Txt(t.name, 14, 700, modifier = Modifier.weight(1f, fill = false))
            if (ai) AiBadge()
        }
        Txt(templateTagline(t), 12, 400, C.Muted, maxLines = Int.MAX_VALUE)
        if (canRun) YellowButton(stringResource(R.string.valt_run_on_all), onRun, Modifier.fillMaxWidth(), height = 40)
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (canRun) Txt(stringResource(R.string.valt_choose_assets), 11, 600, C.Soft, Modifier.clip(r(6)).clickable(onClick = onChoose).padding(vertical = 4.dp))
            Spacer(Modifier.weight(1f))
            Txt(pluralStringResource(R.plurals.valt_see_steps, t.steps.size, t.steps.size), 11, 600, C.Yellow, Modifier.clip(r(6)).clickable(onClick = onChecks).padding(vertical = 4.dp))
        }
    }
}

@Composable
private fun SavedCard(
    sv: ValidationDef,
    steps: Map<String, ValStep>,
    onRun: (() -> Unit)?,
    onEdit: (() -> Unit)?,
    onDelete: (() -> Unit)?,
    showLocked: Boolean = false,
) {
    Column(
        Modifier.padding(horizontal = 12.dp, vertical = 4.dp).fillMaxWidth().clip(r(14)).background(C.Surface)
            .border(1.dp, C.Border, r(14)).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val last = sv.lastRun
        Row(verticalAlignment = Alignment.CenterVertically) {
            Txt(sv.name, 14, 700, modifier = Modifier.weight(1f))
            Txt(
                when {
                    last == null -> stringResource(R.string.valt_not_run_yet)
                    last.status.active -> stringResource(R.string.valt_last_run_running, relativeTime(last.createdAt ?: 0L))
                    else -> stringResource(R.string.valt_last_run_review, relativeTime(last.createdAt ?: 0L), last.openCount)
                },
                11, 400, C.Muted,
            )
        }
        Txt(sv.steps.joinToString(" · ") { stepName(steps, it) }, 11, 400, C.Muted, maxLines = Int.MAX_VALUE)
        if (showLocked) Txt(stringResource(R.string.valt_locked_note), 11, 400, C.Soft, maxLines = Int.MAX_VALUE)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            onRun?.let { YellowButton(stringResource(R.string.valt_run), it, height = 36) }
            Spacer(Modifier.weight(1f))
            onEdit?.let { Txt(stringResource(R.string.action_edit), 12, 700, C.Soft, Modifier.clip(r(8)).clickable(onClick = it).padding(horizontal = 8.dp, vertical = 8.dp)) }
            onDelete?.let { Txt(stringResource(R.string.action_delete), 12, 700, C.Red, Modifier.clip(r(8)).clickable(onClick = it).padding(horizontal = 8.dp, vertical = 8.dp)) }
        }
    }
}

/** Red / orange / green bar for failed / to look at / passed. */
@Composable
internal fun ResultBar(failed: Int, look: Int, passed: Int, modifier: Modifier = Modifier) {
    val total = (failed + look + passed).coerceAtLeast(1)
    Row(modifier.fillMaxWidth().height(6.dp).clip(Capsule).background(C.Raised)) {
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
            Triple(look, stringResource(R.string.valt_count_look), C.Orange),
            Triple(passed, stringResource(R.string.valt_count_passed), C.Green),
        ).forEach { (n, l, c) ->
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                Txt(n.toString(), 11, 700, if (n > 0) c else C.Soft)
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
        Modifier.clip(Capsule).background(bg).padding(horizontal = 9.dp, vertical = 4.dp),
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
        Txt(label, 10, 700, fg)
    }
}

/** The bar of a running job: how far it has got (or a moving bar while it's queued). */
@Composable
internal fun JobProgress(j: ValRun) {
    if (j.status == RunStatus.RUNNING && j.total > 0) {
        Box(Modifier.fillMaxWidth().height(6.dp).clip(Capsule).background(C.Raised)) {
            Box(Modifier.fillMaxWidth((j.processed.toFloat() / j.total).coerceIn(0.08f, 1f)).fillMaxHeight().background(C.Yellow.copy(alpha = 0.7f)))
        }
    } else {
        val p by rememberInfiniteTransition(label = "bar").animateFloat(
            0.1f, 0.9f, infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Reverse), label = "p",
        )
        Box(Modifier.fillMaxWidth().height(6.dp).clip(Capsule).background(C.Raised)) {
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
private fun JobRow(j: ValRun, busy: Boolean, onReview: () -> Unit, onStart: (() -> Unit)?, onDiscard: (() -> Unit)?) {
    val ready = j.status == RunStatus.READY
    Column(
        Modifier.padding(horizontal = 12.dp, vertical = 4.dp).fillMaxWidth().clip(r(14)).background(C.Surface)
            .border(1.dp, C.Border, r(14)).clickable(enabled = !ready, onClick = onReview).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatusChip(j.status)
            Spacer(Modifier.weight(1f))
            Txt(relativeTime(j.startedAt ?: j.createdAt ?: 0L), 11, 400, C.Muted)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Txt(j.name, 14, 700)
                Txt(stringResource(R.string.valt_dot_pair, scopeText(j), pluralStringResource(R.plurals.rev_steps, j.steps.size, j.steps.size)), 11, 400, C.Muted)
            }
            if (!ready) Pill(stringResource(if (j.status.active) R.string.valt_view else R.string.rev_title), onReview, height = 34.dp, hPad = 14.dp, size = 12)
        }
        when {
            ready -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Txt(
                    stringResource(if (j.scope == RunScope.CHANGED) R.string.valt_ready_changed else R.string.valt_ready_not_started),
                    11, 400, C.Muted, Modifier.weight(1f), maxLines = Int.MAX_VALUE,
                )
                onDiscard?.let { Txt(stringResource(R.string.action_discard), 12, 700, C.Red, Modifier.clip(r(8)).clickable(enabled = !busy, onClick = it).padding(horizontal = 8.dp, vertical = 8.dp)) }
                onStart?.let { YellowButton(stringResource(if (busy) R.string.valt_starting else R.string.valt_run), it, enabled = !busy, height = 36) }
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
        modifier.clip(r(12)).background(if (selected) C.YTintBg else C.Bg)
            .border(if (selected) 1.5.dp else 1.dp, if (selected) C.Yellow else C.Border2, r(12))
            .clickable(onClick = onClick).padding(10.dp),
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
private fun RuleChip(text: String, ai: Boolean) {
    Row(
        Modifier.clip(Capsule).background(C.Raised).padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (ai) LineIcon(Icons.Sparkles, 10.dp, C.Purple)
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
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 10.dp, top = 4.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Txt(stringResource(R.string.valt_run_validation), 16, 700, modifier = Modifier.weight(1f))
                CloseButton(close, size = 36.dp, iconSize = 15.dp)
            }
            Txt(stringResource(R.string.val_run_sub), 12, 400, C.Muted, Modifier.padding(start = 16.dp, end = 16.dp, bottom = 10.dp), maxLines = Int.MAX_VALUE)
            Column(
                Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Txt(stringResource(R.string.valt_what_to_check), 12, 600, C.Soft, Modifier.padding(start = 2.dp, bottom = 2.dp))
                options.forEach { o ->
                    val on = sh.source == o.source
                    Column(
                        Modifier.fillMaxWidth().clip(r(12)).background(if (on) C.YTintBg else C.Surface)
                            .border(if (on) 1.5.dp else 1.dp, if (on) C.Yellow else C.Border, r(12))
                            .clickable { vm.setRunSource(o.source) }.padding(10.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Box(Modifier.size(30.dp).clip(r(8)).background(C.Raised), contentAlignment = Alignment.Center) {
                                LineIcon(templateIcon(o.icon), 16.dp, C.Yellow, 2.2f)
                            }
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Txt(o.name, 13, 700, modifier = Modifier.weight(1f, fill = false))
                                    if (o.ai) AiBadge()
                                    if (o.saved) Txt(stringResource(R.string.valt_saved_badge), 10, 600, C.Muted)
                                }
                                Txt(stringResource(R.string.valt_dot_pair, o.tagline, pluralStringResource(R.plurals.rev_steps, o.steps.size, o.steps.size)), 11, 400, C.Muted, maxLines = Int.MAX_VALUE)
                            }
                            RadioDot(on)
                        }
                        // The chosen one shows what it checks.
                        if (on && o.steps.isNotEmpty()) FlowRow(
                            Modifier.padding(start = 40.dp, top = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                            verticalArrangement = Arrangement.spacedBy(5.dp),
                        ) {
                            o.steps.forEach { RuleChip(stepName(steps, it), steps[it]?.usesAi == true) }
                        }
                    }
                }

                Txt(stringResource(R.string.valt_which_assets), 12, 600, C.Soft, Modifier.padding(start = 2.dp, top = 10.dp, bottom = 2.dp))
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
                }?.let { Txt(it, 11, 400, C.Muted, Modifier.padding(start = 2.dp), maxLines = Int.MAX_VALUE) }
                Spacer(Modifier.height(4.dp))
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val ok = chosen != null && count != 0 && !sh.busy
                val name = chosen?.name ?: stringResource(R.string.valt_run_name_fallback)
                val label = when {
                    count == 0 -> stringResource(if (sh.mode == RunMode.CHOSEN) R.string.valt_tick_first else R.string.valt_no_assets_to_check)
                    count < 0 -> stringResource(R.string.valt_run_on_changed, name)
                    else -> pluralStringResource(R.plurals.valt_run_on_n, count, count, name)
                }
                Pill(stringResource(R.string.valt_save_as_ready), { if (ok) vm.submitRunSheet(now = false) }, Modifier.alpha(if (ok) 1f else 0.5f), height = 44.dp, hPad = 14.dp, size = 13)
                YellowButton(label, { vm.submitRunSheet(now = true) }, Modifier.weight(1f), enabled = ok, height = 44)
            }
        }
    }
}

/** Full-width segmented control (the asset scope in Run validation). */
@Composable
private fun ScopeTabs(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(r(10)).background(C.Raised).padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        labels.forEachIndexed { i, l ->
            val on = i == selected
            Box(
                Modifier.weight(1f).height(32.dp).clip(r(8)).background(if (on) C.Yellow else Color.Transparent).clickable { onSelect(i) },
                contentAlignment = Alignment.Center,
            ) { Txt(l, 11, if (on) 700 else 500, if (on) C.OnYellow else C.Muted, maxLines = 1) }
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
            Modifier.fillMaxWidth().clickable { vm.setChosen(list.map { it.id }, !all) }.padding(horizontal = 16.dp, vertical = 10.dp),
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
                    Modifier.fillMaxWidth().topBorder(C.Border).clickable { vm.toggleChosen(a.id) }
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
) {
    BottomSheet(onClose) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 8.dp, bottom = 14.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Txt(name, 22, 700, maxLines = 2)
                Txt(
                    pluralStringResource(if (steps.any { it.usesAi }) R.plurals.valt_steps_some_ai else R.plurals.rev_steps, steps.size, steps.size),
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
                            if (r.usesAi) AiBadge()
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
            onRun?.let { YellowButton(stringResource(R.string.valt_run_on_all), it, Modifier.fillMaxWidth(), height = 44) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                onChoose?.let { Pill(stringResource(R.string.valt_choose_assets), it, Modifier.weight(1f), height = 40.dp, size = 13) }
                onCustomize?.let { Pill(stringResource(R.string.valt_customize), it, Modifier.weight(1f), height = 40.dp, size = 13) }
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
                if (o.loading) Txt(stringResource(R.string.val_loading), 11, 400, C.Muted)
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
        PageBar(stringResource(if (e.id == null) R.string.val_editor_new else R.string.val_editor_edit), null, close)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = pageGutter()).navigationBarsPadding().imePadding().padding(horizontal = 6.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
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
                        Pill(stringResource(R.string.val_cancel), close, height = 44.dp, hPad = 16.dp, size = 13, weight = 700)
                        Pill(
                            stringResource(R.string.val_save), { if (ok) vm.saveEditor(andRun = false) }, Modifier.alpha(if (ok) 1f else 0.5f),
                            height = 44.dp, hPad = 18.dp, size = 13, weight = 700,
                        )
                        if (s.canCheck) Pill(
                            stringResource(R.string.val_editor_save_and_run), { if (ok) vm.saveEditor(andRun = true) },
                            Modifier.weight(1f).alpha(if (ok) 1f else 0.5f),
                            bg = if (ok) C.Yellow else C.Btn, fg = if (ok) C.OnYellow else C.Text, height = 44.dp, size = 13, weight = 700,
                        )
                    }
                }
            }

            // Steps
            CfgCard {
                Column(Modifier.padding(EditorCardPadding), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Txt(stringResource(R.string.val_editor_steps), 15, 700)
                        Box(
                            Modifier.heightIn(min = 20.dp).widthIn(min = 20.dp).clip(Capsule).background(C.Raised).padding(horizontal = 6.dp),
                            contentAlignment = Alignment.Center,
                        ) { Txt(e.steps.size.toString(), 10, 700, C.Soft) }
                        Spacer(Modifier.weight(1f))
                        if (e.steps.isNotEmpty()) {
                            Txt(
                                stringResource(R.string.val_editor_clear_all), 11, 600, C.Soft,
                                Modifier.clip(r(8)).clickable(onClick = vm::clearSteps).padding(4.dp),
                            )
                        }
                    }
                    Txt(stringResource(R.string.val_editor_steps_hint), 11, 400, C.Soft, maxLines = Int.MAX_VALUE)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Txt(stringResource(R.string.val_editor_start_from), 11, 500, C.Soft)
                        Spacer(Modifier.width(8.dp))
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            s.templates.forEach { t ->
                                // The template the steps came from stands out, as on the web.
                                val on = e.templateKey == t.key
                                Box(
                                    Modifier.heightIn(min = 34.dp).clip(Capsule).background(if (on) C.YTintBg else Color.Transparent)
                                        .border(1.dp, if (on) C.Yellow else C.Border2, Capsule).clickable { vm.startFrom(t) }
                                        .padding(horizontal = 12.dp, vertical = 7.dp),
                                    contentAlignment = Alignment.Center,
                                ) { Txt(t.name, 11, 700, if (on) C.Yellow else C.Text) }
                            }
                            if (e.undo != null) {
                                Txt(
                                    stringResource(R.string.val_undo), 11, 700, C.Yellow,
                                    Modifier.clip(r(8)).clickable(onClick = vm::undoSteps).padding(horizontal = 6.dp, vertical = 6.dp),
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
                    Txt(stringResource(R.string.val_editor_library_hint), 11, 400, C.Soft, maxLines = Int.MAX_VALUE)
                    SearchField(q, { q = it }, stringResource(R.string.val_editor_search))
                    val query = q.trim()
                    val all = s.steps.filter { r ->
                        query.isEmpty() || r.name.contains(query, true) || r.description.contains(query, true) ||
                            r.checks.any { it.name.contains(query, true) || it.short.contains(query, true) }
                    }
                    listOf(StepGroup.FIELD, StepGroup.GROUP, StepGroup.AI).map { g -> groupText(g) to all.filter { it.group == g } }
                        .filter { it.second.isNotEmpty() }
                        .forEach { (title, list) ->
                            Txt(title.uppercase(), 10, 700, C.Soft, Modifier.padding(top = 6.dp, bottom = 2.dp), spacing = 0.8f)
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
        Modifier.clip(Capsule).background(if (on) C.YTintBg else Color.Transparent).border(1.dp, if (on) C.Yellow else C.Border2, Capsule)
            .clickable(onClick = onClick).padding(horizontal = 11.dp, vertical = 6.dp),
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
        modifier.fillMaxWidth().clip(r(11)).background(C.Bg).border(1.dp, C.Border, r(11)).padding(start = 6.dp, end = 6.dp, top = 9.dp, bottom = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The drag handle (dragging is handled by the list, which moves the row).
        Box(Modifier.size(30.dp, 40.dp).then(handle), contentAlignment = Alignment.Center) { LineIcon(Icons.Menu, 16.dp, C.Muted, 2.2f) }
        Box(Modifier.size(24.dp).clip(CircleShape).background(C.YTintBg), contentAlignment = Alignment.Center) {
            Txt("${i + 1}", 11, 700, C.Yellow)
        }
        Column(Modifier.weight(1f).padding(horizontal = 10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
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
    Box(Modifier.size(30.dp).clip(CircleShape).clickable(enabled = enabled, onClick = onClick), contentAlignment = Alignment.Center) {
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
        Modifier.fillMaxWidth().clip(r(12)).background(C.Bg).border(1.dp, C.Border, r(12)).padding(start = 12.dp, end = 10.dp, top = 10.dp, bottom = 12.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Txt(rule.name, 13, 700, modifier = Modifier.weight(1f, fill = false), maxLines = 2)
                if (rule.usesAi) AiBadge()
            }
            Txt(rule.description, 11, 600, C.Soft, maxLines = Int.MAX_VALUE)
            if (rule.checks.isNotEmpty()) Txt(rule.checks.joinToString(" · ") { it.name }, 10, 400, C.Muted, maxLines = Int.MAX_VALUE)
        }
        if (added) {
            Row(
                Modifier.clip(Capsule).border(1.dp, C.Border2, Capsule).padding(horizontal = 10.dp, vertical = 5.dp).alpha(0.5f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                LineIcon(Icons.Check, 12.dp, C.Muted, 2.4f)
                Txt(stringResource(R.string.val_editor_added), 11, 600, C.Muted)
            }
        } else {
            Row(
                Modifier.clip(Capsule).border(1.dp, C.Soft.copy(alpha = 0.6f), Capsule).clickable(onClick = onAdd).padding(horizontal = 11.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                LineIcon(Icons.PlusSmall, 12.dp, C.Text, 2.6f)
                Txt(stringResource(R.string.action_add), 11, 700)
            }
        }
    }
}
