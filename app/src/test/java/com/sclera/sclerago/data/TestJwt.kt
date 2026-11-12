package com.sclera.sclerago.data

import com.sclera.sclerago.data.local.AuthSession
import com.sclera.sclerago.data.local.AuthStore
import com.sclera.sclerago.data.mapper.*
import com.sclera.sclerago.data.remote.*
import com.sclera.sclerago.domain.model.AuthError

import org.json.JSONObject
import java.util.Base64

/** Builds an unsigned JWT with [claims] as the payload (only the payload is read by the app). */
internal fun testJwt(claims: Map<String, Any>): String {
    val enc = Base64.getUrlEncoder().withoutPadding()
    val header = enc.encodeToString("""{"alg":"none","typ":"JWT"}""".toByteArray())
    val payload = enc.encodeToString(JSONObject(claims).toString().toByteArray())
    return "$header.$payload.sig"
}
