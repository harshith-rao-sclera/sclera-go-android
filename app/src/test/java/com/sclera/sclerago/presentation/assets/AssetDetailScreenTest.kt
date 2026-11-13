package com.sclera.sclerago.presentation.assets

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sclera.sclerago.R
import com.sclera.sclerago.domain.model.Status
import com.sclera.sclerago.domain.usecase.asset.EvaluateAssetChecksUseCase
import com.sclera.sclerago.domain.validation.AssetCheckId
import com.sclera.sclerago.domain.validation.AssetCheckInput
import com.sclera.sclerago.presentation.common.formatDayAndTime
import com.sclera.sclerago.presentation.theme.ScleraTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.util.Locale

private const val PHONE = "w411dp-h891dp"
private const val TABLET_PORTRAIT = "w800dp-h1280dp"
private const val TABLET_LANDSCAPE = "w1280dp-h800dp"

/** The asset details screen (Info and Validation tabs) on phone and both tablet orientations. */
@RunWith(AndroidJUnit4::class)
class AssetDetailScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private fun str(id: Int, vararg args: Any) = context.getString(id, *args)

    // ---- Info tab

    private fun checkInfoTab() {
        val state = sampleDetailState()
        compose.setContent { ScleraTheme { AssetDetailScreen(state, AssetDetailActions()) } }
        compose.onNodeWithTag(DetailTags.TAB_INFO).assertIsDisplayed()
        compose.onNodeWithTag(DetailTags.TAB_VALIDATION).assertIsDisplayed()
        // The photos are the asset's real ones (two), then the add button.
        compose.onNodeWithTag(PhotoTags.tile(0)).assertIsDisplayed()
        compose.onNodeWithTag(PhotoTags.tile(1)).assertIsDisplayed()
        compose.onNodeWithTag(PhotoTags.ADD).assertIsDisplayed()
        compose.onNodeWithTag(PhotoTags.EMPTY).assertDoesNotExist()
        compose.onNodeWithTag(DetailTags.LOCATION).assertIsDisplayed()
        // Fields read from the nameplate carry OCR, the doubtful one CHECK.
        compose.onNodeWithTag(DetailTags.badge("make")).performScrollTo().assertTextContains(str(R.string.detail_badge_ocr))
        compose.onNodeWithTag(DetailTags.badge("speed")).performScrollTo().assertTextContains(str(R.string.detail_badge_check))
        // Collapsed Classification says what is missing.
        compose.onNodeWithText(str(R.string.detail_required_summary, "JACS code")).performScrollTo().assertIsDisplayed()
        // The status line: status, how it was onboarded, and when it changed (formatted for the locale).
        compose.onNodeWithTag(DetailTags.STATUS_LINE).performScrollTo()
        compose.onNodeWithText(str(R.string.status_input)).assertIsDisplayed()
        compose.onNodeWithText(str(R.string.source_camera)).assertIsDisplayed()
        compose.onNodeWithText(str(R.string.detail_updated, formatDayAndTime(SampleUpdatedAt, Locale.getDefault()))).assertIsDisplayed()
        compose.onNodeWithTag(DetailTags.SAVE).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(str(R.string.detail_no_unsaved)).assertIsDisplayed()
        compose.onNodeWithTag(FloatingTags.AI).assertIsDisplayed()
        compose.onNodeWithTag(FloatingTags.MIC).assertIsDisplayed()
    }

    @Test @Config(qualifiers = PHONE)
    fun phone_infoTab() = checkInfoTab()

    @Test @Config(qualifiers = TABLET_PORTRAIT)
    fun tabletPortrait_infoTab() = checkInfoTab()

    @Test @Config(qualifiers = TABLET_LANDSCAPE)
    fun tabletLandscape_infoTab() = checkInfoTab()

    @Test @Config(qualifiers = PHONE)
    fun statusLineOmitsUpdatedWhenTheAssetWasNeverEdited() {
        val seed = sampleDetailState()
        compose.setContent { ScleraTheme { StatusLine(seed.asset!!.copy(updatedAt = null)) } }
        compose.onNodeWithText(str(R.string.source_camera)).assertIsDisplayed()
        compose.onNodeWithText(str(R.string.detail_updated, "x"), substring = true).assertDoesNotExist()
    }

    @Test @Config(qualifiers = PHONE)
    fun editedAndAiFilledFieldsAreMarked() {
        val state = sampleDetailState(edits = mapOf("make" to "Grundfos", "jacs" to "[JACS code 1]")).copy(aiFilled = setOf("jacs"))
        compose.setContent { ScleraTheme { AssetDetailScreen(state, AssetDetailActions()) } }
        compose.onNodeWithTag(DetailTags.badge("make")).performScrollTo().assertTextContains(str(R.string.detail_badge_edited))
        compose.onNodeWithText(str(R.string.detail_no_unsaved)).assertDoesNotExist()
        compose.onNodeWithText(context.resources.getQuantityString(R.plurals.detail_unsaved, 2, 2)).performScrollTo().assertIsDisplayed()
    }

    @Test @Config(qualifiers = PHONE)
    fun newAsset_hasNoTabsOrActionCard_andSavesAsDraftOrSubmitsAfterConfirming() {
        val created = mutableListOf<Boolean>()
        val state = sampleDetailState(isNew = true, media = emptyList())
        compose.setContent { ScleraTheme { AssetDetailScreen(state, AssetDetailActions(onCreate = { created += it })) } }
        compose.onNodeWithTag(DetailTags.TAB_INFO).assertDoesNotExist()
        compose.onNodeWithTag(PhotoTags.EMPTY).assertIsDisplayed()
        compose.onNodeWithTag(DetailTags.SAVE).performScrollTo().performClick()
        compose.onNodeWithTag(DetailTags.SUBMIT).performScrollTo().performClick()
        compose.onNodeWithText(str(R.string.detail_submit_title)).assertIsDisplayed()
        // The dialog is drawn last, so its Submit is the last one.
        compose.onAllNodesWithText(str(R.string.action_submit)).onLast().performClick()
        assertEquals(listOf(false, true), created)
    }

    @Test @Config(qualifiers = PHONE)
    fun submittedAsset_isReadOnly_withARecollectCard() {
        val state = sampleDetailState(status = Status.SUBMITTED)
        var back = 0
        compose.setContent { ScleraTheme { AssetDetailScreen(state, AssetDetailActions(onBack = { back++ })) } }
        compose.onNodeWithText(str(R.string.detail_recollect)).performScrollTo().performClick()
        assertEquals(1, back)
        compose.onNodeWithTag(PhotoTags.ADD).assertDoesNotExist()
        compose.onNodeWithTag(FloatingTags.AI).assertDoesNotExist()
        compose.onNodeWithTag(DetailTags.SAVE).assertDoesNotExist()
    }

    @Test @Config(qualifiers = PHONE)
    fun photoOpensInTheViewer_andNavigates() {
        compose.setContent { ScleraTheme { AssetDetailScreen(sampleDetailState(), AssetDetailActions()) } }
        compose.onNodeWithTag(PhotoTags.tile(0)).performClick()
        compose.onNodeWithTag(PhotoTags.VIEWER).assertIsDisplayed()
        compose.onNodeWithText(str(R.string.detail_photo_of, 1, 2)).assertIsDisplayed()
        compose.onNodeWithContentDescription(str(R.string.detail_next)).performClick()
        compose.onNodeWithText(str(R.string.detail_photo_of, 2, 2)).assertIsDisplayed()
        compose.onNodeWithContentDescription(str(R.string.action_close)).performClick()
        compose.onNodeWithTag(PhotoTags.VIEWER).assertDoesNotExist()
    }

    @Test @Config(qualifiers = PHONE)
    fun addMenuOffersCameraGalleryAndFiles() {
        val chosen = mutableListOf<String>()
        val actions = AssetDetailActions(
            onTakePhoto = { chosen += "camera" }, onPickGallery = { chosen += "gallery" }, onPickFiles = { chosen += "files" },
        )
        compose.setContent { ScleraTheme { AssetDetailScreen(sampleDetailState(), actions) } }
        listOf(R.string.detail_menu_camera to "camera", R.string.detail_menu_gallery to "gallery", R.string.detail_menu_files to "files").forEach { (label, _) ->
            compose.onNodeWithTag(PhotoTags.ADD).performClick()
            compose.onNodeWithText(str(label)).performClick()
        }
        assertEquals(listOf("camera", "gallery", "files"), chosen)
    }

    // ---- Validation tab

    private fun openValidationTab(state: AssetDetailState, actions: AssetDetailActions = AssetDetailActions()) {
        compose.setContent { ScleraTheme { AssetDetailScreen(state, actions) } }
        compose.onNodeWithTag(DetailTags.TAB_VALIDATION).performClick()
    }

    private fun checkFailingChecklist() {
        openValidationTab(sampleFailingState())
        // The tab badge and the banner say 1 check failed.
        compose.onNodeWithTag(DetailTags.TAB_VALIDATION).assertTextContains("1", substring = true)
        compose.onNodeWithTag(ChecksTags.BANNER).assertIsDisplayed()
        compose.onNodeWithText(context.resources.getQuantityString(R.plurals.check_banner_failed, 1, 1)).assertIsDisplayed()
        compose.onNodeWithText(str(R.string.check_banner_failed_sub)).assertIsDisplayed()
        AssetCheckId.entries.forEach { compose.onNodeWithTag(ChecksTags.row(it)).performScrollTo().assertIsDisplayed() }
        // Passes show their value; the failing JACS check is REQUIRED with its reason and a Fix button.
        compose.onNodeWithText("Mechanical · Pump").assertIsDisplayed()
        compose.onNodeWithText(str(R.string.check_note_required)).assertIsDisplayed()
        compose.onNodeWithText(str(R.string.check_required)).assertIsDisplayed()
        compose.onNodeWithTag(ChecksTags.fix(AssetCheckId.JACS_CODE)).assertIsDisplayed()
        compose.onNodeWithTag(ChecksTags.fix(AssetCheckId.LOW_CONFIDENCE)).assertIsDisplayed()
        compose.onNodeWithTag(ChecksTags.fix(AssetCheckId.SERIAL)).assertDoesNotExist()
        compose.onNodeWithText(str(R.string.check_note_low_confidence, "Speed (RPM)")).assertIsDisplayed()
        // The footer is a disabled-looking Fix button, not Validate.
        compose.onNodeWithTag(ChecksTags.FOOTER).performScrollTo()
        compose.onNode(hasTestTag(ChecksTags.FOOTER) and hasAnyDescendant(hasText(context.resources.getQuantityString(R.plurals.check_fix_footer, 1, 1)))).assertExists()
        compose.onNodeWithText(str(R.string.action_validate)).assertDoesNotExist()
    }

    @Test @Config(qualifiers = PHONE)
    fun phone_failingChecklist() = checkFailingChecklist()

    @Test @Config(qualifiers = TABLET_PORTRAIT)
    fun tabletPortrait_failingChecklist() = checkFailingChecklist()

    @Test @Config(qualifiers = TABLET_LANDSCAPE)
    fun tabletLandscape_failingChecklist() = checkFailingChecklist()

    private fun checkPassingChecklist() {
        var validated = 0
        openValidationTab(samplePassingState(), AssetDetailActions(onValidate = { validated++ }))
        compose.onNodeWithText(str(R.string.check_banner_passed)).assertIsDisplayed()
        compose.onNodeWithTag(ChecksTags.fix(AssetCheckId.JACS_CODE)).assertDoesNotExist()
        compose.onNodeWithTag(ChecksTags.FOOTER).performScrollTo().assertTextContains(str(R.string.action_validate)).assertIsEnabled().performClick()
        assertEquals(1, validated)
    }

    @Test @Config(qualifiers = PHONE)
    fun phone_passingChecklist() = checkPassingChecklist()

    @Test @Config(qualifiers = TABLET_PORTRAIT)
    fun tabletPortrait_passingChecklist() = checkPassingChecklist()

    @Test @Config(qualifiers = TABLET_LANDSCAPE)
    fun tabletLandscape_passingChecklist() = checkPassingChecklist()

    @Test @Config(qualifiers = PHONE)
    fun fixOnJacs_returnsToTheInfoTab_andOpensTheClassificationSection() {
        openValidationTab(sampleFailingState())
        compose.onNodeWithTag(ChecksTags.fix(AssetCheckId.JACS_CODE)).performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithTag(DetailTags.field("jacs")).assertIsDisplayed()
    }

    @Test @Config(qualifiers = PHONE)
    fun fixOnLocationAndPhotos_openThePickerAndTheCamera() {
        val calls = mutableListOf<String>()
        val base = sampleDetailState(media = emptyList())
        val noLocation = base.copy(asset = base.asset!!.copy(loc = null, where = ""))
        val state = noLocation.copy(checks = EvaluateAssetChecksUseCase()(AssetCheckInput(noLocation.values, false, "", 0, true, 1, setOf("jacs"))))
        openValidationTab(state, AssetDetailActions(onChangeLoc = { calls += "location" }, onTakePhoto = { calls += "photo" }))
        compose.onNodeWithTag(ChecksTags.fix(AssetCheckId.LOCATION)).performScrollTo().performClick()
        compose.onNodeWithTag(ChecksTags.fix(AssetCheckId.PHOTO)).performScrollTo().performClick()
        assertEquals(listOf("location", "photo"), calls)
    }

    @Test @Config(qualifiers = PHONE)
    fun submittedAsset_hasNoFixButtons() {
        openValidationTab(sampleDetailState(status = Status.SUBMITTED))
        compose.onNodeWithTag(ChecksTags.fix(AssetCheckId.JACS_CODE)).assertDoesNotExist()
    }
}
