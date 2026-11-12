package com.example.sclerago.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.zIndex
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.example.sclerago.AppViewModel
import com.example.sclerago.data.*
import com.example.sclerago.services.validation.*
import com.example.sclerago.ui.icons.Ico
import com.example.sclerago.ui.icons.Icons
import com.example.sclerago.ui.icons.LineIcon
import com.example.sclerago.ui.theme.C
import com.example.sclerago.ui.theme.Txt

private val SheetTop = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)

private fun templateIcon(id: String): Ico = when (id) {
    "quick" -> Icons.Bolt
    "specs" -> Icons.Sparkles
    "dups" -> Icons.Copy
    "deep" -> Icons.ScanAi
    "full" -> Icons.ShieldCheck
    else -> Icons.Clipboard
}

private fun outcomeColor(o: RuleOutcome) = when (o) {
    RuleOutcome.FAIL -> C.Red
    RuleOutcome.LOOK -> C.Orange
    RuleOutcome.PASS -> C.Green
    RuleOutcome.NA -> C.Muted
}

/**
 * The Validation page (side menu → Validation): run a template on the property's assets, keep your own
 * rule sets, and review earlier runs. Runs happen in the background; results land in "Recent jobs".
 */
@Composable
fun ValidationScreen(vm: AppViewModel) {
    val s = vm.state
    BackHandler {
        when {
            s.valReview != null -> vm.openReview(null)
            else -> vm.closeValidation()
        }
    }
    Box(Modifier.fillMaxSize().background(C.Bg)) {
        Column(Modifier.fillMaxSize()) {
            PageBar("Validation", vm.propName, vm::closeValidation, property = true)
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 160.dp)) {
                item(key = "top") { Spacer(Modifier.height(4.dp)) }
                item(key = "templates") {
                    SectionTitle("Templates", null)
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(TEMPLATES, key = { it.id }) { t ->
                            TemplateCard(
                                t,
                                onRun = { vm.runOnAll(t.id) },
                                onChoose = { vm.openRunSheet(t.id, ValScope.CHOOSE) },
                                onChecks = { vm.openChecks(t.id) },
                            )
                        }
                    }
                }
                item(key = "saved-title") {
                    SectionTitle("Saved validations", vm.savedVals.size)
                }
                if (vm.savedVals.isEmpty()) item(key = "saved-empty") {
                    EmptyNote("No saved validations yet. Tap + to pick the rules you use most and save them here.")
                }
                items(vm.savedVals, key = { it.id }) { sv ->
                    SavedCard(
                        sv, vm.lastRun(sv.id),
                        onRun = { vm.runOnAll(sv.id) }, onDelete = { vm.askDeleteSaved(sv.id) },
                    )
                }
                val jobs = vm.propJobs
                item(key = "jobs-title") { SectionTitle("Recent jobs", jobs.size) }
                if (jobs.isEmpty()) item(key = "jobs-empty") { EmptyNote("Nothing has run here yet.") }
                items(jobs, key = { "job-${it.id}" }) { j -> JobRow(j) { vm.openReview(j.id) } }
            }
        }
        // Create a validation (+, above) and run one (below), floating like the main screen's buttons.
        if (s.valReview == null) {
            AddFab(Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(bottom = 68.dp), vm::newSaved)
            Box(
                Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(end = 14.dp, bottom = 14.dp).size(54.dp).fabShadow()
                    .clip(CircleShape).background(C.Btn).clickable { vm.openRunSheet() },
                contentAlignment = Alignment.Center,
            ) { LineIcon(Icons.Play, 20.dp, C.Text, 2.2f) }
        }

        // Layers on top of the page.
        s.valReview?.let { id -> vm.propJobs.firstOrNull { it.id == id } }?.let { JobReview(it, vm) }
        s.valChecksFor?.let { id -> ChecksSheet(vm.sourceName(id), vm.sourceRules(id)) { vm.openChecks(null) } }
        s.valSheet?.let { RunSheetView(vm, it) }
        if (s.valSheet != null && s.valPicker) AssetPicker(vm, s.valSheet)
        s.valEditor?.let { EditorPage(vm, it) }
        s.valConfirmDelete?.let { id ->
            ConfirmDialog(
                icon = Icons.Trash, iconBg = Color(0xFF2A1919), iconFg = C.Red,
                title = "Delete this validation?",
                body = "“${vm.sourceName(id)}” will be removed. Earlier results stay in Recent jobs.",
                no = "Cancel", yes = "Delete", onNo = { vm.askDeleteSaved(null) }, onYes = vm::deleteSaved,
            )
        }
        AnimatedVisibility(
            s.toastOn,
            Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 64.dp),
            enter = fadeIn() + slideInVertically { -it / 2 }, exit = fadeOut(),
        ) { Toast(s.toast) }
    }
}

