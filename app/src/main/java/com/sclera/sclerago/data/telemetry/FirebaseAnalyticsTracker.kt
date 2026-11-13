package com.sclera.sclerago.data.telemetry

import android.content.Context
import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics
import com.sclera.sclerago.domain.telemetry.AnalyticsEvent
import com.sclera.sclerago.domain.telemetry.AnalyticsTracker
import com.sclera.sclerago.domain.telemetry.TelemetryParams
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Firebase Analytics behind [AnalyticsTracker]. Collection is switched on or off by the manifest (release only). */
@Singleton
class FirebaseAnalyticsTracker @Inject constructor(@ApplicationContext context: Context) : AnalyticsTracker {
    private val analytics = FirebaseAnalytics.getInstance(context)

    override fun log(event: AnalyticsEvent, params: Map<String, String>) {
        val bundle = Bundle()
        TelemetryParams.sanitise(params).forEach { (key, value) -> bundle.putString(key, value) }
        analytics.logEvent(event.id, bundle)
    }
}
