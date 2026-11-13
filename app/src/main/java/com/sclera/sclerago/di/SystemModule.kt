package com.sclera.sclerago.di

import com.sclera.sclerago.data.local.SystemTimeProvider
import com.sclera.sclerago.data.local.UuidIdGenerator
import com.sclera.sclerago.domain.system.IdGenerator
import com.sclera.sclerago.domain.system.TimeProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** The clock and id source business code uses instead of calling the system directly. */
@Module
@InstallIn(SingletonComponent::class)
abstract class SystemModule {
    @Binds abstract fun ids(impl: UuidIdGenerator): IdGenerator
    @Binds abstract fun time(impl: SystemTimeProvider): TimeProvider
}
