package com.sclera.sclerago.presentation.tagging

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sclera.sclerago.R
import com.sclera.sclerago.domain.model.TagKind
import com.sclera.sclerago.presentation.common.UiText
import com.sclera.sclerago.presentation.theme.ScleraTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

private const val PHONE = "w411dp-h891dp"
private const val TABLET_PORTRAIT = "w800dp-h1280dp"
private const val TABLET_LANDSCAPE = "w1280dp-h800dp"

/** The Tags section rows and the tagging flow's screens, sheets and dialogs on phone and both tablet orientations. */
@RunWith(AndroidJUnit4::class)
class TagScreensTest {
    @get:Rule
    val compose = createComposeRule()

    private val calls = mutableListOf<String>()

    private fun flowActions() = TagFlowActions(
        onCancel = { calls += "cancel" },
        onScan = { kind, _ -> calls += "scan-$kind" },
        onScannerFailure = { calls += "scanner-$it" },
        onNfcRead = { _, _ -> calls += "nfc-read" },
        onNfcFailure = { calls += "nfc-$it" },
        onConfirmRescan = { calls += "rescan" },
        onConfirmMove = { calls += "move" },
        onToastShown = { calls += "toast-shown" },
    )

    private fun host(state: TagUiState) = compose.setContent { ScleraTheme { Box(androidx.compose.ui.Modifier.fillMaxSize()) { TagFlowHost(state, flowActions()) } } }

    private fun checkBar() {
        val started = mutableListOf<TagKind>()
        compose.setContent {
            ScleraTheme { TagsBar(TagUiState(tagged = setOf(TagKind.QR)), TagsActions(onStart = { started += it })) }
        }
        compose.onNodeWithTag(TagTags.BAR).assertIsDisplayed()
        compose.onNodeWithTag(TagTags.icon(TagKind.BARCODE)).performClick()
        compose.onNodeWithTag(TagTags.icon(TagKind.NFC)).performClick()
        compose.onNodeWithTag(TagTags.icon(TagKind.QR)).performClick()
        assertEquals(listOf(TagKind.BARCODE, TagKind.NFC, TagKind.QR), started)
    }

    private fun checkRescanSheet() {
        host(TagUiState(phase = TagPhase.ConfirmingRescan))
        compose.onNodeWithTag(TagTags.SHEET_PRIMARY).assertIsDisplayed().performClick()
        compose.onNodeWithTag(TagTags.SHEET_SECONDARY).performClick()
        assertEquals(listOf("rescan", "cancel"), calls)
    }

    private fun checkMoveSheet() {
        host(TagUiState(phase = TagPhase.ConfirmingMove("Pump")))
        compose.onNodeWithTag(TagTags.SHEET_PRIMARY).performClick()
        assertEquals(listOf("move"), calls)
    }

    private fun checkRefusedToast() {
        host(TagUiState(notice = TagNotice.Refused(UiText.Res(R.string.tag_error_generic))))
        compose.onNodeWithTag(TagTags.TOAST).assertIsDisplayed()
        compose.mainClock.advanceTimeBy(5_000)
        assertTrue("toast-shown" in calls)
    }

    private fun checkScanScreen(kind: TagKind) {
        host(TagUiState(phase = TagPhase.Scanning(kind)))
        compose.onNodeWithTag(TagTags.SCREEN).assertIsDisplayed()
        compose.onNodeWithTag(TagTags.TORCH).assertIsDisplayed().performClick()
        compose.onNodeWithTag(TagTags.CLOSE).performClick()
        assertEquals(listOf("cancel"), calls)
    }

    private fun checkNfcDialog() {
        host(TagUiState(phase = TagPhase.ReadingNfc))
        compose.onNodeWithTag(TagTags.NFC_DIALOG).assertIsDisplayed()
        compose.onNodeWithTag(TagTags.NFC_CANCEL).performClick()
        assertTrue("cancel" in calls)
    }

    private fun checkWorkingAndToast() {
        host(TagUiState(phase = TagPhase.Working, notice = TagNotice.Tagged(TagKind.QR)))
        compose.onNodeWithTag(TagTags.WORKING).assertIsDisplayed()
        compose.onNodeWithTag(TagTags.TOAST).assertIsDisplayed()
        compose.mainClock.advanceTimeBy(3_000)
        assertTrue("toast-shown" in calls)
    }

    @Test @Config(qualifiers = PHONE) fun barOnPhone() = checkBar()
    @Test @Config(qualifiers = TABLET_PORTRAIT) fun barOnTabletPortrait() = checkBar()
    @Test @Config(qualifiers = TABLET_LANDSCAPE) fun barOnTabletLandscape() = checkBar()

    @Test @Config(qualifiers = PHONE) fun rescanOnPhone() = checkRescanSheet()
    @Test @Config(qualifiers = TABLET_PORTRAIT) fun rescanOnTabletPortrait() = checkRescanSheet()
    @Test @Config(qualifiers = TABLET_LANDSCAPE) fun rescanOnTabletLandscape() = checkRescanSheet()

    @Test @Config(qualifiers = PHONE) fun moveOnPhone() = checkMoveSheet()
    @Test @Config(qualifiers = TABLET_LANDSCAPE) fun moveOnTabletLandscape() = checkMoveSheet()

    @Test @Config(qualifiers = PHONE) fun refusedToastOnPhone() = checkRefusedToast()
    @Test @Config(qualifiers = TABLET_PORTRAIT) fun refusedToastOnTabletPortrait() = checkRefusedToast()

    @Test @Config(qualifiers = PHONE) fun qrScannerOnPhone() = checkScanScreen(TagKind.QR)
    @Test @Config(qualifiers = TABLET_PORTRAIT) fun qrScannerOnTabletPortrait() = checkScanScreen(TagKind.QR)
    @Test @Config(qualifiers = TABLET_LANDSCAPE) fun qrScannerOnTabletLandscape() = checkScanScreen(TagKind.QR)
    @Test @Config(qualifiers = PHONE) fun barcodeScannerOnPhone() = checkScanScreen(TagKind.BARCODE)
    @Test @Config(qualifiers = TABLET_LANDSCAPE) fun barcodeScannerOnTabletLandscape() = checkScanScreen(TagKind.BARCODE)

    @Test @Config(qualifiers = PHONE) fun nfcOnPhone() = checkNfcDialog()
    @Test @Config(qualifiers = TABLET_PORTRAIT) fun nfcOnTabletPortrait() = checkNfcDialog()
    @Test @Config(qualifiers = TABLET_LANDSCAPE) fun nfcOnTabletLandscape() = checkNfcDialog()

    @Test @Config(qualifiers = PHONE) fun workingAndToastOnPhone() = checkWorkingAndToast()
    @Test @Config(qualifiers = TABLET_LANDSCAPE) fun workingAndToastOnTabletLandscape() = checkWorkingAndToast()

    @Test
    fun `idle shows nothing`() {
        host(TagUiState())
        compose.onNodeWithTag(TagTags.SCREEN).assertDoesNotExist()
        compose.onNodeWithTag(TagTags.TOAST).assertDoesNotExist()
    }
}
