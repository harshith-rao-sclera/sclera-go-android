package com.sclera.sclerago.presentation.access

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.sclera.sclerago.data.Group
import com.sclera.sclerago.data.Permission
import com.sclera.sclerago.data.Person
import com.sclera.sclerago.data.Property
import com.sclera.sclerago.data.VIEW_ONLY_COLOR
import com.sclera.sclerago.data.VIEW_ONLY_ICON
import com.sclera.sclerago.data.plural
import com.sclera.sclerago.domain.model.AuthState
import com.sclera.sclerago.presentation.components.AdminFab
import com.sclera.sclerago.presentation.components.CheckBox
import com.sclera.sclerago.presentation.components.ChoiceSheet
import com.sclera.sclerago.presentation.components.ColorChip
import com.sclera.sclerago.presentation.components.ConfirmDialog
import com.sclera.sclerago.presentation.components.DangerBg
import com.sclera.sclerago.presentation.components.FieldLabel
import com.sclera.sclerago.presentation.components.FormSheet
import com.sclera.sclerago.presentation.components.PageHeading
import com.sclera.sclerago.presentation.components.RadioRing
import com.sclera.sclerago.presentation.components.SearchField
import com.sclera.sclerago.presentation.components.SheetInput
import com.sclera.sclerago.presentation.components.SheetSelect
import com.sclera.sclerago.presentation.components.StatCard
import com.sclera.sclerago.presentation.components.SubPageBar
import com.sclera.sclerago.presentation.components.TintChip
import com.sclera.sclerago.presentation.components.TintText
import com.sclera.sclerago.presentation.components.asColor
import com.sclera.sclerago.presentation.components.iconOf
import com.sclera.sclerago.presentation.components.r
import com.sclera.sclerago.presentation.components.topBorder
import com.sclera.sclerago.presentation.icons.Icons
import com.sclera.sclerago.presentation.icons.LineIcon
import com.sclera.sclerago.presentation.main.AppViewModel
import com.sclera.sclerago.presentation.session.SessionViewModel
import com.sclera.sclerago.presentation.theme.C
import com.sclera.sclerago.presentation.theme.Txt

private val GroupsPurple = Color(0xFFAE9CE6)

private fun Group.tint(): Color = topPermission?.color?.asColor() ?: VIEW_ONLY_COLOR.asColor()
private fun Group.icon() = iconOf(topPermission?.iconPath ?: VIEW_ONLY_ICON)

