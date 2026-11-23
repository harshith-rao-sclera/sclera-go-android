package com.sclera.sclerago.presentation.config

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.sclera.sclerago.domain.model.ClassCount
import com.sclera.sclerago.domain.model.FieldCfg
import com.sclera.sclerago.domain.model.FieldKind
import com.sclera.sclerago.domain.model.FieldSection
import com.sclera.sclerago.domain.model.TypeField
import com.sclera.sclerago.domain.usecase.config.visibleRows
import com.sclera.sclerago.presentation.theme.C
import com.sclera.sclerago.presentation.theme.ScleraTheme

private const val PHONE = "spec:width=390dp,height=844dp"
private const val TABLET_PORTRAIT = "spec:width=800dp,height=1280dp,dpi=240"
private const val TABLET_LANDSCAPE = "spec:width=1280dp,height=800dp,dpi=240"

private val SampleTree = listOf(
    ClassCount("Electrical", 9, "e"),
    ClassCount(
        "HVAC", 29, null,
        listOf(
            ClassCount("Exhaust Fan", 4, "h1"),
            ClassCount("Chiller", 2, "h2"),
            ClassCount("Air Handling Unit", 7, "h3", listOf(ClassCount("Rooftop", 3, "h4"))),
        ),
    ),
    ClassCount("Security", 7, "s"),
)

private val SampleExpanded = setOf(listOf("HVAC"))

private val SampleFields = listOf(
    TypeField("Refrigerant", "dropdown", listOf("R410A", "R32", "R134a", "R407C"), position = 1),
    TypeField("Cooling Capacity (kW)", "number", position = 2),
)

private val SampleStandard = listOf(
    FieldCfg("name", "Name", FieldKind.TEXT, FieldSection.IDENT, required = true),
    FieldCfg("manufacturer", "Manufacturer", FieldKind.TEXT, FieldSection.IDENT),
    FieldCfg("tag", "Asset Tag", FieldKind.TEXT, FieldSection.TAGS, required = true),
)

@Composable
private fun ListSample(query: String = "") = ScleraTheme {
    Box(Modifier.fillMaxSize().background(C.Bg)) {
        AssetTypesPage(
            AssetTypesPageState("Test 861", SampleTree.visibleRows(SampleExpanded, query), 63, query, allOpen = false, hasTypes = true),
            AssetTypesPageActions(),
        )
    }
}

@Preview(name = "Phone", device = PHONE)
@Preview(name = "Tablet portrait", device = TABLET_PORTRAIT)
@Preview(name = "Tablet landscape", device = TABLET_LANDSCAPE)
@Composable
private fun AssetTypesPreview() = ListSample()

@Preview(name = "Phone search", device = PHONE)
@Composable
private fun AssetTypesSearchPreview() = ListSample("chill")

@Preview(name = "Phone", device = PHONE)
@Preview(name = "Tablet portrait", device = TABLET_PORTRAIT)
@Preview(name = "Tablet landscape", device = TABLET_LANDSCAPE)
@Composable
private fun AssetTypeDetailPreview() = ScleraTheme {
    Box(Modifier.fillMaxSize().background(C.Bg)) {
        AssetTypeDetailSheet(
            AssetTypeDetailState(listOf("HVAC", "Chiller"), SampleTree[1].kids[1], SampleFields, LoadState.READY, SampleStandard),
            onOpenSub = {}, onClose = {},
        )
    }
}

@Preview(name = "Phone with sub types", device = PHONE)
@Composable
private fun AssetTypeDetailSubTypesPreview() = ScleraTheme {
    Box(Modifier.fillMaxSize().background(C.Bg)) {
        AssetTypeDetailSheet(
            AssetTypeDetailState(listOf("HVAC"), SampleTree[1], emptyList(), LoadState.READY, SampleStandard),
            onOpenSub = {}, onClose = {},
        )
    }
}
