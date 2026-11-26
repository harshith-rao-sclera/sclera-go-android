package com.sclera.sclerago.presentation.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sclera.sclerago.R
import com.sclera.sclerago.presentation.icons.Icons
import com.sclera.sclerago.presentation.icons.LineIcon
import com.sclera.sclerago.presentation.theme.C
import com.sclera.sclerago.presentation.theme.Txt

// Small controls shared across screens: page bar, empty note, checkbox, inputs, cards, FAB.

/**
 * The Sclera Go mark (the login screen's and the Integration card's artwork), on the right of every page bar. Decorative:
 * TalkBack skips it.
 */
@Composable
fun ScleraGoLogo(modifier: Modifier = Modifier, height: Dp = 24.dp) {
    Image(
        painterResource(R.drawable.login_logo), contentDescription = null,
        modifier = modifier.height(height).aspectRatio(512f / 357f),
    )
}

@Composable
internal fun PageBar(title: String, sub: String?, onBack: () -> Unit, property: Boolean = false, backLabel: String? = null) {
    val textBack = backLabel != null
    Box(
        Modifier.fillMaxWidth().background(C.Surface).bottomBorder(C.Border).statusBarsPadding().height(60.dp)
            .padding(horizontal = if (textBack) 12.dp else 10.dp),
    ) {
        if (backLabel != null) {
            Row(
                Modifier.align(Alignment.CenterStart).height(44.dp).clip(r(8)).clickable(role = Role.Button, onClick = onBack),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                LineIcon(Icons.ChevronLeft, 20.dp, C.Yellow, 2.4f)
                Txt(backLabel, 17, 400, C.Yellow)
            }
        } else RoundButton(42.dp, C.Btn, onBack, Modifier.align(Alignment.CenterStart)) { LineIcon(Icons.ChevronLeft, 22.dp, C.Yellow, 2.6f) }
        Column(Modifier.align(Alignment.Center).padding(horizontal = 56.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            if (textBack) Txt(title, 17, 600) else Txt(title, 16, 700)
            sub?.let {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    if (property) LineIcon(Icons.Building, 13.dp, C.Muted, 2f)
                    Txt(it, 11, 500, C.Muted)
                }
            }
        }
        ScleraGoLogo(Modifier.align(Alignment.CenterEnd).padding(end = 6.dp))
    }
}

/** A list's first load: spinner over [text] (e.g. "Loading properties…"), read out as one line. */
@Composable
internal fun ListLoader(text: String, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(top = 40.dp).semantics(mergeDescendants = true) {},
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CircularProgressIndicator(Modifier.size(28.dp), color = C.Yellow, strokeWidth = 2.5.dp)
        Txt(text, 13, 400, C.Muted, maxLines = Int.MAX_VALUE, align = TextAlign.Center)
    }
}

@Composable
internal fun EmptyNote(text: String) {
    Txt(
        text, 12, 400, C.Muted,
        Modifier.padding(horizontal = 12.dp).fillMaxWidth().dashedBorder(C.Border2, 1.dp, 12.dp).padding(12.dp),
        maxLines = Int.MAX_VALUE,
    )
}

/**
 * A list that couldn't be read from the server: says so ([text]) in place of the list, rather than its "nothing yet"
 * note (a failed read isn't an empty one), with Try again ([onRetry]) to read it again.
 */
@Composable
internal fun LoadFailedNote(text: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().testTag(LoadFailedTags.NOTE),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        EmptyNote(text)
        Pill(stringResource(R.string.scrt_load_try_again), onRetry, Modifier.testTag(LoadFailedTags.RETRY), height = 40.dp, hPad = 16.dp)
    }
}

/** Test tags of [LoadFailedNote]. */
object LoadFailedTags {
    const val NOTE = "load-failed"
    const val RETRY = "load-failed-retry"
}

@Composable
internal fun CheckBox(on: Boolean) {
    Box(
        Modifier.size(22.dp).clip(r(7)).background(if (on) C.Yellow else Color.Transparent)
            .border(1.5.dp, if (on) C.Yellow else C.RadioOff, r(7)),
        contentAlignment = Alignment.Center,
    ) { if (on) LineIcon(Icons.Check, 12.dp, C.OnYellow, 3.4f) }
}

/** The yellow floating + (the same as the main screen's add-asset button). */
@Composable
internal fun AddFab(modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier.padding(end = 10.dp, bottom = 10.dp).size(54.dp).fabShadow()
            .pressClickable(shape = CircleShape, onClick = onClick).background(C.Yellow),
        contentAlignment = Alignment.Center,
    ) { LineIcon(Icons.Plus, 26.dp, C.OnYellow, 2.6f) }
}

@Composable
internal fun LabeledInput(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    singleLine: Boolean = true,
    minLines: Int = 1,
    /** Smaller text and padding, for denser pages. */
    compact: Boolean = true,
    /** The sheets' field of the design: 12 label (a trailing * in red), 44 high box, 15 text. */
    sheet: Boolean = false,
) {
    Column(verticalArrangement = Arrangement.spacedBy(if (compact) 5.dp else 8.dp)) {
        if (sheet) SheetFieldLabel(label) else FieldLabel(label)
        PlainField(
            value, onChange, placeholder,
            Modifier.fillMaxWidth()
                .heightIn(min = if (sheet) (if (minLines <= 1) 44.dp else 88.dp) else if (compact) 42.dp else 54.dp)
                .clip(r(if (compact) 10 else 12)).background(C.Bg)
                .border(1.dp, if (sheet) C.Border else C.Border2, r(if (compact) 10 else 12))
                .padding(horizontal = if (compact) 12.dp else 16.dp, vertical = if (compact) 11.dp else 15.dp),
            size = if (compact && !sheet) 13 else 15, singleLine = singleLine, minLines = minLines,
        )
    }
}

/** A sheet field's label: small and muted; a trailing * marks it mandatory and is drawn red. */
@Composable
internal fun SheetFieldLabel(label: String) {
    val req = label.endsWith("*")
    Row {
        Txt(label.removeSuffix("*").trimEnd(), 12, 600, C.Muted)
        if (req) Txt("*", 12, 700, C.Red, Modifier.padding(start = 2.dp))
    }
}

/** A rounded card the Configuration tabs group their rows in. */
@Composable
internal fun CfgCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().clip(r(14)).background(C.Surface).border(1.dp, C.Border, r(14)), content = content)
}

/** An on/off switch with a yellow track when on. */
@Composable
fun OnOffSwitch(on: Boolean, modifier: Modifier = Modifier, onChange: (Boolean) -> Unit) {
    val track by animateColorAsState(if (on) C.Yellow else C.RadioOff, label = "switch")
    val x by animateDpAsState(if (on) 22.dp else 2.dp, label = "thumb")
    Box(modifier.size(50.dp, 30.dp).pressClickable(shape = Capsule) { onChange(!on) }.background(track)) {
        Box(Modifier.padding(start = x, top = 3.dp).size(24.dp).clip(CircleShape).background(Color.White))
    }
}
