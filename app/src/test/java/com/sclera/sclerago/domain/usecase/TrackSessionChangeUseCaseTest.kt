package com.sclera.sclerago.domain.usecase

import com.sclera.sclerago.domain.model.AuthError
import com.sclera.sclerago.domain.model.AuthState
import com.sclera.sclerago.domain.model.UserSession
import com.sclera.sclerago.domain.telemetry.AnalyticsEvent
import com.sclera.sclerago.domain.usecase.telemetry.TrackSessionChangeUseCase
import com.sclera.sclerago.testutil.RecordingAnalytics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackSessionChangeUseCaseTest {
    private val signedIn = AuthState.SignedIn(UserSession("jane@company.com", "Jane Doe", "Admin", "Acme"))
    private val signedOut = AuthState.SignedOut()
    private val tracker = RecordingAnalytics()
    private val track = TrackSessionChangeUseCase(tracker)

    @Test fun firstStateAfterLaunchIsNotAChange() {
        track(null, signedIn)
        track(null, signedOut)
        assertTrue(tracker.events.isEmpty())
    }

    @Test fun signingInIsReportedWithoutAnyDetails() {
        track(signedOut, signedIn)
        assertEquals(listOf(AnalyticsEvent.SIGN_IN_COMPLETED to emptyMap<String, String>()), tracker.events)
    }

    @Test fun signingOutIsReported() {
        track(signedIn, signedOut)
        assertEquals(listOf(AnalyticsEvent.SIGNED_OUT to emptyMap<String, String>()), tracker.events)
    }

    @Test fun endedSessionsCarryAFixedReasonCode() {
        track(signedIn, AuthState.SignedOut(AuthError.SessionExpired()))
        track(signedIn, AuthState.SignedOut(AuthError.MultipleSession()))
        track(signedIn, AuthState.SignedOut(AuthError.UserBlocked()))
        track(signedIn, AuthState.SignedOut(AuthError.UserNotFound()))
        track(signedIn, AuthState.SignedOut(AuthError.Server("user jane@company.com not allowed")))
        assertEquals(
            listOf("session_expired", "signed_in_elsewhere", "user_blocked", "user_not_found", "other"),
            tracker.events.map { it.second.getValue("reason") },
        )
        assertTrue(tracker.events.all { it.first == AnalyticsEvent.SESSION_ENDED })
    }

    @Test fun staysQuietWhenNothingChanged() {
        track(signedIn, signedIn)
        track(signedOut, signedOut)
        assertTrue(tracker.events.isEmpty())
    }
}
