package com.sclera.sclerago.domain.usecase.asset

import com.sclera.sclerago.domain.model.Asset
import com.sclera.sclerago.domain.model.AssetFilter
import com.sclera.sclerago.domain.model.AssetFilterOptions
import com.sclera.sclerago.domain.model.AuthState
import com.sclera.sclerago.domain.model.FilterChoice
import com.sclera.sclerago.domain.model.FilterPerson
import com.sclera.sclerago.domain.model.MasterFormat
import com.sclera.sclerago.domain.model.OnlineException
import com.sclera.sclerago.domain.model.typeKey
import com.sclera.sclerago.domain.model.typeKeyParts
import com.sclera.sclerago.domain.repository.AccessRepository
import com.sclera.sclerago.domain.repository.AssetAssignmentRepository
import com.sclera.sclerago.domain.repository.AssetFormRepository
import com.sclera.sclerago.domain.repository.AuthRepository
import com.sclera.sclerago.domain.usecase.access.PermissionGuard
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/** Shown between a sub type's system type and asset type ("HVAC › Pumps"), as the web writes a type. */
private const val TYPE_PATH = " › "

/**
 * The filter sheet's choices for [assets] (a tab's assets in the chosen location), with [filter] as chosen so far. Like the
 * web's filters, each part counts what it would show given the other: the people are counted among the assets of the
 * types chosen, and the types among the assets of the people chosen. An asset type lists under the system types chosen
 * (all of them when none is), a sub type under the asset types chosen (else the systems chosen). Only choices with assets
 * are listed, plus anything already chosen. The signed-in user ([me]) is always listed, first; [names] are the people's
 * names by user id; [everyone] are more people to list even with no assets in this list (the property's assignees, as
 * kept on the device).
 */
class BuildFilterOptionsUseCase @Inject constructor() {
    operator fun invoke(
        assets: List<Asset>,
        formats: Map<String, MasterFormat>,
        filter: AssetFilter,
        me: String?,
        names: Map<String, String>,
        everyone: Collection<String> = emptyList(),
    ): AssetFilterOptions {
        val ofTypes = assets.filter { filter.typeMatches(it.masterFormatId, formats) }
        val ofPeople = assets.filter(filter::peopleMatch)
        val typed = ofPeople.mapNotNull { a -> a.masterFormatId?.let(formats::get) }

        val systems = choices(
            typed.groupingBy { it.systemType }.eachCount(), filter.systems, label = { it }, parent = { null },
        )
        val typeScope = typed.filter { filter.systems.isEmpty() || it.systemType in filter.systems }
        val typeCounts = typed.mapNotNull { f -> f.assetType?.let { typeKey(f.systemType, it) } }.groupingBy { it }.eachCount()
        val types = choices(
            typeScope.mapNotNull { f -> f.assetType?.let { typeKey(f.systemType, it) } }.groupingBy { it }.eachCount(),
            filter.types, label = { typeKeyParts(it).second }, parent = { typeKeyParts(it).first }, chosenCounts = typeCounts,
        )
        val subScope = typed.filter { f ->
            when {
                filter.types.isNotEmpty() -> f.assetType != null && typeKey(f.systemType, f.assetType) in filter.types
                filter.systems.isNotEmpty() -> f.systemType in filter.systems
                else -> true
            }
        }
        val subtypes = choices(
            subScope.filter { it.subtype != null }.groupingBy { it.id }.eachCount(), filter.subtypes,
            label = { formats[it]?.subtype.orEmpty() },
            parent = { id -> formats[id]?.let { f -> listOfNotNull(f.systemType, f.assetType).joinToString(TYPE_PATH) } },
            chosenCounts = typed.groupingBy { it.id }.eachCount(),
        )
        return AssetFilterOptions(
            people = people(ofTypes, filter.assignees, me, names, everyone),
            systems = systems,
            types = types,
            subtypes = subtypes,
            untypedCount = ofPeople.count { a -> a.masterFormatId?.let(formats::get) == null },
            untypedSelected = filter.untyped,
        )
    }

