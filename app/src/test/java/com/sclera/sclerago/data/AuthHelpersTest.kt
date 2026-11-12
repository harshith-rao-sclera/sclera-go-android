package com.sclera.sclerago.data

import com.sclera.sclerago.data.local.AuthSession
import com.sclera.sclerago.data.local.AuthStore
import com.sclera.sclerago.data.mapper.*
import com.sclera.sclerago.data.remote.*
import com.sclera.sclerago.domain.model.AuthError

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** AuthSession, AuthConfig and the JWT / cookie / expiry helpers. */
@RunWith(AndroidJUnit4::class)
class AuthHelpersTest {
    private val now = 1_800_000_000_000L

    private fun session(token: String? = "t", expiresAt: Long = 0L) = AuthSession("a@b.com", "sid", token, expiresAt)

    // ---- AuthSession.tokenValid ----

    @Test
    fun tokenValid_falseWithoutToken() = assertFalse(session(token = null).tokenValid(now))

    @Test
    fun tokenValid_trueWhenExpiryUnknown() = assertTrue(session(expiresAt = 0L).tokenValid(now))

    @Test
    fun tokenValid_trueWellBeforeExpiry() = assertTrue(session(expiresAt = now + 10 * 60_000).tokenValid(now))

    @Test
    fun tokenValid_falseInsideRefreshMargin() {
        assertFalse(session(expiresAt = now + AuthConfig.TOKEN_REFRESH_MARGIN_MS).tokenValid(now))
        assertFalse(session(expiresAt = now + 1_000).tokenValid(now))
    }

    @Test
    fun tokenValid_falseWhenExpired() = assertFalse(session(expiresAt = now - 1).tokenValid(now))

    // ---- AuthSession.displayName ----

    @Test
    fun displayName_usesJwtName() =
        assertEquals("Ada Lovelace", session(token = testJwt(mapOf("name" to "Ada Lovelace"))).displayName)

    @Test
    fun displayName_fallsBackToGivenName() =
        assertEquals("Ada", session(token = testJwt(mapOf("given_name" to "Ada"))).displayName)

    @Test
    fun displayName_fallsBackToEmail() {
        assertEquals("a@b.com", session(token = null).displayName)
        assertEquals("a@b.com", session(token = "not-a-jwt").displayName)
        assertEquals("a@b.com", session(token = testJwt(mapOf("name" to ""))).displayName)
    }

    // ---- AuthSession.role ----

    @Test
    fun role_fromRoleClaim() = assertEquals("Admin", session(token = testJwt(mapOf("role" to "admin"))).role)

    @Test
    fun role_fromKeycloakRealmRoles_skippingBuiltIns() {
        val claims = mapOf(
            "realm_access" to JSONObject(mapOf("roles" to org.json.JSONArray(listOf("offline_access", "default-roles-sclera", "field_engineer")))),
        )
        assertEquals("Field engineer", session(token = testJwt(claims)).role)
    }

    // ---- userInfo (/sclera/api/userInfo data)

    @Test
    fun userInfo_roleWinsOverToken() {
        val s = session(token = testJwt(mapOf("role" to "viewer"))).copy(userInfo = """{"role":"ADMIN","email":"a@b.com"}""")
        assertEquals("Admin", s.role)
    }

    @Test
    fun userInfo_withoutRole_fallsBackToToken() {
        val s = session(token = testJwt(mapOf("role" to "viewer"))).copy(userInfo = """{"role":null,"email":"a@b.com"}""")
        assertEquals("Viewer", s.role)
    }

    @Test
    fun userInfo_nameWinsOverToken() {
        val s = session(token = testJwt(mapOf("name" to "token name")))
        assertEquals("Ada Lovelace", s.copy(userInfo = """{"first_name":"Ada","last_name":"Lovelace"}""").displayName)
        assertEquals("Grace", s.copy(userInfo = """{"name":"Grace"}""").displayName)
        assertEquals("token name", s.copy(userInfo = """{"role":"admin"}""").displayName)
        assertEquals("token name", s.copy(userInfo = "not json").displayName)
    }

