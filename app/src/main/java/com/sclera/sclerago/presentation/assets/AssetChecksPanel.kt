package com.sclera.sclerago.presentation.assets

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.sclera.sclerago.R
import com.sclera.sclerago.domain.validation.AssetCheck
import com.sclera.sclerago.domain.validation.AssetCheckId
import com.sclera.sclerago.domain.validation.AssetChecks
import com.sclera.sclerago.domain.validation.CheckNote
import com.sclera.sclerago.domain.validation.CheckOutcome
import com.sclera.sclerago.presentation.components.Capsule
import com.sclera.sclerago.presentation.components.Pill
import com.sclera.sclerago.presentation.components.bottomBorder
import com.sclera.sclerago.presentation.components.r
import com.sclera.sclerago.presentation.components.topBorder
import com.sclera.sclerago.presentation.icons.Icons
import com.sclera.sclerago.presentation.icons.LineIcon
import com.sclera.sclerago.presentation.theme.C
import com.sclera.sclerago.presentation.theme.Txt

/** Test tags of the asset Validation tab. */
object ChecksTags {
    const val BANNER = "checks-banner"
    const val FOOTER = "checks-footer"
    fun row(id: AssetCheckId) = "check-${id.name}"
    fun fix(id: AssetCheckId) = "check-fix-${id.name}"
}

@get:StringRes
val AssetCheckId.titleRes: Int
    get() = when (this) {
        AssetCheckId.LOCATION -> R.string.check_location
        AssetCheckId.ASSET_TYPE -> R.string.check_asset_type
        AssetCheckId.JACS_CODE -> R.string.check_jacs
        AssetCheckId.MAKE_MODEL -> R.string.check_make_model
        AssetCheckId.SERIAL -> R.string.check_serial
        AssetCheckId.LOW_CONFIDENCE -> R.string.check_low_confidence
        AssetCheckId.PHOTO -> R.string.check_photo
    }

/** The line under a check name: the value that passed, or what is wrong. Null when there is nothing to say. */
@Composable
fun AssetCheck.noteText(): String? = when (note) {
    CheckNote.NONE -> null
    CheckNote.VALUE -> args.firstOrNull()
    CheckNote.REQUIRED_BEFORE_VALIDATE -> stringResource(R.string.check_note_required)
    CheckNote.MISSING -> stringResource(R.string.check_note_missing)
    CheckNote.LOW_CONFIDENCE_READING -> stringResource(R.string.check_note_low_confidence, args.firstOrNull().orEmpty())
    CheckNote.PHOTO_COUNT -> pluralStringResource(R.plurals.check_note_photos, count, count)
    CheckNote.NO_PHOTO -> stringResource(R.string.check_note_no_photo)
    CheckNote.PHOTOS_NEEDED -> pluralStringResource(R.plurals.check_note_photos_needed, needed, count, needed)
}

/**
 * The asset Validation tab: how many checks failed, the checks with a Fix button on the ones that need
 * work, and the Validate button (disabled, telling how many to fix, until none fail). [onFix] is null
 * when the asset can not be edited.
 */
@Composable
fun AssetChecksPanel(
    checks: AssetChecks,
    onFix: ((AssetCheck) -> Unit)?,
    onValidate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ChecksBanner(checks.failed)
        Column(Modifier.fillMaxWidth().background(C.Surface).topBorder(C.Border).bottomBorder(C.Border)) {
            checks.items.forEachIndexed { i, check ->
                if (i > 0) Box(Modifier.padding(start = 56.dp).fillMaxWidth().height(1.dp).background(C.Border))
                CheckRow(check, onFix)
            }
        }
        Row(Modifier.padding(horizontal = 12.dp).padding(top = 4.dp)) {
            if (checks.canValidate) {
                Pill(
                    stringResource(R.string.action_validate), onValidate, Modifier.weight(1f).testTag(ChecksTags.FOOTER),
                    bg = C.Yellow, fg = C.OnYellow, height = 48.dp, hPad = 18.dp, size = 15, weight = 700,
                )
            } else {
                // Not clickable: the checks have to be fixed first.
                Box(
                    Modifier.weight(1f).testTag(ChecksTags.FOOTER).height(48.dp).clip(Capsule).background(C.Raised),
                    contentAlignment = Alignment.Center,
                ) { Txt(pluralStringResource(R.plurals.check_fix_footer, checks.failed, checks.failed), 15, 700, C.Muted) }
            }
        }
    }
}

@Composable
private fun ChecksBanner(failed: Int) {
    val ok = failed == 0
    Row(
        Modifier.fillMaxWidth().testTag(ChecksTags.BANNER).background(C.Surface).topBorder(C.Border).bottomBorder(C.Border).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(40.dp).background(if (ok) C.GreenBg else C.DangerBg, r(12)),
            contentAlignment = Alignment.Center,
        ) { LineIcon(if (ok) Icons.ShieldCheck else Icons.ShieldX, 20.dp, if (ok) C.Green else C.Red, 2.3f) }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Txt(
                if (ok) stringResource(R.string.check_banner_passed) else pluralStringResource(R.plurals.check_banner_failed, failed, failed),
                16, 700,
            )
            Txt(
                stringResource(if (ok) R.string.check_banner_passed_sub else R.string.check_banner_failed_sub),
                12, 400, C.Muted, maxLines = 2,
            )
        }
    }
}

@Composable
private fun CheckRow(check: AssetCheck, onFix: ((AssetCheck) -> Unit)?) {
    val title = stringResource(check.id.titleRes)
    val note = check.noteText()
    Row(
        Modifier.fillMaxWidth().testTag(ChecksTags.row(check.id)).heightIn(min = 60.dp).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.width(32.dp), contentAlignment = Alignment.Center) { OutcomeGlyph(check.outcome) }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Txt(title, 14, 600, modifier = Modifier.weight(1f, fill = false))
                if (check.required && check.outcome != CheckOutcome.PASS) Txt(stringResource(R.string.check_required), 9, 700, C.Red, spacing = 0.3f)
            }
            note?.let { Txt(it, 12, 400, C.Muted, maxLines = 3) }
        }
        if (check.fix != null && onFix != null) {
            Box(
                Modifier.testTag(ChecksTags.fix(check.id)).height(48.dp).clickable { onFix(check) }.padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.height(32.dp).clip(Capsule).background(C.Btn).padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
                    Txt(stringResource(R.string.action_fix), 12, 600)
                }
            }
        }
    }
}

/** The filled circle in front of a check: a tick (passed), a cross (failed) or an exclamation mark (worth a look). */
@Composable
private fun OutcomeGlyph(outcome: CheckOutcome) {
    val (color, description) = when (outcome) {
        CheckOutcome.PASS -> C.Green to stringResource(R.string.check_state_passed)
        CheckOutcome.FAIL -> C.Red to stringResource(R.string.check_state_failed)
        CheckOutcome.WARN -> C.Orange to stringResource(R.string.check_state_warning)
    }
    Box(
        Modifier.size(20.dp).background(color, CircleShape).semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        when (outcome) {
            CheckOutcome.PASS -> LineIcon(Icons.Check, 12.dp, C.Bg, 3.2f)
            CheckOutcome.FAIL -> LineIcon(Icons.Close, 11.dp, C.Bg, 3.2f)
            CheckOutcome.WARN -> Txt("!", 13, 800, C.Bg)
        }
    }
}
