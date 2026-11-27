package com.sclera.sclerago.presentation.assets

import com.sclera.sclerago.presentation.components.pressClickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.sclera.sclerago.R
import com.sclera.sclerago.domain.model.FieldKind
import com.sclera.sclerago.domain.model.OWN_FIELD_KINDS
import com.sclera.sclerago.domain.model.OWN_FIELD_NAME_MAX
import com.sclera.sclerago.domain.model.OPTION_KINDS
import com.sclera.sclerago.presentation.common.asString
import com.sclera.sclerago.presentation.common.UiText
import com.sclera.sclerago.presentation.components.AccessGate
import com.sclera.sclerago.presentation.components.Capsule
import com.sclera.sclerago.presentation.components.ChoiceList
import com.sclera.sclerago.presentation.components.CurrencyPicker
import com.sclera.sclerago.presentation.components.FieldLabel
import com.sclera.sclerago.presentation.components.FormSheet
import com.sclera.sclerago.presentation.components.SheetInput
import com.sclera.sclerago.presentation.components.topBorder
import com.sclera.sclerago.presentation.icons.Icons
import com.sclera.sclerago.presentation.icons.LineIcon
import com.sclera.sclerago.presentation.theme.C
import com.sclera.sclerago.presentation.theme.Txt

/** Test tags of Add field. */
object AddFieldTags {
    const val OPEN = "add-field-open"
    const val NAME = "add-field-name"
    const val OPTIONS = "add-field-options"
    fun remove(label: String) = "add-field-remove-$label"
}

/**
 * What Add field does: open its sheet, add the field named and typed there, note the name changed, close it; and
 * remove one of the asset's own fields (by key, once confirmed).
 */
class AddFieldActions(
    val onOpen: () -> Unit = {},
    /** [options]: a dropdown's or multiple check boxes' choices; [currency]: a currency field's code. */
    val onAdd: (name: String, kind: FieldKind, options: List<String>, currency: String?) -> Unit = { _, _, _, _ -> },
    val onNameChange: () -> Unit = {},
    val onClose: () -> Unit = {},
    val onRemove: (key: String) -> Unit = {},
)

/**
 * One of the asset's own fields with its remove button at the end (a 48dp target): [row] is the field's usual row.
 * The line above spans the button too, so the row reads as one. [blocked]: the signed-in user may not change the asset,
 * so the button is greyed out and a tap says why.
 */
@Composable
internal fun OwnFieldRow(label: String, onRemove: () -> Unit, blocked: UiText? = null, row: @Composable () -> Unit) {
    val description = stringResource(R.string.own_field_remove_label, label)
    Row(Modifier.fillMaxWidth().topBorder(C.Border), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f)) { row() }
        AccessGate(blocked) {
            Box(
                Modifier.pressClickable(role = Role.Button, onClick = onRemove).size(48.dp)
                    .semantics { contentDescription = description }.testTag(AddFieldTags.remove(label)),
                contentAlignment = Alignment.Center,
            ) {
                LineIcon(Icons.Trash, 17.dp, C.Muted, 2.2f)
            }
        }
    }
}

/** The last row of the Info tab's fields, as the web's: "+ Add field", for this asset only ([blocked]: greyed out, saying why). */
@Composable
internal fun AddFieldRow(onClick: () -> Unit, blocked: UiText? = null) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).topBorder(C.Border).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        AccessGate(blocked) {
            Box(Modifier.heightIn(min = 48.dp).pressClickable(shape = Capsule, role = Role.Button, onClick = onClick).testTag(AddFieldTags.OPEN), contentAlignment = Alignment.Center) {
                Row(
                    Modifier.height(28.dp).clip(Capsule).background(C.YTintBg).border(1.dp, C.YTintBd, Capsule).padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    LineIcon(Icons.Plus, 12.dp, C.Yellow, 2.4f)
                    Txt(stringResource(R.string.own_field_add), 12, 700, C.Yellow)
                }
            }
        }
        Txt(stringResource(R.string.own_field_hint), 11, 500, C.Muted, Modifier.weight(1f), maxLines = Int.MAX_VALUE)
    }
}

/**
 * Add field (For this asset only): a name and a type, the types Configuration's Add field offers. A dropdown or multiple
 * check boxes also takes its options (one per line), a currency field its currency. The field is added to this asset alone
 * and shows at the end of its fields, empty, to fill in like the rest.
 */
@Composable
internal fun AddFieldSheet(state: AssetDetailState, actions: AddFieldActions) {
    var name by rememberSaveable { mutableStateOf("") }
    var kind by rememberSaveable { mutableStateOf(FieldKind.TEXT) }
    var options by rememberSaveable { mutableStateOf("") }
    var currency by rememberSaveable { mutableStateOf("") }
    val busy = state.addingField
    val labels = OWN_FIELD_KINDS.associateWith { stringResource(it.ownFieldLabel()) }
    val choices = options.lines().map { it.trim() }.filter { it.isNotEmpty() }
    val complete = name.isNotBlank() &&
        (kind !in OPTION_KINDS || choices.isNotEmpty()) && (kind != FieldKind.CURRENCY || currency.isNotBlank())

    FormSheet(
        title = stringResource(R.string.own_field_title),
        sub = stringResource(R.string.own_field_sub),
        primary = stringResource(if (busy) R.string.own_field_adding else R.string.own_field_go),
        primaryEnabled = complete && !busy,
        error = state.addFieldError?.asString(),
        onClose = actions.onClose,
        onPrimary = { if (complete && !busy) actions.onAdd(name, kind, choices, currency.takeIf { kind == FieldKind.CURRENCY }) },
        leadingIcon = Icons.Plus,
        dirty = name.isNotBlank(),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FieldLabel(stringResource(R.string.own_field_name))
            Box(Modifier.testTag(AddFieldTags.NAME)) {
                SheetInput(name, { name = it.take(OWN_FIELD_NAME_MAX); actions.onNameChange() }, stringResource(R.string.own_field_name_hint))
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FieldLabel(stringResource(R.string.own_field_type))
            ChoiceList(OWN_FIELD_KINDS, { labels.getValue(it) }, { it == kind }, { kind = it })
        }
        if (kind in OPTION_KINDS) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FieldLabel(stringResource(R.string.own_field_options))
                Box(Modifier.testTag(AddFieldTags.OPTIONS)) {
                    SheetInput(options, { options = it; actions.onNameChange() }, stringResource(R.string.own_field_options_hint), multiLine = true)
                }
            }
        }
        if (kind == FieldKind.CURRENCY) {
            CurrencyPicker(currency, state.currencies) { currency = it; actions.onNameChange() }
            if (state.currencies.isEmpty()) Txt(stringResource(R.string.own_field_currency_unavailable), 11, 500, C.Muted, maxLines = Int.MAX_VALUE)
        }
    }
}

/** The names of the types a field added to one asset can have. */
private fun FieldKind.ownFieldLabel() = when (this) {
    FieldKind.CHECKBOX -> R.string.own_field_type_checkbox
    FieldKind.MULTI -> R.string.own_field_type_multi
    FieldKind.YESNO -> R.string.own_field_type_yesno
    FieldKind.DATE -> R.string.own_field_type_date
    FieldKind.TIME -> R.string.own_field_type_time
    FieldKind.NUMBER -> R.string.own_field_type_number
    FieldKind.CURRENCY -> R.string.own_field_type_currency
    FieldKind.URL -> R.string.own_field_type_link
    FieldKind.DROPDOWN -> R.string.own_field_type_dropdown
    else -> R.string.own_field_type_text
}