    @Test
    fun prettyRole_formats() {
        assertEquals("Admin", prettyRole("admin"))
        assertEquals("Super admin", prettyRole("SUPER_ADMIN"))
        assertEquals("Field engineer", prettyRole("field-engineer"))
    }

    @Test
    fun role_fromScleraClientRoles_first() {
        val claims = mapOf(
            "realm_access" to JSONObject(mapOf("roles" to org.json.JSONArray(listOf("offline_access", "default-roles-sclera")))),
            "resource_access" to JSONObject(
                mapOf(
                    "account" to JSONObject(mapOf("roles" to org.json.JSONArray(listOf("manage-account", "view-profile")))),
                    "sclera" to JSONObject(mapOf("roles" to org.json.JSONArray(listOf("admin")))),
                ),
            ),
        )
        assertEquals("Admin", session(token = testJwt(claims)).role)
    }

    @Test
    fun role_fromGroups() =
        assertEquals("Admins", session(token = testJwt(mapOf("groups" to org.json.JSONArray(listOf("/Admins"))))).role)

    @Test
    fun role_fromUserRoleClaim() = assertEquals("Field engineer", session(token = testJwt(mapOf("userRole" to "field-engineer"))).role)

    @Test
    fun describeClaims_listsNamesAndRoleClaimsOnly() {
        val d = describeClaims(testJwt(mapOf("email" to "a@b.com", "roles" to org.json.JSONArray(listOf("admin")))))
        assertTrue(d, "email" in d && "roles=[\"admin\"]" in d)
        assertEquals("token is not a readable JWT", describeClaims("x"))
    }

    @Test
    fun role_nullWhenOnlyBuiltIns() {
        val claims = mapOf("realm_access" to JSONObject(mapOf("roles" to org.json.JSONArray(listOf("offline_access", "uma_authorization")))))
        assertNull(session(token = testJwt(claims)).role)
        assertNull(session(token = null).role)
    }

    // ---- jwtPayload / tokenExpiry ----

    @Test
    fun jwtPayload_decodesClaims() {
        assertEquals("x", jwtPayload(testJwt(mapOf("sub" to "x")))?.getString("sub"))
        assertNull(jwtPayload(null))
        assertNull(jwtPayload("garbage"))
    }

    @Test
    fun tokenExpiry_secondsAreConvertedToMillis() =
        assertEquals(1_800_000_000_000L, tokenExpiry(JSONObject("""{"expiresAt":1800000000}"""), "t"))

    @Test
    fun tokenExpiry_millisAreKept() =
        assertEquals(1_800_000_000_000L, tokenExpiry(JSONObject("""{"expiresAt":1800000000000}"""), "t"))

    @Test
    fun tokenExpiry_fallsBackToJwtExp() =
        assertEquals(1_800_000_000_000L, tokenExpiry(JSONObject("{}"), testJwt(mapOf("exp" to 1_800_000_000L))))

    @Test
    fun tokenExpiry_unknownIsZero() = assertEquals(0L, tokenExpiry(JSONObject("{}"), "not-a-jwt"))

    // ---- cookieValue ----

    @Test
    fun cookieValue_findsNamedCookie() {
        val jar = "JSESSIONID=abc; sessionId=s-123; theme=dark"
        assertEquals("s-123", cookieValue(jar, "sessionId"))
        assertEquals("abc", cookieValue(jar, "JSESSIONID"))
        assertEquals("dark", cookieValue(jar, "theme"))
    }

    @Test
    fun cookieValue_requiresExactName() {
        assertNull(cookieValue("oldsessionId=x; session=y", "sessionId"))
        assertNull(cookieValue("", "sessionId"))
    }

    @Test
    fun cookieValue_keepsEqualsSignsInValue() = assertEquals("a=b==", cookieValue("sessionId=a=b==", "sessionId"))

    // ---- AuthConfig ----

