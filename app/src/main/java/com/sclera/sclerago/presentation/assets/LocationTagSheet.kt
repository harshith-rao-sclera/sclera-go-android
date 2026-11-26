package com.sclera.sclerago.presentation.assets

import com.sclera.sclerago.presentation.model.labelRes
import com.sclera.sclerago.presentation.model.locationTagText
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import com.sclera.sclerago.R
import com.sclera.sclerago.domain.model.ASSET_KIND_KEY
import com.sclera.sclerago.domain.model.AssetKind
import com.sclera.sclerago.domain.model.LOCATION_TAG_KEY
import com.sclera.sclerago.domain.model.LocationTag
import com.sclera.sclerago.domain.model.TagSpot
import com.sclera.sclerago.domain.model.TagSurface
import com.sclera.sclerago.presentation.components.BottomSheet
import com.sclera.sclerago.presentation.components.CheckBox
import com.sclera.sclerago.presentation.components.LocalReadOnly
import com.sclera.sclerago.presentation.components.PRESSED_CARD
import com.sclera.sclerago.presentation.components.Pill
import com.sclera.sclerago.presentation.components.SheetHeader
import com.sclera.sclerago.presentation.components.pressClickable
import com.sclera.sclerago.presentation.components.r
import com.sclera.sclerago.presentation.icons.Icons
import com.sclera.sclerago.presentation.icons.LineIcon
import com.sclera.sclerago.presentation.theme.C
import com.sclera.sclerago.presentation.theme.Txt

/** Test tags of the Location tag sheet. */
object LocationTagTags {
    const val SHEET = "location-tag-sheet"
    const val SAVE = "location-tag-save"
    fun surface(surface: TagSurface) = "location-tag-surface-${surface.db}"
    fun spots(surface: TagSurface) = "location-tag-spots-${surface.db}"
}

/**
 * The asset's Type and Location tag (its own, not fields), above its fields as the web's TypeAndLocationTag: Type is a
 * dropdown (Existing / Proposed, "-" for none); Location tag shows "Wall : North" (or "Location not tagged") and opens
 * the Location tag sheet ([onTagLocation]). Both are edits like the form's other values, read-only on a locked form.
 */
@Composable
internal fun TypeAndLocationTagRows(state: AssetDetailState, onEdit: (key: String, value: String) -> Unit, onTagLocation: () -> Unit) {
    val none = stringResource(R.string.detail_value_none)
    SelectRow(
        ASSET_KIND_KEY, stringResource(R.string.asset_kind_label), state.value(ASSET_KIND_KEY),
        listOf("" to none) + AssetKind.entries.map { it.db to stringResource(it.labelRes) },
        state.dirty(ASSET_KIND_KEY), { onEdit(ASSET_KIND_KEY, it) },
    )
    // Read-only, an asset with no tag shows "-", as the web's.
    val untagged = if (LocalReadOnly.current) none else stringResource(R.string.location_tag_none)
    PickRow(
        LOCATION_TAG_KEY, stringResource(R.string.location_tag_label),
        LocationTag.of(state.value(LOCATION_TAG_KEY))?.let { locationTagText(it) }.orEmpty(), untagged,
        state.dirty(LOCATION_TAG_KEY), onTagLocation,
    )
}

/**
 * "Specify the asset location", as the web's Location Tag dialog: tick one surface (the others are then off) and pick
 * where on it; picking a place ticks its surface. Save needs a place on the ticked surface; with nothing ticked it only
 * saves when there is a tag to clear. [onSave] gets the new tag ("wall:north", empty to clear) when it changed.
 * Docked at the bottom on phones, centred on tablets (the shared [BottomSheet]).
 */
