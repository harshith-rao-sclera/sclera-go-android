package com.sclera.sclerago.presentation.validation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sclera.sclerago.presentation.components.Capsule
import com.sclera.sclerago.presentation.components.Pill
import com.sclera.sclerago.presentation.components.r
import com.sclera.sclerago.presentation.components.topBorder
import com.sclera.sclerago.presentation.icons.Icons
import com.sclera.sclerago.presentation.icons.LineIcon
import com.sclera.sclerago.presentation.theme.C
import com.sclera.sclerago.presentation.theme.Txt
import com.sclera.sclerago.services.validation.CheckResult
import com.sclera.sclerago.services.validation.CheckState
import com.sclera.sclerago.services.validation.FixTarget
import com.sclera.sclerago.services.validation.dataQuality

private val RedBg = Color(0xFF2A1919)
private val RedBd = Color(0xFF4A2827)
private val RedDot = Color(0xFF3D2323)
private val GreenBg = Color(0xFF17241D)
private val GreenBd = Color(0xFF27402F)

/** How the Validation tab badge should look for these results. */
fun validationFailCount(results: List<CheckResult>) = results.count { it.state == CheckState.FAILED }

fun relativeTime(at: Long, now: Long = System.currentTimeMillis()): String {
    val s = (now - at) / 1000
    return when {
        s < 60 -> "just now"
        s < 3600 -> "${s / 60} min ago"
        s < 48 * 3600 -> "${s / 3600} h ago"
        else -> "${s / 86400} d ago"
    }
}

/**
 * The Validation tab: result banner, score, the nine checks (failures expanded with a fix link),
 * and revalidate controls.
 */
