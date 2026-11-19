package com.sclera.sclerago.presentation.assets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.sclera.sclerago.R
import com.sclera.sclerago.domain.model.ClonePlace
import com.sclera.sclerago.domain.model.FieldCfg
import com.sclera.sclerago.domain.model.MAX_CLONE_COPIES
import com.sclera.sclerago.domain.model.NO_INPUT_KINDS
import com.sclera.sclerago.presentation.components.ChoiceList
import com.sclera.sclerago.presentation.components.FieldLabel
import com.sclera.sclerago.presentation.components.FormSheet
import com.sclera.sclerago.presentation.components.SheetInput
import com.sclera.sclerago.presentation.icons.Icons
import com.sclera.sclerago.presentation.theme.C
import com.sclera.sclerago.presentation.theme.Txt

/** The type fields, in order: an asset type needs its system type, a sub type its asset type (the copies keep the type). */
internal val CLONE_TYPE_KEYS = listOf("sys", "typ", "sub")

/** The System details choices: the type fields, then the JACS code. */
private val CLONE_SYSTEM_KEYS = CLONE_TYPE_KEYS + "jacs"

/**
 * Not among the field groups: the System details (offered on their own). The name is a field like the others (a
 * mandatory one), ticked to start with, as the web's Clone copies it with the field values it ticks.
 */
private val NOT_IN_FIELD_GROUPS = CLONE_SYSTEM_KEYS.toSet()

/** Test tags of the Clone sheet. */
object CloneTags {
    const val QUANTITY = "clone-quantity"
}

/**
 * Clone, as the web's dialog: how many copies (1 to 100), then what they take: the location details (building, floor,
 * location; a lower level needs the ones above it), the System details and the asset's fields with a value, by group
 * (mandatory validation fields, technician input, custom fields). Everything starts ticked. Photos are never copied (as
 * the web's clone): each copy starts without any. [onClone] gets the copies, how much of the place and the field keys.
 */
@Composable
internal fun CloneSheet(
    state: AssetDetailState,
    onClone: (copies: Int, place: ClonePlace, keys: Set<String>) -> Unit,
    onClose: () -> Unit,
) {
    val asset = state.asset ?: return
    val levels = listOfNotNull(
        ClonePlace.BUILDING.takeIf { asset.buildingId != null },
        ClonePlace.FLOOR.takeIf { asset.floorId != null },
        ClonePlace.LOCATION.takeIf { asset.locationId != null },
    )
    val hasValue = { key: String -> state.saved[key].orEmpty().isNotBlank() }
    val system = CLONE_SYSTEM_KEYS.filter(hasValue)
    val fields = state.fields.filter { it.key !in NOT_IN_FIELD_GROUPS && it.kind !in NO_INPUT_KINDS && hasValue(it.key) }
    val groups = listOf(
        R.string.clone_mandatory to fields.filter { it.required },
        R.string.clone_technician to fields.filter { !it.required && !it.custom },
        R.string.clone_custom to fields.filter { !it.required && it.custom },
    ).filter { it.second.isNotEmpty() }

    var copies by rememberSaveable(asset.id) { mutableStateOf("1") }
    var place by rememberSaveable(asset.id) { mutableStateOf(levels.lastOrNull() ?: ClonePlace.NONE) }
    val allKeys = (system + fields.map { it.key }).toSet()
    var keys by remember(asset.id) { mutableStateOf(allKeys) }
    // Anything changed from how the sheet opened: closing asks before discarding it.
    val dirty = copies != "1" || place != (levels.lastOrNull() ?: ClonePlace.NONE) || keys != allKeys
    val count = copies.trim().toIntOrNull()
    val valid = count != null && count in 1..MAX_CLONE_COPIES

    FormSheet(
        title = stringResource(R.string.clone_title),
        sub = stringResource(R.string.clone_sub),
        primary = stringResource(if (state.cloning) R.string.clone_going else R.string.clone_go),
        primaryEnabled = valid && !state.cloning,
        error = null,
        onClose = onClose,
        onPrimary = { if (count != null && valid && !state.cloning) onClone(count, place, keys) },
        leadingIcon = Icons.Clone,
        dirty = dirty,
    ) {
        // How many
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Txt(stringResource(R.string.clone_copies_title), 14, 700)
            FieldLabel(stringResource(R.string.clone_quantity))
            Box(Modifier.testTag(CloneTags.QUANTITY)) {
                SheetInput(copies, { copies = it.filter(Char::isDigit).take(3) }, stringResource(R.string.clone_quantity_hint), keyboard = KeyboardType.Number)
            }
            Txt(stringResource(R.string.clone_quantity_range), 11, 500, if (valid) C.Muted else C.Orange)
        }

        Txt(stringResource(R.string.clone_pick_title), 14, 700)

        if (levels.isNotEmpty()) {
            CloneGroup(stringResource(R.string.clone_location)) {
                // A level stays ticked with every level above it; unticking one unticks those below it.
                val names = levels.associateWith { stringResource(it.labelRes()) }
                ChoiceList(
                    levels, { names.getValue(it) }, { it.ordinal <= place.ordinal },
                    onPick = { level -> place = if (level.ordinal <= place.ordinal) ClonePlace.entries[level.ordinal - 1] else level },
                    multi = true,
                )
            }
        }

        if (system.isNotEmpty()) {
            CloneGroup(stringResource(R.string.clone_system)) {
                // Named as the form names them, else as the web's dialog.
                val names = system.associateWith { key -> state.fields.firstOrNull { it.key == key }?.label ?: stringResource(systemLabelRes(key)) }
                ChoiceList(
                    system, { names.getValue(it) }, { it in keys },
                    onPick = { key -> keys = toggleSystem(keys, key) },
                    multi = true,
                )
            }
        }

        groups.forEach { (title, list) ->
            CloneGroup(stringResource(title)) {
                ChoiceList(list, FieldCfg::label, { it.key in keys }, { f -> keys = if (f.key in keys) keys - f.key else keys + f.key }, multi = true)
            }
        }
    }
}

/** A titled group of choices. */
@Composable
private fun CloneGroup(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Txt(title, 12, 600, C.Soft)
        content()
    }
}

/**
 * Ticks or unticks [key] of the System details: the type fields go in order (an asset type needs its system type, a sub
 * type its asset type), so ticking one ticks those above it and unticking one unticks those below it.
 */
private fun toggleSystem(keys: Set<String>, key: String): Set<String> {
    val at = CLONE_TYPE_KEYS.indexOf(key)
    if (at < 0) return if (key in keys) keys - key else keys + key
    return if (key in keys) keys - CLONE_TYPE_KEYS.drop(at).toSet() else keys + CLONE_TYPE_KEYS.take(at + 1)
}

private fun ClonePlace.labelRes() = when (this) {
    ClonePlace.BUILDING -> R.string.clone_building
    ClonePlace.FLOOR -> R.string.clone_floor
    else -> R.string.clone_location_one
}

private fun systemLabelRes(key: String) = when (key) {
    "sys" -> R.string.clone_system_type
    "typ" -> R.string.clone_asset_type
    "sub" -> R.string.clone_sub_type
    else -> R.string.clone_jacs
}
