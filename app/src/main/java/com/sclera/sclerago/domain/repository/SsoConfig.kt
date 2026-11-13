package com.sclera.sclerago.domain.repository

/** Where and how the single sign-on page is reached (which host, which pages end the login). */
interface SsoConfig {
    /** The accounts host, shown under the login page title. */
    val host: String

    /** The first page of the sign-in for [email]: the org's SSO page, with the email prefilled. */
    fun loginUrl(email: String): String

    /** True once SSO has sent the browser back to the app domain, i.e. login finished. */
    fun isReturnUrl(url: String?): Boolean

    /** An accounts JSON API response shown in the web view (e.g. the email was rejected). */
    fun isAccountsApiUrl(url: String?): Boolean

    /** True for an https page on the Sclera domain; the sign-in web view loads nothing else. */
    fun isTrustedUrl(url: String?): Boolean

    /** True when [host] is the Sclera domain or one of its sub-domains. */
    fun isTrustedHost(host: String?): Boolean
}
