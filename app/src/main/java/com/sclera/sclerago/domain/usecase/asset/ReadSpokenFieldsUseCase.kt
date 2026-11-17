package com.sclera.sclerago.domain.usecase.asset

import com.sclera.sclerago.domain.model.ANSWER_NO
import com.sclera.sclerago.domain.model.ANSWER_YES
import com.sclera.sclerago.domain.model.CHECKED
import com.sclera.sclerago.domain.model.FieldCfg
import com.sclera.sclerago.domain.model.FieldKind
import com.sclera.sclerago.domain.model.FieldSection
import com.sclera.sclerago.domain.model.UNCHECKED
import javax.inject.Inject

/**
 * The field values in what was said, as "<field name> <value>" pairs in any order ("Serial number 24A-118903
 * manufacturer Daikin"): a field's name marks where its value starts, and the value runs to the next field's name.
 * Values come back as the form stores them, by field key; a field named twice keeps the last value.
 *
 * Choice fields take only one of their own options (several for a multiple choice), yes / no fields and checkboxes a
 * yes or a no, numbers only their digits. Date and time fields and the classification (picked from the type catalogue)
 * are not filled by voice.
 */
class ReadSpokenFieldsUseCase @Inject constructor() {
    operator fun invoke(said: String, fields: List<FieldCfg>): Map<String, String> {
        val words = WORD.findAll(said).map { Word(norm(it.value), it.range.first, it.range.last + 1) }.toList()
        val names = fields
            .filter { it.visible && it.section != FieldSection.CLASS && it.kind !in NOT_SPOKEN }
            .map { it to words(it.label) }
            .filter { it.second.isNotEmpty() }
            .sortedByDescending { it.second.size }
        // Where each field's name was said, left to right; the longest name wins where names overlap ("serial number" over "number").
        val marks = mutableListOf<Mark>()
        var i = 0
        while (i < words.size) {
            val hit = names.firstOrNull { (_, name) -> i + name.size <= words.size && name.indices.all { words[i + it].norm == name[it] } }
            if (hit == null) {
                i++
                continue
            }
            marks += Mark(hit.first, i, i + hit.second.size)
            i += hit.second.size
        }
        return buildMap {
            marks.forEachIndexed { n, m ->
                val end = marks.getOrNull(n + 1)?.let { words[it.from].start } ?: said.length
                val text = clean(said.substring(words[m.to - 1].end, end))
                valueFor(m.field, text)?.let { put(m.field.key, it) }
            }
        }
    }

    /** [text] as [field] stores it, or null when it is not a value the field takes. */
    private fun valueFor(field: FieldCfg, text: String): String? {
        if (text.isEmpty()) return null
        return when (field.kind) {
            FieldKind.NUMBER -> number(text)
            FieldKind.DROPDOWN -> option(field.options, text)
            FieldKind.MULTI -> field.options.filter { o -> contains(words(text), words(o)) }.takeIf { it.isNotEmpty() }?.joinToString(",")
            FieldKind.YESNO -> yesNo(text)?.let { if (it) ANSWER_YES else ANSWER_NO }
            FieldKind.CHECKBOX -> yesNo(text)?.let { if (it) CHECKED else UNCHECKED }
            else -> text
        }
    }

    /** The option said: the one [text] names exactly, else the longest one it mentions. */
    private fun option(options: List<String>, text: String): String? {
        val said = words(text)
        return options.firstOrNull { words(it) == said }
            ?: options.filter { words(it).isNotEmpty() && contains(said, words(it)) }.maxByOrNull { words(it).size }
    }

    private fun yesNo(text: String): Boolean? {
        val first = words(text).firstOrNull() ?: return null
        return when (first) {
            in YES_WORDS -> true
            in NO_WORDS -> false
            else -> null
        }
    }

    /** Only what a number holds: an optional minus, digits and one decimal point (the web's `voiceToNumber`). */
    private fun number(text: String): String? {
        val cleaned = text.replace(NOT_NUMERIC, "")
        val digits = cleaned.replace("-", "")
        val dot = digits.indexOf('.')
        val n = (if (dot < 0) digits else digits.substring(0, dot + 1) + digits.substring(dot + 1).replace(".", "")).removeSuffix(".")
        if (n.none { it.isDigit() }) return null
        return if (cleaned.startsWith("-")) "-$n" else n
    }

    /** The value without the words and marks people put between a name and its value ("is", ":") or after it. */
    private fun clean(text: String) = text.replace(LEADING_FILLER, "").replace(TRAILING_MARKS, "").trim()

    private fun contains(said: List<String>, part: List<String>) = part.isNotEmpty() && said.windowed(part.size).any { it == part }

    private fun words(text: String) = WORD.findAll(text).map { norm(it.value) }.filter { it.isNotEmpty() }.toList()

    private fun norm(word: String) = word.lowercase().filter { it.isLetterOrDigit() }

    private data class Word(val norm: String, val start: Int, val end: Int)

    /** [field]'s name was said from word [from] up to (not including) word [to]. */
    private data class Mark(val field: FieldCfg, val from: Int, val to: Int)

    private companion object {
        val WORD = Regex("""[\p{L}\p{N}]+(?:[-./'][\p{L}\p{N}]+)*""")
        val NOT_NUMERIC = Regex("""[^0-9.-]""")
        val LEADING_FILLER = Regex("""^(?:[\s:=,.\-]+|(?:is|equals|equal to|set to)\b)+""", RegexOption.IGNORE_CASE)
        val TRAILING_MARKS = Regex("""[\s,.;:]+$""")
        val NOT_SPOKEN = setOf(FieldKind.DATE, FieldKind.TIME)
        val YES_WORDS = setOf("yes", "yeah", "yep", "true", "checked", "on")
        val NO_WORDS = setOf("no", "nope", "false", "unchecked", "off", "none")
    }
}
