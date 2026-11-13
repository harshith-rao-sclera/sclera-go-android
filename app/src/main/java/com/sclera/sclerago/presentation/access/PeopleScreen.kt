package com.sclera.sclerago.presentation.access

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sclera.sclerago.R
import com.sclera.sclerago.domain.model.AuthState
import com.sclera.sclerago.domain.model.Group
import com.sclera.sclerago.domain.model.Person
import com.sclera.sclerago.domain.model.Property
import com.sclera.sclerago.presentation.common.asString
import com.sclera.sclerago.presentation.components.CircleFab
import com.sclera.sclerago.presentation.components.ConfirmDialog
import com.sclera.sclerago.presentation.components.SearchFab
import com.sclera.sclerago.presentation.components.SecureWindow
import com.sclera.sclerago.presentation.components.StatCard
import com.sclera.sclerago.presentation.components.readableWidth
import com.sclera.sclerago.presentation.components.SelectionSummary
import com.sclera.sclerago.presentation.components.ActionDock
import com.sclera.sclerago.presentation.components.StatRow
import com.sclera.sclerago.presentation.components.SubPageBar
import com.sclera.sclerago.presentation.components.asColor
import com.sclera.sclerago.presentation.components.iconOf
import com.sclera.sclerago.presentation.components.pageGutter
import com.sclera.sclerago.domain.model.Permission
import com.sclera.sclerago.presentation.icons.Icons
import com.sclera.sclerago.presentation.main.AppViewModel
import com.sclera.sclerago.presentation.session.SessionViewModel
import com.sclera.sclerago.presentation.theme.C
import com.sclera.sclerago.presentation.theme.Txt

/** People and access (side menu → Users): people, the group each is in, and what groups can do. */
@Composable
fun PeopleScreen(
    vm: AppViewModel,
    access: AccessViewModel = hiltViewModel(),
    sessionVm: SessionViewModel = hiltViewModel(),
) {
    BackHandler(onBack = vm::closeAdmin)
    // While groups are ticked, back clears the ticks first.
    BackHandler(enabled = access.state.pickedGroups.isNotEmpty(), onBack = access::clearGroupPick)
    SecureWindow() // emails and who can open which property are shown here
    val authState by sessionVm.authState.collectAsStateWithLifecycle()
    val me = (authState as? AuthState.SignedIn)?.session?.email
    val s = access.state
    val notice = s.notice?.asString()
    LaunchedEffect(notice) {
        if (notice != null) {
            vm.flash(notice)
            access.consumeNotice()
        }
    }
    val properties = vm.properties

    Box(Modifier.fillMaxSize()) {
        PeopleContent(
            list = access.listState(me), properties = properties,
            onBack = vm::openMenu, onTab = access::setTab, onQuery = access::setQuery,
            onAdd = { if (s.tab == AccessTab.PEOPLE) access.newPerson() else access.newGroup() },
            onEditPerson = access::editPerson, onEditGroup = access::editGroup, onGroupFilter = access::setGroupFilter,
            selection = GroupSelectionActions(
                onToggle = access::toggleGroupPick, onSelectAll = access::selectAllGroups,
                onClear = access::clearGroupPick, onDelete = access::askDeletePicked,
            ),
        )
        s.personForm?.let { form ->
            PersonSheet(
                initial = form, groups = access.groups, properties = properties,
                isMe = form.email.equals(me, ignoreCase = true),
                problem = access::personProblem, onSave = access::savePerson,
                onClose = access::closePersonForm, onRemove = access::askRemovePerson,
            )
        }
        s.groupForm?.let { form ->
            GroupSheet(
                initial = form, problem = access::groupProblem, givenAll = access::givenAllProperties,
                onSave = access::saveGroup, onClose = access::closeGroupForm,
                canDelete = form.id.isNotEmpty() && access.canDelete(form), onDelete = access::askDeleteGroup,
            )
        }
        s.groupToConfirm?.let { g ->
            val n = access.givenAllProperties(g)
            BackHandler(onBack = access::cancelGroupConfirm)
            ConfirmDialog(
                Icons.ShieldCheck, C.YTintBg, C.Yellow,
                stringResource(R.string.access_elevate_title), pluralStringResource(R.plurals.access_elevate_notice, n, n),
                stringResource(R.string.action_cancel), stringResource(R.string.access_elevate_confirm),
                access::cancelGroupConfirm, access::confirmGroupSave, yesBg = C.Yellow,
            )
        }
        s.confirmDelete?.let { g ->
            BackHandler { access.askDeleteGroup(null) }
            ConfirmDialog(
                Icons.Trash, C.DangerBg, C.Red,
                stringResource(R.string.access_delete_title, g.name), stringResource(R.string.access_delete_message),
                stringResource(R.string.action_cancel), stringResource(R.string.access_action_delete),
                { access.askDeleteGroup(null) }, { access.deleteGroup(g) },
            )
        }
        if (s.confirmDeletePicked) {
            val n = access.pickedGroups.size
            BackHandler(onBack = access::cancelDeletePicked)
            ConfirmDialog(
                Icons.Trash, C.DangerBg, C.Red,
                pluralStringResource(R.plurals.access_delete_groups_title, n, n), pluralStringResource(R.plurals.access_delete_groups_message, n, n),
                stringResource(R.string.action_cancel), stringResource(R.string.access_action_delete),
                access::cancelDeletePicked, access::deletePicked,
            )
        }
        s.personToRemove?.let { p ->
            BackHandler { access.askRemovePerson(null) }
            ConfirmDialog(
                Icons.Trash, C.DangerBg, C.Red,
                stringResource(R.string.access_remove_title, p.name), stringResource(R.string.access_remove_message, p.email),
                stringResource(R.string.action_cancel), stringResource(R.string.access_action_remove),
                { access.askRemovePerson(null) }, { access.removePerson(p) },
            )
        }
    }
}

