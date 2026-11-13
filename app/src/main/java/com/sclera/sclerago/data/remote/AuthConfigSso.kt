package com.sclera.sclerago.data.remote

import android.net.Uri
import com.sclera.sclerago.domain.repository.SsoConfig
import javax.inject.Inject

/** [SsoConfig] for the Sclera accounts service of the current build type ([AuthConfig]). */
class AuthConfigSso @Inject constructor() : SsoConfig {
    override val host: String get() = AuthConfig.accountsHost
    override fun loginUrl(email: String): String = AuthConfig.loginUrl(email)
    override fun isReturnUrl(url: String?): Boolean = AuthConfig.isReturnUrl(url)
    override fun isAccountsApiUrl(url: String?): Boolean = AuthConfig.isAccountsApiUrl(url)
    override fun isTrustedHost(host: String?): Boolean = AuthConfig.isTrustedHost(host)

    override fun isTrustedUrl(url: String?): Boolean {
        val uri = url?.let(Uri::parse) ?: return false
        return uri.scheme.equals("https", ignoreCase = true) && isTrustedHost(uri.host)
    }
}
