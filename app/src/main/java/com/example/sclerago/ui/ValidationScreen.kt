package com.example.sclerago.ui

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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
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
            PageBar("Validation", vm.propName, vm::closeValidation)
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 32.dp)) {
                item(key = "banner") { Banner { vm.openRunSheet() } }
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
                    SectionTitle("Saved validations", vm.savedVals.size) {
                        Pill("New", vm::newSaved, height = 34.dp, hPad = 12.dp, size = 12)
                    }
                }
                if (vm.savedVals.isEmpty()) item(key = "saved-empty") {
                    EmptyNote("No saved validations yet. Pick the rules you use most and save them here.")
                }
                items(vm.savedVals, key = { it.id }) { sv ->
                    SavedCard(
                        sv, vm.lastRun(sv.id),
                        onRun = { vm.runOnAll(sv.id) }, onEdit = { vm.editSaved(sv.id) }, onDelete = { vm.askDeleteSaved(sv.id) },
                    )
                }
                val jobs = vm.propJobs
                item(key = "jobs-title") { SectionTitle("Recent jobs", jobs.size) }
                if (jobs.isEmpty()) item(key = "jobs-empty") { EmptyNote("Nothing has run here yet.") }
                items(jobs, key = { "job-${it.id}" }) { j -> JobRow(j) { vm.openReview(j.id) } }
            }
        }

        // Layers on top of the page.
        s.valReview?.let { id -> vm.propJobs.firstOrNull { it.id == id } }?.let { JobReview(it, vm) }
        s.valChecksFor?.let { id -> ChecksSheet(vm.sourceName(id), vm.sourceRules(id), onRun = { vm.openRunSheet(id) }) { vm.openChecks(null) } }
        s.valSheet?.let { RunSheetView(vm, it) }
        if (s.valSheet != null && s.valPicker) AssetPicker(vm, s.valSheet)
        s.valEditor?.let { EditorSheet(vm, it) }
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
internal fun PageBar(title: String, sub: String?, onBack: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(C.Surface).bottomBorder(C.Border).statusBarsPadding().height(56.dp)
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        RoundButton(44.dp, Color.Transparent, onBack) { LineIcon(Icons.ChevronLeft, 22.dp, C.Yellow, 2.4f) }
        Column(Modifier.weight(1f)) {
            Txt(title, 18, 700)
            sub?.let { Txt(it, 12, 400, C.Muted) }
        }
    }
}