/** Ticking groups to delete together: tick or untick one, select all, clear, and delete the ticked ones. */
internal class GroupSelectionActions(
    val onToggle: (Group) -> Unit = {},
    val onSelectAll: () -> Unit = {},
    val onClear: () -> Unit = {},
    val onDelete: () -> Unit = {},
)

/** The page itself, stateless: [list] in, taps out. */
@Composable
internal fun PeopleContent(
    list: AccessListState,
    properties: List<Property>,
    onBack: () -> Unit,
    onTab: (AccessTab) -> Unit,
    onQuery: (String) -> Unit,
    onAdd: () -> Unit,
    onEditPerson: (Person) -> Unit,
    onEditGroup: (Group) -> Unit,
    onGroupFilter: (Permission?) -> Unit = {},
    selection: GroupSelectionActions = GroupSelectionActions(),
) {
    val gutter = pageGutter()
    val onPeople = list.tab == AccessTab.PEOPLE
    val selecting = !onPeople && list.pickedGroups.isNotEmpty()
    Box(Modifier.fillMaxSize().background(C.Bg)) {
        Column(Modifier.fillMaxSize()) {
            SubPageBar(stringResource(R.string.access_title), property = null, onMenu = onBack)
            LazyColumn(
                Modifier.weight(1f).fillMaxWidth().testTag(AccessTags.LIST),
                contentPadding = PaddingValues(start = gutter + 10.dp, end = gutter + 10.dp, top = 10.dp, bottom = 160.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                item { Header(list, onTab, onGroupFilter) }
                if (onPeople) {
                    items(list.people, key = { it.person.id }) { row ->
                        PersonCard(row, properties) { onEditPerson(row.person) }
                    }
                    if (list.people.isEmpty()) item {
                        EmptyText(
                            if (list.query.isBlank()) stringResource(R.string.access_empty_people)
                            else stringResource(R.string.access_no_person_match, list.query.trim()),
                        )
                    }
                } else {
                    items(list.groups, key = { it.group.id }) { row ->
                        GroupCard(
                            row, onEdit = { onEditGroup(row.group) },
                            picked = row.group.id in list.pickedGroups, selecting = selecting,
                            onToggle = { selection.onToggle(row.group) },
                        )
                    }
                    if (list.groups.isEmpty()) item { EmptyText(stringResource(R.string.access_no_group_match, list.query.trim())) }
                }
            }
        }
        // Ticked groups: × / "N selected" / "Select all", and Delete at the end. Replaces the + and search buttons.
        if (selecting) {
            Box(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().readableWidth().testTag(AccessTags.GROUP_DOCK)) {
                ActionDock(onValidate = null, onAssign = null, onSubmit = null, onDelete = selection.onDelete, floating = true) {
                    SelectionSummary(
                        list.pickedGroups.size, list.selectableGroups, list.pickedGroups.size >= list.selectableGroups,
                        selection.onClear, selection.onSelectAll, Modifier.testTag(AccessTags.GROUP_SELECT_ALL),
                    )
                }
            }
        } else Column(
            Modifier.align(Alignment.BottomEnd).navigationBarsPadding().imePadding().padding(end = 14.dp, bottom = 14.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            CircleFab(
                Icons.Plus,
                stringResource(if (onPeople) R.string.access_add_person else R.string.access_new_group),
                onAdd, Modifier.testTag(AccessTags.FAB_ADD),
            )
            SearchFab(
                list.query, onQuery,
                stringResource(if (onPeople) R.string.access_search_people else R.string.access_search_groups),
                stringResource(R.string.action_search), stringResource(R.string.action_close_search),
            )
        }
    }
}

/**
 * The People / Groups tiles (tap to switch) and the tab switch; on Groups also the permission filter.
 * The app bar already names the page, so there's no heading.
 */
@Composable
private fun Header(list: AccessListState, onTab: (AccessTab) -> Unit, onGroupFilter: (Permission?) -> Unit) {
    val onPeople = list.tab == AccessTab.PEOPLE
    Column(Modifier.padding(top = 2.dp, bottom = 7.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        StatRow {
            StatCard(
                Icons.Users, C.Yellow, list.peopleTotal.toString(), stringResource(R.string.access_stat_people),
                Modifier.weight(1f).testTag(AccessTags.TILE_PEOPLE).clickable(role = Role.Tab) { onTab(AccessTab.PEOPLE) },
                valueSize = 17, labelSize = 11,
            )
            StatCard(
                iconOf(Permission.MANAGE_ACCESS.iconPath), GroupsPurple, list.groupsTotal.toString(), stringResource(R.string.access_stat_groups),
                Modifier.weight(1f).testTag(AccessTags.TILE_GROUPS).clickable(role = Role.Tab) { onTab(AccessTab.GROUPS) },
                valueSize = 17, labelSize = 11,
            )
        }
        TabSwitch(
            listOf(
                stringResource(R.string.access_tab_people, list.peopleTotal),
                stringResource(R.string.access_tab_groups, list.groupsTotal),
            ),
            listOf(AccessTags.TAB_PEOPLE, AccessTags.TAB_GROUPS), if (onPeople) 0 else 1,
        ) { onTab(if (it == 0) AccessTab.PEOPLE else AccessTab.GROUPS) }
        if (!onPeople) GroupFilterChips(list.groupFilter, list.groupsTotal, list.permissionCounts, onGroupFilter)
    }
}

private val GroupsPurple = Permission.MANAGE_PROPERTIES.color.asColor()

@Composable
private fun EmptyText(text: String) = Txt(
    text, 13, 400, C.Muted,
    Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 30.dp).testTag(AccessTags.EMPTY),
    maxLines = 3, align = TextAlign.Center,
)
