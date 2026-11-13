package com.sclera.sclerago.domain.telemetry

/**
 * The only usage events the app reports. A fixed list (not free text) so a new event is a reviewed change
 * and no screen can send an email, a name, a location or an access code by accident.
 */
enum class AnalyticsEvent(val id: String) {
    SIGN_IN_COMPLETED("sign_in_completed"),
    SIGNED_OUT("signed_out"),
    SESSION_ENDED("session_ended"),
}

/** Parameter names the events above may carry. */
object AnalyticsParam {
    const val REASON = "reason"
}
