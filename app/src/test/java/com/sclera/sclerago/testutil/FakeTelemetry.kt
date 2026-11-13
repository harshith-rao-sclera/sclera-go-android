package com.sclera.sclerago.testutil

import com.sclera.sclerago.domain.telemetry.AnalyticsEvent
import com.sclera.sclerago.domain.telemetry.AnalyticsTracker
import com.sclera.sclerago.domain.telemetry.CrashReporter

/** Ignores every event, for tests that are not about telemetry. */
object NoopAnalytics : AnalyticsTracker {
    override fun log(event: AnalyticsEvent, params: Map<String, String>) = Unit
}

/** Remembers what was reported so a test can assert on it. */
class RecordingAnalytics : AnalyticsTracker {
    val events = mutableListOf<Pair<AnalyticsEvent, Map<String, String>>>()

    override fun log(event: AnalyticsEvent, params: Map<String, String>) {
        events += event to params
    }
}

class RecordingCrashReporter : CrashReporter {
    val codes = mutableListOf<String>()

    override fun recordNonFatal(code: String) {
        codes += code
    }
}