// ---------------------------------------------------------------- Page pieces

@Composable
internal fun PageBar(title: String, sub: String?, onBack: () -> Unit, property: Boolean = false) {
    Box(Modifier.fillMaxWidth().background(C.Surface).bottomBorder(C.Border).statusBarsPadding().height(60.dp).padding(horizontal = 10.dp)) {
        RoundButton(42.dp, C.Btn, onBack, Modifier.align(Alignment.CenterStart)) { LineIcon(Icons.ChevronLeft, 22.dp, C.Yellow, 2.6f) }
        Column(Modifier.align(Alignment.Center).padding(horizontal = 56.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Txt(title, 18, 700)
            sub?.let {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    if (property) LineIcon(Icons.Building, 13.dp, C.Muted, 2f)
                    Txt(it, 12, 500, C.Muted)
                }
            }
        }
    }
}

@Composable
internal fun SectionTitle(title: String, count: Int?, trailing: (@Composable () -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp, top = 18.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Txt(title, 16, 700)
        count?.let {
            Box(Modifier.size(22.dp).clip(CircleShape).background(C.Raised), contentAlignment = Alignment.Center) {
                Txt(it.toString(), 11, 700, C.Soft)
            }
        }
        Spacer(Modifier.weight(1f))
        trailing?.invoke()
    }
}

@Composable
internal fun EmptyNote(text: String) {
    Txt(
        text, 13, 400, C.Muted,
        Modifier.padding(horizontal = 12.dp).fillMaxWidth().dashedBorder(C.Border2, 1.dp, 12.dp).padding(16.dp),
        maxLines = 3,
    )
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
        Txt(text, 14, 700, fg)
    }
}

@Composable
private fun AiBadge() {
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
private fun TemplateCard(t: ValidationTemplate, onRun: () -> Unit, onChoose: () -> Unit, onChecks: () -> Unit) {
    Column(
        Modifier.width(272.dp).clip(r(14)).background(C.Surface).border(1.dp, C.Border, r(14)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(36.dp).clip(r(9)).background(C.YTintBg), contentAlignment = Alignment.Center) {
                LineIcon(templateIcon(t.id), 18.dp, C.Yellow, 2.2f)
            }
            Txt(t.name, 15, 700, modifier = Modifier.weight(1f, fill = false))
            if (t.ai) AiBadge()
        }
        Txt(t.tagline, 13, 400, C.Muted)
        YellowButton("Run on all assets", onRun, Modifier.fillMaxWidth(), height = 40)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Txt("Choose assets", 12, 600, C.Soft, Modifier.clip(r(6)).clickable(onClick = onChoose).padding(vertical = 4.dp))
            Spacer(Modifier.weight(1f))
            Txt("See the ${t.ruleIds.size} checks", 12, 600, C.Yellow, Modifier.clip(r(6)).clickable(onClick = onChecks).padding(vertical = 4.dp))
        }
    }
}

@Composable
private fun SavedCard(sv: SavedValidation, lastRun: Long?, onRun: () -> Unit, onDelete: () -> Unit) {
    Column(
        Modifier.padding(horizontal = 12.dp, vertical = 4.dp).fillMaxWidth().clip(r(14)).background(C.Surface)
            .border(1.dp, C.Border, r(14)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Txt(sv.name, 15, 700, modifier = Modifier.weight(1f))
            Txt(lastRun?.let { "Last run " + relativeTime(it) } ?: "Not run yet", 12, 400, C.Muted)
        }
        Txt(sv.rules.joinToString(" · ") { it.name }, 12, 400, C.Muted, maxLines = 3)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            YellowButton("Run", onRun, height = 36)
            Spacer(Modifier.weight(1f))
            Txt("Delete", 13, 700, C.Red, Modifier.clip(r(8)).clickable(onClick = onDelete).padding(horizontal = 8.dp, vertical = 8.dp))
        }
    }
}