    /**
     * The choices with assets ([counts]), plus the ones [chosen] already; a chosen one outside the list's scope (an asset
     * type under a system type no longer chosen) is counted from [chosenCounts].
     */
    private fun choices(
        counts: Map<String, Int>,
        chosen: Set<String>,
        label: (String) -> String,
        parent: (String) -> String?,
        chosenCounts: Map<String, Int> = counts,
    ): List<FilterChoice> {
        val keys = (counts.keys + chosen).filter { label(it).isNotBlank() }
        val list = keys.map { FilterChoice(it, label(it), parent(it), counts[it] ?: chosenCounts[it] ?: 0, it in chosen) }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
        // The parent only helps tell choices apart when they don't all sit under the same one.
        return if (list.mapTo(HashSet()) { it.parent }.size <= 1) list.map { it.copy(parent = null) } else list
    }

    private fun people(
        assets: List<Asset>,
        chosen: Set<String>,
        me: String?,
        names: Map<String, String>,
        everyone: Collection<String>,
    ): List<FilterPerson> {
        val counts = assets.mapNotNull { it.assignedTo }.groupingBy { it }.eachCount()
        val ids = (listOfNotNull(me) + counts.keys + chosen + everyone).distinct()
        return ids.map { FilterPerson(it, names[it], it == me, counts[it] ?: 0, it in chosen) }
            .sortedWith(compareByDescending<FilterPerson> { it.me }.thenBy { (it.name ?: "￿").lowercase() })
    }
}

/**
 * The names of the people assets are assigned to, by user id: the people kept on this device (admins have the list), the
 * signed-in user's own name, and, when [propertyId] is given and the device is online, who the server says has assets
 * there (`GET /assets/assignees`, which anyone who can open the property may read). Offline, the server's part is skipped
 * and what is known is returned.
 */
class LoadAssigneeNamesUseCase @Inject constructor(
    private val access: AccessRepository,
    private val assignment: AssetAssignmentRepository,
    private val auth: AuthRepository,
    private val guard: PermissionGuard,
) {
    /**
     * The people [propertyId]'s assets are assigned to, as kept on the device for every user (`property_assignees`), by
     * user id to name, else email: the filter lists each of them, admins and everyone else alike.
     */
    fun storedFor(propertyId: String?): Map<String, String> =
        propertyId?.takeIf { it.isNotBlank() }?.let { assignment.keptAssigneesOf(it).mapValues { (_, a) -> a.label } }.orEmpty()

    /** Reads [propertyId]'s kept assignees and, online, fetches them again (when the property is opened). */
    suspend fun open(propertyId: String) = assignment.openAssignees(propertyId)

    suspend operator fun invoke(propertyId: String?): Map<String, String> {
        val known = access.people.value.associate { it.id to it.name }.toMutableMap()
        assignment.keptAssignees().forEach { (id, a) -> known.putIfAbsent(id, a.label) }
        val myName = (auth.state.value as? AuthState.SignedIn)?.session?.displayName
        guard.userId()?.let { id -> myName?.takeIf { it.isNotBlank() }?.let { known.putIfAbsent(id, it) } }
        if (propertyId.isNullOrBlank()) return known
        return try {
            known + assignment.assigneeNames(propertyId)
        } catch (_: OnlineException) {
            known
        }
    }
}

/** The whole asset type catalogue by id, inactive types included (assets keep pointing at a type switched off). */
class ObserveFormatsByIdUseCase @Inject constructor(private val repo: AssetFormRepository) {
    operator fun invoke(): Flow<Map<String, MasterFormat>> = repo.formatsById()
}

/** The asset list filter's use cases, for injecting as one; [me] is the signed-in user's id. */
class AssetFilterUseCases @Inject constructor(
    val options: BuildFilterOptionsUseCase,
    val names: LoadAssigneeNamesUseCase,
    val formats: ObserveFormatsByIdUseCase,
    private val guard: PermissionGuard,
) {
    fun me(): String? = guard.userId()
}
