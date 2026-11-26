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
import com.sclera.sclerago.presentation.tagging.TagTags
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
        // An added asset can be tagged (QR, barcode, NFC).
        compose.onNodeWithTag(TagTags.BAR).assertIsDisplayed()
        compose.onNodeWithTag(DetailTags.TAB_VALIDATION).assertIsDisplayed()
        // The photos are the asset's real ones (two), then the add button.
        compose.onNodeWithTag(PhotoTags.tile(0)).assertIsDisplayed()
        compose.onNodeWithTag(PhotoTags.tile(1)).assertIsDisplayed()
        compose.onNodeWithTag(PhotoTags.ADD).assertIsDisplayed()
        compose.onNodeWithTag(DetailTags.LOCATION).assertIsDisplayed()
        // Only a doubtful value read from the nameplate carries a badge (CHECK); a sure one has none (OCR is gone).
        compose.onNodeWithTag(DetailTags.badge("make")).assertDoesNotExist()
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
        val state = sampleDetailState(edits = mapOf("make" to "Grundfos", "jacs" to "[JACS code 1]"))
        compose.setContent { ScleraTheme { AssetDetailScreen(state, AssetDetailActions()) } }
        compose.onNodeWithTag(DetailTags.badge("make")).performScrollTo().assertTextContains(str(R.string.detail_badge_edited))
        compose.onNodeWithText(str(R.string.detail_no_unsaved)).assertDoesNotExist()
        compose.onNodeWithText(context.resources.getQuantityString(R.plurals.detail_unsaved, 2, 2)).performScrollTo().assertIsDisplayed()
    }

    // A new asset is tagged only once it has been added: no Tags bar while it is being filled in.
    private fun checkNewAssetHasNoTagsBar() {
        val state = sampleDetailState(isNew = true, media = emptyList())
        compose.setContent { ScleraTheme { AssetDetailScreen(state, AssetDetailActions()) } }
        compose.onNodeWithTag(DetailTags.SAVE).performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag(TagTags.BAR).assertDoesNotExist()
    }

    @Test @Config(qualifiers = PHONE)
    fun phone_newAsset_hasNoTagsBar() = checkNewAssetHasNoTagsBar()

    @Test @Config(qualifiers = TABLET_PORTRAIT)
    fun tabletPortrait_newAsset_hasNoTagsBar() = checkNewAssetHasNoTagsBar()

    @Test @Config(qualifiers = TABLET_LANDSCAPE)
    fun tabletLandscape_newAsset_hasNoTagsBar() = checkNewAssetHasNoTagsBar()

    @Test @Config(qualifiers = PHONE)
    fun newAsset_hasNoTabsOrActionCard_andSavesAsDraftOrSubmitsAfterConfirming() {
        val created = mutableListOf<Boolean>()
        val state = sampleDetailState(isNew = true, media = emptyList())
        compose.setContent { ScleraTheme { AssetDetailScreen(state, AssetDetailActions(onCreate = { created += it })) } }
        compose.onNodeWithTag(DetailTags.TAB_INFO).assertDoesNotExist()
        compose.onNodeWithTag(DetailTags.SAVE).performScrollTo().performClick()
        compose.onNodeWithTag(DetailTags.SUBMIT).performScrollTo().performClick()
        compose.onNodeWithText(str(R.string.detail_submit_title)).assertIsDisplayed()
        // The dialog is drawn last, so its Submit is the last one.
        compose.onAllNodesWithText(str(R.string.action_submit)).onLast().performClick()
        assertEquals(listOf(false, true), created)
    }

    @Test @Config(qualifiers = PHONE)
    fun submittedAsset_isReadOnly() {
        val state = sampleDetailState(status = Status.SUBMITTED)
        compose.setContent { ScleraTheme { AssetDetailScreen(state, AssetDetailActions()) } }
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
}
