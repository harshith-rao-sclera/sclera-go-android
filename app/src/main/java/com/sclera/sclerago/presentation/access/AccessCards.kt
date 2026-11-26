package com.sclera.sclerago.presentation.access

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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
import com.sclera.sclerago.presentation.common.UiText
import com.sclera.sclerago.presentation.components.AccessGate
import com.sclera.sclerago.presentation.components.Capsule
import com.sclera.sclerago.presentation.components.LocalToaster
import com.sclera.sclerago.presentation.components.PRESSED_CARD
import com.sclera.sclerago.presentation.components.ToastKind
import com.sclera.sclerago.presentation.components.asColor
import com.sclera.sclerago.presentation.components.pressClickable
import com.sclera.sclerago.presentation.components.agoText
import com.sclera.sclerago.presentation.components.r
import com.sclera.sclerago.presentation.icons.Icons
import com.sclera.sclerago.presentation.icons.LineIcon
import com.sclera.sclerago.presentation.theme.C
import com.sclera.sclerago.presentation.theme.Txt

// Cards and the tab switch of People and access.

/** The two-segment Users / Groups switch (40dp-high tabs). */
@Composable
internal fun TabSwitch(labels: List<String>, tags: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(r(14)).background(C.Surface).border(1.dp, C.Border, r(14)).padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        labels.forEachIndexed { i, l ->
            val on = i == selected
            val bg by animateColorAsState(if (on) C.Raised else Color.Transparent, label = "tab")
            Box(
                Modifier.weight(1f).height(32.dp).pressClickable(shape = r(9), role = Role.Tab) { onSelect(i) }.background(bg).testTag(tags[i])
                    .semantics { this.selected = on },
                contentAlignment = Alignment.Center,
            ) { Txt(l, 12, 600, if (on) C.Text else C.Muted) }
        }
    }
}

/** The surface and edge every card of this page shares. */
private fun Modifier.cardLook(): Modifier = fillMaxWidth().clip(r(12)).background(C.Surface).border(1.dp, C.Border, r(12))

/**
 * A card's row, the same for users, people not added yet and groups: the whole card tappable, its parts centred, and
 * every card as tall as the others ([CardHeight]: each holds at most three single lines, so none grows past it).
 */
@Composable
private fun CardRow(modifier: Modifier, onClick: () -> Unit, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier.pressClickable(shape = r(12), pressed = PRESSED_CARD, role = Role.Button, onClick = onClick).cardLook()
            .heightIn(min = CardHeight).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) { content() }
}

/**
 * A person: their name (the email when there is none) with, at its end, when they were last seen (or "Invited" while they
 * haven't signed in yet), their email, then their group's pill and the properties they can open. Tap to edit.
 */
@Composable
internal fun PersonCard(row: PersonRow, properties: List<Property>, nowMillis: Long, onEdit: () -> Unit) {
    val p = row.person
    val tint = row.group?.tint() ?: C.Muted
    CardRow(Modifier.testTag(AccessTags.person(p.id)), onEdit) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Txt(p.name, 14, 600, modifier = Modifier.weight(1f), maxLines = 1)
                // At the end of the name's line: when they were last active ("just now", "3 min ago", a date), or "Invited"
                // while they haven't signed in yet.
                when {
                    // Signed in but never seen since (no time from the server): "Signed in".
                    p.signedIn -> Txt(agoText(p.lastSeenAt, nowMillis).ifEmpty { stringResource(R.string.access_signed_in) }, 11, 400, C.Muted, maxLines = 1)
                    p.hasAccess -> Tag(stringResource(R.string.access_invited), C.Orange, bordered = false)
                }
            }
            // The email goes under the name only when there is a name (otherwise it is the name).
            if (p.name != p.email) Txt(p.email, 12, 400, C.Muted, maxLines = 1)
            Row(
                Modifier.padding(top = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                row.group?.let { Tag(it.name, tint) }
                    ?: Txt(stringResource(R.string.access_no_access), 12, 500, C.Muted)
                if (row.group != null) {
                    Txt(
                        "· " + when {
                            p.allProperties -> stringResource(R.string.access_all_properties)
                            p.propertyIds.isEmpty() -> stringResource(R.string.access_none_chosen)
                            else -> propertiesText(p.propertyIds, properties)
                        },
                        12, 400, C.Muted, maxLines = 1,
                    )
                }
            }
        }
        LineIcon(Icons.ChevronRight, 12.dp, C.Dim, 3f)
    }
}

