package com.sclera.sclerago.presentation.access

import com.sclera.sclerago.presentation.components.SecureWindow
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.sclera.sclerago.domain.model.AuthState
import com.sclera.sclerago.domain.model.Group
import com.sclera.sclerago.domain.model.Permission
import com.sclera.sclerago.domain.model.Person
import com.sclera.sclerago.domain.model.Property
import com.sclera.sclerago.domain.model.VIEW_ONLY_COLOR
import com.sclera.sclerago.domain.model.VIEW_ONLY_ICON
import com.sclera.sclerago.domain.usecase.access.groupProblem
import com.sclera.sclerago.domain.usecase.access.personProblem
import com.sclera.sclerago.domain.usecase.auth.ValidateEmailUseCase
import com.sclera.sclerago.presentation.common.plural
import com.sclera.sclerago.presentation.components.AdminFab
import com.sclera.sclerago.presentation.components.CheckBox
import com.sclera.sclerago.presentation.components.ChoiceSheet
import com.sclera.sclerago.presentation.components.ColorChip
import com.sclera.sclerago.presentation.components.ConfirmDialog
import com.sclera.sclerago.presentation.components.DangerBg
import com.sclera.sclerago.presentation.components.FormGroup
import com.sclera.sclerago.presentation.components.FormSheet
import com.sclera.sclerago.presentation.components.InlineField
import com.sclera.sclerago.presentation.components.InlineInput
import com.sclera.sclerago.presentation.components.InlineSelect
import com.sclera.sclerago.presentation.components.PillTone
import com.sclera.sclerago.presentation.components.SearchField
import com.sclera.sclerago.presentation.components.StatusPill
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
import com.sclera.sclerago.presentation.model.color
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
    SecureWindow() // emails and who can open which property are shown here
    val me = (sessionVm.authState.collectAsState().value as? AuthState.SignedIn)?.session?.email
    val properties = vm.properties
    LaunchedEffect(me) { access.ensureSeeded(me, properties.map { it.id }.toSet()) }
    val s = access.state
    val onPeople = s.tab == AccessTab.PEOPLE

    Box(Modifier.fillMaxSize().background(C.Bg)) {
        Column(Modifier.fillMaxSize()) {
            SubPageBar("Users", vm.propName, vm::openMenu)
            LazyColumn(
                Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                item {
                    Column(Modifier.padding(bottom = 2.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        TabSwitch(listOf("People (${access.people.size})", "Groups (${access.groups.size})"), if (onPeople) 0 else 1) {
                            access.setTab(if (it == 0) AccessTab.PEOPLE else AccessTab.GROUPS)
                        }
                        SearchField(s.query, access::setQuery, if (onPeople) "Search people" else "Search groups", height = 40.dp)
                    }
                }
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
        Modifier.fillMaxWidth().clip(r(12)).background(C.Surface).border(1.dp, C.Border, r(12)).padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        labels.forEachIndexed { i, l ->
            val on = i == selected
            val bg by animateColorAsState(if (on) C.Raised else Color.Transparent, label = "tab")
            Box(
                Modifier.weight(1f).height(34.dp).clip(r(9)).background(bg).clickable { onSelect(i) },
                contentAlignment = Alignment.Center,
            ) { Txt(l, 13, 600, if (on) C.Text else C.Muted) }
        }
    }
}

/** A list row: rounded card, 8 / 10 padding. */
@Composable
private fun RowCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxWidth().clip(r(12)).background(C.Surface).border(1.dp, C.Border, r(12)).padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        content = content,
    )
}

/**
 * A person on one line: initials, name (with a green dot once they've been seen) and email, then
 * their group and which properties they can open. Tap to edit.
 */
