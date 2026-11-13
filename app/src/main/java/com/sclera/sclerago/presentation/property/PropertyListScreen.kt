package com.sclera.sclerago.presentation.property

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sclera.sclerago.R
import com.sclera.sclerago.presentation.common.asString
import com.sclera.sclerago.presentation.components.CircleFab
import com.sclera.sclerago.presentation.components.ImportStrip
import com.sclera.sclerago.presentation.components.SearchFab
import com.sclera.sclerago.presentation.components.StatCard
import com.sclera.sclerago.presentation.components.StatRow
import com.sclera.sclerago.presentation.components.SubPageBar
import com.sclera.sclerago.presentation.components.Toast
import com.sclera.sclerago.presentation.components.pageGutter
import com.sclera.sclerago.presentation.components.r
import com.sclera.sclerago.presentation.icons.Icons
import com.sclera.sclerago.presentation.icons.LineIcon
import com.sclera.sclerago.presentation.main.AppViewModel
import com.sclera.sclerago.presentation.theme.C
import com.sclera.sclerago.presentation.theme.Txt

/** Test tags of the Property list. */
object PropertyTags {
    const val LIST = "property_list"
    const val ADD = "property_add"
    const val IMPORT = "property_import"
    const val EMPTY = "property_empty"
    const val COUNT = "property_count"
    fun row(id: String) = "property_row_$id"
}

/** Property list (side menu): counts, import, search, open a property, add one. */
@Composable
fun PropertyListScreen(app: AppViewModel, vm: PropertyViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    BackHandler(onBack = app::closeAdmin)
    DisposableEffect(Unit) { onDispose(vm::reset) }
    val notice = state.notice?.asString()
    LaunchedEffect(notice) {
        if (notice != null) {
            app.flash(notice)
            vm.consumeNotice()
        }
    }
    Box(Modifier.fillMaxSize()) {
        PropertyListContent(
            state, app.state.prop,
            onBack = app::openMenu, onQuery = vm::setQuery, onOpen = app::openPropertyFromList,
            onAdd = vm::openAdd, onImport = vm::openImport,
        )
        AnimatedVisibility(
            app.state.toastOn && !state.addOpen,
            Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 72.dp),
            enter = fadeIn(), exit = fadeOut(),
        ) { Toast(app.state.toast.asString()) }
        if (state.importOpen) ImportSheet(vm::closeImport, vm::importFile, vm::importProblem, vm::writeSample)
        if (state.addOpen) AddPropertySheet(state.properties, vm::closeAdd, vm::addProperty)
    }
}

/** The page itself, stateless: [state] in, taps out. */
@Composable
internal fun PropertyListContent(
    state: PropertyListState,
    currentId: String,
    onBack: () -> Unit,
    onQuery: (String) -> Unit,
    onOpen: (String) -> Unit,
    onAdd: () -> Unit,
    onImport: () -> Unit,
) {
    val gutter = pageGutter()
    Box(Modifier.fillMaxSize().background(C.Bg)) {
        Column(Modifier.fillMaxSize()) {
            SubPageBar(stringResource(R.string.property_list_title), property = null, onMenu = onBack)
            LazyColumn(
                Modifier.weight(1f).fillMaxWidth().testTag(PropertyTags.LIST),
                contentPadding = PaddingValues(start = gutter + 10.dp, end = gutter + 10.dp, top = 10.dp, bottom = 160.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                item { Header(state, onImport) }
                items(state.rows, key = { it.property.id }) { row ->
                    PropertyCard(row.property.id, row.property.name, row.property.id == currentId) { onOpen(row.property.id) }
                }
                if (state.rows.isEmpty()) item {
                    Txt(
                        stringResource(R.string.property_none_match, state.query.trim()), 13, 400, C.Muted,
                        Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 30.dp).testTag(PropertyTags.EMPTY),
                        maxLines = 3, align = TextAlign.Center,
                    )
                }
            }
        }
        Column(
            Modifier.align(Alignment.BottomEnd).navigationBarsPadding().imePadding().padding(end = 14.dp, bottom = 14.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            CircleFab(Icons.Plus, stringResource(R.string.property_add_fab), onAdd, Modifier.testTag(PropertyTags.ADD))
            SearchFab(
                state.query, onQuery, stringResource(R.string.property_list_search_hint),
                stringResource(R.string.action_search), stringResource(R.string.action_close_search),
            )
        }
    }
}

/** The Properties / Assets tiles and the import strip (the app bar already names the page). */
@Composable
private fun Header(state: PropertyListState, onImport: () -> Unit) {
    Column(Modifier.padding(bottom = 7.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        StatRow {
            StatCard(
                Icons.Building, C.Yellow, state.propertyTotal.toString(), stringResource(R.string.label_properties),
                Modifier.weight(1f).testTag(PropertyTags.COUNT), valueSize = 17, labelSize = 11,
            )
        }
        ImportStrip(
            stringResource(R.string.property_import_strip), stringResource(R.string.property_import_format), onImport,
            Modifier.testTag(PropertyTags.IMPORT), labelSize = 13, formatSize = 11,
        )
    }
}

/** A property: round building icon and name. The current one is marked for accessibility only. */
@Composable
private fun PropertyCard(id: String, name: String, current: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 60.dp).clip(r(12)).background(C.Surface).border(1.dp, C.Border, r(12))
            .semantics { selected = current }.testTag(PropertyTags.row(id))
            .clickable(role = Role.Button, onClick = onClick).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(C.Raised).border(1.dp, C.Border, CircleShape),
            contentAlignment = Alignment.Center,
        ) { LineIcon(Icons.Building, 17.dp, C.Muted, 1.9f) }
        Txt(name, 14, 600, modifier = Modifier.weight(1f), maxLines = 2)
        LineIcon(Icons.ChevronRight, 13.dp, C.Dim, 2.8f)
    }
}
