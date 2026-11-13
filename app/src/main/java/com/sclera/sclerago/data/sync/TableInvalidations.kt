package com.sclera.sclerago.data.sync

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import javax.inject.Inject
import javax.inject.Singleton

/** Tells repositories which tables of the offline database changed (by the app or by the sync), so they can query again. */
@Singleton
class TableInvalidations @Inject constructor() {
    private val events = MutableSharedFlow<Set<String>>(extraBufferCapacity = 64, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    fun emit(tables: Set<String>) {
        if (tables.isNotEmpty()) events.tryEmit(tables)
    }

    /** Every table may have changed (the database just became ready). */
    fun emitAll() = emit(setOf(ALL))

    /** Emits once straight away, then whenever one of [tables] changes. */
    fun observe(vararg tables: String): Flow<Unit> =
        events.filter { changed -> ALL in changed || tables.any { it in changed } }.map { }.onStart { emit(Unit) }

    private companion object {
        const val ALL = "*"
    }
}
