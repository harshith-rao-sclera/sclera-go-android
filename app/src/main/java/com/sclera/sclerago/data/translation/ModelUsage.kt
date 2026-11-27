package com.sclera.sclerago.data.translation

/**
 * Which downloaded language models were used most recently, so only the last [limit] stay on the phone.
 * [touch] marks a model used and returns the ones to delete.
 */
internal class ModelUsage(private val store: Store, private val limit: Int = MAX_MODELS) {
    /** Where the order is kept (SharedPreferences `mlkit_translate_prefs`). */
    interface Store {
        fun read(): List<String>
        fun write(order: List<String>)
    }

    fun touch(model: String): List<String> {
        val order = (store.read().filter { it != model } + model)
        val evict = order.dropLast(limit)
        store.write(order.takeLast(limit))
        return evict
    }

    fun forget(model: String) = store.write(store.read().filter { it != model })

    companion object {
        const val MAX_MODELS = 4
    }
}