/** Red / orange / green bar for failed / to look at / passed. */
@Composable
private fun ResultBar(failed: Int, look: Int, passed: Int, modifier: Modifier = Modifier) {
    val total = (failed + look + passed).coerceAtLeast(1)
    Row(modifier.fillMaxWidth().height(6.dp).clip(Capsule).background(C.Raised)) {
        if (failed > 0) Box(Modifier.weight(failed.toFloat() / total).fillMaxHeight().background(C.Red))
        if (look > 0) Box(Modifier.weight(look.toFloat() / total).fillMaxHeight().background(C.Orange))
        if (passed > 0) Box(Modifier.weight(passed.toFloat() / total).fillMaxHeight().background(C.Green))
    }
}

@Composable
private fun Counts(failed: Int, look: Int, passed: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        listOf(Triple(failed, "failed", C.Red), Triple(look, "to look at", C.Orange), Triple(passed, "passed", C.Green)).forEach { (n, l, c) ->
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                Txt(n.toString(), 12, 700, if (n > 0) c else C.Soft)
                Txt(l, 12, 400, C.Muted)
            }
        }
    }
}

@Composable
private fun StatusChip(running: Boolean) {
    Row(
        Modifier.clip(Capsule).background(if (running) C.YTintBg else Color(0xFF17241D))
            .padding(horizontal = 9.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        if (running) {
            val spin by rememberInfiniteTransition(label = "job").animateFloat(
                0f, 360f, infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart), label = "spin",
            )
            Box(Modifier.size(9.dp).rotate(spin).border(1.5.dp, C.Yellow, CircleShape).padding(2.dp))
            Txt("Running", 11, 700, C.Yellow)
        } else {
            Box(Modifier.size(7.dp).clip(CircleShape).background(C.Green))
            Txt("Completed", 11, 700, C.Green)
        }
    }
}

@Composable
private fun JobRow(j: ValJob, onReview: () -> Unit) {
    Column(
        Modifier.padding(horizontal = 12.dp, vertical = 4.dp).fillMaxWidth().clip(r(14)).background(C.Surface)
            .border(1.dp, C.Border, r(14)).clickable(enabled = !j.running, onClick = onReview).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatusChip(j.running)
            Spacer(Modifier.weight(1f))
            Txt(relativeTime(j.startedAt), 12, 400, C.Muted)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Txt(j.title, 15, 700)
                Txt("${j.scopeLabel} · ${plural(j.checks, "check", "checks")}", 12, 400, C.Muted)
            }
            if (!j.running) Pill("Review", onReview, height = 34.dp, hPad = 14.dp, size = 12)
        }
        if (j.running) {
            val p by rememberInfiniteTransition(label = "bar").animateFloat(
                0.1f, 0.9f, infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Reverse), label = "p",
            )
            Box(Modifier.fillMaxWidth().height(6.dp).clip(Capsule).background(C.Raised)) {
                Box(Modifier.fillMaxWidth(p).fillMaxHeight().background(C.Yellow.copy(alpha = 0.7f)))
            }
            Txt("Checking ${plural(j.assetIds.size, "asset", "assets")}…", 12, 400, C.Muted)
        } else {
            ResultBar(j.failed, j.look, j.passed)
            Counts(j.failed, j.look, j.passed)
        }
    }
}

// ---------------------------------------------------------------- Sheets

/** A bottom sheet over a dimmed backdrop. Back and a tap on the backdrop close it. */
@Composable
internal fun BottomSheet(onDismiss: () -> Unit, maxHeightFraction: Float = 0.92f, content: @Composable ColumnScope.() -> Unit) {
    BackHandler(onBack = onDismiss)
    Box(Modifier.fillMaxSize().background(C.Scrim).noRippleClick(onDismiss).blockSwipe().imePadding()) {
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(maxHeightFraction).wrapContentHeight(Alignment.Bottom)
                .clip(SheetTop).background(C.Surface).topBorder(C.Border).noRippleClick { }
                .navigationBarsPadding(),
        ) {
            SheetHandle(Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp, bottom = 4.dp))
            content()
        }
    }
}