@Composable
internal fun LocationTagSheet(value: String, onSave: (String) -> Unit, onClose: () -> Unit) {
    val start = LocationTag.of(value)
    var surface by remember(value) { mutableStateOf(start?.surface) }
    var spots by remember(value) { mutableStateOf(start?.let { mapOf(it.surface to it.spot) }.orEmpty()) }
    val tag = surface?.let { s -> spots[s]?.let { LocationTag(s, it) } }
    val canSave = if (surface != null) tag != null else start != null

    BottomSheet(onDismiss = onClose) {
        Column(Modifier.testTag(LocationTagTags.SHEET)) {
            SheetHeader(stringResource(R.string.location_tag_title), stringResource(R.string.location_tag_sub), onClose)
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                TagSurface.entries.forEach { s ->
                    SurfaceRow(
                        s, ticked = surface == s, off = surface != null && surface != s, spot = spots[s],
                        onTick = { surface = if (it) s else null },
                        onSpot = { spot ->
                            spots = spots + (s to spot)
                            surface = s // picking a place ticks its surface
                        },
                    )
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 16.dp, bottom = 14.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Pill(stringResource(R.string.action_cancel), onClose, Modifier.weight(1f), height = 46.dp, size = 13, weight = 700)
                Pill(
                    stringResource(R.string.action_save),
                    {
                        if (canSave) {
                            val saved = tag?.db.orEmpty()
                            if (saved != start?.db.orEmpty()) onSave(saved)
                            onClose()
                        }
                    },
                    Modifier.weight(1f).testTag(LocationTagTags.SAVE).alpha(if (canSave) 1f else 0.45f),
                    bg = C.Yellow, fg = C.OnYellow, height = 46.dp, size = 13, weight = 700,
                )
            }
        }
    }
}

/** One surface: its tick box (a 48dp target) and, under its name, the dropdown of places on it; both greyed out when [off]. */
@Composable
private fun SurfaceRow(surface: TagSurface, ticked: Boolean, off: Boolean, spot: TagSpot?, onTick: (Boolean) -> Unit, onSpot: (TagSpot) -> Unit) {
    val name = stringResource(surface.labelRes)
    Row(
        Modifier.fillMaxWidth().alpha(if (off) 0.45f else 1f),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier.size(48.dp).pressClickable(shape = r(8), enabled = !off, role = Role.Checkbox) { onTick(!ticked) }
                .semantics { contentDescription = name; toggleableState = ToggleableState(ticked) }.testTag(LocationTagTags.surface(surface)),
            contentAlignment = Alignment.Center,
        ) { CheckBox(ticked) }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Txt(name, 12, 600, C.Muted)
            SpotDropdown(surface, name, spot, enabled = !off, onSpot)
        }
    }
}

/** The places on [surface] to pick from: the chosen one, or "Select", in a box that opens the list. */
@Composable
private fun SpotDropdown(surface: TagSurface, name: String, spot: TagSpot?, enabled: Boolean, onPick: (TagSpot) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val label = stringResource(R.string.location_tag_position, name)
    Box {
        Row(
            Modifier.fillMaxWidth().height(48.dp).pressClickable(shape = r(12), pressed = PRESSED_CARD, enabled = enabled) { open = true }
                .background(C.Bg).border(1.dp, C.Border2, r(12)).padding(horizontal = 12.dp)
                .semantics { contentDescription = label }.testTag(LocationTagTags.spots(surface)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Txt(
                spot?.let { stringResource(it.labelRes) } ?: stringResource(R.string.detail_select), 14, 500,
                if (spot == null) C.Dim else C.Text, Modifier.weight(1f),
            )
            LineIcon(Icons.ChevronDown, 14.dp, C.Muted, 2.4f)
        }
        DropdownMenu(open, { open = false }, containerColor = C.Surface) {
            surface.spots.forEach { s ->
                DropdownMenuItem(
                    text = { Txt(stringResource(s.labelRes), 14, if (s == spot) 700 else 500, if (s == spot) C.Yellow else C.Text) },
                    onClick = { onPick(s); open = false },
                )
            }
        }
    }
}