/** People and access (side menu → Users): people, the group each is in, and what groups can do. */
@Composable
fun PeopleScreen(
    vm: AppViewModel,
    access: AccessViewModel = hiltViewModel(),
    sessionVm: SessionViewModel = hiltViewModel(),
) {
    BackHandler(onBack = vm::closeAdmin)
    val me = (sessionVm.authState.collectAsState().value as? AuthState.SignedIn)?.session?.email
    val properties = vm.state.properties
    LaunchedEffect(me) { access.ensureSeeded(me, properties.map { it.id }.toSet()) }
    val s = access.state
    val onPeople = s.tab == AccessTab.PEOPLE

    Box(Modifier.fillMaxSize().background(C.Bg)) {
        Column(Modifier.fillMaxSize()) {
            SubPageBar("Users", vm.propName, vm::openMenu)
            LazyColumn(
                Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 120.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item {
                    PageHeading(
                        "People and access",
                        "A group says what its people can do. Each person is in one group, for all properties or the ones chosen for them.",
                    )
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatCard(Icons.Users, C.Yellow, s.people.size.toString(), "People", Modifier.weight(1f))
                        StatCard(iconOf(Permission.MANAGE_ACCESS.iconPath), GroupsPurple, s.groups.size.toString(), "Groups", Modifier.weight(1f))
                    }
                }
                item {
                    TabSwitch(listOf("People (${s.people.size})", "Groups (${s.groups.size})"), if (onPeople) 0 else 1) {
                        access.setTab(if (it == 0) AccessTab.PEOPLE else AccessTab.GROUPS)
                    }
                }
                item { SearchField(s.query, access::setQuery, if (onPeople) "Search people" else "Search groups") }
                if (onPeople) {
                    items(access.visiblePeople, key = { it.id }) { p ->
                        PersonCard(p, access.group(p.groupId), properties, isMe = p.email.equals(me, ignoreCase = true)) { access.editPerson(p) }
                    }
                    if (access.visiblePeople.isEmpty()) item {
                        EmptyText(if (s.query.isBlank()) "No one has access yet." else "No one matches “${s.query.trim()}”.")
                    }
                } else {
                    items(access.visibleGroups, key = { it.id }) { g ->
                        GroupCard(g, access.peopleIn(g.id), access.canDelete(g), onEdit = { access.editGroup(g) }, onDelete = { access.askDeleteGroup(g) })
                    }
                    if (access.visibleGroups.isEmpty()) item { EmptyText("No group matches “${s.query.trim()}”.") }
                }
            }
        }
        AdminFab(if (onPeople) "Add person" else "New group", if (onPeople) access::newPerson else access::newGroup, Modifier.align(Alignment.BottomEnd))

        s.personForm?.let { PersonSheet(it, access, properties, isMe = it.email.equals(me, ignoreCase = true)) }
        s.groupForm?.let { GroupSheet(it, access) }
        s.confirmDelete?.let { g ->
            BackHandler { access.askDeleteGroup(null) }
            ConfirmDialog(
                Icons.Trash, DangerBg, C.Red, "Delete ${g.name}?", "The group will be removed. This can’t be undone.",
                "Cancel", "Delete", { access.askDeleteGroup(null) }, { access.deleteGroup(g) },
            )
        }
        s.personToRemove?.let { p ->
            BackHandler { access.askRemovePerson(null) }
            ConfirmDialog(
                Icons.Trash, DangerBg, C.Red, "Remove ${p.name}?", "${p.email} will lose access to Sclera.",
                "Cancel", "Remove", { access.askRemovePerson(null) }, { access.closePersonForm(); access.removePerson(p) },
            )
        }
    }
}

@Composable
private fun EmptyText(text: String) = Txt(text, 14, 400, C.Muted, Modifier.padding(top = 8.dp), maxLines = 2)

@Composable
private fun TabSwitch(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(r(14)).background(C.Surface).border(1.dp, C.Border, r(14)).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        labels.forEachIndexed { i, l ->
            val on = i == selected
            val bg by animateColorAsState(if (on) C.Raised else Color.Transparent, label = "tab")
            Box(
                Modifier.weight(1f).height(40.dp).clip(r(10)).background(bg).clickable { onSelect(i) },
                contentAlignment = Alignment.Center,
            ) { Txt(l, 14, 600, if (on) C.Text else C.Muted) }
        }
    }
}

@Composable
private fun CardShell(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxWidth().clip(r(18)).background(C.Surface).border(1.dp, C.Border, r(18)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

@Composable
private fun SmallLabel(text: String) = Txt(text, 11, 600, C.Muted, spacing = 0.6f)

/** A person: tinted initials, name and email, then GROUP / PROPERTIES / LAST SEEN. Tap to edit. */
@Composable
private fun PersonCard(p: Person, group: Group?, properties: List<Property>, isMe: Boolean, onEdit: () -> Unit) {
    CardShell(Modifier.clickable(onClick = onEdit)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(C.Yellow.copy(alpha = 0.18f)).border(1.dp, C.Yellow.copy(alpha = 0.5f), CircleShape),
                contentAlignment = Alignment.Center,
            ) { Txt(initialsOf(p.name), 15, 700, C.Yellow) }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Txt(p.name, 16, 600)
                Txt(p.email, 13, 400, C.Muted)
            }
            LineIcon(Icons.ChevronRight, 16.dp, C.Dim, 2.4f)
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(C.Border))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SmallLabel("GROUP")
                group?.let { ColorChip(it.name, it.tint(), it.icon()) } ?: Txt("—", 14, 500)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                SmallLabel("PROPERTIES")
                Txt(
                    if (p.allProperties) "All properties"
                    else properties.filter { it.id in p.propertyIds }.let { if (it.size == 1) it[0].name else plural(it.size, "property", "properties") },
                    14, 500, maxLines = 2,
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                SmallLabel("LAST SEEN")
                val seen = p.lastSeen ?: if (isMe) "just now" else null
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (seen != null) Box(Modifier.size(7.dp).background(C.Green, CircleShape))
                    Txt(seen ?: "Not yet", 14, 400, C.Soft)
                }
            }
        }
    }
}

