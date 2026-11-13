package com.sclera.sclerago.presentation.launch

import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sclera.sclerago.presentation.theme.ScleraTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

private const val PHONE = "w411dp-h891dp"
private const val TABLET_PORTRAIT = "w800dp-h1280dp"
private const val TABLET_LANDSCAPE = "w1280dp-h800dp"
private const val DESTINATION = "destination"

@RunWith(AndroidJUnit4::class)
class LaunchHostTest {
    @get:Rule
    val compose = createComposeRule()

    private var finished = 0

    private fun show(play: Boolean) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            ScleraTheme {
                LaunchHost(play = play, onFinished = { finished++ }) {
                    Box(Modifier.testTag(DESTINATION).launchEntrance(Entrance.Rise))
                }
            }
        }
    }

    private fun splashShowsThenYields() {
        show(play = true)
        compose.onNodeWithTag(LAUNCH_SPLASH_TAG).assertIsDisplayed()
        compose.mainClock.advanceTimeBy(1000)
        compose.onNodeWithTag(LAUNCH_SPLASH_TAG).assertIsDisplayed()
        compose.mainClock.advanceTimeBy(LaunchMotion.SplashEndMs - 1000L + 100)
        compose.onNodeWithTag(LAUNCH_SPLASH_TAG).assertDoesNotExist()
        assertEquals(0, finished)
        compose.mainClock.advanceTimeBy(LaunchMotion.TotalMs.toLong())
        assertEquals(1, finished)
        compose.onNodeWithTag(DESTINATION).assertIsDisplayed()
    }

    private fun noSplashWhenNotPlaying() {
        show(play = false)
        compose.onNodeWithTag(LAUNCH_SPLASH_TAG).assertDoesNotExist()
        compose.onNodeWithTag(DESTINATION).assertIsDisplayed()
        assertEquals(0, finished)
    }

    @Test @Config(qualifiers = PHONE) fun splashThenDestinationPhone() = splashShowsThenYields()

    @Test @Config(qualifiers = TABLET_PORTRAIT) fun splashThenDestinationTabletPortrait() = splashShowsThenYields()

    @Test @Config(qualifiers = TABLET_LANDSCAPE) fun splashThenDestinationTabletLandscape() = splashShowsThenYields()

    @Test @Config(qualifiers = PHONE) fun skippedPhone() = noSplashWhenNotPlaying()

    @Test @Config(qualifiers = TABLET_PORTRAIT) fun skippedTabletPortrait() = noSplashWhenNotPlaying()

    @Test @Config(qualifiers = TABLET_LANDSCAPE) fun skippedTabletLandscape() = noSplashWhenNotPlaying()
}