@Composable
private fun PersonCard(p: Person, group: Group?, properties: List<Property>, isMe: Boolean, onEdit: () -> Unit) {
    val seen = p.lastSeen ?: if (isMe) "just now" else null
    RowCard(Modifier.clickable(onClick = onEdit)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(C.Yellow.copy(alpha = 0.18f)).border(1.dp, C.Yellow.copy(alpha = 0.5f), CircleShape),
                contentAlignment = Alignment.Center,
            ) { Txt(initialsOf(p.name), 13, 700, C.Yellow) }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Txt(p.name, 15, 600, modifier = Modifier.weight(1f, fill = false))
                    if (seen != null) Box(Modifier.size(7.dp).background(C.Green, CircleShape).semantics { contentDescription = "Active $seen" })
                }
                Txt(p.email, 12, 400, C.Muted)
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                group?.let { ColorChip(it.name, it.tint(), it.icon(), small = true) }
                Txt(
                    if (p.allProperties) "All properties"
                    else properties.filter { it.id in p.propertyIds }.let { if (it.size == 1) it[0].name else plural(it.size, "property", "properties") },
                    11, 400, C.Muted,
                )
            }
            LineIcon(Icons.ChevronRight, 16.dp, C.Dim, 2.4f)
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
    RowCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(40.dp).clip(r(10)).background(g.tint().copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                LineIcon(g.icon(), 18.dp, g.tint(), 1.9f)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Txt(g.name, 15, 600)
                    Txt(plural(people, "person", "people"), 12, 400, C.Muted)
                }
                if (g.description.isNotBlank()) Txt(g.description, 12, 400, C.Muted)
            }
            SquareAction(true, onEdit) { LineIcon(Icons.Pencil, 15.dp, C.Text, 1.9f) }
            SquareAction(canDelete, onDelete) { LineIcon(Icons.Trash, 15.dp, if (canDelete) C.Red else C.RadioOff, 1.9f) }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (g.permissions.isEmpty() && !g.locked) ColorChip("View only", VIEW_ONLY_COLOR.asColor(), iconOf(VIEW_ONLY_ICON), small = true)
            else Permission.entries.filter { g.locked || it in g.permissions }.forEach { ColorChip(it.title, it.color.asColor(), iconOf(it.iconPath), small = true) }
        }
    }
}

