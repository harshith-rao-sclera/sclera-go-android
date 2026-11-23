package com.sclera.sclerago.presentation.validation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sclera.sclerago.R
import com.sclera.sclerago.domain.validation.Decision
import com.sclera.sclerago.domain.validation.RunStatus
import com.sclera.sclerago.domain.validation.StepGroup
import com.sclera.sclerago.domain.validation.StepOutcome
import com.sclera.sclerago.domain.validation.StepState
import com.sclera.sclerago.domain.validation.ValRun
import com.sclera.sclerago.domain.validation.ValStep
import com.sclera.sclerago.presentation.common.asString
import com.sclera.sclerago.presentation.components.Capsule
import com.sclera.sclerago.presentation.components.r
import com.sclera.sclerago.presentation.icons.Ico
import com.sclera.sclerago.presentation.icons.Icons
import com.sclera.sclerago.presentation.icons.LineIcon
import com.sclera.sclerago.presentation.theme.C
import com.sclera.sclerago.presentation.theme.Txt
import java.text.DateFormat
import java.util.Date
import kotlin.math.roundToInt

// Pieces the Validation page, its sheets and the run review share.

internal fun outcomeColor(o: StepOutcome): Color = when (o) {
    StepOutcome.FAILED -> C.Red
    StepOutcome.WARNING -> C.Orange
    StepOutcome.PASSED -> C.Green
    StepOutcome.SKIPPED -> C.Dim
}

internal fun stepStateColor(s: StepState): Color = when (s) {
    StepState.FAILED -> C.Red
    StepState.WARNING -> C.Orange
    StepState.PASSED -> C.Green
    StepState.SKIPPED, StepState.PENDING -> C.Dim
}

internal fun outcomeIcon(o: StepOutcome): Ico = when (o) {
    StepOutcome.FAILED -> Icons.OctagonX
    StepOutcome.WARNING -> Icons.Alert
    StepOutcome.PASSED -> Icons.CheckCircle
    StepOutcome.SKIPPED -> Icons.Minus
}

@Composable
internal fun outcomeLabel(o: StepOutcome): String = stringResource(
    when (o) {
        StepOutcome.PASSED -> R.string.val_outcome_passed
        StepOutcome.WARNING -> R.string.val_outcome_warning
        StepOutcome.FAILED -> R.string.val_outcome_failed
        StepOutcome.SKIPPED -> R.string.val_outcome_skipped
    },
)

@Composable
internal fun decisionLabel(d: Decision): String = stringResource(
    when (d) {
        Decision.VALIDATED -> R.string.rev_decision_validated
        Decision.RECOLLECT -> R.string.rev_decision_recollect
        Decision.EXCLUDED, Decision.CLEAR -> R.string.rev_decision_excluded
    },
)

internal fun decisionColor(d: Decision): Color = when (d) {
    Decision.VALIDATED -> C.Green
    Decision.RECOLLECT -> C.Orange
    Decision.EXCLUDED, Decision.CLEAR -> C.Dim
}

internal fun scoreColor(score: Double): Color = when {
    score >= 85 -> C.Green
    score >= 60 -> C.Orange
    else -> C.Red
}

/** A step's name: the catalogue's, or its key when the catalogue isn't loaded (the server's names are the keys capitalised). */
internal fun stepName(steps: Map<String, ValStep>, key: String): String = steps[key]?.name ?: key.replaceFirstChar { it.uppercase() }

internal fun stepShort(steps: Map<String, ValStep>, key: String): String = steps[key]?.short ?: key.replaceFirstChar { it.uppercase() }

@Composable
internal fun SmallChip(text: String, color: Color = C.Soft, bg: Color = C.Raised) {
    Box(Modifier.height(22.dp).clip(Capsule).background(bg).padding(horizontal = 8.dp), contentAlignment = Alignment.Center) { Txt(text, 11, 700, color) }
}

/** A small text button (≥48dp tall to touch). */
@Composable
internal fun TextAction(text: String, onClick: () -> Unit, color: Color = C.Yellow, enabled: Boolean = true, icon: Ico? = null) {
    Row(
        Modifier.heightIn(min = 48.dp).clip(r(10)).clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .alpha(if (enabled) 1f else 0.4f).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        icon?.let { LineIcon(it, 15.dp, color, 2.2f) }
        Txt(text, 12, 700, color)
    }
}

@Composable
internal fun Dot(color: Color, size: Dp = 8.dp, modifier: Modifier = Modifier) {
    Box(modifier.size(size).clip(CircleShape).background(color))
}
