package com.sclera.sclerago.presentation.assets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sclera.sclerago.R
import com.sclera.sclerago.domain.model.MasterFormat
import com.sclera.sclerago.domain.model.UNCLASSIFIED
import com.sclera.sclerago.domain.model.assetTypes
import com.sclera.sclerago.domain.model.formatOf
import com.sclera.sclerago.domain.model.subtypes
import com.sclera.sclerago.domain.model.systemTypes
import com.sclera.sclerago.presentation.components.PRESSED_CARD
import com.sclera.sclerago.presentation.components.BottomSheet
import com.sclera.sclerago.presentation.components.CloseButton
import com.sclera.sclerago.presentation.components.SearchField
import com.sclera.sclerago.presentation.components.bottomBorder
import com.sclera.sclerago.presentation.components.pressClickable
import com.sclera.sclerago.presentation.components.topBorder
import com.sclera.sclerago.presentation.icons.Icons
import com.sclera.sclerago.presentation.icons.LineIcon
import com.sclera.sclerago.presentation.icons.typeIco
import com.sclera.sclerago.presentation.theme.C
import com.sclera.sclerago.presentation.theme.Txt

/** Test tags of the type picker. */
object TypePickerTags {
    const val SHEET = "type-picker"
    const val SEARCH = "type-picker-search"
    const val CLOSE = "type-picker-close"
    fun option(value: String) = "type-picker-$value"
}

/** Which of the asset's type rows is being picked ("sys", "typ" or "sub") and the row's label, the sheet's title. */
data class TypePick(val key: String, val label: String)

/**
 * Picks the asset's system type, asset type or sub type ([pick]) from the property's catalogue ([formats]) in a sheet:
 * titled with the row's name, a close button at the top, a search over the names, and one row per type with its icon. The asset
 * and sub types are those filed under what [values] already choose above them. A tap picks the type ([onPick]) and
 * closes the sheet; "Not classified" clears a type already chosen.
 */
@Composable
internal fun TypePickerSheet(
    pick: TypePick,
    formats: List<MasterFormat>,
    values: (String) -> String,
    onPick: (key: String, value: String) -> Unit,
    onClose: () -> Unit,
) {
    var query by remember(pick.key) { mutableStateOf("") }
    val sys = values("sys")
    val typ = values("typ")
    val current = values(pick.key)
    val choices = when (pick.key) {
        "sys" -> formats.systemTypes()
        "typ" -> formats.assetTypes(sys)
        else -> formats.subtypes(sys, typ)
    }.filter { it != UNCLASSIFIED }
    val q = query.trim()
    val shown = if (q.isEmpty()) choices else choices.filter { it.contains(q, ignoreCase = true) }
    val chosen = current.isNotBlank() && current != UNCLASSIFIED

    fun formatId(name: String): String? = when (pick.key) {
        "sys" -> formats.formatOf(name)
        "typ" -> formats.formatOf(sys, name)
        else -> formats.formatOf(sys, typ, name)
    }?.id

    // A full-height bottom sheet with its handle (it keeps its height while searching): dragged down (or a tap above it, or Back) it closes, as the close button does.
    // Docked at the bottom on phones, centred on tablets.
    BottomSheet(onDismiss = onClose, fullHeight = true) {
        Column(Modifier.weight(1f).fillMaxWidth().testTag(TypePickerTags.SHEET)) {
            // The row's name in the middle, the close button at the end.
            Box(Modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp, top = 4.dp, bottom = 12.dp), contentAlignment = Alignment.Center) {
                Txt(pick.label.removeSuffix(" *"), 17, 700, C.Text, Modifier.padding(horizontal = 84.dp), maxLines = 1, align = TextAlign.Center)
                Box(Modifier.align(Alignment.CenterEnd).testTag(TypePickerTags.CLOSE)) { CloseButton(onClose, size = 40.dp, iconSize = 16.dp) }
            }
            SearchField(
                query, { query = it }, stringResource(R.string.action_search),
                Modifier.padding(horizontal = 16.dp).padding(bottom = 10.dp).testTag(TypePickerTags.SEARCH),
                height = 46.dp, bg = C.Raised,
            )
            LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp).topBorder(C.Border)) {
                items(shown, key = { it }) { name ->
                    TypeRow(name, typeIco(formatId(name)), on = name.equals(current, ignoreCase = true)) {
                        onPick(pick.key, name)
                        onClose()
                    }
                }
                if (shown.isEmpty()) {
                    item {
                        // Nothing to list until the type above is chosen; otherwise the search matches nothing.
                        val empty = when {
                            choices.isNotEmpty() -> R.string.cfgt_no_match
                            pick.key == "typ" -> R.string.type_picker_system_first
                            pick.key == "sub" -> R.string.type_picker_type_first
                            else -> R.string.type_picker_none_listed
                        }
                        Txt(stringResource(empty), 14, 400, C.Muted, Modifier.padding(vertical = 20.dp), maxLines = Int.MAX_VALUE)
                    }
                }
                // A type already chosen can be cleared ("-" on the form), as the dropdown's first choice did.
                if (chosen && q.isEmpty()) {
                    item(key = UNCLASSIFIED) {
                        TypeRow(stringResource(R.string.type_picker_none), Icons.Close, on = false, muted = true) {
                            onPick(pick.key, UNCLASSIFIED)
                            onClose()
                        }
                    }
                }
            }
        }
    }
}

/** One type: its icon, then its name over a line that starts at the name; the chosen one in yellow with a tick. */
@Composable
private fun TypeRow(name: String, icon: com.sclera.sclerago.presentation.icons.Ico, on: Boolean, muted: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().pressClickable(pressed = PRESSED_CARD, onClick = onClick).testTag(TypePickerTags.option(name))
            .semantics { selected = on; role = Role.RadioButton },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        LineIcon(icon, 22.dp, if (muted) C.Muted else C.Yellow, 1.9f)
        Row(
            Modifier.weight(1f).heightIn(min = 58.dp).bottomBorder(C.Border).padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Txt(name, 15, if (on) 700 else 500, if (on) C.Yellow else if (muted) C.Muted else C.Text, Modifier.weight(1f), maxLines = Int.MAX_VALUE)
            if (on) LineIcon(Icons.Check, 18.dp, C.Yellow, 2.6f)
        }
    }
}
