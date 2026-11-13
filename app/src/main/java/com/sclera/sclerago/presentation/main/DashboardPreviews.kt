package com.sclera.sclerago.presentation.main

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.sclera.sclerago.presentation.preview.SAMPLE_ASSETS
import com.sclera.sclerago.domain.model.AssetListData
import com.sclera.sclerago.domain.model.AssetListQuery
import com.sclera.sclerago.domain.model.LocationFilter
import com.sclera.sclerago.domain.usecase.asset.BuildAssetListUseCase
import com.sclera.sclerago.presentation.assets.AssetListBinding
import com.sclera.sclerago.presentation.assets.assetList
import com.sclera.sclerago.presentation.components.readableWidth
import com.sclera.sclerago.presentation.theme.C
import com.sclera.sclerago.presentation.theme.ScleraTheme

private const val PHONE = "spec:width=390dp,height=844dp"
private const val TABLET_PORTRAIT = "spec:width=800dp,height=1280dp,dpi=240"
private const val TABLET_LANDSCAPE = "spec:width=1280dp,height=800dp,dpi=240"

/** The dashboard's list for the sample assets, filtered by [query]; used by the previews and the UI tests. */
internal fun sampleAssetList(query: String = "", picked: Set<String> = emptySet()): AssetListBinding {
    val data: AssetListData = BuildAssetListUseCase()(
        SAMPLE_ASSETS.filter { it.propertyId == "p1" }, AssetListQuery(sources = null, location = LocationFilter.All, text = query),
    )
    return AssetListBinding(data, picked, query, onChip = {}, onToggle = {}, onOpen = {})
}

/**
 * The dashboard page without its view models: header, list, the + and search buttons (or, while assets are
 * selected, the dock) and the bottom tabs, as the screen puts them together.
 */
@Composable
internal fun DashboardSample(
    list: AssetListBinding,
    selected: LocFilter = LocFilter.ALL,
    searchOpen: Boolean = false,
    onOpen: (String) -> Unit = {},
    onToggle: (String) -> Unit = {},
    onSearchOpenChange: (Boolean) -> Unit = {},
    onQuery: (String) -> Unit = {},
) {
    val binding = AssetListBinding(list.data, list.picked, list.query, list.onChip, onToggle, onOpen)
    Column(Modifier.fillMaxSize().background(C.Bg)) {
        DashboardHeader("Test 861", listOf("building 1", "floor 1", "location1"), selected, {}, {}, {})
        Box(Modifier.weight(1f).fillMaxWidth()) {
            LazyColumn(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) { assetList(binding) }
            if (list.picked.isEmpty()) {
                Box(Modifier.align(Alignment.BottomCenter).readableWidth()) {
                    DashboardFabs(searchOpen, onSearchOpenChange, list.query, onQuery, {}, Modifier.align(Alignment.BottomEnd))
                }
            } else {
                SelectionDock(
                    count = list.picked.size, visible = list.data.items.size, allPicked = list.picked.size == list.data.items.size,
                    onClear = {}, onSelectAll = {}, onValidate = {}, onAssign = {}, onSubmit = {}, onDelete = {},
                    modifier = Modifier.align(Alignment.BottomCenter).readableWidth(),
                )
            }
        }
        DashboardNav(Tab.entries, 2f, {})
    }
}

@Preview(name = "Phone", device = PHONE)
@Preview(name = "Tablet portrait", device = TABLET_PORTRAIT)
@Preview(name = "Tablet landscape", device = TABLET_LANDSCAPE)
@Composable
private fun DashboardPreview() = ScleraTheme { DashboardSample(sampleAssetList()) }

@Preview(name = "Phone", device = PHONE)
@Preview(name = "Tablet portrait", device = TABLET_PORTRAIT)
@Preview(name = "Tablet landscape", device = TABLET_LANDSCAPE)
@Composable
private fun DashboardSelectingPreview() = ScleraTheme { DashboardSample(sampleAssetList(picked = setOf("a1", "a2"))) }

@Preview(name = "Phone", device = PHONE)
@Preview(name = "Tablet portrait", device = TABLET_PORTRAIT)
@Preview(name = "Tablet landscape", device = TABLET_LANDSCAPE)
@Composable
private fun DashboardSearchPreview() = ScleraTheme { DashboardSample(sampleAssetList(query = "pump"), searchOpen = true) }

@Preview(name = "Phone", device = PHONE)
@Preview(name = "Tablet portrait", device = TABLET_PORTRAIT)
@Preview(name = "Tablet landscape", device = TABLET_LANDSCAPE)
@Composable
private fun DashboardEmptySearchPreview() = ScleraTheme { DashboardSample(sampleAssetList(query = "zzz"), searchOpen = true) }

