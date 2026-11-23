package com.sclera.sclerago.data.local

import coil3.ImageLoader
import com.sclera.sclerago.domain.repository.UserDataCleaner
import dagger.Lazy
import javax.inject.Inject
import javax.inject.Singleton

/**
 * People's profile photos (and their links, the cache keys) are personal data: on sign-out Coil's memory cache and its
 * disk cache (the app's private cache folder, never backed up) are emptied. Blocking: called off the main thread.
 */
@Singleton
class ProfilePhotoCache @Inject constructor(private val imageLoader: Lazy<ImageLoader>) : UserDataCleaner {
    override fun clear() {
        val loader = imageLoader.get()
        loader.memoryCache?.clear()
        loader.diskCache?.clear()
    }
}