private fun initialsOf(name: String): String {
    val parts = name.split(' ', '.', '_', '-').filter { it.isNotBlank() }
    return listOfNotNull(parts.firstOrNull(), parts.drop(1).lastOrNull()).joinToString("") { it.first().uppercase() }.ifEmpty { "?" }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GroupCard(g: Group, people: Int, canDelete: Boolean, onEdit: () -> Unit, onDelete: () -> Unit) {
    CardShell {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(44.dp).clip(r(12)).background(g.tint().copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                LineIcon(g.icon(), 18.dp, g.tint(), 1.9f)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Txt(g.name, 16, 600)
                    Txt(plural(people, "person", "people"), 13, 400, C.Muted)
                }
                if (g.description.isNotBlank()) Txt(g.description, 13, 400, C.Soft, maxLines = 3)
            }
            SquareAction(true, onEdit) { LineIcon(Icons.Pencil, 16.dp, C.Text, 1.9f) }
            SquareAction(canDelete, onDelete) { LineIcon(Icons.Trash, 16.dp, if (canDelete) C.Red else C.RadioOff, 1.9f) }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (g.permissions.isEmpty() && !g.locked) ColorChip("View only", VIEW_ONLY_COLOR.asColor(), iconOf(VIEW_ONLY_ICON))
            else Permission.entries.filter { g.locked || it in g.permissions }.forEach { ColorChip(it.title, it.color.asColor(), iconOf(it.iconPath)) }
        }
    }
}

