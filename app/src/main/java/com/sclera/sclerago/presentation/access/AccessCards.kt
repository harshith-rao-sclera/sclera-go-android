package com.sclera.sclerago.presentation.access

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
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.minimumInteractiveComponentSize
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
import com.sclera.sclerago.presentation.components.PersonAvatar
import com.sclera.sclerago.presentation.components.agoText
import com.sclera.sclerago.presentation.components.asColor
import com.sclera.sclerago.presentation.components.initialsOf
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
                Modifier.weight(1f).height(32.dp).clip(r(9)).background(bg).testTag(tags[i])
                    .semantics { this.selected = on }.clickable(role = Role.Tab) { onSelect(i) },
                contentAlignment = Alignment.Center,
            ) { Txt(l, 12, 600, if (on) C.Text else C.Muted) }
        }
    }
}

/** The surface and edge every card of this page shares. */
private fun Modifier.cardLook(): Modifier = fillMaxWidth().clip(r(14)).background(C.Surface).border(1.dp, C.Border, r(14))

/** A card's row: the whole card tappable, its parts centred. */
@Composable
private fun CardRow(modifier: Modifier, onClick: () -> Unit, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier.cardLook().clickable(role = Role.Button, onClick = onClick).padding(start = 12.dp, end = 10.dp, top = 9.dp, bottom = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) { content() }
}

/**
 * A person: initials in their group's colour, their name (the email when there is none) and email, then their group's
 * pill and the properties they can open; at the top right, when they were last seen, or "Invited" while they haven't
 * signed in yet. Tap to edit.
 */
@Composable
internal fun PersonCard(row: PersonRow, properties: List<Property>, nowMillis: Long, onEdit: () -> Unit) {
    val p = row.person
    val tint = row.group?.tint() ?: C.Muted
    CardRow(Modifier.testTag(AccessTags.person(p.id)), onEdit) {
        PersonAvatar(initialsOf(p.fullName?.takeIf { it.isNotBlank() } ?: p.email.substringBefore('@')), tint, size = 36.dp, textSize = 13)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Txt(p.name, 14, 700, modifier = Modifier.weight(1f), maxLines = 1)
                when {
                    p.signedIn -> agoText(p.lastSeenAt, nowMillis).takeIf { it.isNotEmpty() }?.let { Txt(it, 11, 400, C.Muted, maxLines = 1) }
                    p.hasAccess -> Tag(stringResource(R.string.access_invited), C.Orange)
                }
            }
            // The email goes under the name only when there is a name (otherwise it is the name).
            if (p.name != p.email) Txt(p.email, 12, 400, C.Muted, maxLines = 1)
            Row(
                Modifier.padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                row.group?.let { Tag(it.name, tint) } ?: Txt(stringResource(R.string.access_no_access), 12, 500, C.Muted)
                if (row.group != null) {
                    Txt("·", 12, 400, C.Muted)
                    Txt(
                        when {
                            p.allProperties -> stringResource(R.string.access_all_properties)
                            p.propertyIds.isEmpty() -> stringResource(R.string.access_none_chosen)
                            else -> propertiesText(p.propertyIds, properties)
                        },
                        12, 400, C.Muted, maxLines = 1,
                    )
                }
            }
        }
        LineIcon(Icons.ChevronRight, 12.dp, C.Dim, 2.6f)
    }
}

/**
 * Someone else the organisation's directory lists, not added here yet (as the web lists them under the users): their
 * name and email, "Not added", and Add. Tapping the card or Add opens Add user with them filled in; Add only works once
 * there is a group to put them in ([canAdd]).
 */
@Composable
internal fun DirectoryCard(person: DirectoryPerson, canAdd: Boolean, onAdd: () -> Unit) {
    val name = person.name?.trim()?.takeIf { it.isNotEmpty() }
    CardRow(Modifier.testTag(AccessTags.directory(person.email)), onAdd) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Txt(name ?: person.email, 14, 700, maxLines = 1)
            if (name != null) Txt(person.email, 12, 400, C.Muted, maxLines = 1)
            Txt(stringResource(R.string.access_not_added), 12, 600, C.Orange, Modifier.padding(top = 1.dp))
        }
        Box(
            Modifier.minimumInteractiveComponentSize().clip(r(8)).clickable(enabled = canAdd, role = Role.Button, onClick = onAdd)
                .padding(horizontal = 6.dp),
            contentAlignment = Alignment.Center,
        ) { Txt(stringResource(R.string.access_action_add), 13, 700, if (canAdd) C.Yellow else C.Dim) }
    }
}

/** A name in a pill of its colour (a group, a permission, "Invited"). */
@Composable
private fun Tag(text: String, tint: Color) {
    Box(
        Modifier.clip(Capsule).background(tint.copy(alpha = 0.14f)).border(1.dp, tint.copy(alpha = 0.45f), Capsule)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) { Txt(text, 11, 600, tint, maxLines = 1) }
}

/** The one chosen property's name, or how many were chosen (as the web words it). */
@Composable
private fun propertiesText(ids: Set<String>, properties: List<Property>): String {
    val names = properties.filter { it.id in ids }.map { it.name }
    return if (names.size == 1) names[0] else pluralStringResource(R.plurals.access_property_count, ids.size, ids.size)
}

/** How many permission tags a group's card shows before "+N". */
private const val MAX_TAGS = 3

/**
 * A group: its initial in its colour (a lock for the built-in Admins group), its name and how many people are in it,
 * its description, then a tag for each thing it can do, at most [MAX_TAGS] and "+N" for the rest ("View only" when
 * nothing). Tap to open it, to edit or delete it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun GroupCard(row: GroupRow, onEdit: () -> Unit) {
    val g = row.group
    val tint = g.tint()
    CardRow(Modifier.testTag(AccessTags.group(g.id)), onEdit) {
        if (g.locked) {
            Box(
                Modifier.size(36.dp).clip(CircleShape).background(tint.copy(alpha = 0.18f)).border(1.dp, tint.copy(alpha = 0.5f), CircleShape),
                contentAlignment = Alignment.Center,
            ) { LineIcon(Icons.Lock, 15.dp, tint, 2.2f) }
        } else {
            PersonAvatar(initialsOf(g.name).take(1), tint, size = 36.dp, textSize = 13)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Txt(g.name, 14, 700, modifier = Modifier.weight(1f), maxLines = 1)
                Txt(pluralStringResource(R.plurals.access_person_count, row.members, row.members), 11, 400, C.Muted)
            }
            if (g.description.isNotBlank()) Txt(g.description.trim(), 12, 400, C.Muted, maxLines = 2)
            FlowRow(
                Modifier.padding(top = 3.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                val perms = orderedPermissions().filter(g::can)
                if (perms.isEmpty()) Tag(stringResource(R.string.access_view_only), VIEW_ONLY_COLOR.asColor())
                else {
                    perms.take(MAX_TAGS).forEach { Tag(it.title(), it.color.asColor()) }
                    if (perms.size > MAX_TAGS) Tag(stringResource(R.string.access_more_tags, perms.size - MAX_TAGS), C.Muted)
                }
            }
        }
        LineIcon(Icons.ChevronRight, 12.dp, C.Dim, 2.6f)
    }
}
