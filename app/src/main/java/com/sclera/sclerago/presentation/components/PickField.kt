package com.sclera.sclerago.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.sclera.sclerago.R
import com.sclera.sclerago.domain.model.Currency
import com.sclera.sclerago.presentation.icons.Icons
import com.sclera.sclerago.presentation.icons.LineIcon
import com.sclera.sclerago.presentation.theme.C
import com.sclera.sclerago.presentation.theme.Txt

/** A box that opens a list to pick from, with its [label] above it (none when null). */
@Composable
internal fun PickField(label: String?, value: String, placeholder: String, options: List<String>, modifier: Modifier = Modifier, onPick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        label?.let { FieldLabel(it) }
        Box {
            Row(
                Modifier.fillMaxWidth().height(44.dp).pressClickable(shape = r(12), pressed = PRESSED_CARD) { open = true }.background(C.Bg).border(1.dp, C.Border2, r(12))
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Txt(value.ifEmpty { placeholder }, 13, 400, if (value.isEmpty()) C.Dim else C.Text, Modifier.weight(1f))
                LineIcon(Icons.ChevronDown, 14.dp, C.Muted, 2.4f)
            }
            DropdownMenu(open, { open = false }, Modifier.heightIn(max = 360.dp), containerColor = C.Surface) {
                options.forEach { o ->
                    DropdownMenuItem(
                        text = { Txt(o, 14, if (o == value) 700 else 500, if (o == value) C.Yellow else C.Text) },
                        onClick = { onPick(o); open = false },
                    )
                }
            }
        }
    }
}

/** Test tags of a full-screen, searchable picker. */
object SearchablePickerTags {
    fun sheet(id: String) = "$id-picker"
    fun search(id: String) = "$id-picker-search"
    fun close(id: String) = "$id-picker-close"
    fun option(id: String, value: String) = "$id-picker-$value"
}

/** A labelled picker that opens the same searchable sheet used by the type selectors. */
@Composable
internal fun SearchablePickField(
    label: String,
    value: String,
    placeholder: String,
    options: List<Pair<String, String>>,
    pickerId: String,
    modifier: Modifier = Modifier,
    onPick: (String) -> Unit,
) {
    val shown = options.firstOrNull { it.first.equals(value, ignoreCase = true) }?.second ?: value
    var open by remember { mutableStateOf(false) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        FieldLabel(label)
        Row(
            Modifier.fillMaxWidth().height(44.dp).pressClickable(shape = r(12), pressed = PRESSED_CARD) { open = true }.background(C.Bg).border(1.dp, C.Border2, r(12))
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Txt(shown.ifEmpty { placeholder }, 13, 400, if (shown.isEmpty()) C.Dim else C.Text, Modifier.weight(1f))
            LineIcon(Icons.ChevronDown, 14.dp, C.Muted, 2.4f)
        }
    }
    if (open) SearchablePickerSheet(
        title = label,
        options = options,
        selectedValue = value,
        pickerId = pickerId,
        onPick = { picked -> onPick(picked); open = false },
        onClose = { open = false },
    )
}

/**
 * A currency field's currency (its unit), picked from [currencies] as "USD - US Dollar" (the web's list); a code the
 * list doesn't have yet (still being read) shows as it is.
 */
@Composable
internal fun CurrencyPicker(code: String, currencies: List<Currency>, onPick: (String) -> Unit) {
    val names = currencies.map { it.code to stringResource(R.string.cfg_currency_option, it.code, it.name) }
    SearchablePickField(
        label = stringResource(R.string.cfg_field_currency), value = code, placeholder = stringResource(R.string.cfg_field_currency_select),
        options = names, pickerId = "currency", onPick = onPick,
    )
}

/** The full-screen list and search used by long picker lists such as currencies and building address data. */
@Composable
internal fun SearchablePickerSheet(
    title: String,
    options: List<Pair<String, String>>,
    selectedValue: String,
    pickerId: String,
    onPick: (String) -> Unit,
    onClose: () -> Unit,
    descriptions: Map<String, String> = emptyMap(),
) {
    var query by remember { mutableStateOf("") }
    val q = query.trim()
    val shown = if (q.isEmpty()) options else options.filter { (value, name) -> value.contains(q, ignoreCase = true) || name.contains(q, ignoreCase = true) }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
    BottomSheet(onDismiss = onClose, fullHeight = true) {
        Column(Modifier.weight(1f).fillMaxWidth().testTag(SearchablePickerTags.sheet(pickerId))) {
            Box(Modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp, top = 4.dp, bottom = 12.dp), contentAlignment = Alignment.Center) {
                Txt(title, 17, 700, C.Text, Modifier.padding(horizontal = 84.dp), maxLines = 1, align = TextAlign.Center)
                Box(Modifier.align(Alignment.CenterEnd).testTag(SearchablePickerTags.close(pickerId))) { CloseButton(onClose, size = 40.dp, iconSize = 16.dp) }
            }
            SearchField(
                query, { query = it }, stringResource(R.string.action_search),
                Modifier.padding(horizontal = 16.dp).padding(bottom = 10.dp).testTag(SearchablePickerTags.search(pickerId)),
                height = 46.dp, bg = C.Raised,
            )
            LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp).topBorder(C.Border)) {
                items(shown, key = { it.first }) { (value, name) ->
                    val isSelected = value.equals(selectedValue, ignoreCase = true)
                    Row(
                        Modifier.fillMaxWidth().pressClickable(pressed = PRESSED_CARD) { onPick(value) }.testTag(SearchablePickerTags.option(pickerId, value))
                            .semantics { selected = isSelected; role = Role.RadioButton },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            Modifier.weight(1f).heightIn(min = 58.dp).bottomBorder(C.Border).padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Txt(name, 15, if (isSelected) 700 else 500, if (isSelected) C.Yellow else C.Text, maxLines = Int.MAX_VALUE)
                                descriptions[value]?.takeIf { it.isNotBlank() }?.let { Txt(it, 11, 400, C.Muted, maxLines = Int.MAX_VALUE) }
                            }
                            if (isSelected) LineIcon(Icons.Check, 18.dp, C.Yellow, 2.6f)
                        }
                    }
                }
                if (shown.isEmpty()) item { Txt(stringResource(R.string.cfgt_no_match), 14, 400, C.Muted, Modifier.padding(vertical = 20.dp), maxLines = Int.MAX_VALUE) }
            }
        }
    }
    }
}
