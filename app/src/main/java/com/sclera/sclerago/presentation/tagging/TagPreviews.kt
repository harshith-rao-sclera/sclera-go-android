package com.sclera.sclerago.presentation.tagging

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.sclera.sclerago.domain.model.TagKind
import com.sclera.sclerago.presentation.theme.C
import com.sclera.sclerago.presentation.theme.ScleraTheme

private const val PHONE = "spec:width=390dp,height=844dp"
private const val TABLET_PORTRAIT = "spec:width=800dp,height=1280dp,dpi=240"
private const val TABLET_LANDSCAPE = "spec:width=1280dp,height=800dp,dpi=240"

private val NoActions = TagFlowActions({}, { _, _ -> }, {}, { _, _ -> }, {}, {}, {}, {})

@Composable
private fun Section() = ScleraTheme {
    Box(Modifier.fillMaxSize().background(C.Surface)) { TagsBar(TagUiState(tagged = setOf(TagKind.QR, TagKind.BARCODE)), TagsActions()) }
}

@Composable
private fun Placement() = ScleraTheme {
    Box(Modifier.fillMaxSize().background(C.Surface)) { TagsBar(TagUiState(phase = TagPhase.ChoosingPlacement), TagsActions()) }
}

@Composable
private fun Flow(state: TagUiState) = ScleraTheme { Box(Modifier.fillMaxSize().background(C.Bg)) { TagFlowHost(state, NoActions) } }

@Preview(name = "Tags - phone", device = PHONE) @Composable private fun TagRowsPhone() = Section()
@Preview(name = "Tags - tablet portrait", device = TABLET_PORTRAIT) @Composable private fun TagRowsTabletPortrait() = Section()
@Preview(name = "Tags - tablet landscape", device = TABLET_LANDSCAPE) @Composable private fun TagRowsTabletLandscape() = Section()

@Preview(name = "QR placement - phone", device = PHONE) @Composable private fun PlacementPhone() = Placement()
@Preview(name = "QR placement - tablet portrait", device = TABLET_PORTRAIT) @Composable private fun PlacementTabletPortrait() = Placement()
@Preview(name = "QR placement - tablet landscape", device = TABLET_LANDSCAPE) @Composable private fun PlacementTabletLandscape() = Placement()

@Preview(name = "Rescan - phone", device = PHONE) @Composable private fun RescanPhone() = Flow(TagUiState(phase = TagPhase.ConfirmingRescan))
@Preview(name = "Rescan - tablet portrait", device = TABLET_PORTRAIT) @Composable private fun RescanTabletPortrait() = Flow(TagUiState(phase = TagPhase.ConfirmingRescan))
@Preview(name = "Rescan - tablet landscape", device = TABLET_LANDSCAPE) @Composable private fun RescanTabletLandscape() = Flow(TagUiState(phase = TagPhase.ConfirmingRescan))

@Preview(name = "NFC - phone", device = PHONE) @Composable private fun NfcPhone() = Flow(TagUiState(phase = TagPhase.ReadingNfc))
@Preview(name = "NFC - tablet portrait", device = TABLET_PORTRAIT) @Composable private fun NfcTabletPortrait() = Flow(TagUiState(phase = TagPhase.ReadingNfc))
@Preview(name = "NFC - tablet landscape", device = TABLET_LANDSCAPE) @Composable private fun NfcTabletLandscape() = Flow(TagUiState(phase = TagPhase.ReadingNfc))

@Preview(name = "Retag - phone", device = PHONE) @Composable private fun RetagPhone() = Flow(TagUiState(phase = TagPhase.ConfirmingMove("Pump · KSB Etanorm")))
@Preview(name = "Retag - tablet landscape", device = TABLET_LANDSCAPE) @Composable private fun RetagTabletLandscape() = Flow(TagUiState(phase = TagPhase.ConfirmingMove("Pump · KSB Etanorm")))

// The QR and barcode scanner screens show the live camera, so they have no preview.
