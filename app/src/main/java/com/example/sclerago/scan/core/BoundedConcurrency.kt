//
//  BoundedConcurrency.kt
//  AssetOnBoard
//
//  Runs a large batch of probes with a fixed number in flight.
//
//  The iOS build hand-rolls this over a TaskGroup, refilling it as each child
//  finishes. Coroutines make the same shape a worker pool: N consumers pulling
//  from one channel, results funnelled back through another so the caller sees
//  them as they land rather than in a lump at the end.
//
//  A plain `map { async { it() } }` would not do — a /23 sweep is tens of
//  thousands of jobs, and launching them all at once exhausts the descriptor
//  budget long before the first one times out.
//

package com.example.sclerago.scan.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch

/**
 * Runs [jobs] with at most [concurrency] active, invoking [onResult] for each
 * completed job in completion order.
 *
 * [onResult] is called from a single coroutine, so it needs no locking of its
 * own even though the jobs themselves run in parallel.
 */
suspend fun <T> runJobs(
    jobs: List<suspend () -> T>,
    concurrency: Int,
    onResult: suspend (T) -> Unit,
) {
    if (jobs.isEmpty()) return
    val width = concurrency.coerceIn(1, jobs.size)

    coroutineScope {
        val work = Channel<suspend () -> T>(capacity = width)
        val results = Channel<T>(capacity = Channel.BUFFERED)

        val workers = List(width) {
            launch(Dispatchers.IO) {
                for (job in work) {
                    if (!isActive) break
                    val value = job()
                    results.send(value)
                }
            }
        }

        val feeder = launch(Dispatchers.Default) {
            for (job in jobs) {
                if (!isActive) break
                work.send(job)
            }
            work.close()
        }

        launch(Dispatchers.Default) {
            workers.joinAll()
            feeder.join()
            results.close()
        }

        for (value in results) onResult(value)
    }
}
