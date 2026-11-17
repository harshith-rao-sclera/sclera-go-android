package com.sclera.sclerago.presentation.validation

import com.sclera.sclerago.presentation.common.asString
import com.sclera.sclerago.presentation.components.pageGutter
import com.sclera.sclerago.presentation.model.icon
import com.sclera.sclerago.presentation.common.plural
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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
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
import com.sclera.sclerago.presentation.components.Pill
import com.sclera.sclerago.presentation.components.RadioDot
import com.sclera.sclerago.presentation.components.SearchField
import com.sclera.sclerago.presentation.components.SheetHeader
import com.sclera.sclerago.presentation.components.Toast
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
private fun templateTagline(t: ValTemplate): String = when (t.key) {
    "quick_check" -> "The basics before handover"
    "clean_up" -> "Find duplicates and mismatches"
    "ai_check" -> "Published specs, JACS codes and an AI review"
    "full_audit" -> "Every step, all at once"
    else -> t.description
}

/** "Each asset", "Across assets" or "AI and web": what a step compares. */
private fun groupText(g: StepGroup?) = when (g) {
    StepGroup.GROUP -> "Across assets"
    StepGroup.AI -> "AI and web"
    else -> "Each asset"
}

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
    vm: ValidationViewModel = hiltViewModel(),
) {
    LaunchedEffect(propertyId) { vm.bind(propertyId) }
    val s = vm.state
    val close = {
        vm.closeOverlays()
        onClose()
    }
    BackHandler(onBack = close)
    Box(Modifier.fillMaxSize().background(C.Bg)) {
        Column(Modifier.fillMaxSize()) {
            PageBar("Validation", propName, close, property = true)
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(start = pageGutter(), end = pageGutter(), bottom = 160.dp)) {
                item(key = "top") { Spacer(Modifier.height(4.dp)) }
                item(key = "lead") {
                    Txt(stringResource(R.string.val_lead), 12, 400, C.Muted, Modifier.padding(horizontal = 16.dp, vertical = 4.dp), maxLines = 3)
                }
                if (s.offline) item(key = "offline") { EmptyNote("You're offline. This is what was last synced; running and changing validations needs a connection.") }
                item(key = "templates") {
                    SectionTitle("Templates", null) {
                        if (s.catalogLoaded) Txt("Default order", 11, 600, C.Yellow, Modifier.clip(r(6)).clickable(onClick = vm::openOrder).padding(4.dp))
                    }
                    if (s.templates.isEmpty()) {
                        EmptyNote(if (s.offline) "Templates load when you're online." else "Loading templates…")
                    } else LazyRow(
                        contentPadding = PaddingValues(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(s.templates, key = { it.key }) { t ->
                            TemplateCard(
                                t, ai = t.steps.any { s.stepsByKey[it]?.usesAi == true }, canRun = s.canCheck && !s.offline,
                                onRun = { vm.runOnAll(RunSource.Template(t.key)) },
                                onChoose = { vm.openRunSheet(RunSource.Template(t.key), pick = true) },
                                onChecks = { vm.showTemplate(t.key) },
                            )
                        }
                    }
                }
                item(key = "saved-title") {
                    SectionTitle("Saved validations", s.saved.size)
                }
                if (s.saved.isEmpty()) item(key = "saved-empty") {
                    EmptyNote(
                        when {
                            s.loading -> "Loading…"
                            s.canValidate -> stringResource(R.string.val_saved_empty_reviewer)
                            else -> stringResource(R.string.val_saved_empty_checker)
                        },
                    )
                }
                items(s.saved, key = { it.id }) { sv ->
                    SavedCard(
                        sv, s.stepsByKey,
                        onRun = if (s.canCheck && !s.offline) ({ vm.openRunSheet(RunSource.Saved(sv.id)) }) else null,
                        // Only reviewers (validate) keep saved validations; anyone with check may run them.
                        onEdit = if (s.canValidate && !s.offline) ({ vm.editValidation(sv) }) else null,
                        onDelete = if (s.canValidate && !s.offline) ({ vm.askDelete(sv) }) else null,
                    )
                }
                item(key = "jobs-title") { SectionTitle("Recent jobs", s.runs.size) }
                if (s.runs.isEmpty()) item(key = "jobs-empty") { EmptyNote(if (s.loading) "Loading…" else stringResource(R.string.val_jobs_empty)) }
                items(s.runs, key = { "job-${it.id}" }) { j ->
                    JobRow(
                        j, busy = j.id in s.busyRuns, onReview = { vm.openReview(j.id) },
                        onStart = if (s.canCheck && !s.offline) ({ vm.startReady(j) }) else null,
                        onDiscard = if (s.canValidate && !s.offline) ({ vm.discard(j) }) else null,
                    )
                }
            }
        }
        // Create a validation (+, above) and run one (below), floating like the main screen's buttons.
        if (s.reviewJobId == null && !s.offline) {
            if (s.canValidate) AddFab(Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(bottom = 68.dp)) { vm.newValidation() }
            if (s.canCheck && s.catalogLoaded) Box(
                Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(end = 14.dp, bottom = 14.dp).size(54.dp).fabShadow()
                    .clip(CircleShape).background(C.Btn).clickable { vm.openRunSheet() },
                contentAlignment = Alignment.Center,
            ) { LineIcon(Icons.Play, 20.dp, C.Text, 2.2f) }
        }

        // Layers on top of the page.
        s.reviewJobId?.let { id ->
            JobReview(s.propertyId, id, s.stepsByKey, assets, onOpenAsset, onBack = { vm.openReview(null) }, onRunAgain = { run ->
                vm.openReview(null)
                vm.openRunSheet(run.validationId?.let { RunSource.Saved(it) } ?: RunSource.Steps(run.steps))
            })
        }
        s.templateDetails?.let { key -> s.templates.firstOrNull { it.key == key } }?.let { t ->
            val canRun = s.canCheck && !s.offline
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
        s.editor?.let { EditorPage(vm, s, it, propName) }
        s.confirmDelete?.let { v ->
            ConfirmDialog(
                icon = Icons.Trash, iconBg = Color(0xFF2A1919), iconFg = C.Red,
                title = stringResource(R.string.val_delete_title, v.name),
                body = stringResource(R.string.val_delete_body),
                no = "Cancel", yes = "Delete", onNo = { vm.askDelete(null) }, onYes = vm::delete,
            )
        }
        AnimatedVisibility(
            s.message != null,
            Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 64.dp),
            enter = fadeIn() + slideInVertically { -it / 2 }, exit = fadeOut(),
        ) { s.message?.let { Toast(it.asString()) } }
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
        Txt("AI", 10, 700, C.Purple)
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
        Txt(templateTagline(t), 12, 400, C.Muted)
        if (canRun) YellowButton("Run on all assets", onRun, Modifier.fillMaxWidth(), height = 40)
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (canRun) Txt("Choose assets", 11, 600, C.Soft, Modifier.clip(r(6)).clickable(onClick = onChoose).padding(vertical = 4.dp))
            Spacer(Modifier.weight(1f))
            Txt("See the ${plural(t.steps.size, "step", "steps")}", 11, 600, C.Yellow, Modifier.clip(r(6)).clickable(onClick = onChecks).padding(vertical = 4.dp))
        }
    }
}