@Composable
internal fun SheetHeader(title: String, sub: String?, onClose: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 10.dp, top = 6.dp, bottom = 10.dp), verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Txt(title, 18, 700)
            sub?.let { Txt(it, 13, 400, C.Muted, maxLines = 2) }
        }
        CloseButton(onClose, size = 36.dp, iconSize = 16.dp)
    }
}

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
            .clickable(onClick = onClick).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Txt(title, 14, 700, modifier = Modifier.weight(1f, fill = false), maxLines = 2)
            badge?.invoke()
        }
        Txt(body, 12, 400, if (selected) C.Soft else C.Muted, maxLines = 6)
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
        Txt(text, 12, 500, C.Soft)
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun RunSheetView(vm: AppViewModel, sh: com.example.sclerago.RunSheet) {
    val targets = vm.scopeAssets(sh)
    // (id, name, tagline, ai, saved): the templates, then the user's saved validations.
    val options = TEMPLATES.map { t -> RunOption(t.id, t.name, t.tagline, t.ai, false) } +
        vm.savedVals.map { sv -> RunOption(sv.id, sv.name, sv.description.ifBlank { "Saved validation" }, false, true) }
    val chosen = options.firstOrNull { it.id == sh.sourceId }
    BottomSheet(vm::closeRunSheet) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 10.dp, top = 4.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Txt("Run validation", 17, 700, modifier = Modifier.weight(1f))
            CloseButton(vm::closeRunSheet, size = 36.dp, iconSize = 15.dp)
        }
        Column(
            Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Txt("What to check", 13, 600, C.Soft, Modifier.padding(start = 2.dp, bottom = 2.dp))
            options.forEach { o ->
                val on = sh.sourceId == o.id
                val rules = vm.sourceRules(o.id)
                Column(
                    Modifier.fillMaxWidth().clip(r(12)).background(if (on) C.YTintBg else C.Surface)
                        .border(if (on) 1.5.dp else 1.dp, if (on) C.Yellow else C.Border, r(12))
                        .clickable { vm.setRunSource(o.id) }.padding(10.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(Modifier.size(30.dp).clip(r(8)).background(C.Raised), contentAlignment = Alignment.Center) {
                            LineIcon(templateIcon(o.id), 16.dp, C.Yellow, 2.2f)
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Txt(o.name, 14, 700, modifier = Modifier.weight(1f, fill = false))
                                if (o.ai) AiBadge()
                                if (o.saved) Txt("saved", 10, 600, C.Muted)
                            }
                            Txt("${o.tagline} · ${plural(rules.size, "check", "checks")}", 12, 400, C.Muted)
                        }
                        RadioDot(on)
                    }
                    // The chosen one shows what it checks.
                    if (on && rules.isNotEmpty()) FlowRow(
                        Modifier.padding(start = 40.dp, top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        rules.take(3).forEach { RuleChip(it.name, it.ai) }
                        if (rules.size > 3) RuleChip("+${rules.size - 3}", false)
                    }
                }
            }

            Txt("Which assets", 13, 600, C.Soft, Modifier.padding(start = 2.dp, top = 10.dp, bottom = 2.dp))
            ScopeTabs(
                listOf("All · ${vm.assets.size}", "Choose", "New or changed"), ValScope.entries.indexOf(sh.scope),
            ) { i ->
                val sc = ValScope.entries[i]
                if (sc == ValScope.CHOOSE && sh.scope == sc) vm.openPicker() else vm.setRunScope(sc)
            }
            when (sh.scope) {
                ValScope.CHOOSE -> "${plural(sh.chosen.size, "asset", "assets")} ticked · tap Choose to change"
                ValScope.CHANGED -> if (vm.lastRun(sh.sourceId) == null) "Not run yet, so every asset."
                else "${plural(targets.size, "asset", "assets")} added or edited since the last run"
                ValScope.ALL -> null
            }?.let { Txt(it, 12, 400, C.Muted, Modifier.padding(start = 2.dp)) }
            Spacer(Modifier.height(4.dp))
        }
        Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp)) {
            val label = when {
                targets.isEmpty() -> if (sh.scope == ValScope.CHOOSE) "Tick some assets first" else "Nothing new or changed"
                else -> "Run ${chosen?.name ?: "validation"} on ${plural(targets.size, "asset", "assets")}"
            }
            YellowButton(label, vm::runFromSheet, Modifier.fillMaxWidth(), enabled = targets.isNotEmpty(), height = 44)
        }
    }
}

