package com.example.sclerago.auth

import org.json.JSONObject
import java.util.Base64

/** Builds an unsigned JWT with [claims] as the payload (only the payload is read by the app). */
internal fun testJwt(claims: Map<String, Any>): String {
    val enc = Base64.getUrlEncoder().withoutPadding()
    val header = enc.encodeToString("""{"alg":"none","typ":"JWT"}""".toByteArray())
    val payload = enc.encodeToString(JSONObject(claims).toString().toByteArray())
    return "$header.$payload.sig"
}
