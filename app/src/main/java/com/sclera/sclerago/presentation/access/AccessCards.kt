package com.sclera.sclerago.presentation.access

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.sclera.sclerago.R
import com.sclera.sclerago.domain.model.DirectoryPerson
import com.sclera.sclerago.domain.model.Property
import com.sclera.sclerago.domain.model.VIEW_ONLY_COLOR
import com.sclera.sclerago.presentation.components.Capsule
import com.sclera.sclerago.presentation.components.Pill
import com.sclera.sclerago.presentation.components.agoText
import java.util.Locale
import com.sclera.sclerago.presentation.components.asColor
import com.sclera.sclerago.presentation.components.r
import com.sclera.sclerago.presentation.icons.Icons
import com.sclera.sclerago.presentation.icons.LineIcon
import com.sclera.sclerago.presentation.theme.C
import com.sclera.sclerago.presentation.theme.Txt

// Cards and the tab switch of People and access.

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

/** A list card: one row, whole card tappable. */
@Composable
private fun RowCard(modifier: Modifier, onClick: () -> Unit, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier.cardLook().heightIn(min = 60.dp).clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) { content() }
}

/** The surface and edge every card of this page shares. */
private fun Modifier.cardLook(): Modifier = fillMaxWidth().clip(r(12)).background(C.Surface).border(1.dp, C.Border, r(12))

/**
 * A person, as the web lists them: initials in their group's colour, their name (the email when there is none)
 * and email; under a line, their group's name, the properties they can open and when they were last seen.
 * People with no group are listed too, with no access. Tap to edit.
 */
@Composable
internal fun PersonCard(row: PersonRow, properties: List<Property>, nowMillis: Long, onEdit: () -> Unit) {
    val p = row.person
    val tint = row.group?.tint() ?: C.Muted
    Column(
        Modifier.cardLook().testTag(AccessTags.person(p.id)).clickable(role = Role.Button, onClick = onEdit)
            .padding(start = 12.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(tint.copy(alpha = 0.18f)).border(1.dp, tint.copy(alpha = 0.5f), CircleShape),
                contentAlignment = Alignment.Center,
            ) { Txt(initialsOf(p.fullName?.takeIf { it.isNotBlank() } ?: p.email.substringBefore('@')), 14, 700, tint) }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Txt(p.name, 15, 600)
                // The email goes under the name only when there is a name (otherwise it is the name).
                if (p.name != p.email) Txt(p.email, 12, 400, C.Muted)
            }
            LineIcon(Icons.ChevronRight, 13.dp, C.Dim, 2.8f)
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(C.Border))
        Row(Modifier.padding(end = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PersonFact(stringResource(R.string.access_field_group), Modifier.weight(1f)) {
                row.group?.let { Txt(it.name, 13, 600, maxLines = 2) } ?: Txt(stringResource(R.string.access_none), 13, 600, C.Muted)
            }
            PersonFact(stringResource(R.string.label_properties), Modifier.weight(1f)) {
                when {
                    !p.hasAccess -> Txt(stringResource(R.string.access_no_access), 13, 600, maxLines = 2)
                    p.allProperties -> Txt(stringResource(R.string.access_all_properties), 13, 600, maxLines = 2)
                    p.propertyIds.isEmpty() -> Txt(stringResource(R.string.access_none_chosen), 13, 600, C.Muted, maxLines = 2)
                    else -> Txt(propertiesText(p.propertyIds, properties), 13, 600, maxLines = 2)
                }
            }
            PersonFact(stringResource(R.string.access_last_seen), Modifier.weight(1f)) {
                val seen = when {
                    p.signedIn -> agoText(p.lastSeenAt, nowMillis).ifEmpty { stringResource(R.string.access_signed_in) }
                    p.hasAccess -> stringResource(R.string.access_invited_waiting)
                    else -> stringResource(R.string.access_not_signed_in)
                }
                Txt(seen, 13, 600, maxLines = 2)
            }
        }
    }
}

