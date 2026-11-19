package com.sclera.sclerago.presentation.main

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sclera.sclerago.R
import com.sclera.sclerago.domain.model.ANSWER_NO
import com.sclera.sclerago.domain.model.ANSWER_YES
import com.sclera.sclerago.domain.model.CHECKED
import com.sclera.sclerago.domain.model.FieldCfg
import com.sclera.sclerago.domain.model.FieldKind
import com.sclera.sclerago.domain.model.UNCHECKED
import com.sclera.sclerago.presentation.assets.FieldLabel
import com.sclera.sclerago.presentation.assets.FieldRow
import com.sclera.sclerago.presentation.assets.MultiCheckRow
import com.sclera.sclerago.presentation.assets.SelectRow
import com.sclera.sclerago.presentation.assets.WhenKind
import com.sclera.sclerago.presentation.assets.WhenRow
import com.sclera.sclerago.presentation.components.BottomSheet
import com.sclera.sclerago.presentation.components.GuardedClose
import com.sclera.sclerago.presentation.components.Pill
import com.sclera.sclerago.presentation.components.SheetHeader
import com.sclera.sclerago.presentation.components.topBorder
import com.sclera.sclerago.presentation.icons.Icons
import com.sclera.sclerago.presentation.icons.LineIcon
import com.sclera.sclerago.presentation.theme.C
import com.sclera.sclerago.presentation.theme.Txt

/**
 * The web's bulk Edit for the ticked assets: the location (picked in the location sheet, [onPickPlace]) and every
 * field, with the asset form's own rows. Only what is filled in changes; [onUpdated] once it is written.
 */
@Composable
fun BulkEditSheet(s: BulkEditState, vm: BulkEditViewModel, onPickPlace: () -> Unit, onUpdated: () -> Unit) {
    val fields by vm.fields.collectAsStateWithLifecycle()
    GuardedClose(s.hasChanges, vm::close) { close ->
        BottomSheet(close) {
            SheetHeader(
                pluralStringResource(R.plurals.bulk_title, s.ids.size, s.ids.size),
                stringResource(R.string.bulk_hint),
                close,
            )
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(bottom = 8.dp)) {
                PlaceRow(s.placeLabel, onPickPlace, vm::clearPlace)
                fields.forEach { f -> BulkFieldRow(f, s.values[f.key].orEmpty()) { vm.setValue(f.key, it) } }
            }
            Row(
                Modifier.fillMaxWidth().topBorder(C.Border).padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Pill(stringResource(R.string.bulk_cancel), close, height = 44.dp, hPad = 18.dp)
                Pill(
                    stringResource(R.string.bulk_update), { vm.update(onUpdated) },
                    Modifier.weight(1f).alpha(if (s.hasChanges && !s.saving) 1f else 0.45f),
                    bg = C.Yellow, fg = C.OnYellow, height = 44.dp, weight = 700,
                )
            }
        }
    }
}

/** The location: the one picked (with a clear button), or "Leave as is"; a tap opens the location sheet. */
@Composable
private fun PlaceRow(label: String?, onClick: () -> Unit, onClear: () -> Unit) {
    val name = stringResource(R.string.bulk_location)
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).topBorder(C.Border).clickable(role = Role.Button, onClick = onClick)
            .padding(start = 12.dp, end = if (label != null) 0.dp else 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        FieldLabel(name, Modifier.width(92.dp))
        Txt(label ?: stringResource(R.string.bulk_leave), 14, 600, if (label != null) C.Text else C.Muted, Modifier.weight(1f), maxLines = 2)
        if (label != null) ClearButton(name, onClear) else LineIcon(Icons.ChevronRight, 16.dp, C.Muted, 2.4f)
    }
}

/** Puts a picked value back to "Leave as is" (48dp to touch, named for screen readers). */
@Composable
private fun ClearButton(field: String, onClick: () -> Unit) {
    val label = stringResource(R.string.bulk_clear, field)
    Box(
        Modifier.size(48.dp).clickable(role = Role.Button, onClick = onClick).semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { LineIcon(Icons.Close, 14.dp, C.Muted, 2.6f) }
}

/** One field, by its kind, as the asset form shows it; empty means "leave as it is". */
@Composable
private fun BulkFieldRow(f: FieldCfg, value: String, onChange: (String) -> Unit) {
    val k = f.key
    val edited = value.isNotBlank()
    when (f.kind) {
        FieldKind.DATE, FieldKind.TIME -> {
            // A picked date or time can be cleared back to "leave as it is".
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    WhenRow(k, f.label, value, if (f.kind == FieldKind.DATE) WhenKind.DATE else WhenKind.TIME, edited, onChange, Color.Transparent)
                }
                if (edited) Box(Modifier.topBorder(C.Border)) { ClearButton(f.label) { onChange("") } }
            }
            return
        }
        FieldKind.MULTI -> if (f.options.isNotEmpty()) {
            MultiCheckRow(k, f.label, value, f.options, edited, onChange, Color.Transparent)
            return
        }
        else -> {}
    }
    val choices = when (f.kind) {
        FieldKind.YESNO -> listOf(ANSWER_YES to stringResource(R.string.detail_yes), ANSWER_NO to stringResource(R.string.detail_no))
        FieldKind.CHECKBOX -> listOf(CHECKED to stringResource(R.string.detail_yes), UNCHECKED to stringResource(R.string.detail_no))
        FieldKind.DROPDOWN -> f.options.map { it to it }
        else -> emptyList()
    }
    if (choices.isNotEmpty()) {
        SelectRow(k, f.label, value, listOf("" to stringResource(R.string.bulk_leave)) + choices, edited, onChange)
    } else {
        FieldRow(k, f.label, value, onChange, edited, multiLine = f.kind == FieldKind.TEXTAREA)
    }
}
