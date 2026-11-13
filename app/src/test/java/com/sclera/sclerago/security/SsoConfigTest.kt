package com.sclera.sclerago.security

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sclera.sclerago.data.remote.AuthConfig
import com.sclera.sclerago.data.remote.AuthConfigSso
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** MASVS-PLATFORM / NETWORK: the sign-in web view only loads https pages on the Sclera domain. */
@RunWith(AndroidJUnit4::class)
class SsoConfigTest {
    private val sso = AuthConfigSso()
    private val domain = AuthConfig.trustedDomain

    @Test
    fun trustedDomain_isTheAccountsHostsParent() {
        assertEquals("sclera.com", domain)
    }

    @Test
    fun trustedHost_acceptsTheDomainAndItsSubdomains() {
        assertTrue(sso.isTrustedHost(domain))
        assertTrue(sso.isTrustedHost("accounts.$domain"))
        assertTrue(sso.isTrustedHost("QA-ACCOUNTS.$domain"))
        assertTrue(sso.isTrustedHost("a.b.c.$domain"))
    }

    @Test
    fun trustedHost_rejectsLookalikes() {
        listOf("evil-$domain", "$domain.evil.com", "sclera.com.attacker.io", "xsclera.com", "", null).forEach {
            assertFalse("$it", sso.isTrustedHost(it))
        }
    }

    @Test
    fun trustedUrl_needsHttpsAndATrustedHost() {
        assertTrue(sso.isTrustedUrl("https://accounts.$domain/sclera/api/x?y=1"))
        assertFalse(sso.isTrustedUrl("http://accounts.$domain/"))
        assertFalse(sso.isTrustedUrl("https://evil.example.com/login"))
        assertFalse(sso.isTrustedUrl("javascript:alert(1)"))
        assertFalse(sso.isTrustedUrl("file:///data/data/x"))
        assertFalse(sso.isTrustedUrl("https://$domain@evil.com/"))
        assertFalse(sso.isTrustedUrl(null))
    }

    @Test
    fun loginUrl_isHttpsOnTheAccountsHost() {
        val url = sso.loginUrl("a@b.com")
        assertTrue(url.startsWith("https://"))
        assertTrue(sso.isTrustedUrl(url))
        assertTrue(url.contains("a%40b.com"))
    }
}