/**
 * Someone else the organisation's directory lists, not added here yet (as the web lists them under the users): their
 * name and email, "Not added", no properties, and Add. Tapping the card or Add opens Add user with them filled in;
 * Add only works once there is a group to put them in ([canAdd]).
 */
@Composable
internal fun DirectoryCard(person: DirectoryPerson, canAdd: Boolean, onAdd: () -> Unit) {
    val name = person.name?.trim()?.takeIf { it.isNotEmpty() }
    Column(
        Modifier.cardLook().testTag(AccessTags.directory(person.email)).clickable(role = Role.Button, onClick = onAdd)
            .padding(start = 12.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(C.Muted.copy(alpha = 0.18f)).border(1.dp, C.Muted.copy(alpha = 0.5f), CircleShape),
                contentAlignment = Alignment.Center,
            ) { Txt(initialsOf(name ?: person.email.substringBefore('@')), 14, 700, C.Muted) }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Txt(name ?: person.email, 15, 600)
                if (name != null) Txt(person.email, 12, 400, C.Muted)
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(C.Border))
        Row(Modifier.padding(end = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Bottom) {
            PersonFact(stringResource(R.string.access_field_group), Modifier.weight(1f)) {
                Txt(stringResource(R.string.access_not_added), 13, 600, maxLines = 2)
            }
            PersonFact(stringResource(R.string.label_properties), Modifier.weight(1f)) {
                Txt(stringResource(R.string.access_none), 13, 600, C.Muted)
            }
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                Pill(
                    stringResource(R.string.access_action_add), { if (canAdd) onAdd() },
                    fg = if (canAdd) C.Text else C.Dim,
                )
            }
        }
    }
}

/** One column under a person: a small capitalised [label] over its value. */
@Composable
private fun PersonFact(label: String, modifier: Modifier, value: @Composable () -> Unit) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Txt(label.uppercase(Locale.getDefault()), 10, 600, C.Muted, spacing = 0.4f)
        value()
    }
}

/** A permission's name in a pill of its colour (no icon). */
@Composable
private fun Tag(text: String, tint: Color) {
    Box(
        Modifier.clip(Capsule).background(tint.copy(alpha = 0.14f)).border(1.dp, tint.copy(alpha = 0.45f), Capsule)
            .padding(horizontal = 9.dp, vertical = 3.dp),
    ) { Txt(text, 11, 600, tint) }
}

/** The one chosen property's name, or how many were chosen (as the web words it). */
@Composable
private fun propertiesText(ids: Set<String>, properties: List<Property>): String {
    val names = properties.filter { it.id in ids }.map { it.name }
    return if (names.size == 1) names[0] else pluralStringResource(R.plurals.access_property_count, ids.size, ids.size)
}

/**
 * A group, as the web lists it: its name and how many people are in it, its description, then a tag for each
 * thing it can do ("View only" when nothing). Tap to open it, to edit or delete it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun GroupCard(row: GroupRow, onEdit: () -> Unit) {
    val g = row.group
    RowCard(Modifier.testTag(AccessTags.group(g.id)), onEdit) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Txt(g.name, 14, 600, modifier = Modifier.weight(1f, fill = false))
                Txt(pluralStringResource(R.plurals.access_person_count, row.members, row.members), 12, 400, C.Muted)
            }
            if (g.description.isNotBlank()) Txt(g.description.trim(), 12, 400, C.Muted, maxLines = 2)
            FlowRow(
                Modifier.padding(top = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                val perms = orderedPermissions().filter(g::can)
                if (perms.isEmpty()) Tag(stringResource(R.string.access_view_only), VIEW_ONLY_COLOR.asColor())
                else perms.forEach { Tag(it.title(), it.color.asColor()) }
            }
        }
        LineIcon(Icons.ChevronRight, 13.dp, C.Dim, 2.8f)
    }
}
