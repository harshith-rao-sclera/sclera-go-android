package com.sclera.sclerago.data.remote.adc

import com.sclera.sclerago.BuildConfig

/** Where the asset data collection (ADC) API is: set per build type in app/build.gradle.kts (QA for debug; production for release, which `ADC_API_URL_RELEASE` may override). */
object AdcConfig {
    /** The `/v1` base URL, https, ending in `/`. */
    const val API_URL = BuildConfig.ADC_API_URL

    const val TIMEOUT_SECONDS = 30L

    /**
     * A call that waits on the server's AI and web search (reading a photo with `lookup=true`) asks for a longer read
     * timeout with this header, as `"$SLOW_CALL_HEADER: <seconds>"`; [SlowCallInterceptor] applies it and drops the header.
     */
    const val SLOW_CALL_HEADER = "X-Adc-Read-Timeout"
    const val SLOW_CALL_SECONDS = 300L

    /** The header line a slow call carries (for Retrofit's `@Headers`). */
    const val SLOW_CALL = "$SLOW_CALL_HEADER: 300"

    /** Properties and people are read this many at a time, following `nextCursor`. */
    const val PAGE_SIZE = 200
}
