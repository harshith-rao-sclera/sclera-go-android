package com.sclera.sclerago.data.remote.adc

import com.sclera.sclerago.domain.repository.AccessRefusals
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The 403s [OnlineCall] gets from the ADC API, as one signal: many refusals at once count as one (only the latest is
 * held while the last one is being acted on), and nothing about the call (its URL, body or the server's text) is kept.
 */
@Singleton
class AccessRefusalsImpl @Inject constructor() : AccessRefusals {
    private val events = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    override val refused: Flow<Unit> = events.asSharedFlow()

    /** A call was refused (403). */
    fun report() {
        events.tryEmit(Unit)
    }
}
