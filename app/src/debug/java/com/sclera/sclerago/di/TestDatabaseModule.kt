package com.sclera.sclerago.di

import com.sclera.sclerago.data.sync.LocalTestDatabase
import com.sclera.sclerago.data.sync.TestDatabase
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Debug only: makes the local test database available to the sync and the repositories. */
@Module
@InstallIn(SingletonComponent::class)
abstract class TestDatabaseModule {
    @Binds abstract fun testDatabase(impl: LocalTestDatabase): TestDatabase
}
