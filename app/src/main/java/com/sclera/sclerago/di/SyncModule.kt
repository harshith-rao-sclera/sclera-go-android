package com.sclera.sclerago.di

import com.sclera.sclerago.data.online.OnlineIdentity
import com.sclera.sclerago.data.repository.AuthRepositoryImpl
import com.sclera.sclerago.data.sync.AdcSyncController
import com.sclera.sclerago.data.sync.MeCredentialsProvider
import com.sclera.sclerago.data.sync.SyncCredentialsProvider
import com.sclera.sclerago.data.sync.SyncDataCleaner
import com.sclera.sclerago.domain.repository.UserDataCleaner
import com.sclera.sclerago.domain.sync.OfflineSync
import dagger.Binds
import com.sclera.sclerago.data.sync.TestDatabase
import dagger.BindsOptionalOf
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

/** The offline data sync: its contract, the user's credentials for it and its wipe on sign-out. */
@Module
@InstallIn(SingletonComponent::class)
abstract class SyncModule {
    @Binds @Singleton abstract fun offlineSync(impl: AdcSyncController): OfflineSync
    /** Absent in release; the debug source set binds it so a local test database can replace the sync. */
    @BindsOptionalOf abstract fun testDatabase(): TestDatabase
    @Binds @IntoSet abstract fun clearSync(impl: SyncDataCleaner): UserDataCleaner

    companion object {
        /** Lives as long as the process, for work that must outlive any screen (the sync). */
        @Provides
        @Singleton
        @ApplicationScope
        fun applicationScope(): CoroutineScope = CoroutineScope(SupervisorJob())

        @Provides
        @Singleton
        fun credentials(auth: AuthRepositoryImpl, identity: OnlineIdentity, @ApplicationScope scope: CoroutineScope): SyncCredentialsProvider =
            MeCredentialsProvider(auth, identity, scope)
    }
}
