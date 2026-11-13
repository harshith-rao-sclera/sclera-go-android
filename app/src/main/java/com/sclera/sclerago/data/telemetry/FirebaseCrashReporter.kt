package com.sclera.sclerago.data.telemetry

import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.sclera.sclerago.domain.telemetry.CrashReporter
import com.sclera.sclerago.domain.telemetry.TelemetryParams
import javax.inject.Inject
import javax.inject.Singleton

/** Firebase Crashlytics behind [CrashReporter]. Collection is switched on or off by the manifest (release only). */
@Singleton
class FirebaseCrashReporter @Inject constructor() : CrashReporter {
    override fun recordNonFatal(code: String) {
        FirebaseCrashlytics.getInstance().recordException(HandledProblem(TelemetryParams.code(code)))
    }

    /** Carries only the short code; the stack trace shows where it was recorded. */
    private class HandledProblem(code: String) : RuntimeException(code)
}
