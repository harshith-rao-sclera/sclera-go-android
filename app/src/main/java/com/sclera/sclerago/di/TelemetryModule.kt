package com.sclera.sclerago.di

import com.sclera.sclerago.data.telemetry.FirebaseAnalyticsTracker
import com.sclera.sclerago.data.telemetry.FirebaseCrashReporter
import com.sclera.sclerago.domain.telemetry.AnalyticsTracker
import com.sclera.sclerago.domain.telemetry.CrashReporter
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Crash reporting and anonymous usage events. Performance monitoring is automatic (Gradle plugin). */
@Module
@InstallIn(SingletonComponent::class)
abstract class TelemetryModule {
    @Binds abstract fun analytics(impl: FirebaseAnalyticsTracker): AnalyticsTracker
    @Binds abstract fun crashes(impl: FirebaseCrashReporter): CrashReporter
}
