package com.sclera.sclerago.di

import com.sclera.sclerago.data.remote.AuthConfigSso
import com.sclera.sclerago.domain.repository.SsoConfig
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class SsoModule {
    @Binds abstract fun ssoConfig(impl: AuthConfigSso): SsoConfig
}
