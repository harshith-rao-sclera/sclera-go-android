package com.sclera.sclerago.data.remote

/**
 * Runs Sclera API calls with the signed-in user's access token: a token the server rejects (401/403)
 * is regenerated and the call retried once, and errors that end the session sign the user out.
 * Throws [com.sclera.sclerago.domain.model.AuthError] for any failed call.
 */
fun interface AuthorizedCaller {
    suspend fun call(request: suspend (accessToken: String) -> HttpResult): HttpResult
}
