package com.sclera.sclerago.presentation.property

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.sclera.sclerago.domain.model.Property
import com.sclera.sclerago.domain.model.PropertyDownload
import com.sclera.sclerago.domain.model.PropertyDownloads
import com.sclera.sclerago.domain.model.PropertySummary
import com.sclera.sclerago.presentation.theme.ScleraTheme

private const val PHONE = "spec:width=390dp,height=844dp"
private const val TABLET_PORTRAIT = "spec:width=800dp,height=1280dp,dpi=240"
private const val TABLET_LANDSCAPE = "spec:width=1280dp,height=800dp,dpi=240"

internal val SamplePropertyState = PropertyListState(
    rows = listOf("Test 861" to 25, "Dhanush 16/10 2.1.0" to 0, "Post production 12/10" to 0, "Harbour Point Office Tower" to 3)
        .mapIndexed { i, (name, assets) -> PropertySummary(Property("p${i + 1}", name, false), assets) },
    propertyTotal = 4,
)

@Preview(name = "Phone", device = PHONE)
@Preview(name = "Tablet portrait", device = TABLET_PORTRAIT)
@Preview(name = "Tablet landscape", device = TABLET_LANDSCAPE)
@Composable
private fun PropertyListPreview() {
    ScleraTheme {
        PropertyListContent(SamplePropertyState, "p1", onBack = {}, onQuery = {}, onOpen = {}, onAdd = {}, onImport = {})
    }
}

/** One property downloading (percent, MB and the bottom edge), one unzipping (icon and the sliding edge), two downloaded. */
@Preview(name = "Phone", device = PHONE)
@Preview(name = "Tablet portrait", device = TABLET_PORTRAIT)
@Preview(name = "Tablet landscape", device = TABLET_LANDSCAPE)
@Composable
private fun PropertyListDownloadsPreview() {
    val downloads = PropertyDownloads(
        currentId = "p1",
        downloadedIds = setOf("p1", "p4"),
        active = mapOf("p2" to PropertyDownload.Running(0.42f, 5.2, 12.4), "p3" to PropertyDownload.Unpacking),
        restored = true,
    )
    ScleraTheme {
        PropertyListContent(SamplePropertyState.copy(downloads = downloads), "p1", onBack = {}, onQuery = {}, onOpen = {}, onAdd = {}, onImport = {})
    }
}

@Preview(name = "Phone", device = PHONE)
@Preview(name = "Tablet portrait", device = TABLET_PORTRAIT)
@Preview(name = "Tablet landscape", device = TABLET_LANDSCAPE)
@Composable
private fun PropertyListNoMatchPreview() {
    ScleraTheme {
        PropertyListContent(
            PropertyListState(query = "zzz", propertyTotal = 4), "p1",
            onBack = {}, onQuery = {}, onOpen = {}, onAdd = {}, onImport = {},
        )
    }
}

/** First open with nothing saved: a loader (and no count) until the server answers. */
@Preview(name = "Phone", device = PHONE)
@Preview(name = "Tablet portrait", device = TABLET_PORTRAIT)
@Preview(name = "Tablet landscape", device = TABLET_LANDSCAPE)
@Composable
private fun PropertyListLoadingPreview() {
    ScleraTheme {
        PropertyListContent(PropertyListState(loading = true), "", onBack = {}, onQuery = {}, onOpen = {}, onAdd = {}, onImport = {})
    }
}

/** The server answered with no properties. */
@Preview(name = "Phone", device = PHONE)
@Preview(name = "Tablet portrait", device = TABLET_PORTRAIT)
@Preview(name = "Tablet landscape", device = TABLET_LANDSCAPE)
@Composable
private fun PropertyListEmptyPreview() {
    ScleraTheme {
        PropertyListContent(PropertyListState(), "", onBack = {}, onQuery = {}, onOpen = {}, onAdd = {}, onImport = {})
    }
}

@Preview(name = "Phone", device = PHONE)
@Preview(name = "Tablet portrait", device = TABLET_PORTRAIT)
@Preview(name = "Tablet landscape", device = TABLET_LANDSCAPE)
@Composable
private fun ImportSheetPreview() {
    ScleraTheme { ImportSheetContent(null, -1, null, onClose = {}, onChoose = {}, onSample = {}, onProceed = {}) }
}

@Preview(name = "Phone", device = PHONE)
@Preview(name = "Tablet portrait", device = TABLET_PORTRAIT)
@Preview(name = "Tablet landscape", device = TABLET_LANDSCAPE)
@Composable
private fun ImportSheetWithFilePreview() {
    ScleraTheme { ImportSheetContent("properties.xlsx", 48_000, null, onClose = {}, onChoose = {}, onSample = {}, onProceed = {}) }
}

@Preview(name = "Phone", device = PHONE)
@Preview(name = "Tablet portrait", device = TABLET_PORTRAIT)
@Preview(name = "Tablet landscape", device = TABLET_LANDSCAPE)
@Composable
private fun AddPropertySheetPreview() {
    ScleraTheme { AddPropertySheet(onClose = {}, onAdd = { null }) }
}