private class RunOption(val id: String, val name: String, val tagline: String, val ai: Boolean, val saved: Boolean)

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
            ) { Txt(l, 12, if (on) 700 else 500, if (on) C.OnYellow else C.Muted, maxLines = 1) }
        }
    }
}

@Composable
internal fun CheckBox(on: Boolean) {
    Box(
        Modifier.size(22.dp).clip(r(6)).background(if (on) C.Yellow else Color.Transparent)
            .border(1.5.dp, if (on) C.Yellow else C.RadioOff, r(6)),
        contentAlignment = Alignment.Center,
    ) { if (on) LineIcon(Icons.Check, 14.dp, C.OnYellow, 3f) }
}

@Composable
private fun AssetPicker(vm: AppViewModel, sh: com.example.sclerago.RunSheet) {
    val list = vm.assets
    BottomSheet(vm::closePicker, maxHeightFraction = 0.85f) {
        SheetHeader("Choose assets", plural(sh.chosen.size, "asset", "assets") + " ticked", vm::closePicker)
        val all = list.isNotEmpty() && list.all { it.id in sh.chosen }
        Row(
            Modifier.fillMaxWidth().clickable { vm.chooseAll(!all) }.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CheckBox(all)
            Txt("Select all (${list.size})", 14, 600)
        }
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
                        Txt(a.name, 14, 600)
                        Txt(a.detail, 12, 400, C.Muted)
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

@Composable
private fun ChecksSheet(name: String, rules: List<Rule>, onClose: () -> Unit) {
    BottomSheet(onClose) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 8.dp, bottom = 14.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Txt(name, 24, 700, maxLines = 2)
                Txt(plural(rules.size, "check", "checks"), 14, 400, C.Muted)
            }
            CloseButton(onClose, size = 40.dp, iconSize = 16.dp)
        }
        Column(
            Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            rules.forEach { r ->
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    LineIcon(Icons.CheckCircle, 22.dp, C.Yellow, 2f, Modifier.padding(top = 1.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Txt(r.name, 16, 600, modifier = Modifier.weight(1f, fill = false), maxLines = 2)
                            if (r.ai) AiBadge()
                        }
                        RULE_DESC[r.id]?.let { Txt(it, 13, 400, C.Muted, maxLines = 6) }
                    }
                }
            }
        }
    }
}

/**
 * New validation (full page): name and description, its steps (checks every asset goes through, in
 * order: start from a template, reorder with the handle or arrows) and the checks to add to them.
 */