@Composable
private fun SavedCard(sv: ValidationDef, steps: Map<String, ValStep>, onRun: (() -> Unit)?, onEdit: (() -> Unit)?, onDelete: (() -> Unit)?) {
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
                    last == null -> "Not run yet"
                    last.status.active -> "Last run " + relativeTime(last.createdAt ?: 0L) + " · running"
                    else -> "Last run " + relativeTime(last.createdAt ?: 0L) + " · ${last.openCount} to review"
                },
                11, 400, C.Muted,
            )
        }
        Txt(sv.steps.joinToString(" · ") { stepName(steps, it) }, 11, 400, C.Muted, maxLines = 3)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            onRun?.let { YellowButton("Run", it, height = 36) }
            Spacer(Modifier.weight(1f))
            onEdit?.let { Txt("Edit", 12, 700, C.Soft, Modifier.clip(r(8)).clickable(onClick = it).padding(horizontal = 8.dp, vertical = 8.dp)) }
            onDelete?.let { Txt("Delete", 12, 700, C.Red, Modifier.clip(r(8)).clickable(onClick = it).padding(horizontal = 8.dp, vertical = 8.dp)) }
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
        listOf(Triple(failed, "failed", C.Red), Triple(look, "to look at", C.Orange), Triple(passed, "passed", C.Green)).forEach { (n, l, c) ->
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
        RunStatus.RUNNING -> Triple(C.YTintBg, C.Yellow, "Running")
        RunStatus.QUEUED -> Triple(C.YTintBg, C.Yellow, "Queued")
        RunStatus.READY -> Triple(C.Raised, C.Blue, "Ready")
        RunStatus.FAILED -> Triple(Color(0xFF2A1919), C.Red, "Failed")
        RunStatus.COMPLETED -> Triple(Color(0xFF17241D), C.Green, "Completed")
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
    Txt(
        (j.phase ?: "Checking ${plural(j.total, "asset", "assets")}") + if (j.total > 0) " · ${j.processed}/${j.total}" else "…",
        11, 400, C.Muted, maxLines = 2,
    )
}