/**
 * Someone else the organisation's directory lists, not added here yet (as the web lists them under the users): their
 * name and email, "Not added", and Add. Tapping the card or Add opens Add user with them filled in. When they can't be
 * added ([addReason]: no Manage access, or no group to put them in yet) Add is greyed out and a tap on it or the card
 * says why instead.
 */
@Composable
internal fun DirectoryCard(person: DirectoryPerson, addReason: UiText?, onAdd: () -> Unit) {
    val name = person.name?.trim()?.takeIf { it.isNotEmpty() }
    val toaster = LocalToaster.current
    CardRow(Modifier.testTag(AccessTags.directory(person.email)), { addReason?.let { toaster.show(it, ToastKind.INFO) } ?: onAdd() }) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Txt(name ?: person.email, 14, 600, maxLines = 1)
            if (name != null) Txt(person.email, 12, 400, C.Muted, maxLines = 1)
            Txt(stringResource(R.string.access_not_added), 12, 600, C.Orange)
        }
        AccessGate(addReason) {
            Box(
                Modifier.minimumInteractiveComponentSize().pressClickable(shape = r(8), role = Role.Button, onClick = onAdd)
                    .padding(horizontal = 6.dp),
                contentAlignment = Alignment.Center,
            ) { Txt(stringResource(R.string.access_action_add), 13, 600, C.Yellow) }
        }
    }
}

/** A name in a pill of its colour (a group, a permission, "Invited"); [size] and the padding follow the card it is on. */
@Composable
private fun Tag(text: String, tint: Color, size: Int = 11, hPad: Int = 8, vPad: Int = 3, bordered: Boolean = true) {
    Box(
        Modifier.clip(Capsule).background(tint.copy(alpha = 0.15f))
            .then(if (bordered) Modifier.border(1.dp, tint.copy(alpha = 0.3f), Capsule) else Modifier)
            .padding(horizontal = hPad.dp, vertical = vPad.dp),
    ) { Txt(text, size, 600, tint, maxLines = 1) }
}

/** The one chosen property's name, or how many were chosen (as the web words it). */
@Composable
private fun propertiesText(ids: Set<String>, properties: List<Property>): String {
    val names = properties.filter { it.id in ids }.map { it.name }
    return if (names.size == 1) names[0] else pluralStringResource(R.plurals.access_property_count, ids.size, ids.size)
}

/** How many permission tags a group's card shows before "+N". */
private const val MAX_TAGS = 3

/** Every user, directory and group card's height: a name, a line under it and a row of tags. */
private val CardHeight = 88.dp

/**
 * A group: its name and how many people are in it, its description (one line), then one row of tags for what it can do,
 * at most [MAX_TAGS] and "+N" for the rest ("View only" when nothing), and a chevron. Tap to open it (its sheet has Delete).
 */
@Composable
internal fun GroupCard(row: GroupRow, onEdit: () -> Unit) {
    val g = row.group
    Box(Modifier.testTag(AccessTags.group(g.id))) {
        CardRow(Modifier, onEdit) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Txt(g.name, 14, 600, modifier = Modifier.weight(1f), maxLines = 1)
                    Txt(pluralStringResource(R.plurals.access_person_count, row.members, row.members), 12, 400, C.Muted)
                }
                if (g.description.isNotBlank()) Txt(g.description.trim(), 12, 400, C.Muted, maxLines = 1)
                Row(
                    Modifier.padding(top = 3.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    val perms = orderedPermissions().filter(g::can)
                    // Nothing ticked: the group can't open a property (the web's warning tag).
                    if (perms.isEmpty()) Tag(stringResource(R.string.access_no_permissions), C.Orange)
                    else {
                        perms.take(MAX_TAGS).forEach { Tag(it.title(), it.color.asColor()) }
                        if (perms.size > MAX_TAGS) {
                            Tag(stringResource(R.string.access_more_tags, perms.size - MAX_TAGS), C.IconGrey)
                        }
                    }
                    // Its people work in a fenced property only from inside it (the web's warning tag).
                    if (g.heldToFence) Tag(stringResource(R.string.access_geo_restriction), C.Orange)
                }
            }
            LineIcon(Icons.ChevronRight, 12.dp, C.Dim, 3f)
        }
    }
}
