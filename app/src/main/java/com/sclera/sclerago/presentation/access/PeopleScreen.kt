package com.sclera.sclerago.presentation.access

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.foundation.lazy.rememberLazyListState
import kotlinx.coroutines.flow.first
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.sclera.sclerago.R
import com.sclera.sclerago.domain.model.DirectoryPerson
import com.sclera.sclerago.domain.model.Group
import com.sclera.sclerago.domain.model.Person
import com.sclera.sclerago.domain.model.Property
import com.sclera.sclerago.presentation.common.asString
import com.sclera.sclerago.presentation.components.CircleFab
import com.sclera.sclerago.presentation.components.ConfirmDialog
import com.sclera.sclerago.presentation.components.ListLoader
import com.sclera.sclerago.presentation.components.PullRefresh
import com.sclera.sclerago.presentation.components.SearchFab
import com.sclera.sclerago.presentation.components.SecureWindow
import com.sclera.sclerago.presentation.components.SubPageBar
import com.sclera.sclerago.presentation.components.pageGutter
import com.sclera.sclerago.presentation.icons.Icons
import com.sclera.sclerago.presentation.main.AppViewModel
import com.sclera.sclerago.presentation.theme.C
import com.sclera.sclerago.presentation.theme.Txt

/** People and access (side menu → Users), as the web's Users page: people, the group each is in, and what groups can do. */
@Composable
fun PeopleScreen(
    vm: AppViewModel,
    access: AccessViewModel = hiltViewModel(),
) {
    BackHandler(onBack = vm::closeAdmin)
    SecureWindow() // emails and who can open which property are shown here
    val s = access.state
    val notice = s.notice?.asString()
    LaunchedEffect(notice) {
        if (notice != null) {
            vm.flash(notice)
            access.consumeNotice()
        }
    }
    val properties = vm.properties

    // Permission names, descriptions and order come from the server, as on the web.
    CompositionLocalProvider(LocalPermissionInfo provides access.permissionInfo) {
        Box(Modifier.fillMaxSize()) {
            PeopleContent(
                list = access.listState(), properties = properties,
                onBack = vm::openMenu, onTab = access::setTab, onQuery = access::setQuery,
                onAdd = { if (s.tab == AccessTab.PEOPLE) access.newPerson() else access.newGroup() },
                onEditPerson = access::editPerson, onEditGroup = access::editGroup,
                onRefresh = access::refresh, onInvite = access::newPerson, onMoreOthers = access::moreOthers,
            )
            s.personForm?.let { form ->
                PersonSheet(
                    initial = form, groups = access.groups, properties = properties,
                    problem = access::personProblem, onSave = access::savePerson, onClose = access::closePersonForm,
                    suggestions = access.suggestions, suggestionHint = access.suggestionHint,
                    onEmailTyped = access::suggest, onSuggestionPicked = access::clearSuggestions,
                )
            }
            s.groupForm?.let { form ->
                GroupSheet(
                    initial = form, problem = access::groupProblem, onSave = access::saveGroup, onClose = access::closeGroupForm,
                    // As on the web, any group but Admins can be deleted (the server refuses one with people in it).
                    canDelete = form.id.isNotEmpty() && !form.locked, onDelete = access::askDeleteGroup,
                )
            }
            s.confirmDelete?.let { g ->
                BackHandler { access.askDeleteGroup(null) }
                ConfirmDialog(
                    Icons.Trash, C.DangerBg, C.Red,
                    stringResource(R.string.access_delete_title, g.name), stringResource(R.string.access_delete_has_people),
                    stringResource(R.string.action_cancel), stringResource(R.string.access_action_delete),
                    { access.askDeleteGroup(null) }, { access.deleteGroup(g) },
                )
            }
        }
    }
}

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
    onRefresh: () -> Unit = {},
    onInvite: (DirectoryPerson) -> Unit = {},
    onMoreOthers: () -> Unit = {},
) {
    val gutter = pageGutter()
    val onPeople = list.tab == AccessTab.PEOPLE
    val scroll = rememberLazyListState()
    // Scrolling near the end of the Users list loads the directory's next page by itself (no "Show more" button).
    // Keyed on the list and its loading, so a page that still leaves the end in view asks for the next one.
    val loadMore by rememberUpdatedState(onMoreOthers)
    LaunchedEffect(scroll, onPeople, list.others.size, list.othersHasMore, list.othersLoading) {
        if (!onPeople || !list.othersHasMore || list.othersLoading) return@LaunchedEffect
        snapshotFlow {
            val info = scroll.layoutInfo
            (info.visibleItemsInfo.lastOrNull()?.index ?: 0) >= info.totalItemsCount - LoadAhead
        }.first { it }
        loadMore()
    }
    Box(Modifier.fillMaxSize().background(C.Bg)) {
        Column(Modifier.fillMaxSize()) {
            SubPageBar(stringResource(R.string.access_title), property = null, onMenu = onBack)
            // The first read shows its own loader, so a pull waits until it is done.
            PullRefresh(list.refreshing, onRefresh, Modifier.weight(1f).fillMaxWidth(), enabled = !list.loading) {
                LazyColumn(
                    Modifier.fillMaxSize().testTag(AccessTags.LIST),
                    state = scroll,
                    contentPadding = PaddingValues(start = gutter + 10.dp, end = gutter + 10.dp, top = 10.dp, bottom = 160.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    item { Header(list, onTab) }
                    val unavailable = list.unavailable
                    when {
                        // First read from the server with nothing saved yet: a loader, not an empty list that fills suddenly.
                        list.loading -> item { ListLoader(stringResource(R.string.access_loading), Modifier.testTag(AccessTags.LOADING)) }
                        unavailable != null -> item {
                            Blank(stringResource(R.string.access_unavailable), unavailable.asString(), Modifier.testTag(AccessTags.UNAVAILABLE))
                        }
                        onPeople -> {
                            items(list.people, key = { it.person.id }) { row ->
                                PersonCard(row, properties, list.nowMillis) { onEditPerson(row.person) }
                            }
                            // Everyone else in the organisation, as Sclera lists them: no access here until added.
                            items(list.others, key = { "directory-${it.email}" }) { person ->
                                DirectoryCard(person, canAdd = list.canAdd) { onInvite(person) }
                            }
                            if (list.othersLoading || list.othersReason != null) item { OthersFooter(list) }
                            if (list.people.isEmpty() && list.others.isEmpty()) item {
                                val searching = list.query.isNotBlank()
                                Blank(
                                    stringResource(if (searching) R.string.access_nobody_by_name else R.string.access_nobody_yet),
                                    stringResource(if (searching) R.string.access_nobody_by_name_hint else R.string.access_nobody_yet_hint),
                                    Modifier.testTag(AccessTags.EMPTY),
                                )
                            }
                        }
                        else -> items(list.groups, key = { it.group.id }) { row ->
                            GroupCard(row) { onEditGroup(row.group) }
                        }
                    }
                }
            }
        }
        // Add person / New group (only when it can be used, as the web's button); the web searches people only.
        Column(
            Modifier.align(Alignment.BottomEnd).navigationBarsPadding().imePadding().padding(end = 14.dp, bottom = 14.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (list.canAdd) {
                CircleFab(
                    Icons.Plus,
                    stringResource(if (onPeople) R.string.access_add_person else R.string.access_new_group),
                    onAdd, Modifier.testTag(AccessTags.FAB_ADD),
                )
            }
            if (onPeople) {
                SearchFab(
                    list.query, onQuery, stringResource(R.string.access_search_people),
                    stringResource(R.string.action_search), stringResource(R.string.action_close_search),
                )
            }
        }
    }
}

/**
 * What the page is for and the People / Groups tab switch (the app bar names the page). The tabs count people and
 * groups once there are some, as the web's do.
 */
@Composable
private fun Header(list: AccessListState, onTab: (AccessTab) -> Unit) {
    val onPeople = list.tab == AccessTab.PEOPLE
    Column(Modifier.padding(top = 2.dp, bottom = 7.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Txt(stringResource(R.string.access_lead), 12, 400, C.Muted, maxLines = 4)
        TabSwitch(
            listOf(
                if (list.peopleTotal > 0) stringResource(R.string.access_tab_people, list.peopleTotal) else stringResource(R.string.access_stat_people),
                if (list.groupsTotal > 0) stringResource(R.string.access_tab_groups, list.groupsTotal) else stringResource(R.string.access_stat_groups),
            ),
            listOf(AccessTags.TAB_PEOPLE, AccessTags.TAB_GROUPS), if (onPeople) 0 else 1,
        ) { onTab(if (it == 0) AccessTab.PEOPLE else AccessTab.GROUPS) }
    }
}


/** A bold line over a muted one, centred: nobody yet, nobody by that name, or why the page isn't available. */
@Composable
private fun Blank(title: String, body: String, modifier: Modifier) {
    Column(
        modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 30.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Txt(title, 14, 600, align = TextAlign.Center)
        Txt(body, 13, 400, C.Muted, maxLines = 4, align = TextAlign.Center)
    }
}

/** Under the directory's people: that more are loading, and why they couldn't be listed. */
@Composable
private fun OthersFooter(list: AccessListState) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (list.othersLoading) Txt(stringResource(R.string.access_loading_users), 12, 400, C.Muted, Modifier.testTag(AccessTags.LOADING_USERS))
        list.othersReason?.let { Txt(stringResource(R.string.access_others_unavailable, it.asString()), 12, 400, C.Muted, maxLines = 4) }
    }
}

/** How many rows before the end of the Users list the next page of the directory is asked for. */
private const val LoadAhead = 5