    @Test
    fun loginUrl_matchesWebAppCall() {
        // Web app: `${accounts_url}/sclera/api/user/${email}/getRedirectUrlByEmail?domain=${encodeURIComponent(domain)}`
        assertEquals(
            "https://qa-accounts.sclera.com/sclera/api/user/masteruser.brijesh%40sclera.com/getRedirectUrlByEmail" +
                "?domain=https%3A%2F%2Fqa-app.sclera.com",
            AuthConfig.loginUrl("masteruser.brijesh@sclera.com"),
        )
        assertTrue(AuthConfig.loginUrl("first.last+tag@example.com").contains("/user/first.last%2Btag%40example.com/"))
    }

    @Test
    fun keycloakPages_areNotTheReturnToApp() {
        val keycloak = "https://qa-accounts.sclera.com/auth/realms/Sclera/protocol/openid-connect/auth?response_type=code" +
            "&client_id=sclera&redirect_uri=https://qa-accounts.sclera.com/sclera/oauth2/v1/authorize/callback" +
            "&login_hint=masteruser.brijesh@sclera.com"
        assertFalse(AuthConfig.isReturnUrl(keycloak))
        assertFalse(AuthConfig.isReturnUrl("https://qa-accounts.sclera.com/sclera/oauth2/v1/authorize/callback?code=abc"))
        assertFalse(AuthConfig.isAccountsApiUrl(keycloak))
    }

    // ---- signedInEmail ----

    @Test
    fun signedInEmail_prefersSessionData() =
        assertEquals("s@x.com", signedInEmail(JSONObject("""{"email":"s@x.com"}"""), testJwt(mapOf("email" to "t@x.com"))))

    @Test
    fun signedInEmail_fallsBackToTokenClaims() {
        assertEquals("t@x.com", signedInEmail(JSONObject("{}"), testJwt(mapOf("email" to "t@x.com"))))
        assertEquals("kc@x.com", signedInEmail(JSONObject("{}"), testJwt(mapOf("preferred_username" to "kc@x.com"))))
        assertEquals("u@x.com", signedInEmail(JSONObject("{}"), testJwt(mapOf("upn" to "u@x.com"))))
    }

    @Test
    fun signedInEmail_skipsValuesThatAreNotEmails() =
        assertEquals("t@x.com", signedInEmail(JSONObject("""{"email":""}"""), testJwt(mapOf("preferred_username" to "rashmi", "email" to "t@x.com"))))

    @Test
    fun signedInEmail_nullWhenUnknown() {
        assertNull(signedInEmail(JSONObject("{}"), null))
        assertNull(signedInEmail(JSONObject("{}"), testJwt(mapOf("preferred_username" to "rashmi"))))
    }

    @Test
    fun isReturnUrl_matchesAppHostOnly() {
        assertTrue(AuthConfig.isReturnUrl("https://qa-app.sclera.com/"))
        assertTrue(AuthConfig.isReturnUrl("https://QA-APP.sclera.com/dashboard?x=1"))
        assertFalse(AuthConfig.isReturnUrl("https://qa-accounts.sclera.com/login"))
        assertFalse(AuthConfig.isReturnUrl("https://idp.example.com/?redirect=https://qa-app.sclera.com"))
        assertFalse(AuthConfig.isReturnUrl("https://qa-app.sclera.com.evil.com/"))
        assertFalse(AuthConfig.isReturnUrl(null))
    }

    @Test
    fun isAccountsApiUrl_matchesApiPathOnAccountsHost() {
        assertTrue(AuthConfig.isAccountsApiUrl("https://qa-accounts.sclera.com/sclera/api/user/a/getRedirectUrlByEmail"))
        assertFalse(AuthConfig.isAccountsApiUrl("https://qa-accounts.sclera.com/login"))
        assertFalse(AuthConfig.isAccountsApiUrl("https://qa-app.sclera.com/sclera/api/session"))
        assertFalse(AuthConfig.isAccountsApiUrl(null))
    }

    @Test
    fun accountsHost_isParsedFromUrl() = assertEquals("qa-accounts.sclera.com", AuthConfig.accountsHost)
}
