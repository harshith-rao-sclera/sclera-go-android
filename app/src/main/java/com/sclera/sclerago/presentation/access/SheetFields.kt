package com.sclera.sclerago.presentation.access

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.sclera.sclerago.presentation.common.UiText
import com.sclera.sclerago.presentation.components.AccessGate
import com.sclera.sclerago.presentation.components.PlainField
import com.sclera.sclerago.presentation.components.TintChip
import com.sclera.sclerago.presentation.components.r
import com.sclera.sclerago.presentation.icons.Icons
import com.sclera.sclerago.presentation.icons.LineIcon
import com.sclera.sclerago.presentation.theme.C
import com.sclera.sclerago.presentation.theme.Txt

// The stacked fields and option cards of the Add person and Group forms (design "AddPerson", "EditGroup"). Each control
// takes a `reason`: when it is set the control stays, greyed out, and a tap says why instead (the User Access Guide).

/** A label (13/600, muted) over its control. */
@Composable
internal fun StackedField(label: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Txt(label, 13, 600, C.Muted)
        content()
    }
}

/** The rounded 44dp box of a text input or select: dark fill, 1dp edge. */
@Composable
internal fun FieldBox(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier.fillMaxWidth().heightIn(min = 44.dp).clip(r(12)).background(C.Bg).border(1.dp, C.Border2, r(12))
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) { content() }
}

/** A text input in a [FieldBox]. */
@Composable
internal fun BoxedInput(
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    keyboard: KeyboardType = KeyboardType.Text,
    reason: UiText? = null,
) {
    AccessGate(reason, Modifier.fillMaxWidth()) {
        FieldBox { PlainField(value, onChange, placeholder, modifier.fillMaxWidth(), keyboard = keyboard) }
    }
}

/** A select in a [FieldBox]: its value (the placeholder, dim, while empty) and a chevron. */
@Composable
internal fun BoxedSelect(value: String, placeholder: String, reason: UiText? = null, onClick: () -> Unit) {
    AccessGate(reason, Modifier.fillMaxWidth()) {
        FieldBox(onClick = onClick) {
            Txt(value.ifEmpty { placeholder }, 15, 400, if (value.isEmpty()) C.Dim else C.Text, Modifier.weight(1f))
            LineIcon(Icons.ChevronDown, 12.dp, C.Muted, 3f)
        }
    }
}

/**
 * A choice drawn as a card (radio for one of several, checkbox for any): a yellow-tinted card with a yellow edge when
 * on. [marker] is the radio or checkbox, [title] the name with an optional [chip], [description] under it.
 */
@Composable
internal fun ChoiceCard(
    on: Boolean,
    role: Role,
    marker: @Composable () -> Unit,
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    chip: String? = null,
    reason: UiText? = null,
    onClick: () -> Unit,
) {
    AccessGate(reason, modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().clip(r(14)).background(if (on) C.YTintBg else Color.Transparent)
                .border(1.5.dp, if (on) C.Yellow else C.Border, r(14)).semantics { selected = on }
                .clickable(role = role, onClick = onClick).padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            marker()
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Txt(title, 14, 600, maxLines = 2, modifier = Modifier.weight(1f, fill = false))
                    chip?.let { TintChip(it, bg = C.Raised, fg = C.Soft) }
                }
                Txt(description, 12, 400, C.Muted, maxLines = Int.MAX_VALUE)
            }
        }
    }
}

/** Rows in a rounded card (the property list of the Add person form). */
@Composable
internal fun RowsCard(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().clip(r(14)).background(C.Surface).border(1.dp, C.Border, r(14)), content = content)
}

/** One 44dp row of a [RowsCard]. */
@Composable
internal fun CardOptionRow(
    modifier: Modifier = Modifier,
    reason: UiText? = null,
    onClick: () -> Unit,
    content: @Composable RowScope.() -> Unit,
) {
    AccessGate(reason, modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 44.dp).clickable(role = Role.Checkbox, onClick = onClick).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) { content() }
    }
}