/** "All assets (12)", "3 chosen assets"… what a job covered. */
internal fun scopeText(j: ValRun): String = when (j.scope) {
    RunScope.ALL -> "All assets (${j.total})"
    RunScope.CHANGED -> plural(j.total, "new or changed asset", "new or changed assets")
    RunScope.TYPES -> plural(j.total, "asset of the chosen types", "assets of the chosen types")
    RunScope.SELECTED -> plural(j.total, "chosen asset", "chosen assets")
}

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
                Txt("${scopeText(j)} · ${plural(j.steps.size, "step", "steps")}", 11, 400, C.Muted)
            }
            if (!ready) Pill(if (j.status.active) "View" else "Review", onReview, height = 34.dp, hPad = 14.dp, size = 12)
        }
        when {
            ready -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Txt(
                    if (j.scope == RunScope.CHANGED) "Ready to run · picks up new or changed assets when it starts"
                    else "Ready to run · not started yet",
                    11, 400, C.Muted, Modifier.weight(1f), maxLines = 2,
                )
                onDiscard?.let { Txt("Discard", 12, 700, C.Red, Modifier.clip(r(8)).clickable(enabled = !busy, onClick = it).padding(horizontal = 8.dp, vertical = 8.dp)) }
                onStart?.let { YellowButton(if (busy) "Starting…" else "Run", it, enabled = !busy, height = 36) }
            }
            j.status.active -> JobProgress(j)
            j.status == RunStatus.FAILED && j.processed == 0 -> Txt(j.statusDetail ?: "Failed", 11, 400, C.Red, maxLines = 3)
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
        Txt(body, 11, 400, if (selected) C.Soft else C.Muted, maxLines = 6)
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
        s.saved.map { sv -> RunOption(RunSource.Saved(sv.id), "", sv.name, sv.description?.takeIf { it.isNotBlank() } ?: "Saved validation", sv.steps.any { steps[it]?.usesAi == true }, true, sv.steps) } +
        listOfNotNull((sh.source as? RunSource.Steps)?.let { src -> RunOption(src, "", "These steps", "The steps of the job you ran", src.keys.any { steps[it]?.usesAi == true }, false, src.keys) })
    val chosen = options.firstOrNull { it.source == sh.source }
    val count = when (sh.mode) {
        RunMode.ALL -> assets.count { isServerId(it.id) }
        RunMode.CHOSEN -> sh.chosen.size
        RunMode.CHANGED -> -1
    }
    BottomSheet(vm::closeRunSheet) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 10.dp, top = 4.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Txt("Run validation", 16, 700, modifier = Modifier.weight(1f))
            CloseButton(vm::closeRunSheet, size = 36.dp, iconSize = 15.dp)
        }
        Txt(stringResource(R.string.val_run_sub), 12, 400, C.Muted, Modifier.padding(start = 16.dp, end = 16.dp, bottom = 10.dp), maxLines = 3)
        Column(
            Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Txt("What to check", 12, 600, C.Soft, Modifier.padding(start = 2.dp, bottom = 2.dp))
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
                                if (o.saved) Txt("saved", 10, 600, C.Muted)
                            }
                            Txt("${o.tagline} · ${plural(o.steps.size, "step", "steps")}", 11, 400, C.Muted)
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

            Txt("Which assets", 12, 600, C.Soft, Modifier.padding(start = 2.dp, top = 10.dp, bottom = 2.dp))
            val modes = listOf(RunMode.ALL, RunMode.CHOSEN, RunMode.CHANGED)
            ScopeTabs(
                listOf("All · ${assets.count { isServerId(it.id) }}", "Choose", "New or changed"), modes.indexOf(sh.mode),
            ) { i ->
                val m = modes[i]
                if (m == RunMode.CHOSEN) vm.openPicker() else vm.setRunMode(m)
            }
            when (sh.mode) {
                RunMode.CHOSEN -> "${plural(sh.chosen.size, "asset", "assets")} ticked · tap Choose to change"
                RunMode.CHANGED -> "Only what was added or changed since this last ran"
                RunMode.ALL -> null
            }?.let { Txt(it, 11, 400, C.Muted, Modifier.padding(start = 2.dp)) }
            Spacer(Modifier.height(4.dp))
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val ok = chosen != null && count != 0 && !sh.busy
            val label = when {
                count == 0 -> if (sh.mode == RunMode.CHOSEN) "Tick some assets first" else "No assets to check"
                count < 0 -> "Run ${chosen?.name ?: "validation"} on new or changed assets"
                else -> "Run ${chosen?.name ?: "validation"} on ${plural(count, "asset", "assets")}"
            }
            Pill("Save as ready", { if (ok) vm.submitRunSheet(now = false) }, Modifier.alpha(if (ok) 1f else 0.5f), height = 44.dp, hPad = 14.dp, size = 13)
            YellowButton(label, { vm.submitRunSheet(now = true) }, Modifier.weight(1f), enabled = ok, height = 44)
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
        SheetHeader("Choose assets", plural(sh.chosen.size, "asset", "assets") + " ticked", vm::closePicker)
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
        if (list.isEmpty()) Txt(stringResource(R.string.val_no_asset_match), 12, 400, C.Muted, Modifier.padding(horizontal = 16.dp, vertical = 12.dp))
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
                        LineIcon(a.type.icon, 17.dp, C.Yellow, 1.9f)
                    }
                    Column(Modifier.weight(1f)) {
                        Txt(a.name.ifBlank { stringResource(R.string.val_unnamed) }, 13, 600)
                        Txt(listOf(a.detail, a.where).filter { it.isNotBlank() }.joinToString(" · "), 11, 400, C.Muted)
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().topBorder(C.Border).padding(horizontal = 16.dp, vertical = 12.dp)) {
            Pill(
                "Done", vm::closePicker, Modifier.fillMaxWidth(), bg = C.Yellow, fg = C.OnYellow, height = 44.dp, size = 14, weight = 700,
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
                    plural(steps.size, "step", "steps") + if (steps.any { it.usesAi }) stringResource(R.string.val_some_ai) else "",
                    13, 400, C.Muted, maxLines = 3,
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
                        Txt(r.description, 12, 400, C.Muted, maxLines = 6)
                        Txt(
                            plural(r.checks.size, "check", "checks") + ": " + r.checks.joinToString(" · ") { it.short },
                            11, 400, C.Soft, maxLines = 4,
                        )
                        Txt(stringResource(stepKind(r.group)), 11, 500, C.Muted)
                    }
                }
            }
            Txt(stringResource(R.string.val_steps_in_order), 12, 400, C.Muted)
            onRun?.let { YellowButton("Run on all assets", it, Modifier.fillMaxWidth(), height = 44) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                onChoose?.let { Pill("Choose assets", it, Modifier.weight(1f), height = 40.dp, size = 13) }
                onCustomize?.let { Pill("Customize", it, Modifier.weight(1f), height = 40.dp, size = 13) }
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
    BottomSheet(vm::closeOrder) {
        SheetHeader("Default order", stringResource(R.string.val_order_sub), vm::closeOrder)
        Column(
            Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (o.loading) Txt("Loading…", 11, 400, C.Muted)
            ReorderableSteps(o.steps, { id, by -> vm.moveOrderStep(o.steps.indexOf(id), by) }) { i, key, modifier, handle ->
                StepRow(
                    i, stepName(s.stepsByKey, key), groupText(s.stepsByKey[key]?.group), first = i == 0, last = i == o.steps.size - 1,
                    onMove = { vm.moveOrderStep(i, it) }, onInfo = null, onRemove = null, modifier = modifier, handle = handle,
                )
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (o.custom) Pill("Reset to default", { if (!o.saving) vm.resetOrder() }, height = 44.dp, hPad = 14.dp, size = 13)
            Pill(
                "Save order", { if (!o.loading && !o.saving) vm.saveOrder() }, Modifier.weight(1f), bg = C.Yellow, fg = C.OnYellow,
                height = 44.dp, size = 13, weight = 700,
            )
        }
    }
}

/**
 * New validation (full page): name and description, its steps (checked on every asset, in order: start from a
 * template, reorder with the handle or arrows; the gear shows a step and what it looks at) and the steps to add.
 */
@Composable
private fun EditorPage(vm: ValidationViewModel, s: ValidationUiState, e: EditorState, propName: String) {
    // Leaving with anything changed asks first.
    Box(Modifier.fillMaxSize()) { GuardedClose(e.dirty, vm::closeEditor) { close -> EditorPageBody(vm, s, e, propName, close) } }
}

@Composable
private fun EditorPageBody(vm: ValidationViewModel, s: ValidationUiState, e: EditorState, propName: String, close: () -> Unit) {
    BackHandler(onBack = close)
    var q by remember { mutableStateOf("") }
    var infoFor by remember { mutableStateOf<String?>(null) }
    val ok = e.complete && !e.saving
    val steps = s.stepsByKey
    Column(Modifier.fillMaxSize().background(C.Bg).noRippleClick { }.blockSwipe()) {
        PageBar(if (e.id == null) "New validation" else "Edit validation", propName, close, property = true)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = pageGutter()).navigationBarsPadding().imePadding().padding(horizontal = 6.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Name, description, save
            CfgCard {
                Column(Modifier.padding(horizontal = 9.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    LabeledInput("Name", e.name, vm::setEditorName, "e.g. Main Validation", compact = true)
                    LabeledInput("Description (optional)", e.description, vm::setEditorDescription, "What this is for", compact = true)
                    Row(Modifier.padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Pill("Cancel", close, height = 38.dp, hPad = 14.dp, size = 13)
                        Pill("Save", { if (ok) vm.saveEditor(andRun = false) }, Modifier.alpha(if (ok) 1f else 0.5f), height = 38.dp, hPad = 16.dp, size = 13)
                        if (s.canCheck) Pill(
                            "Save and run", { if (ok) vm.saveEditor(andRun = true) }, Modifier.weight(1f).alpha(if (ok) 1f else 0.5f),
                            bg = if (ok) C.Yellow else C.Btn, fg = if (ok) C.OnYellow else C.Text, height = 38.dp, size = 13, weight = 700,
                        )
                    }
                }
            }

            // Steps
            CfgCard {
                Column(Modifier.padding(horizontal = 9.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Txt("Steps", 14, 700)
                        Box(Modifier.size(20.dp).clip(CircleShape).background(C.Raised), contentAlignment = Alignment.Center) {
                            Txt(e.steps.size.toString(), 10, 700, C.Soft)
                        }
                        Spacer(Modifier.weight(1f))
                        if (e.steps.isNotEmpty()) Txt("Clear all", 11, 600, C.Soft, Modifier.clip(r(8)).clickable(onClick = vm::clearSteps).padding(4.dp))
                    }
                    Txt("Every asset goes through these in order. Drag the handle or use the arrows to reorder.", 11, 400, C.Muted, maxLines = 3)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Txt("Start from", 11, 500, C.Muted)
                        Spacer(Modifier.width(8.dp))
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            s.templates.forEach { t ->
                                // The template the steps came from stands out, as on the web.
                                val on = e.templateKey == t.key
                                Box(
                                    Modifier.clip(Capsule).background(if (on) C.YTintBg else C.Surface)
                                        .border(1.dp, if (on) C.Yellow else C.Border2, Capsule).clickable { vm.startFrom(t) }
                                        .padding(horizontal = 11.dp, vertical = 6.dp),
                                ) { Txt(t.name.substringBefore(" and"), 11, 600) }
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
                        Txt("No steps yet. Add steps below, or start from a template.", 11, 400, C.Muted, maxLines = 2)
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
                Column(Modifier.padding(horizontal = 9.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Txt("Checks", 14, 700)
                    Txt("Press Add to put a step at the end of the steps.", 11, 400, C.Muted)
                    SearchField(q, { q = it }, "Search checks")
                    val query = q.trim()
                    val all = s.steps.filter { r ->
                        query.isEmpty() || r.name.contains(query, true) || r.description.contains(query, true) ||
                            r.checks.any { it.name.contains(query, true) || it.short.contains(query, true) }
                    }
                    listOf(StepGroup.FIELD, StepGroup.GROUP, StepGroup.AI).map { g -> groupText(g) to all.filter { it.group == g } }
                        .filter { it.second.isNotEmpty() }
                        .forEach { (title, list) ->
                            Txt(title.uppercase(), 10, 600, C.Muted, Modifier.padding(top = 4.dp), spacing = 0.8f)
                            list.forEach { r -> CheckRow(r, r.key in e.steps) { vm.addStep(r.key) } }
                        }
                    if (s.steps.isEmpty()) Txt("Steps load when you're online.", 11, 400, C.Muted)
                    else if (all.isEmpty()) Txt("No check matches “$query”.", 11, 400, C.Muted)
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
    infoFor?.let { id -> steps[id] }?.let { r -> StepInfoSheet(vm, s, e, r) { infoFor = null } }
}

/** What a step in the editor is set to: its kind, plus its fields and severity when set. */
private fun stepSetting(e: EditorState, key: String, group: StepGroup?): String {
    val parts = mutableListOf(groupText(group))
    e.stepFields[key]?.takeIf { it.isNotEmpty() }?.let { parts += (if (key == "unique") "Match on " else "") + plural(it.size, "field", "fields") }
    e.stepSeverity[key]?.let { parts += if (it == StepSeverity.ERROR) "As errors" else "As warnings" }
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
            Txt(r.description, 14, 400, C.Soft, maxLines = 8)
            r.checks.forEach { c ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LineIcon(Icons.CheckCircle, 16.dp, C.Yellow, 2f)
                    Txt(c.name, 12, 500, C.Soft, Modifier.weight(1f, fill = false), maxLines = 2)
                    if (c.usesAi) AiBadge()
                }
            }
            if (e.steps.contains(r.key)) {
                Txt(if (unique) "Match on" else "Fields", 12, 600, C.Soft, Modifier.padding(top = 6.dp))
                Txt(if (unique) "Assets count as duplicates when these fields match." else "Check only these fields, or all of them.", 11, 400, C.Muted, maxLines = 2)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SettingChip("All fields", fields.isEmpty()) { vm.setStepFields(r.key, emptyList()) }
                    s.fields.forEach { f ->
                        val on = f.id in fields
                        SettingChip(f.name, on) { vm.setStepFields(r.key, if (on) fields - f.id else fields + f.id) }
                    }
                }
                Txt("Report problems as", 12, 600, C.Soft, Modifier.padding(top = 6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SettingChip("As each check decides", severity == null) { vm.setStepSeverity(r.key, null) }
                    SettingChip("Warnings", severity == StepSeverity.WARNING) { vm.setStepSeverity(r.key, StepSeverity.WARNING) }
                    SettingChip("Errors", severity == StepSeverity.ERROR) { vm.setStepSeverity(r.key, StepSeverity.ERROR) }
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

/** A step that can be added; greyed "Added" once it's one of the steps. */
@Composable
private fun CheckRow(rule: ValStep, added: Boolean, onAdd: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(r(11)).background(C.Bg).border(1.dp, C.Border, r(11)).padding(start = 14.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Txt(rule.name, 12, 600, modifier = Modifier.weight(1f, fill = false), maxLines = 2)
                if (rule.usesAi) AiBadge()
            }
            Txt(rule.description, 10, 400, C.Muted, maxLines = 2)
        }
        if (added) {
            Row(
                Modifier.clip(Capsule).border(1.dp, C.Border2, Capsule).padding(horizontal = 10.dp, vertical = 5.dp).alpha(0.5f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                LineIcon(Icons.Check, 12.dp, C.Muted, 2.4f)
                Txt("Added", 11, 600, C.Muted)
            }
        } else {
            Row(
                Modifier.clip(Capsule).border(1.dp, C.Soft.copy(alpha = 0.6f), Capsule).clickable(onClick = onAdd).padding(horizontal = 11.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                LineIcon(Icons.PlusSmall, 12.dp, C.Text, 2.6f)
                Txt("Add", 11, 700)
            }
        }
    }
}
