package com.sclera.sclerago.domain.telemetry

import org.junit.Assert.assertEquals
import org.junit.Test

class TelemetryParamsTest {
    @Test fun keepsPlainCodes() {
        val params = mapOf("reason" to "session_expired", "screen" to "asset-list.v2")
        assertEquals(params, TelemetryParams.sanitise(params))
    }

    @Test fun dropsAnythingThatLooksLikePersonalData() {
        val result = TelemetryParams.sanitise(
            mapOf(
                "email" to "someone@company.com",
                "name" to "Jane Doe",
                "url" to "https://app.sclera.com/x?token=abc",
                "reason" to "ok",
            ),
        )
        assertEquals(mapOf("reason" to "ok"), result)
    }

    @Test fun dropsBadKeysAndLongValues() {
        val result = TelemetryParams.sanitise(
            mapOf("Bad Key" to "x", "1start" to "x", "ok_key" to "y".repeat(TelemetryParams.MAX_VALUE_LENGTH + 1), "fine" to "z"),
        )
        assertEquals(mapOf("fine" to "z"), result)
    }

    @Test fun capsTheNumberOfParams() {
        val many = (1..25).associate { "k$it" to "v" }
        assertEquals(TelemetryParams.MAX_PARAMS, TelemetryParams.sanitise(many).size)
    }

    @Test fun crashCodesAreSnakeCaseOrReplaced() {
        assertEquals("import_failed", TelemetryParams.code("import_failed"))
        assertEquals("invalid_code", TelemetryParams.code("Import failed for jane@x.com"))
        assertEquals("invalid_code", TelemetryParams.code(""))
    }
}
