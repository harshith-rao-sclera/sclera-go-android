package com.sclera.sclerago.presentation.access

import android.text.format.DateUtils
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sclera.sclerago.R
import com.sclera.sclerago.domain.model.Permission
import com.sclera.sclerago.domain.model.Property
import com.sclera.sclerago.domain.model.VIEW_ONLY_COLOR
import com.sclera.sclerago.domain.model.VIEW_ONLY_ICON
import com.sclera.sclerago.presentation.components.ColorChip
import com.sclera.sclerago.presentation.components.asColor
import com.sclera.sclerago.presentation.components.iconOf
import com.sclera.sclerago.presentation.components.r
import com.sclera.sclerago.presentation.icons.Icons
import com.sclera.sclerago.presentation.icons.LineIcon
import com.sclera.sclerago.presentation.theme.C
import com.sclera.sclerago.presentation.theme.Txt

/** Cards and the tab switch of People and access. */

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
            ) { Txt(l, 14, 600, if (on) C.Text else C.Muted) }
        }
    }
}

@Composable
private fun RowCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier.fillMaxWidth().clip(r(12)).background(C.Surface).border(1.dp, C.Border, r(12)).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) { content() }
}

/** "just now" within the last minute, otherwise the platform's localized relative time ("5 minutes ago"). */
@Composable
internal fun lastSeenText(at: Long, now: Long): String =
    if (now - at < JustNowMillis) stringResource(R.string.access_just_now)
    else DateUtils.getRelativeTimeSpanString(at, now, DateUtils.MINUTE_IN_MILLIS).toString()

private const val JustNowMillis = 60_000L

@Composable
private fun ColumnLabel(text: String) = Txt(text.uppercase(), 11, 600, C.Muted, spacing = 0.6f)

/**
 * A person: initials, name and email, then a divider and three columns (GROUP, PROPERTIES, LAST SEEN).
 * Tap to edit.
 */
@Composable
internal fun PersonCard(row: PersonRow, properties: List<Property>, now: Long, onEdit: () -> Unit) {
    val p = row.person
    RowCard(Modifier.testTag(AccessTags.person(p.id)).clickable(role = Role.Button, onClick = onEdit)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(C.Yellow.copy(alpha = 0.18f)).border(1.dp, C.Yellow.copy(alpha = 0.5f), CircleShape),
                contentAlignment = Alignment.Center,
            ) { Txt(initialsOf(p.name), 15, 700, C.Yellow) }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Txt(p.name, 16, 600)
                Txt(p.email, 13, 400, C.Muted)
            }
            LineIcon(Icons.ChevronRight, 13.dp, C.Dim, 2.8f)
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(C.Border))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                ColumnLabel(stringResource(R.string.access_col_group))
                row.group?.let { ColorChip(it.name, it.tint(), it.icon(), iconSize = 10.dp) }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                ColumnLabel(stringResource(R.string.access_col_properties))
                Txt(propertiesText(p.allProperties, p.propertyIds, properties), 14, 500, maxLines = 2)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                ColumnLabel(stringResource(R.string.access_col_last_seen))
                row.lastSeenAt?.let { at ->
                    val text = lastSeenText(at, now)
                    val description = stringResource(R.string.access_last_seen_description, text)
                    Row(
                        Modifier.semantics(mergeDescendants = true) { contentDescription = description },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Box(Modifier.size(7.dp).background(C.Green, CircleShape))
                        Txt(text, 14, 400, C.Soft, maxLines = 2)
                    }
                }
            }
        }
    }
}

@Composable
private fun propertiesText(all: Boolean, ids: Set<String>, properties: List<Property>): String {
    if (all) return stringResource(R.string.access_all_properties)
    val chosen = properties.filter { it.id in ids }
    return if (chosen.size == 1) chosen[0].name else pluralStringResource(R.plurals.access_property_count, chosen.size, chosen.size)
}

/** A group: icon, name, member count, description, edit and delete buttons, and its permission chips. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun GroupCard(row: GroupRow, onEdit: () -> Unit, onDelete: () -> Unit) {
    val g = row.group
    RowCard(Modifier.testTag(AccessTags.group(g.id))) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(40.dp).clip(r(11)).background(g.tint().copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                LineIcon(g.icon(), 17.dp, g.tint(), 1.9f)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Txt(g.name, 16, 600, modifier = Modifier.weight(1f, fill = false))
                    Txt(pluralStringResource(R.plurals.access_person_count, row.members, row.members), 13, 400, C.Muted)
                }
                if (g.description.isNotBlank()) Txt(g.description, 13, 400, C.Soft, maxLines = 3, lineHeight = 18.sp)
            }
            SquareAction(
                true, stringResource(R.string.access_edit_group, g.name), onEdit, Modifier.testTag(AccessTags.editGroup(g.id)),
            ) { LineIcon(Icons.Pencil, 14.dp, C.Text, 1.9f) }
            SquareAction(
                row.canDelete,
                stringResource(if (row.canDelete) R.string.access_delete_group else R.string.access_delete_locked, g.name),
                onDelete, Modifier.testTag(AccessTags.deleteGroup(g.id)),
            ) { LineIcon(Icons.Trash, 14.dp, if (row.canDelete) C.Red else C.RadioOff, 1.9f) }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            if (g.permissions.isEmpty() && !g.locked) {
                ColorChip(stringResource(R.string.access_view_only), VIEW_ONLY_COLOR.asColor(), iconOf(VIEW_ONLY_ICON), iconSize = 10.dp)
            } else {
                Permission.entries.filter { g.locked || it in g.permissions }.forEach {
                    ColorChip(stringResource(it.titleRes()), it.color.asColor(), iconOf(it.iconPath), iconSize = 10.dp)
                }
            }
        }
    }
}

/** A 38dp icon button; at least 48dp to tap. */
@Composable
private fun SquareAction(enabled: Boolean, description: String, onClick: () -> Unit, modifier: Modifier, icon: @Composable () -> Unit) {
    Box(Modifier.size(48.dp).then(modifier), contentAlignment = Alignment.Center) {
        Box(
            Modifier.size(38.dp).clip(r(11)).background(C.Raised).border(1.dp, C.Border2, r(11))
                .semantics { contentDescription = description; if (!enabled) disabled() }
                .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) { icon() }
    }
}
