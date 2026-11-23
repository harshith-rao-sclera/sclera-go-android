package com.sclera.sclerago.presentation.common

/** [this] cut to at most [max] characters with "…" in the middle (a long email keeps its start and its end). */
fun String.ellipsizeMiddle(max: Int): String {
    if (length <= max || max < 3) return this
    val head = (max - 1) / 2 + 1
    val tail = max - 1 - head
    return take(head) + "…" + takeLast(tail)
}
