package com.sclera.sclerago.domain.model

/** How sure voice fill must be of a value for it to start ticked (the server keeps uncertain ones, like a date without a day, below this). */
const val VOICE_SURE_ENOUGH = 0.7

/** One field of the Info form that voice fill may fill: its form key, its name, its kind, what it holds now and its choices. */
data class VoiceField(val key: String, val label: String, val kind: FieldKind, val value: String, val options: List<String> = emptyList())

/**
 * What the server's voice fill read from what was said: new values by form key (as the form stores them), how sure it
 * is of each (0..1), the keys whose new value differs from what the form has, and what was said that no field took.
 */
data class VoiceFillResult(
    val values: Map<String, String>,
    val confidence: Map<String, Double> = emptyMap(),
    val differs: Set<String> = emptySet(),
    val unmatched: List<Unheard> = emptyList(),
)

/** Something said that no field took: the [text], and why (as the server says it; null when it gives no reason). */
data class Unheard(val text: String, val reason: String?)
