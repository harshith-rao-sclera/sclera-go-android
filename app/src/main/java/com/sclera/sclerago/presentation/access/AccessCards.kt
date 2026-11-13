package com.sclera.sclerago.presentation.access

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.sclera.sclerago.R
import com.sclera.sclerago.domain.model.Permission
import com.sclera.sclerago.domain.model.Property
import com.sclera.sclerago.domain.model.VIEW_ONLY_COLOR
import com.sclera.sclerago.domain.model.VIEW_ONLY_ICON
import com.sclera.sclerago.presentation.components.Capsule
import com.sclera.sclerago.presentation.components.asColor
import com.sclera.sclerago.presentation.components.iconOf
import com.sclera.sclerago.presentation.components.r
import com.sclera.sclerago.presentation.icons.Ico
import com.sclera.sclerago.presentation.icons.Icons
import com.sclera.sclerago.presentation.icons.LineIcon
import com.sclera.sclerago.presentation.theme.C
import com.sclera.sclerago.presentation.theme.Txt

// Cards, the tab switch and the group filter of People and access.

/** The two-segment People / Groups switch (36dp high). */
@Composable
internal fun TabSwitch(labels: List<String>, tags: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(r(12)).background(C.Surface).border(1.dp, C.Border, r(12)).padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        labels.forEachIndexed { i, l ->
            val on = i == selected
            val bg by animateColorAsState(if (on) C.Raised else Color.Transparent, label = "tab")
            Box(
                Modifier.weight(1f).height(36.dp).clip(r(9)).background(bg).testTag(tags[i])
                    .semantics { this.selected = on }.clickable(role = Role.Tab) { onSelect(i) },
                contentAlignment = Alignment.Center,
            ) { Txt(l, 13, 600, if (on) C.Text else C.Muted) }
        }
    }
}

/** A list card: one row, whole card tappable; [onLongClick] (if any) starts selecting; [picked] tints it. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RowCard(
    modifier: Modifier,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    picked: Boolean = false,
    content: @Composable RowScope.() -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    Row(
        modifier.fillMaxWidth().heightIn(min = 60.dp).clip(r(12)).background(if (picked) C.PickedRow else C.Surface)
            .border(1.dp, if (picked) C.YTintBd else C.Border, r(12))
            .semantics { if (onLongClick != null) selected = picked }
            .combinedClickable(
                role = Role.Button, onClick = onClick,
                onLongClick = onLongClick?.let { { haptics.performHapticFeedback(HapticFeedbackType.LongPress); it() } },
            ).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) { content() }
}

/**
 * A person: initials, name, email and "group · property access", then their group's icon. Tap to edit.
 */
@Composable
internal fun PersonCard(row: PersonRow, properties: List<Property>, onEdit: () -> Unit) {
    val p = row.person
    RowCard(Modifier.testTag(AccessTags.person(p.id)), onEdit) {
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(C.Yellow.copy(alpha = 0.18f)).border(1.dp, C.Yellow.copy(alpha = 0.5f), CircleShape),
            contentAlignment = Alignment.Center,
        ) { Txt(initialsOf(p.name), 14, 700, C.Yellow) }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Txt(p.name, 14, 600)
            Txt(p.email, 12, 400, C.Muted)
            val access = propertiesText(p.allProperties, p.propertyIds, properties)
            Txt(row.group?.let { stringResource(R.string.access_pair, it.name, access) } ?: access, 12, 400, C.Muted)
        }
        row.group?.let { LineIcon(it.icon(), 20.dp, it.tint(), 1.9f) }
        LineIcon(Icons.ChevronRight, 13.dp, C.Dim, 2.8f)
    }
}

@Composable
private fun propertiesText(all: Boolean, ids: Set<String>, properties: List<Property>): String {
    if (all) return stringResource(R.string.access_all_properties)
    val chosen = properties.filter { it.id in ids }
    return if (chosen.size == 1) chosen[0].name else pluralStringResource(R.plurals.access_property_count, chosen.size, chosen.size)
}

/**
 * A group: icon, name, "N people · description", then an icon for each thing it can do. Tap to edit;
 * long-press (or tap its icon) to tick it for deleting. While [selecting], a tap ticks instead, and groups
 * that can't be deleted (Admins, groups with people) are dimmed.
 */
