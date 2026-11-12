package com.sclera.sclerago.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
internal fun EmptyNote(text: String) {
    Txt(
        text, 13, 400, C.Muted,
        Modifier.padding(horizontal = 12.dp).fillMaxWidth().dashedBorder(C.Border2, 1.dp, 12.dp).padding(16.dp),
        maxLines = 3,
    )
}

@Composable
internal fun CheckBox(on: Boolean) {
    Box(
        Modifier.size(22.dp).clip(r(6)).background(if (on) C.Yellow else Color.Transparent)
            .border(1.5.dp, if (on) C.Yellow else C.RadioOff, r(6)),
        contentAlignment = Alignment.Center,
    ) { if (on) LineIcon(Icons.Check, 14.dp, C.OnYellow, 3f) }
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
) {
    Column(verticalArrangement = Arrangement.spacedBy(if (compact) 5.dp else 8.dp)) {
        FieldLabel(label)
        PlainField(
            value, onChange, placeholder,
            Modifier.fillMaxWidth().heightIn(min = if (compact) 42.dp else 54.dp).clip(r(if (compact) 10 else 12)).background(C.Bg)
                .border(1.dp, C.Border2, r(if (compact) 10 else 12))
                .padding(horizontal = if (compact) 12.dp else 16.dp, vertical = if (compact) 11.dp else 15.dp),
            size = if (compact) 14 else 16, singleLine = singleLine, minLines = minLines,
        )
    }
}

/** A rounded card the Configuration tabs group their rows in. */
@Composable
internal fun CfgCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().clip(r(14)).background(C.Surface).border(1.dp, C.Border, r(14)), content = content)
}
