package com.sclera.sclerago.presentation.property

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.sclera.sclerago.domain.model.AddressSuggestion
import com.sclera.sclerago.presentation.components.*
import com.sclera.sclerago.presentation.icons.Icons
import com.sclera.sclerago.presentation.icons.LineIcon
import com.sclera.sclerago.presentation.theme.C
import com.sclera.sclerago.presentation.theme.Txt

/** Full-width address suggestions with a free-text fallback. */
@Composable
internal fun AddressPickerSheet(field: AddressField, title: String, country: String, selectedValue: String,
    suggestions: AddressSuggestions?, onSearch: (String) -> Unit, onPick: (AddressSuggestion) -> Unit, onClose: () -> Unit) {
    var query by remember(field) { mutableStateOf("") }
    LaunchedEffect(field, query) { onSearch(query) }
    val here = suggestions?.takeIf { it.field == field }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        BottomSheet(onDismiss = onClose, fullHeight = true) {
            Column(Modifier.weight(1f).fillMaxWidth()) {
                Box(Modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp, top = 4.dp, bottom = 12.dp), contentAlignment = Alignment.Center) {
                    Txt(title, 17, 700, C.Text, Modifier.padding(horizontal = 60.dp), maxLines = 2, align = TextAlign.Center)
                    Box(Modifier.align(Alignment.CenterEnd)) { CloseButton(onClose, size = 40.dp, iconSize = 16.dp) }
                }
                if (field != AddressField.COUNTRY && country.isNotBlank()) Txt(country, 12, 500, C.Muted, Modifier.padding(start = 16.dp, bottom = 8.dp))
                SearchField(query, { query = it }, stringResource(R.string.action_search), Modifier.padding(horizontal = 16.dp).padding(bottom = 10.dp), height = 46.dp, bg = C.Raised)
                LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp).topBorder(C.Border)) {
                    if (here?.searching != false) item { Txt(stringResource(R.string.scrt_suggestions_loading), 14, 400, C.Muted, Modifier.padding(vertical = 20.dp)) }
                    else {
                        itemsIndexed(here.items) { _, suggestion ->
                            val isSelected = suggestion.name.equals(selectedValue, ignoreCase = true)
                            Row(Modifier.fillMaxWidth().pressClickable(pressed = PRESSED_CARD) { onPick(suggestion) }
                                .semantics { selected = isSelected; role = Role.RadioButton }
                                .heightIn(min = 58.dp).bottomBorder(C.Border).padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Txt(suggestion.name, 15, if (isSelected) 700 else 500, if (isSelected) C.Yellow else C.Text, maxLines = Int.MAX_VALUE)
                                    suggestion.detail?.let { Txt(it, 11, 400, C.Muted, maxLines = Int.MAX_VALUE) }
                                }
                                if (isSelected) LineIcon(Icons.Check, 18.dp, C.Yellow, 2.6f)
                            }
                        }
                        if (here.items.isEmpty()) item { Txt(stringResource(R.string.cfgt_no_match), 14, 400, C.Muted, Modifier.padding(vertical = 20.dp)) }
                    }
                    if (query.isNotBlank()) item {
                        Txt(stringResource(R.string.address_use_typed, query.trim()), 14, 600, C.Yellow,
                            Modifier.fillMaxWidth().pressClickable(pressed = PRESSED_CARD) { onPick(AddressSuggestion(query.trim())) }.padding(vertical = 16.dp), maxLines = Int.MAX_VALUE)
                    }
                }
            }
        }
    }
}
