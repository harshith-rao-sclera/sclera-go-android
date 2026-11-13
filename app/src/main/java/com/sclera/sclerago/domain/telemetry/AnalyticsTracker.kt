package com.sclera.sclerago.domain.telemetry

/** Reports anonymous usage events. Implementations drop any parameter that fails [TelemetryParams.sanitise]. */
interface AnalyticsTracker {
    fun log(event: AnalyticsEvent, params: Map<String, String> = emptyMap())
}
