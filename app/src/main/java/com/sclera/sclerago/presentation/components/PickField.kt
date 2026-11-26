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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.sclera.sclerago.R
import com.sclera.sclerago.domain.model.Currency
import com.sclera.sclerago.presentation.icons.Icons
import com.sclera.sclerago.presentation.icons.LineIcon
import com.sclera.sclerago.presentation.theme.C
import com.sclera.sclerago.presentation.theme.Txt

/** A labelled box that opens a list to pick from. */
@Composable
internal fun PickField(label: String, value: String, placeholder: String, options: List<String>, modifier: Modifier = Modifier, onPick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        FieldLabel(label)
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

/**
 * A currency field's currency (its unit), picked from [currencies] as "USD - US Dollar" (the web's list); a code the
 * list doesn't have yet (still being read) shows as it is.
 */
@Composable
internal fun CurrencyPicker(code: String, currencies: List<Currency>, onPick: (String) -> Unit) {
    val names = currencies.map { it.code to stringResource(R.string.cfg_currency_option, it.code, it.name) }
    val shown = names.firstOrNull { it.first.equals(code, ignoreCase = true) }?.second ?: code
    PickField(stringResource(R.string.cfg_field_currency), shown, stringResource(R.string.cfg_field_currency_select), names.map { it.second }) { name ->
        names.firstOrNull { it.second == name }?.let { onPick(it.first) }
    }
}