@Composable
private fun SquareAction(enabled: Boolean, onClick: () -> Unit, icon: @Composable () -> Unit) {
    Box(
        Modifier.size(34.dp).clip(r(10)).background(C.Raised).border(1.dp, C.Border2, r(10)).clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { icon() }
}


// ---------------------------------------------------------------- Add / edit person

private const val ALL_PROPERTIES = "All properties"
private const val CHOSEN_PROPERTIES = "Chosen properties"

/** Add a person / edit one: a Person section (email, group) and a Property access section. */
@Composable
private fun PersonSheet(initial: Person, access: AccessViewModel, properties: List<Property>, isMe: Boolean) {
    val isNew = initial.id.isEmpty()
    var email by remember { mutableStateOf(initial.email) }
    var groupId by remember { mutableStateOf(initial.groupId) }
    var all by remember { mutableStateOf(initial.allProperties) }
    var chosen by remember { mutableStateOf(initial.propertyIds) }
    var picking by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val group = access.group(groupId)
    val forced = group?.needsAllProperties == true
    val isAll = all || forced
    val draft = initial.copy(email = email, groupId = groupId, allProperties = isAll, propertyIds = chosen)
    val valid = personProblem(draft, access.people, group) == null
    val emailOk = !isNew || ValidateEmailUseCase.EMAIL_ADDRESS.matches(email.trim())
    val scopeOk = isAll || chosen.isNotEmpty()
    val progress = if (isNew) ((if (emailOk) 1 else 0) + (if (scopeOk) 1 else 0)) / 2f else if (scopeOk) 1f else 0f

    Box(Modifier.fillMaxSize()) {
        FormSheet(
            if (isNew) "Add a person" else initial.name,
            if (isNew) "They get this access the first time they sign in." else initial.email,
            if (isNew) "Add" else "Save", valid, error, access::closePersonForm,
            onPrimary = { error = access.savePerson(draft) },
            leadingIcon = Icons.Users, progress = progress,
            bodyBackground = C.Bg, bodyPadding = PaddingValues(vertical = 12.dp), bodySpacing = 12.dp,
        ) {
            FormGroup("Person", Icons.Users, trailing = {
                if (emailOk) StatusPill("Ready", PillTone.READY) else StatusPill("1 required", PillTone.NEEDED)
            }) {
                if (isNew) {
                    InlineField("Email", required = true) {
                        InlineInput(email, { email = it; error = null }, "name@company.com", keyboard = KeyboardType.Email)
                    }
                }
                InlineField("Group", required = true) { InlineSelect(group?.name.orEmpty(), "Choose a group") { picking = "group" } }
                group?.description?.takeIf { it.isNotBlank() }?.let {
                    Txt(it, 12, 400, C.Muted, Modifier.padding(start = 12.dp, end = 12.dp, bottom = 10.dp), maxLines = 3)
                }
            }
            FormGroup("Property access", Icons.Building, trailing = {
                if (scopeOk) StatusPill(if (isAll) "All" else "${chosen.size} chosen", PillTone.READY)
                else StatusPill("1 required", PillTone.NEEDED)
            }) {
                InlineField("Access", required = true) {
                    InlineSelect(if (isAll) ALL_PROPERTIES else CHOSEN_PROPERTIES, "", enabled = !forced) { picking = "scope" }
                }
                if (forced) {
                    Txt(
                        "${group?.name} has permissions that need all properties.", 12, 400, C.Muted,
                        Modifier.padding(start = 12.dp, end = 12.dp, bottom = 10.dp), maxLines = 3,
                    )
                }
                if (!isAll) {
                    properties.forEach { p ->
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 48.dp).topBorder(C.Border)
                                .clickable { chosen = if (p.id in chosen) chosen - p.id else chosen + p.id; error = null }
                                .padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            CheckBox(p.id in chosen, shape = r(5), offColor = C.Soft, checkSize = 12.dp)
                            Txt(p.name, 14, 500, modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
            if (!isNew && !isMe) {
                Txt(
                    "Remove ${initial.name}", 14, 600, C.Red,
                    Modifier.padding(horizontal = 16.dp).clip(r(8)).clickable { access.askRemovePerson(initial) }.padding(vertical = 6.dp),
                )
            }
        }
        when (picking) {
            "group" -> ChoiceSheet(
                "Choose a group", access.groups.map { it.name to it.description.ifBlank { null } }, group?.name,
                onPick = { name -> access.groups.firstOrNull { it.name == name }?.let { groupId = it.id }; picking = null; error = null },
                onClose = { picking = null },
            )
            "scope" -> ChoiceSheet(
                "Property access",
                listOf(
                    ALL_PROPERTIES to "Every property, including the ones added later.",
                    CHOSEN_PROPERTIES to "New properties aren’t included until you add them here.",
                ),
                if (isAll) ALL_PROPERTIES else CHOSEN_PROPERTIES,
                onPick = { all = it == ALL_PROPERTIES; picking = null; error = null },
                onClose = { picking = null },
            )
        }
    }
}

// ---------------------------------------------------------------- New / edit group

/** New group / edit one: Group details (name, description) and What they can do (permissions). */
@Composable
private fun GroupSheet(initial: Group, access: AccessViewModel) {
    val isNew = initial.id.isEmpty()
    val locked = initial.locked
    var name by remember { mutableStateOf(initial.name) }
    var description by remember { mutableStateOf(initial.description) }
    var perms by remember { mutableStateOf(initial.permissions) }
    var error by remember { mutableStateOf<String?>(null) }
    val draft = initial.copy(name = name, description = description, permissions = perms)
    val valid = groupProblem(draft, access.groups) == null
    val named = name.isNotBlank()

    FormSheet(
        if (isNew) "New group" else "Edit ${initial.name}",
        when {
            locked -> "${initial.name} always have every permission."
            isNew -> "Tick what people in this group can do. With nothing ticked they can only look."
            else -> "Choose what people in this group can do."
        },
        if (isNew) "Add group" else "Save", valid, error, access::closeGroupForm,
        onPrimary = { error = access.saveGroup(draft) },
        leadingIcon = iconOf(Permission.MANAGE_ACCESS.iconPath), progress = if (named) 1f else 0f,
        bodyBackground = C.Bg, bodyPadding = PaddingValues(vertical = 12.dp), bodySpacing = 12.dp,
    ) {
        FormGroup("Group details", Icons.Users, trailing = {
            if (named) StatusPill("Ready", PillTone.READY) else StatusPill("1 required", PillTone.NEEDED)
        }) {
            InlineField("Name", required = true, dim = locked) {
                InlineInput(name, { name = it; error = null }, "Group name", enabled = !locked)
            }
            InlineField("Description") { InlineInput(description, { description = it }, "What this group is for") }
        }
        FormGroup("What they can do", Icons.ShieldCheck, trailing = {
            if (locked) StatusPill("Locked", PillTone.NEUTRAL) else StatusPill("${perms.size} chosen", PillTone.READY)
        }) {
            if (locked) {
                Row(
                    Modifier.padding(start = 12.dp, end = 12.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    LineIcon(Icons.Lock, 13.dp, TintText, 2.2f)
                    Txt("These permissions are locked for ${initial.name}.", 12, 400, TintText, maxLines = 2)
                }
            }
            Permission.entries.forEach { p ->
                val on = locked || p in perms
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 48.dp).topBorder(C.Border).alpha(if (locked) 0.75f else 1f)
                        .clickable(enabled = !locked) { perms = if (p in perms) perms - p else perms + p }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(Modifier.padding(top = 1.dp)) { CheckBox(on, shape = r(5), offColor = C.Soft, checkSize = 12.dp) }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            LineIcon(iconOf(p.iconPath), 14.dp, p.color.asColor(), 1.9f)
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