@Composable
private fun SquareAction(enabled: Boolean, onClick: () -> Unit, icon: @Composable () -> Unit) {
    Box(
        Modifier.size(44.dp).clip(r(12)).background(C.Raised).border(1.dp, C.Border2, r(12)).clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { icon() }
}

// ---------------------------------------------------------------- Add / edit person

@Composable
private fun PersonSheet(initial: Person, access: AccessViewModel, properties: List<Property>, isMe: Boolean) {
    val isNew = initial.id.isEmpty()
    var email by remember { mutableStateOf(initial.email) }
    var groupId by remember { mutableStateOf(initial.groupId) }
    var all by remember { mutableStateOf(initial.allProperties) }
    var chosen by remember { mutableStateOf(initial.propertyIds) }
    var picking by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val group = access.group(groupId)
    val forced = group?.needsAllProperties == true
    val isAll = all || forced
    val draft = initial.copy(email = email, groupId = groupId, allProperties = isAll, propertyIds = chosen)
    val valid = personProblem(draft, access.state.people, group) == null

    Box(Modifier.fillMaxSize()) {
        FormSheet(
            if (isNew) "Add a person" else initial.name,
            if (isNew) "They get this access the first time they sign in." else initial.email,
            if (isNew) "Add" else "Save", valid, error, access::closePersonForm,
            onPrimary = { error = access.savePerson(draft) },
        ) {
            if (isNew) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    FieldLabel("Email")
                    SheetInput(email, { email = it; error = null }, "name@company.com", keyboard = KeyboardType.Email)
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                FieldLabel("Group")
                SheetSelect(group?.name ?: "Choose a group", picking) { picking = true }
                group?.description?.takeIf { it.isNotBlank() }?.let { Txt(it, 12, 400, C.Muted) }
            }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                FieldLabel("Properties")
                ScopeCard("All properties", "Every property, including the ones added later.", isAll) { all = true; error = null }
                ScopeCard(
                    "Only chosen properties",
                    if (forced) "${group?.name} has permissions that need all properties." else "New properties aren’t included until you add them here.",
                    !isAll, enabled = !forced, badge = if (!isAll) "${chosen.size} chosen" else null,
                ) { all = false; error = null }
                if (!isAll) {
                    Column(Modifier.fillMaxWidth().clip(r(14)).background(C.Surface).border(1.dp, C.Border, r(14))) {
                        properties.forEachIndexed { i, p ->
                            Row(
                                Modifier.fillMaxWidth().heightIn(min = 50.dp).then(if (i > 0) Modifier.topBorder(C.Border) else Modifier)
                                    .clickable { chosen = if (p.id in chosen) chosen - p.id else chosen + p.id; error = null }
                                    .padding(horizontal = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                CheckBox(p.id in chosen, shape = r(5), offColor = C.Soft, checkSize = 12.dp)
                                Txt(p.name, 14, 500, modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
            if (!isNew && !isMe) {
                Txt(
                    "Remove ${initial.name}", 14, 600, C.Red,
                    Modifier.clip(r(8)).clickable { access.askRemovePerson(initial) }.padding(vertical = 6.dp),
                )
            }
        }
        if (picking) {
            ChoiceSheet(
                "Choose a group", access.state.groups.map { it.name to it.description.ifBlank { null } }, group?.name,
                onPick = { name -> access.state.groups.firstOrNull { it.name == name }?.let { groupId = it.id }; picking = false; error = null },
                onClose = { picking = false },
            )
        }
    }
}

/** "All properties" / "Only chosen properties" choice. */
@Composable
private fun ScopeCard(title: String, body: String, on: Boolean, enabled: Boolean = true, badge: String? = null, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().alpha(if (enabled) 1f else 0.55f).clip(r(14)).background(if (on) C.YTintBg else C.Surface)
            .border(1.dp, if (on) C.YTintBd else C.Border, r(14)).clickable(enabled = enabled, onClick = onClick).padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.padding(top = 1.dp)) { RadioRing(on, 18.dp) }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Txt(title, 15, 600)
                badge?.let { TintChip(it) }
            }
            Txt(body, 12, 400, C.Muted, maxLines = 3)
        }
    }
}

// ---------------------------------------------------------------- New / edit group

@Composable
private fun GroupSheet(initial: Group, access: AccessViewModel) {
    val isNew = initial.id.isEmpty()
    val locked = initial.locked
    var name by remember { mutableStateOf(initial.name) }
    var description by remember { mutableStateOf(initial.description) }
    var perms by remember { mutableStateOf(initial.permissions) }
    var error by remember { mutableStateOf<String?>(null) }
    val draft = initial.copy(name = name, description = description, permissions = perms)
    val valid = groupProblem(draft, access.state.groups) == null

    FormSheet(
        if (isNew) "New group" else "Edit ${initial.name}",
        when {
            locked -> "${initial.name} always have every permission."
            isNew -> "Tick what people in this group can do. With nothing ticked they can only look."
            else -> "Choose what people in this group can do."
        },
        if (isNew) "Add group" else "Save", valid, error, access::closeGroupForm,
        onPrimary = { error = access.saveGroup(draft) },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            FieldLabel("Name")
            SheetInput(name, { name = it; error = null }, "Group name", enabled = !locked)
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            FieldLabel("Description", optional = true)
            SheetInput(description, { description = it }, "What this group is for")
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            FieldLabel("What they can do")
            if (locked) {
                Row(
                    Modifier.fillMaxWidth().clip(r(12)).background(C.YTintBg).padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    LineIcon(Icons.Lock, 14.dp, TintText, 2.2f)
                    Txt("These permissions are locked for ${initial.name}.", 13, 400, TintText, maxLines = 2)
                }
            }
            Permission.entries.forEach { p ->
                val on = locked || p in perms
                Row(
                    Modifier.fillMaxWidth().clip(r(14)).background(if (on) Color(0xCC2E2914) else C.Surface)
                        .border(1.dp, if (on) C.YTintBd else C.Border, r(14))
                        .clickable(enabled = !locked) { perms = if (p in perms) perms - p else perms + p }.padding(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(Modifier.alpha(if (locked) 0.6f else 1f)) { CheckBox(on, shape = r(5), offColor = C.Soft, checkSize = 12.dp) }
                    Column(Modifier.weight(1f).alpha(if (locked) 0.75f else 1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            LineIcon(iconOf(p.iconPath), 16.dp, p.color.asColor(), 1.9f)
                            Txt(p.title, 15, 600)
                            if (p.needsAllProperties) TintChip("Needs all properties")
                        }
                        Txt(p.description, 12, 400, C.Muted, maxLines = 3)
                    }
                }
            }
        }
    }
}