@Composable
internal fun GroupCard(
    row: GroupRow,
    onEdit: () -> Unit,
    picked: Boolean = false,
    selecting: Boolean = false,
    onToggle: () -> Unit = {},
) {
    val g = row.group
    val selectable = row.canDelete
    RowCard(
        Modifier.testTag(AccessTags.group(g.id)),
        onClick = { if (selecting) { if (selectable) onToggle() } else onEdit() },
        onLongClick = if (selectable) onToggle else null,
        picked = picked,
    ) {
        GroupBadge(g.icon(), g.tint(), picked, selecting, selectable, Modifier.testTag(AccessTags.groupTick(g.id)), onToggle)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Txt(g.name, 14, 600)
            val members = pluralStringResource(R.plurals.access_person_count, row.members, row.members)
            // On the card the description follows the count, so its closing full stop goes (the sheet keeps it).
            val description = g.description.trim().removeSuffix(".")
            Txt(if (description.isBlank()) members else stringResource(R.string.access_pair, members, description), 12, 400, C.Muted, maxLines = 2)
        }
        val perms = g.effectivePermissions
        val names = if (perms.isEmpty()) listOf(stringResource(R.string.access_view_only)) else perms.map { stringResource(it.titleRes()) }
        Row(
            Modifier.semantics { contentDescription = names.joinToString() },
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (perms.isEmpty()) LineIcon(iconOf(VIEW_ONLY_ICON), 18.dp, VIEW_ONLY_COLOR.asColor(), 1.9f)
            else perms.forEach { LineIcon(iconOf(it.iconPath), 18.dp, it.color.asColor(), 1.9f) }
        }
    }
}

/** The group's icon box; ticked: a yellow box with a check. Tapping it ticks the group when it can be deleted. */
@Composable
private fun GroupBadge(icon: Ico, tint: Color, picked: Boolean, selecting: Boolean, selectable: Boolean, modifier: Modifier, onToggle: () -> Unit) {
    val bg by animateColorAsState(if (picked) C.Yellow else tint.copy(alpha = 0.16f), label = "badge")
    Box(
        modifier.size(40.dp).alpha(if (selecting && !selectable) DimmedAlpha else 1f).clip(r(11)).background(bg)
            .then(if (selecting && selectable && !picked) Modifier.border(2.dp, C.RadioOff, r(11)) else Modifier)
            .clickable(enabled = selectable, role = Role.Checkbox, onClick = onToggle),
        contentAlignment = Alignment.Center,
    ) {
        if (picked) LineIcon(Icons.Check, 20.dp, C.OnYellow, 3f) else LineIcon(icon, 18.dp, tint, 1.9f)
    }
}

/** How faded a group that can't be ticked looks while ticking. */
private const val DimmedAlpha = 0.4f

/**
 * The Groups tab's filter: All, then one chip per permission with how many groups can do it.
 * Scrolls sideways when the chips don't fit.
 */
@Composable
internal fun GroupFilterChips(
    selected: Permission?,
    total: Int,
    counts: Map<Permission, Int>,
    onSelect: (Permission?) -> Unit,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(contentPadding),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(stringResource(R.string.access_all), total, null, C.Yellow, selected == null, Modifier.testTag(AccessTags.FILTER_ALL)) { onSelect(null) }
        Permission.entries.forEach { p ->
            FilterChip(
                stringResource(p.titleRes()), counts[p] ?: 0, iconOf(p.iconPath), p.color.asColor(), selected == p,
                Modifier.testTag(AccessTags.filter(p)),
            ) { onSelect(p) }
        }
    }
}

@Composable
private fun FilterChip(label: String, count: Int, icon: Ico?, tint: Color, on: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Row(
        modifier.heightIn(min = 34.dp).clip(Capsule).background(if (on) C.YTintBg else Color.Transparent)
            .border(1.dp, if (on) C.Yellow else C.Border2, Capsule)
            .semantics { selected = on }.clickable(role = Role.Tab, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        icon?.let { LineIcon(it, 12.dp, tint, 2.2f) }
        Txt(label, 12, 600, if (on) C.Yellow else C.Soft)
        Txt(count.toString(), 12, 600, if (on) C.Yellow else C.Muted)
    }
}
