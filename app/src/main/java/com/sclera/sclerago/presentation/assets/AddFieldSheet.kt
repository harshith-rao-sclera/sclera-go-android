package com.sclera.sclerago.presentation.assets

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
import androidx.compose.foundation.shape.CircleShape
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
import com.sclera.sclerago.presentation.common.asString
import com.sclera.sclerago.presentation.components.Capsule
import com.sclera.sclerago.presentation.components.ChoiceList
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
    fun remove(label: String) = "add-field-remove-$label"
}

/**
 * What Add field does: open its sheet, add the field named and typed there, note the name changed, close it; and
 * remove one of the asset's own fields (by key, once confirmed).
 */
class AddFieldActions(
    val onOpen: () -> Unit = {},
    val onAdd: (name: String, kind: FieldKind) -> Unit = { _, _ -> },
    val onNameChange: () -> Unit = {},
    val onClose: () -> Unit = {},
    val onRemove: (key: String) -> Unit = {},
)

/**
 * One of the asset's own fields with its remove button at the end (a 48dp target): [row] is the field's usual row.
 * The line above spans the button too, so the row reads as one.
 */
@Composable
internal fun OwnFieldRow(label: String, onRemove: () -> Unit, row: @Composable () -> Unit) {
    val description = stringResource(R.string.own_field_remove_label, label)
    Row(Modifier.fillMaxWidth().topBorder(C.Border), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f)) { row() }
        Box(
            Modifier.size(48.dp).clickable(role = Role.Button, onClick = onRemove)
                .semantics { contentDescription = description }.testTag(AddFieldTags.remove(label)),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(24.dp).clip(CircleShape).background(C.Raised), contentAlignment = Alignment.Center) {
                LineIcon(Icons.Close, 11.dp, C.Muted, 2.6f)
            }
        }
    }
}

/** The last row of the Info tab's fields, as the web's: "+ Add field", for this asset only. */
@Composable
internal fun AddFieldRow(onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).topBorder(C.Border).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.heightIn(min = 48.dp).clip(Capsule).clickable(role = Role.Button, onClick = onClick).testTag(AddFieldTags.OPEN), contentAlignment = Alignment.Center) {
            Row(
                Modifier.height(28.dp).clip(Capsule).background(C.YTintBg).border(1.dp, C.YTintBd, Capsule).padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                LineIcon(Icons.Plus, 12.dp, C.Yellow, 2.4f)
                Txt(stringResource(R.string.own_field_add), 12, 700, C.Yellow)
            }
        }
        Txt(stringResource(R.string.own_field_hint), 11, 500, C.Muted, Modifier.weight(1f))
    }
}

/**
 * Add field, as the web's (For this asset only): a name and a type (Text, Number, Date, Yes / No, Link). The field is
 * added to this asset alone and shows at the end of its fields, empty, to fill in like the rest.
 */
@Composable
internal fun AddFieldSheet(state: AssetDetailState, actions: AddFieldActions) {
    var name by rememberSaveable { mutableStateOf("") }
    var kind by rememberSaveable { mutableStateOf(FieldKind.TEXT) }
    val busy = state.addingField
    val labels = OWN_FIELD_KINDS.associateWith { stringResource(it.ownFieldLabel()) }

    FormSheet(
        title = stringResource(R.string.own_field_title),
        sub = stringResource(R.string.own_field_sub),
        primary = stringResource(if (busy) R.string.own_field_adding else R.string.own_field_go),
        primaryEnabled = name.isNotBlank() && !busy,
        error = state.addFieldError?.asString(),
        onClose = actions.onClose,
        onPrimary = { if (name.isNotBlank() && !busy) actions.onAdd(name, kind) },
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
    }
}

/** The web's names for the types a field added to one asset can have. */
private fun FieldKind.ownFieldLabel() = when (this) {
    FieldKind.NUMBER -> R.string.own_field_type_number
    FieldKind.DATE -> R.string.own_field_type_date
    FieldKind.YESNO -> R.string.own_field_type_yesno
    FieldKind.URL -> R.string.own_field_type_link
    else -> R.string.own_field_type_text
}
