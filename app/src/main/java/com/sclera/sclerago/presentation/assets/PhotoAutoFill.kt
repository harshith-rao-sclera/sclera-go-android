package com.sclera.sclerago.presentation.assets

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * When to fill the form from photos just added, for the details view model (runs in its [scope]): half a second after
 * the last photos are added, so the new ones have joined the page; adding more meanwhile starts again with the latest
 * set (a look-up under way is dropped).
 */
internal class PhotoAutoFill(private val scope: CoroutineScope, private val run: suspend () -> Unit) {
    private var job: Job? = null

    fun schedule() {
        job?.cancel()
        job = scope.launch {
            delay(WAIT_MS)
            run()
        }
    }

    fun cancel() {
        job?.cancel()
        job = null
    }

    private companion object {
        const val WAIT_MS = 500L
    }
}
