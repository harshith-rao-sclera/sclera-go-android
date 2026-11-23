package com.sclera.sclerago.presentation.config

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.sclera.sclerago.R
import com.sclera.sclerago.domain.model.FieldCfg
import com.sclera.sclerago.domain.usecase.config.TypeRow
import com.sclera.sclerago.presentation.components.CfgCard
import com.sclera.sclerago.presentation.components.EmptyNote
import com.sclera.sclerago.presentation.components.SearchField
import com.sclera.sclerago.presentation.components.pageGutter
import com.sclera.sclerago.presentation.components.topBorder
import com.sclera.sclerago.presentation.icons.Icons
import com.sclera.sclerago.presentation.icons.LineIcon
import com.sclera.sclerago.presentation.theme.C
import com.sclera.sclerago.presentation.theme.Txt

/** Test tags of Configuration → Asset types. */
object AssetTypesTags {
    const val SEARCH = "types_search"
    const val EXPAND_ALL = "types_expand_all"
    const val DETAIL = "type_detail"
    fun row(path: List<String>) = "type_row_" + path.joinToString("/")
    fun toggle(path: List<String>) = "type_toggle_" + path.joinToString("/")
}

/** The Asset types tab of Configuration; [standardFields] are the property's standard fields every type's form also has. */
@Composable
internal fun AssetTypesTab(propName: String, standardFields: List<FieldCfg>, vm: AssetTypesViewModel = hiltViewModel()) {
    AssetTypesPage(
        AssetTypesPageState(propName, vm.rows, vm.totalAssets, vm.query, vm.allOpen, hasTypes = vm.tree.isNotEmpty()),
        AssetTypesPageActions(vm::search, vm::toggle, vm::toggleAll, vm::open),
    )
    val open = vm.detail
    val path = vm.selected
    if (open != null && path != null) {
        AssetTypeDetailSheet(
            AssetTypeDetailState(path, open, vm.fields, vm.fieldsLoad, standardFields.filter { it.visible }),
            onOpenSub = { sub -> vm.open(path + sub) },
            onClose = vm::close,
        )
    }
}

internal class AssetTypesPageState(
    val propName: String,
    val rows: List<TypeRow>,
    val totalAssets: Int,
    val query: String,
    val allOpen: Boolean,
    val hasTypes: Boolean,
)

internal class AssetTypesPageActions(
    val onSearch: (String) -> Unit = {},
    val onToggle: (List<String>) -> Unit = {},
    val onToggleAll: () -> Unit = {},
    val onOpen: (List<String>) -> Unit = {},
)

/** The type list: intro, search, and the tree as one card, each type a row that opens its detail. */
@Composable
internal fun AssetTypesPage(state: AssetTypesPageState, actions: AssetTypesPageActions) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = pageGutter()).padding(start = 8.dp, end = 8.dp, top = 12.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Txt(stringResource(R.string.cfgt_types_intro, state.propName), 13, 400, C.Muted, maxLines = Int.MAX_VALUE)
        Txt(stringResource(R.string.cfgt_types_readonly), 12, 400, C.Muted, maxLines = Int.MAX_VALUE)
        SearchField(state.query, actions.onSearch, stringResource(R.string.cfgt_types_search), Modifier.testTag(AssetTypesTags.SEARCH))
        CfgCard {
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Txt(stringResource(R.string.cfgt_types_title), 15, 700)
                    Txt(stringResource(R.string.cfgt_types_assets_count, state.totalAssets), 11, 400, C.Muted)
                }
                if (state.hasTypes && state.query.isBlank()) {
                    Box(
                        Modifier.heightIn(min = 44.dp).clickable(onClick = actions.onToggleAll).testTag(AssetTypesTags.EXPAND_ALL),
                        contentAlignment = Alignment.Center,
                    ) {
                        Txt(stringResource(if (state.allOpen) R.string.cfgt_types_collapse_all else R.string.cfgt_types_expand_all), 12, 600, C.Yellow)
                    }
                }
            }
            if (state.rows.isEmpty()) {
                Box(Modifier.topBorder(C.Border).padding(vertical = 12.dp)) {
                    EmptyNote(stringResource(if (state.hasTypes) R.string.cfgt_types_no_match else R.string.cfgt_types_empty))
                }
            }
            state.rows.forEach { TypeRowItem(it, actions) }
        }
    }
}

@Composable
private fun TypeRowItem(row: TypeRow, actions: AssetTypesPageActions) {
    val top = row.depth == 0
    Row(
        Modifier.fillMaxWidth().topBorder(C.Border).heightIn(min = 44.dp).padding(start = (6 + row.depth * 18).dp, end = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // The arrow opens or closes the types under this one; a type without any keeps the space.
        val toggleLabel = stringResource(if (row.open) R.string.cfgt_types_collapse else R.string.cfgt_types_expand, row.name)
        Box(
            Modifier.size(width = 28.dp, height = 44.dp).then(
                if (row.hasKids) Modifier.clickable { actions.onToggle(row.path) }.semantics { contentDescription = toggleLabel }.testTag(AssetTypesTags.toggle(row.path))
                else Modifier,
            ),
            contentAlignment = Alignment.Center,
        ) {
            if (row.hasKids) LineIcon(Icons.ChevronRight, 11.dp, C.Muted, 3.2f, Modifier.rotate(if (row.open) 90f else 0f))
        }
        Row(
            Modifier.weight(1f).heightIn(min = 44.dp).clickable { actions.onOpen(row.path) }.testTag(AssetTypesTags.row(row.path)),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Txt(row.name, if (top) 15 else 14, if (top) 600 else 400, modifier = Modifier.weight(1f), maxLines = 2)
            Txt(row.assets.toString(), 12, 600, C.Muted)
            LineIcon(Icons.ChevronRight, 10.dp, C.Dim, 3f)
        }
    }
}
