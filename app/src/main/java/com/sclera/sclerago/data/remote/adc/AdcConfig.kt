package com.sclera.sclerago.data.remote.adc

import com.sclera.sclerago.BuildConfig

/** Where the asset data collection (ADC) API is: set per build type in app/build.gradle.kts (QA for debug, `ADC_API_URL_RELEASE` for release). */
object AdcConfig {
    /** The `/v1` base URL, https, ending in `/`. */
    const val API_URL = BuildConfig.ADC_API_URL

    const val TIMEOUT_SECONDS = 30L

    /** Properties and people are read this many at a time, following `nextCursor`. */
    const val PAGE_SIZE = 200
}
