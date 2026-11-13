package com.sclera.sclerago.data.remote.adc

import com.sclera.sclerago.domain.model.OnlineError
import com.sclera.sclerago.domain.model.OnlineException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Asks for a server job until [finished] says it is done: the wait grows from [FIRST_DELAY_MS] by a second each
 * time up to [MAX_DELAY_MS], and the job is given up on after [limitMs] ([OnlineError.Server]). Cancelling the
 * caller stops the polling at the next wait (the job carries on at the server).
 */
internal suspend fun <T : Any> pollUntil(limitMs: Long, finished: (T) -> Boolean, fetch: suspend () -> T): T {
    val last = withTimeoutOrNull(limitMs) {
        var wait = FIRST_DELAY_MS
        var job = fetch()
        while (!finished(job)) {
            delay(wait)
            wait = (wait + FIRST_DELAY_MS).coerceAtMost(MAX_DELAY_MS)
            job = fetch()
        }
        job
    }
    return last ?: throw OnlineException(OnlineError.Server)
}

private const val FIRST_DELAY_MS = 1_000L
private const val MAX_DELAY_MS = 5_000L
