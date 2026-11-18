package com.sclera.sclerago.di

import com.sclera.sclerago.domain.photos.PhotoReader
import com.sclera.sclerago.services.vision.MlKitPhotoReader
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Reading photos on the device (ML Kit), which sorts them into nameplate, label ID or asset. */
@Module
@InstallIn(SingletonComponent::class)
abstract class VisionModule {
    @Binds abstract fun photoReader(impl: MlKitPhotoReader): PhotoReader
}
