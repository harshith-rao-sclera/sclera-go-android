package com.sclera.sclerago.data.local

import com.sclera.sclerago.domain.model.TypeField
import com.sclera.sclerago.domain.repository.UserDataCleaner
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The fields each type of a property brings (`GET /master-formats/fields`), kept per property so a collector can pick a
 * type and see its fields with no signal, and so saving an asset offline can add them. Held in memory and in the
 * [EncryptedCache] (wiped on sign-out with it). Blocking: call on the IO dispatcher.
 */
@Singleton
class TypeFieldsStore @Inject constructor(private val cache: EncryptedCache) : UserDataCleaner {
    private val byProperty = ConcurrentHashMap<String, Map<String, List<TypeField>>>()

    /** The fields kept for the type [masterFormatId] of [property], or null when none are kept. */
    fun get(property: String, masterFormatId: String): List<TypeField>? = load(property)[masterFormatId]

    /** Keeps [fields] for the type [masterFormatId] of [property], next to the others. */
    fun put(property: String, masterFormatId: String, fields: List<TypeField>) = save(property, load(property) + (masterFormatId to fields))

    /** Replaces everything kept for [property] with [all] (type id to its fields). */
    fun putAll(property: String, all: Map<String, List<TypeField>>) = save(property, all)

    override fun clear() = byProperty.clear()

    private fun load(property: String): Map<String, List<TypeField>> = byProperty.getOrPut(property) {
        cache.read(name(property))?.let { text -> runCatching { json.decodeFromString(SERIALIZER, text) }.getOrNull() }
            ?.mapValues { (_, list) -> list.map { it.toField() } }
            .orEmpty()
    }

    private fun save(property: String, all: Map<String, List<TypeField>>) {
        byProperty[property] = all
        runCatching { cache.write(name(property), json.encodeToString(SERIALIZER, all.mapValues { (_, list) -> list.map { it.toSaved() } })) }
    }

    private fun name(property: String) = "type_fields_" + property.filter { it.isLetterOrDigit() || it == '-' }

    /** A type field as kept on the device. */
    @Serializable
    private data class Saved(val name: String, val type: String, val options: List<String> = emptyList(), val helpText: String? = null, val position: Int = 0)

    private fun Saved.toField() = TypeField(name, type, options, helpText, position)
    private fun TypeField.toSaved() = Saved(name, type, options, helpText, position)

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
        val SERIALIZER = MapSerializer(String.serializer(), ListSerializer(Saved.serializer()))
    }
}