@Composable
fun ValidationPanel(
    results: List<CheckResult>,
    checkedAt: Long,
    fullCheck: Boolean,
    running: Boolean,
    runningFrom: Int,
    edited: Boolean,
    onRevalidate: (fromIndex: Int) -> Unit,
    onRunAll: () -> Unit,
    onFix: (FixTarget) -> Unit,
    onSubmit: (() -> Unit)?,
) {
    val failed = results.withIndex().filter { it.value.state == CheckState.FAILED }
    val applicable = results.count { it.state != CheckState.NA }
    val na = results.size - applicable
    val firstFail = failed.firstOrNull()?.index
    val quality = dataQuality(results)
    val ok = failed.isEmpty()

    Column(Modifier.padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Result banner
        Column(
            Modifier.fillMaxWidth().clip(r(14)).background(if (ok) GreenBg else RedBg)
                .border(1.dp, if (ok) GreenBd else RedBd, r(14)).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    Modifier.size(32.dp).background(if (ok) Color(0xFF1F3A2A) else RedDot, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    if (ok) LineIcon(Icons.Check, 16.dp, C.Green, 3f) else LineIcon(Icons.Close, 16.dp, C.Red, 2.8f)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Txt(
                        if (ok) "All $applicable checks passed"
                        else "${failed.size} of ${results.size} checks need attention",
                        15, 700, maxLines = 2,
                    )
                    Txt(
                        if (ok) (if (na > 0) "$na not applicable · " else "") + "ready to submit"
                        else "${failed.size} failed · fix, then revalidate",
                        13, 400, C.Soft, maxLines = 2,
                    )
                }
            }
            if (!ok) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Txt("Failed:", 12, 400, C.Muted)
                    failed.forEach { (_, r) ->
                        Box(Modifier.clip(r(6)).border(1.dp, Color(0xFF6A3433), r(6)).padding(horizontal = 9.dp, vertical = 3.dp)) {
                            Txt(r.name, 12, 600, C.Red)
                        }
                    }
                }
                Pill(
                    if (running) "Checking…" else "Revalidate from step ${firstFail!! + 1}",
                    { if (!running) onRevalidate(firstFail!!) }, Modifier.fillMaxWidth(),
                    bg = C.Yellow, fg = C.OnYellow, height = 46.dp, size = 15, weight = 700,
                )
            }
        }

        // Summary line
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                Modifier.clip(Capsule).background(if (ok) GreenBg else RedBg).border(1.dp, if (ok) GreenBd else RedBd, Capsule)
                    .padding(horizontal = 9.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (ok) LineIcon(Icons.Check, 11.dp, C.Green, 3.2f) else LineIcon(Icons.Close, 11.dp, C.Red, 3f)
                Txt(if (ok) "Passed" else "Failed", 12, 700, if (ok) C.Green else C.Red)
            }
            Txt("Data quality $quality/100", 13, 500, C.Soft)
            Txt((if (fullCheck) "Full check · " else "Quick check · ") + relativeTime(checkedAt), 13, 400, C.Muted)
        }
        if (edited) {
            Txt("You’ve edited this asset since this check. Run every step to include your changes.", 12, 500, C.Orange, maxLines = 3)
        }

        // The checks
        Column(Modifier.fillMaxWidth().clip(r(14)).background(C.Surface).border(1.dp, C.Border, r(14))) {
            results.forEachIndexed { i, r ->
                val pending = running && i >= runningFrom
                val isFirstFail = i == firstFail
                Column(
                    Modifier.fillMaxWidth().then(if (i > 0) Modifier.topBorder(C.Border) else Modifier)
                        .background(if (r.state == CheckState.FAILED && !pending) C.PickedRow else Color.Transparent)
                        .padding(horizontal = 14.dp, vertical = if (r.state == CheckState.FAILED && !pending) 12.dp else 0.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = if (r.state == CheckState.FAILED && !pending) 24.dp else 48.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        StepMarker(i + 1, if (pending) null else r.state)
                        Txt(
                            r.name, 14, if (r.state == CheckState.FAILED) 700 else if (r.state == CheckState.NA) 500 else 600,
                            if (r.state == CheckState.NA && !pending) C.Muted else C.Text, Modifier.weight(1f),
                        )
                        if (isFirstFail && !pending) {
                            Box(Modifier.clip(Capsule).background(C.YTintBg).border(1.dp, C.YTintBd, Capsule).padding(horizontal = 8.dp, vertical = 2.dp)) {
                                Txt("Starts here", 11, 600, C.Yellow)
                            }
                        }
                        when {
                            pending -> Txt("Checking…", 12, 500, C.Muted)
                            r.state == CheckState.PASSED -> Txt("Passed", 12, 400, C.Muted)
                            r.state == CheckState.NA -> Txt("Not applicable", 12, 400, C.Muted)
                            else -> Txt("Failed", 12, 600, C.Red)
                        }
                    }
                    if (r.state == CheckState.FAILED && !pending) {
                        r.messages.forEach { m ->
                            Row(Modifier.padding(start = 36.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Box(Modifier.padding(top = 7.dp).size(6.dp).background(C.Red, CircleShape))
                                Txt(m, 13, 400, C.Text, maxLines = 4, lineHeight = 19.sp)
                            }
                        }
                        r.fix?.let { target ->
                            Row(
                                Modifier.padding(start = 36.dp).heightIn(min = 32.dp).clip(r(8)).clickable { onFix(target) }
                                    .padding(end = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Txt(r.fixLabel ?: "Fix", 13, 700, C.Yellow)
                                LineIcon(Icons.ChevronRight, 14.dp, C.Yellow, 2.6f)
                            }
                        }
                    }
                }
            }
        }

        // Controls
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            when {
                !ok -> Pill(
                    if (running) "Checking…" else "Revalidate from step ${firstFail!! + 1}",
                    { if (!running) onRevalidate(firstFail!!) },
                    bg = C.Yellow, fg = C.OnYellow, height = 44.dp, hPad = 18.dp, size = 14, weight = 700,
                )
                onSubmit != null -> Row(
                    Modifier.height(44.dp).clip(Capsule).background(C.Blue).clickable(onClick = onSubmit).padding(horizontal = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    LineIcon(Icons.Upload, 16.dp, C.OnYellow, 2.4f)
                    Txt("Submit asset", 14, 700, C.OnYellow)
                }
            }
            if (!running) Pill(
                "Run every step", onRunAll,
                bg = Color.Transparent, fg = C.Yellow, height = 44.dp, hPad = 6.dp, size = 14, weight = 700,
            )
            if (running && ok) Txt("Checking…", 14, 700, C.Yellow)
        }
        Txt(
            when {
                !ok && firstFail!! > 0 -> "Steps 1–$firstFail keep their pass; checking starts again at ${results[firstFail].name}."
                !ok -> "Checking starts again at ${results[0].name}."
                else -> "Run every step after you edit the asset to check it again."
            },
            13, 400, C.Muted, maxLines = 3, lineHeight = 19.sp,
        )
    }
}

@Composable
private fun StepMarker(step: Int, state: CheckState?) {
    val (bg, fg) = when (state) {
        CheckState.PASSED -> GreenBg to C.Green
        CheckState.FAILED -> RedDot to C.Red
        else -> C.Raised to C.Muted
    }
    Box(Modifier.size(24.dp).background(bg, CircleShape), contentAlignment = Alignment.Center) {
        when (state) {
            CheckState.PASSED -> LineIcon(Icons.Check, 13.dp, fg, 3f)
            null -> CircularProgressIndicator(Modifier.size(14.dp), color = C.Muted, strokeWidth = 2.dp)
            else -> Txt(step.toString(), 12, 800, fg)
        }
    }
}
