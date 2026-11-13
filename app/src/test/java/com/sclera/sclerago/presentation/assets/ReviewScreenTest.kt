package com.sclera.sclerago.presentation.assets

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sclera.sclerago.R
import com.sclera.sclerago.presentation.theme.ScleraTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

private const val PHONE = "w411dp-h891dp"
private const val TABLET_PORTRAIT = "w800dp-h1280dp"
private const val TABLET_LANDSCAPE = "w1280dp-h800dp"

/** Review and onboard: the rows, the toggles and the footer buttons, on phone and both tablet orientations. */
@RunWith(AndroidJUnit4::class)
class ReviewScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private fun str(id: Int, vararg args: Any) = context.getString(id, *args)
    private fun plural(id: Int, n: Int) = context.resources.getQuantityString(id, n, n)

    private fun checkReview() {
        val items = mutableStateOf(SampleReviewItems)
        val toggled = mutableListOf<String>()
        var added = 0
        var discarded = 0
        var onboarded = 0
        compose.setContent {
            ScleraTheme {
                ReviewScreen(
                    sub = plural(R.plurals.review_items_sub, 3), addLabel = str(R.string.review_add_more), onAdd = { added++ },
                    items = items.value,
                    onToggle = { id -> toggled += id; items.value = items.value.map { if (it.id == id) it.copy(on = !it.on) else it } },
                    onDiscard = { discarded++ }, onGo = { onboarded++ }, thumb = {},
                )
            }
        }
        compose.onNodeWithText(str(R.string.review_title)).assertIsDisplayed()
        compose.onNodeWithText(plural(R.plurals.review_items_sub, 3)).assertIsDisplayed()
        SampleReviewItems.forEach { compose.onNodeWithTag(ReviewTags.item(it.id)).assertIsDisplayed() }
        // Two of the three are on, so the footer onboards two assets.
        compose.onNodeWithTag(ReviewTags.GO).assertTextContains(plural(R.plurals.review_onboard, 2))
        compose.onNodeWithText(str(R.string.review_ready)).assertIsDisplayed()
        // Turning one on adds it to the count.
        compose.onNodeWithTag(ReviewTags.toggle("b")).performClick()
        compose.onNodeWithTag(ReviewTags.GO).assertTextContains(plural(R.plurals.review_onboard, 3))
        compose.onNodeWithTag(ReviewTags.ADD).performClick()
        compose.onNodeWithTag(ReviewTags.DISCARD).performClick()
        compose.onNodeWithTag(ReviewTags.GO).performClick()
        assertEquals(listOf("b"), toggled)
        assertEquals(listOf(1, 1, 1), listOf(added, discarded, onboarded))
    }

    @Test @Config(qualifiers = PHONE)
    fun phone_review() = checkReview()

    @Test @Config(qualifiers = TABLET_PORTRAIT)
    fun tabletPortrait_review() = checkReview()

    @Test @Config(qualifiers = TABLET_LANDSCAPE)
    fun tabletLandscape_review() = checkReview()

    @Test @Config(qualifiers = PHONE)
    fun nothingSelected_footerAsksToSelectItems() {
        compose.setContent {
            ScleraTheme {
                ReviewScreen("", "Add", {}, SampleReviewItems.map { it.copy(on = false) }, {}, {}, {}) { }
            }
        }
        compose.onNodeWithTag(ReviewTags.GO).assertTextContains(str(R.string.review_select_items))
    }
}
