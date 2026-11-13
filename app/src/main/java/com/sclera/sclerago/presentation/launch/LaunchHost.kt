package com.sclera.sclerago.presentation.launch

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier

/**
 * Shows [content] (the sign-in screen or the app) and, while [play] is true, runs the launch
 * animation over it: the splash lockup first, then the content's [launchEntrance] elements enter.
 * [onFinished] is called once the animation is over. [content] keeps its place in the tree
 * when [play] turns false, so its state survives the end of the animation.
 */
@Composable
fun LaunchHost(play: Boolean, onFinished: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val timeline = remember { LaunchTimeline() }
    LaunchedEffect(play) {
        if (play) {
            timeline.play()
            onFinished()
        }
    }
    Box(modifier) {
        CompositionLocalProvider(LocalLaunchTimeline provides if (play) timeline else null) { content() }
        if (play && timeline.timeMs < LaunchMotion.SplashEndMs) LaunchSplash(timeline.timeMs)
    }
}