@Composable
private fun Banner(onRun: () -> Unit) {
    Column(
        Modifier.padding(12.dp).fillMaxWidth().clip(r(16)).background(Color(0xFF221F14)).border(1.dp, C.YTintBd, r(16))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(38.dp).clip(r(10)).background(C.YTintBg).border(1.dp, C.YTintBd, r(10)), contentAlignment = Alignment.Center) {
                LineIcon(Icons.ShieldCheck, 19.dp, C.Yellow, 2.2f)
            }
            Txt("Find and fix gaps and mistakes in your assets.", 14, 600, modifier = Modifier.weight(1f), maxLines = 2)
        }
        YellowButton("Run validation", onRun, Modifier.fillMaxWidth())
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
private fun SavedCard(sv: SavedValidation, lastRun: Long?, onRun: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
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
            Pill("Edit", onEdit, height = 36.dp, hPad = 14.dp)
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
    Box(Modifier.fillMaxSize().background(C.Scrim).noRippleClick(onDismiss).imePadding()) {
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
    val s = vm.state
    val targets = vm.scopeAssets(sh)
    BottomSheet(vm::closeRunSheet) {
        SheetHeader("Run validation", "Checks run in the background, so you can keep working.", vm::closeRunSheet)
        Column(
            Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Txt("WHAT TO CHECK", 11, 700, C.Muted, spacing = 0.6f)
            // (id, name, kind) where kind is "ai", "saved" or "".
            val options = TEMPLATES.map { t -> Triple(t.id, t.name, if (t.ai) "ai" else "") } +
                vm.savedVals.map { sv -> Triple(sv.id, sv.name, "saved") }
            options.chunked(2).forEach { pair ->
                Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    pair.forEach { (id, name, kind) ->
                        val body = TEMPLATES.firstOrNull { it.id == id }?.description ?: "Saved validation · ${plural(vm.sourceRules(id).size, "rule", "rules")}"
                        OptionCard(
                            name, body, sh.sourceId == id, { vm.setRunSource(id) }, Modifier.weight(1f).fillMaxHeight(),
                            badge = when (kind) {
                                "ai" -> ({ AiBadge() })
                                "saved" -> ({ Txt("saved", 11, 600, C.Muted) })
                                else -> null
                            },
                        )
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                vm.sourceRules(sh.sourceId).forEach { RuleChip(it.name, it.ai) }
            }
            Txt("WHICH ASSETS", 11, 700, C.Muted, Modifier.padding(top = 6.dp), spacing = 0.6f)
            ValScope.entries.forEach { sc ->
                val sub = when (sc) {
                    ValScope.CHOOSE -> if (sh.scope == sc) "${plural(sh.chosen.size, "asset", "assets")} ticked · tap to change" else sc.sub
                    ValScope.CHANGED -> if (vm.lastRun(sh.sourceId) == null) "${sc.sub} Not run yet, so every asset." else sc.sub
                    else -> sc.sub
                }
                OptionCard(sc.title, sub, sh.scope == sc, { if (sc == ValScope.CHOOSE && sh.scope == sc) vm.openPicker() else vm.setRunScope(sc) }, Modifier.fillMaxWidth())
            }
            Spacer(Modifier.height(4.dp))
        }
        Row(
            Modifier.fillMaxWidth().topBorder(C.Border).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val summary = when {
                sh.scope == ValScope.ALL -> "Every asset in this property"
                targets.isEmpty() -> if (sh.scope == ValScope.CHOOSE) "No assets ticked yet" else "Nothing new or changed"
                else -> plural(targets.size, "asset", "assets")
            }
            Txt(summary, 12, 500, C.Muted, Modifier.weight(1f), maxLines = 2)
            Pill("Cancel", vm::closeRunSheet, height = 40.dp, hPad = 14.dp)
            YellowButton("Run", vm::runFromSheet, enabled = targets.isNotEmpty(), height = 40)
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
private fun ChecksSheet(name: String, rules: List<Rule>, onRun: () -> Unit, onClose: () -> Unit) {
    BottomSheet(onClose, maxHeightFraction = 0.8f) {
        SheetHeader(name, plural(rules.size, "check", "checks"), onClose)
        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
            rules.forEachIndexed { i, r ->
                Row(
                    Modifier.fillMaxWidth().topBorder(C.Border).padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Txt("${i + 1}", 12, 700, C.Muted, Modifier.width(18.dp))
                    Txt(r.name, 14, 500, modifier = Modifier.weight(1f), maxLines = 2)
                    if (r.ai) AiBadge()
                }
            }
        }
        Row(Modifier.fillMaxWidth().topBorder(C.Border).padding(horizontal = 16.dp, vertical = 12.dp)) {
            YellowButton("Run this check", onRun, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun EditorSheet(vm: AppViewModel, e: com.example.sclerago.SavedEditor) {
    BottomSheet(vm::closeEditor) {
        SheetHeader(if (e.id == null) "New validation" else "Edit validation", "Pick the rules to check together.", vm::closeEditor)
        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
            PlainField(
                e.name, vm::setEditorName, "Name, e.g. Handover check",
                Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(r(10)).background(C.Bg)
                    .border(1.5.dp, if (e.name.isBlank()) C.Border else C.Yellow, r(10)).padding(12.dp),
                size = 15,
            )
            Txt("RULES · ${e.ruleIds.size} SELECTED", 11, 700, C.Muted, Modifier.padding(start = 16.dp, top = 16.dp, bottom = 6.dp), spacing = 0.6f)
            RULES.values.forEach { r ->
                Row(
                    Modifier.fillMaxWidth().topBorder(C.Border).clickable { vm.toggleEditorRule(r.id) }
                        .padding(horizontal = 16.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    CheckBox(r.id in e.ruleIds)
                    Txt(r.name, 14, 500, modifier = Modifier.weight(1f), maxLines = 2)
                    if (r.ai) AiBadge()
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().topBorder(C.Border).padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Pill("Cancel", vm::closeEditor, height = 44.dp, hPad = 16.dp)
            val ok = e.name.isNotBlank() && e.ruleIds.isNotEmpty()
            Pill(
                "Save", { if (ok) vm.saveEditor() }, Modifier.weight(1f).alpha(if (ok) 1f else 0.4f),
                bg = C.Yellow, fg = C.OnYellow, height = 44.dp, size = 14, weight = 700,
            )
        }
    }
}

// ---------------------------------------------------------------- Review

@Composable
private fun JobReview(j: ValJob, vm: AppViewModel) {
    var filter by remember(j.id) { mutableStateOf<RuleOutcome?>(null) }
    Column(Modifier.fillMaxSize().background(C.Bg).noRippleClick { }) {
        PageBar(j.title, "${j.scopeLabel} · ${plural(j.checks, "check", "checks")} · ${relativeTime(j.startedAt)}") { vm.openReview(null) }
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
