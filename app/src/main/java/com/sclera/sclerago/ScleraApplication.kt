package com.sclera.sclerago

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import dagger.Lazy
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * Hilt's entry point: builds the app-wide dependency graph (see di/). Coil's composables (people's profile photos) use
 * the [ImageLoader] Hilt provides (di/PhotoModule), built the first time a photo is shown.
 */
@HiltAndroidApp
class ScleraApplication : Application(), SingletonImageLoader.Factory {
    @Inject lateinit var imageLoader: Lazy<ImageLoader>

    override fun newImageLoader(context: PlatformContext): ImageLoader = imageLoader.get()
}
