package com.sclera.sclerago.domain.telemetry

/**
 * Records handled problems for the crash dashboard. Only a short [code] is sent, never a message or cause:
 * exception text can carry emails, tokens, URLs or locations. Crashes themselves are captured automatically.
 */
interface CrashReporter {
    fun recordNonFatal(code: String)
}