@Composable
private fun EditorPage(vm: AppViewModel, e: com.example.sclerago.SavedEditor) {
    BackHandler(onBack = vm::closeEditor)
    var q by remember { mutableStateOf("") }
    var infoFor by remember { mutableStateOf<String?>(null) }
    val ok = e.name.isNotBlank() && e.ruleIds.isNotEmpty()
    // Drag to reorder steps: which step is held, how far it has moved, and each step's height.
    var dragId by remember { mutableStateOf<String?>(null) }
    // The finger's travel is applied straight away (not queued), so nothing left over can move the
    // step after release and leave it half-way between two places.
    var dragY by remember { mutableFloatStateOf(0f) }
    var settle by remember { mutableStateOf<Job?>(null) }
    val heights = remember { mutableStateMapOf<String, Int>() }
    val scope = rememberCoroutineScope()
    val gapPx = with(LocalDensity.current) { 8.dp.toPx() }
    /** Release: the step goes to the nearest place (past half-way counts as the next one), then slides into it. */
    fun drop(id: String) {
        val steps = vm.state.valEditor?.ruleIds ?: return
        val f = steps.indexOf(id)
        val slot = (heights[id] ?: 0) + gapPx
        if (f < 0 || slot <= 0f) {
            dragId = null
            dragY = 0f
            return
        }
        val t = (f + (dragY / slot).roundToInt()).coerceIn(0, steps.lastIndex)
        if (t != f) {
            vm.moveStep(id, t - f)
            dragY -= (t - f) * slot
        }
        settle = scope.launch {
            animate(dragY, 0f, animationSpec = tween(200)) { v, _ -> dragY = v }
            dragY = 0f
            dragId = null
        }
    }
    Column(Modifier.fillMaxSize().background(C.Bg).noRippleClick { }.blockSwipe()) {
        PageBar(if (e.id == null) "New validation" else "Edit validation", vm.propName, vm::closeEditor, property = true)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).navigationBarsPadding().imePadding().padding(horizontal = 6.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Name, description, save
            CfgCard {
                Column(Modifier.padding(horizontal = 9.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    LabeledInput("Name", e.name, vm::setEditorName, "e.g. Main Validation", compact = true)
                    LabeledInput("Description (optional)", e.description, vm::setEditorDesc, "What this is for", compact = true)
                    Row(Modifier.padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Pill("Cancel", vm::closeEditor, height = 38.dp, hPad = 14.dp, size = 13)
                        Pill("Save", { if (ok) vm.saveEditor() }, Modifier.alpha(if (ok) 1f else 0.5f), height = 38.dp, hPad = 16.dp, size = 13)
                        Pill(
                            "Save and run", { if (ok) vm.saveEditor(run = true) }, Modifier.weight(1f).alpha(if (ok) 1f else 0.5f),
                            bg = if (ok) C.Yellow else C.Btn, fg = if (ok) C.OnYellow else C.Text, height = 38.dp, size = 13, weight = 700,
                        )
                    }
                }
            }

            // Steps
            CfgCard {
                Column(Modifier.padding(horizontal = 9.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Txt("Steps", 15, 700)
                        Box(Modifier.size(20.dp).clip(CircleShape).background(C.Raised), contentAlignment = Alignment.Center) {
                            Txt(e.ruleIds.size.toString(), 11, 700, C.Soft)
                        }
                        Spacer(Modifier.weight(1f))
                        if (e.ruleIds.isNotEmpty()) Txt("Clear all", 12, 600, C.Soft, Modifier.clip(r(8)).clickable(onClick = vm::clearSteps).padding(4.dp))
                    }
                    Txt("Every asset goes through these in order. Drag the handle or use the arrows to reorder.", 12, 400, C.Muted, maxLines = 3)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Txt("Start from", 12, 500, C.Muted)
                        Spacer(Modifier.width(8.dp))
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            TEMPLATES.forEach { t ->
                                Box(
                                    Modifier.clip(Capsule).border(1.dp, C.Border2, Capsule).clickable { vm.stepsFrom(t.id) }
                                        .padding(horizontal = 11.dp, vertical = 6.dp),
                                ) { Txt(t.name.substringBefore(" and"), 12, 600) }
                            }
                        }
                    }
                    if (e.ruleIds.isEmpty()) {
                        Txt("No steps yet. Add checks below, or start from a template.", 12, 400, C.Muted, maxLines = 2)
                    }
                    // Drag to reorder: the held step follows the finger (lifted, above the rest), the steps it
                    // passes slide out of its way, and on release it settles into its new place.
                    val from = e.ruleIds.indexOf(dragId)
                    val slot = dragId?.let { (heights[it] ?: 0) + gapPx } ?: 0f
                    val target = if (from < 0 || slot <= 0f) from
                    else (from + (dragY / slot).roundToInt()).coerceIn(0, e.ruleIds.lastIndex)
                    e.ruleIds.forEachIndexed { i, id ->
                        key(id) {
                            RULES[id]?.let { rule ->
                                val held = id == dragId
                                val shift = when {
                                    from < 0 || held -> 0f
                                    target > from && i in (from + 1)..target -> -slot
                                    target < from && i in target until from -> slot
                                    else -> 0f
                                }
                                // A fresh animation per order, so a step that just moved doesn't slide back.
                                val slide = remember(e.ruleIds) { Animatable(0f) }
                                LaunchedEffect(shift) { slide.animateTo(shift, tween(180)) }
                                val lift by animateFloatAsState(if (held) 1f else 0f, tween(150), label = "lift")
                                StepRow(
                                    i, rule, first = i == 0, last = i == e.ruleIds.size - 1,
                                    onMove = { vm.moveStep(id, it) }, onInfo = { infoFor = id }, onRemove = { vm.removeStep(id) },
                                    modifier = Modifier
                                        .onSizeChanged { heights[id] = it.height }
                                        .zIndex(if (held) 1f else 0f)
                                        .graphicsLayer {
                                            translationY = if (held) dragY else slide.value
                                            scaleX = 1f + 0.03f * lift
                                            scaleY = 1f + 0.03f * lift
                                            shadowElevation = 14.dp.toPx() * lift
                                            shape = r(11)
                                        },
                                    handle = Modifier.pointerInput(id) {
                                        detectDragGestures(
                                            onDragStart = {
                                                dragId = id
                                                settle?.cancel(); dragY = 0f
                                            },
                                            onDragEnd = { drop(id) },
                                            onDragCancel = { drop(id) },
                                        ) { change, amount ->
                                            change.consume()
                                            dragY += amount.y
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            }

            // Checks to add
            CfgCard {
                Column(Modifier.padding(horizontal = 9.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Txt("Checks", 15, 700)
                    Txt("Press Add to put a check at the end of the steps.", 12, 400, C.Muted)
                    SearchField(q, { q = it }, "Search checks")
                    val query = q.trim()
                    val all = RULES.values.filter { r ->
                        query.isEmpty() || r.name.contains(query, true) || RULE_DESC[r.id].orEmpty().contains(query, true)
                    }
                    listOf("Each asset" to all.filter { it.id !in ACROSS_RULES }, "Across assets" to all.filter { it.id in ACROSS_RULES })
                        .filter { it.second.isNotEmpty() }
                        .forEach { (title, rules) ->
                            Txt(title.uppercase(), 11, 600, C.Muted, Modifier.padding(top = 4.dp), spacing = 0.8f)
                            rules.forEach { r -> CheckRow(r, r.id in e.ruleIds) { vm.addStep(r.id) } }
                        }
                    if (all.isEmpty()) Txt("No check matches “$query”.", 12, 400, C.Muted)
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
    infoFor?.let { id -> RULES[id] }?.let { r ->
        BottomSheet({ infoFor = null }) {
            SheetHeader(r.name, if (r.id in ACROSS_RULES) "Across assets" else "Each asset", { infoFor = null })
            Txt(RULE_DESC[r.id].orEmpty(), 15, 400, C.Soft, Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp), maxLines = 8)
        }
    }
}

/** One step: drag handle, number, check name and scope, then info, up, down and remove. */
@Composable
private fun StepRow(
    i: Int,
    rule: Rule,
    first: Boolean,
    last: Boolean,
    onMove: (Int) -> Unit,
    onInfo: () -> Unit,
    onRemove: () -> Unit,
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
            Txt("${i + 1}", 12, 700, C.Yellow)
        }
        Column(Modifier.weight(1f).padding(horizontal = 10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Txt(rule.name, 13, 600, maxLines = 2)
            Txt(if (rule.id in ACROSS_RULES) "Across assets" else "Each asset", 11, 400, C.Muted)
        }
        StepIcon(Icons.Gear, true, onInfo)
        StepIcon(Icons.ChevronDown, !first, { onMove(-1) }, flip = true)
        StepIcon(Icons.ChevronDown, !last, { onMove(1) })
        StepIcon(Icons.Close, true, onRemove)
    }
}

@Composable
private fun StepIcon(icon: Ico, enabled: Boolean, onClick: () -> Unit, flip: Boolean = false) {
    Box(Modifier.size(30.dp).clip(CircleShape).clickable(enabled = enabled, onClick = onClick), contentAlignment = Alignment.Center) {
        LineIcon(icon, 15.dp, if (enabled) C.Text else C.Raised, 2.2f, if (flip) Modifier.rotate(180f) else Modifier)
    }
}

/** A check that can be added to the steps; greyed "Added" once it's one of them. */
@Composable
private fun CheckRow(rule: Rule, added: Boolean, onAdd: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(r(11)).background(C.Bg).border(1.dp, C.Border, r(11)).padding(start = 14.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Txt(rule.name, 13, 600, modifier = Modifier.weight(1f, fill = false), maxLines = 2)
                if (rule.ai) AiBadge()
            }
            Txt(RULE_DESC[rule.id].orEmpty(), 11, 400, C.Muted, maxLines = 2)
        }
        if (added) {
            Row(
                Modifier.clip(Capsule).border(1.dp, C.Border2, Capsule).padding(horizontal = 10.dp, vertical = 5.dp).alpha(0.5f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                LineIcon(Icons.Check, 12.dp, C.Muted, 2.4f)
                Txt("Added", 12, 600, C.Muted)
            }
        } else {
            Row(
                Modifier.clip(Capsule).border(1.dp, C.Soft.copy(alpha = 0.6f), Capsule).clickable(onClick = onAdd).padding(horizontal = 11.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                LineIcon(Icons.PlusSmall, 12.dp, C.Text, 2.6f)
                Txt("Add", 12, 700)
            }
        }
    }
}

// ---------------------------------------------------------------- Review

@Composable
private fun JobReview(j: ValJob, vm: AppViewModel) {
    var filter by remember(j.id) { mutableStateOf<RuleOutcome?>(null) }
    Column(Modifier.fillMaxSize().background(C.Bg).noRippleClick { }) {
        PageBar(j.title, "${j.scopeLabel} · ${plural(j.checks, "check", "checks")} · ${relativeTime(j.startedAt)}", onBack = { vm.openReview(null) })
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 24.dp)) {
            item(key = "summary") {
                Column(
                    Modifier.padding(12.dp).fillMaxWidth().clip(r(14)).background(C.Surface).border(1.dp, C.Border, r(14)).padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Txt("${j.passed} of ${j.results.size} assets pass every rule", 15, 700, maxLines = 2)
                    ResultBar(j.failed, j.look, j.passed)
                    Counts(j.failed, j.look, j.passed)
                }
            }
            item(key = "filters") {
                Row(Modifier.padding(horizontal = 12.dp).padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf<Triple<RuleOutcome?, String, Int>>(
                        Triple(null, "All", j.results.size), Triple(RuleOutcome.FAIL, "Failed", j.failed),
                        Triple(RuleOutcome.LOOK, "To look at", j.look), Triple(RuleOutcome.PASS, "Passed", j.passed),
                    ).forEach { (o, label, n) ->
                        val on = filter == o
                        Row(
                            Modifier.height(32.dp).clip(Capsule).background(if (on) C.YTintBg else C.Surface)
                                .border(1.dp, if (on) C.Yellow else C.ChipBd, Capsule).clickable { filter = o }.padding(horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                        ) {
                            o?.let { Box(Modifier.size(7.dp).clip(CircleShape).background(outcomeColor(it))) }
                            Txt(label, 12, 600, if (on) C.Yellow else C.Soft)
                            Txt(n.toString(), 11, 700, (if (on) C.Yellow else C.Soft).copy(alpha = 0.8f))
                        }
                    }
                }
            }
            val shown = j.results.filter { filter == null || it.outcome == filter }
                .sortedBy { listOf(RuleOutcome.FAIL, RuleOutcome.LOOK, RuleOutcome.PASS, RuleOutcome.NA).indexOf(it.outcome) }
            if (shown.isEmpty()) item(key = "none") { EmptyNote("No assets here.") }
            items(shown, key = { it.assetId }) { res -> ResultRow(res, exists = vm.assets.any { it.id == res.assetId }) { vm.openAsset(res.assetId) } }
        }
    }
}

@Composable
private fun ResultRow(res: AssetResult, exists: Boolean, onOpen: () -> Unit) {
    Column(
        Modifier.padding(horizontal = 12.dp, vertical = 3.dp).fillMaxWidth().clip(r(12)).background(C.Surface)
            .border(1.dp, C.Border, r(12)).clickable(enabled = exists, onClick = onOpen).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            val c = outcomeColor(res.outcome)
            val icon = when (res.outcome) {
                RuleOutcome.FAIL -> Icons.OctagonX
                RuleOutcome.LOOK -> Icons.Alert
                else -> Icons.CheckCircle
            }
            LineIcon(icon, 20.dp, c, 2.2f)
            Txt(res.assetName, 15, 600, modifier = Modifier.weight(1f))
            if (exists) LineIcon(Icons.ChevronRight, 16.dp, C.Muted, 2.4f)
        }
        if (res.findings.isEmpty()) Txt("Passed every rule", 12, 400, C.Muted, Modifier.padding(start = 30.dp))
        res.findings.forEach { f ->
            Row(Modifier.padding(start = 30.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.padding(top = 6.dp).size(6.dp).clip(CircleShape).background(outcomeColor(f.outcome)))
                Column(Modifier.weight(1f)) {
                    Txt(f.rule, 13, 600, C.Soft, maxLines = 2)
                    f.message?.let { Txt(it, 12, 400, C.Muted, maxLines = 3) }
                }
            }
        }
    }
}
