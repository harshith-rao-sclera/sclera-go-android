package com.sclera.sclerago.data.translation

import android.content.Context

/** [ModelUsage.Store] in SharedPreferences: language codes only, nothing about the user. */
internal class PrefsModelUsageStore(context: Context) : ModelUsage.Store {
    private val prefs = context.getSharedPreferences("mlkit_translate_prefs", Context.MODE_PRIVATE)

    override fun read(): List<String> = prefs.getString(KEY, "").orEmpty().split(',').filter { it.isNotEmpty() }

    override fun write(order: List<String>) = prefs.edit().putString(KEY, order.joinToString(",")).apply()

    private companion object {
        const val KEY = "order"
    }
}
