package com.sclera.sclerago.presentation.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sclera.sclerago.presentation.icons.Icons
import com.sclera.sclerago.presentation.icons.LineIcon
import com.sclera.sclerago.presentation.theme.C
import com.sclera.sclerago.presentation.theme.Txt

// Small controls shared across screens: page bar, empty note, checkbox, inputs, cards, FAB.

@Composable
internal fun PageBar(title: String, sub: String?, onBack: () -> Unit, property: Boolean = false) {
    Box(Modifier.fillMaxWidth().background(C.Surface).bottomBorder(C.Border).statusBarsPadding().height(60.dp).padding(horizontal = 10.dp)) {
        RoundButton(42.dp, C.Btn, onBack, Modifier.align(Alignment.CenterStart)) { LineIcon(Icons.ChevronLeft, 22.dp, C.Yellow, 2.6f) }
        Column(Modifier.align(Alignment.Center).padding(horizontal = 56.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Txt(title, 16, 700)
            sub?.let {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    if (property) LineIcon(Icons.Building, 13.dp, C.Muted, 2f)
                    Txt(it, 11, 500, C.Muted)
                }
            }
        }
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

@Composable
internal fun CheckBox(on: Boolean) {
    Box(
        Modifier.size(22.dp).clip(r(7)).background(if (on) C.Yellow else Color.Transparent)
            .border(2.dp, if (on) C.Yellow else C.RadioOff, r(7)),
        contentAlignment = Alignment.Center,
    ) { if (on) LineIcon(Icons.Check, 12.dp, C.OnYellow, 3.4f) }
}

/** The yellow floating + (the same as the main screen's add-asset button). */
@Composable
internal fun AddFab(modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier.padding(end = 10.dp, bottom = 10.dp).size(54.dp).fabShadow()
            .clip(CircleShape).background(C.Yellow).clickable(onClick = onClick),
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
    Box(modifier.size(51.dp, 31.dp).clip(Capsule).background(track).clickable { onChange(!on) }) {
        Box(Modifier.padding(start = x, top = 2.dp).size(27.dp).clip(CircleShape).background(Color.White))
    }
}
