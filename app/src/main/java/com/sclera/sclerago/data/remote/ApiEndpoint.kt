package com.sclera.sclerago.data.remote

/**
 * The Sclera ADC (asset data collection) API: base URL and paths.
 *
 * Every call is HTTPS with the signed-in user's bearer token (see [AdcApi]); nothing sensitive goes in
 * these paths or their query strings.
 */
object ApiEndpoint {
    /** Base URL of the ADC gateway; must end in `/`. TODO: production host once it is known (QA for now). */
    const val ADC_BASE_URL = "https://adc.vdms163.qa-gateway.scleravdms.com/"

    /** The signed-in user: id, organisation, role, group and permissions. */
    const val ME = "api/v1/me"

    /** One page of an organisation's properties; pass the previous page's `nextCursor` as [CURSOR]. */
    const val PROPERTIES = "api/v1/organisations/{orgId}/properties"

    const val LIMIT = "limit"
    const val CURSOR = "cursor"

    /** Properties asked for per page. */
    const val PROPERTIES_PAGE_SIZE = 16
}
