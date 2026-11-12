package com.sclera.sclerago.presentation.common

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * A repository's [StateFlow] as a ViewModel reads it: [value] is always the flow's current value
 * (so a read right after a change sees it), and reading it in a composable recomposes when it changes.
 */
class Observed<T>(private val flow: StateFlow<T>, scope: CoroutineScope) {
    private var version by mutableIntStateOf(0)

    init {
        scope.launch { flow.collect { version++ } }
    }

    val value: T
        get() {
            version // registers the read with Compose
            return flow.value
        }
}

fun <T> StateFlow<T>.observedIn(scope: CoroutineScope) = Observed(this, scope)
