package com.sclera.sclerago.domain.usecase.config

import com.sclera.sclerago.domain.model.AssetConfig
import com.sclera.sclerago.domain.model.FieldCfg
import com.sclera.sclerago.domain.model.FieldKind
import com.sclera.sclerago.domain.model.FieldSection
import com.sclera.sclerago.domain.model.PhotoReq
import com.sclera.sclerago.domain.model.Permission
import com.sclera.sclerago.domain.model.ValueRule
import com.sclera.sclerago.domain.repository.AssetConfigRepository
import com.sclera.sclerago.domain.repository.FileRepository
import com.sclera.sclerago.domain.usecase.access.PermissionGuard
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

// Configuration → Asset fields, photos and geofencing.

class ObserveAssetConfigUseCase @Inject constructor(private val repo: AssetConfigRepository) {
    operator fun invoke(): StateFlow<AssetConfig> = repo.config
}

/** Changes the configured fields; needs the Configure permission. */
private suspend fun AssetConfigRepository.updateFields(guard: PermissionGuard, f: (List<FieldCfg>) -> List<FieldCfg>) {
    guard.requireOffline(Permission.CONFIGURE)
    update { it.copy(fields = f(it.fields)) }
}

/** Changes the configuration other than the fields; needs the Configure permission. */
private suspend fun AssetConfigRepository.updateConfig(guard: PermissionGuard, f: (AssetConfig) -> AssetConfig) {
    guard.requireOffline(Permission.CONFIGURE)
    update(f)
}

/** Shows or hides a field on the asset form. Mandatory and locked fields always stay visible. */
class ToggleFieldVisibleUseCase @Inject constructor(private val repo: AssetConfigRepository, private val guard: PermissionGuard) {
    enum class Result { CHANGED, UNCHANGED, MANDATORY }

    suspend operator fun invoke(key: String): Result {
        val f = repo.config.value.fields.firstOrNull { it.key == key } ?: return Result.UNCHANGED
        if (f.locked) return Result.UNCHANGED
        if (f.required && f.visible) return Result.MANDATORY
        repo.updateFields(guard) { l -> l.map { if (it.key == key) it.copy(visible = !it.visible) else it } }
        return Result.CHANGED
    }
}

/** Makes a field mandatory (and so visible) or optional. Locked fields stay mandatory. */
class ToggleFieldRequiredUseCase @Inject constructor(private val repo: AssetConfigRepository, private val guard: PermissionGuard) {
    suspend operator fun invoke(key: String) = repo.updateFields(guard) { l ->
        l.map { f -> if (f.key != key || f.locked) f else f.copy(required = !f.required, visible = f.visible || !f.required) }
    }
}

/** Adds a custom field under More details; false when it has no name. */
class AddFieldUseCase @Inject constructor(private val repo: AssetConfigRepository, private val guard: PermissionGuard) {
    suspend operator fun invoke(label: String, kind: FieldKind, required: Boolean): Boolean {
        val l = label.trim()
        if (l.isEmpty()) return false
        val key = "cf_" + System.currentTimeMillis()
        repo.updateFields(guard) { it + FieldCfg(key, l, kind, FieldSection.MORE, visible = true, required = required, custom = true) }
        return true
    }
}

/** Removes a custom field (built-in ones stay). */
class RemoveFieldUseCase @Inject constructor(private val repo: AssetConfigRepository, private val guard: PermissionGuard) {
    suspend operator fun invoke(key: String) = repo.updateFields(guard) { l -> l.filter { it.key != key || !it.custom } }
}

/** Field sheet: saves the whole field (mandatory makes it visible; the name field stays mandatory). False when it has no name. */
class SaveFieldUseCase @Inject constructor(private val repo: AssetConfigRepository, private val guard: PermissionGuard) {
    suspend operator fun invoke(f: FieldCfg): Boolean {
        if (f.label.isBlank()) return false
        val clean = f.copy(
            label = f.label.trim(), defaultValue = f.defaultValue.trim(), tooltip = f.tooltip.trim(),
            options = f.options.map { it.trim() }.filter { it.isNotEmpty() }.distinct(),
        ).let { if (it.locked) it.copy(required = true, visible = true) else it.copy(visible = it.visible || it.required) }
        repo.updateFields(guard) { l -> l.map { if (it.key == f.key) clean else it } }
        return true
    }
}

/** Add field sheet: adds the fields set up there under More details, skipping names already used. Returns how many. */
class AddFieldsUseCase @Inject constructor(private val repo: AssetConfigRepository, private val guard: PermissionGuard) {
    suspend operator fun invoke(list: List<FieldCfg>): Int {
        val have = repo.config.value.fields.map { it.label.lowercase() }.toSet()
        val add = list.filter { it.label.isNotBlank() && it.label.trim().lowercase() !in have }
            .map { it.copy(label = it.label.trim(), section = FieldSection.MORE, custom = true, visible = it.visible || it.required) }
        if (add.isNotEmpty()) repo.updateFields(guard) { it + add }
        return add.size
    }
}

/**
 * Import from Excel: one field per column. The header row names the fields; the values below pick
 * each field's kind (numbers, dates, yes/no, links, a short list → dropdown, else text). Columns that
 * match a field already here are skipped. The new fields are returned for review, not added.
 */
class ImportFieldsUseCase @Inject constructor(
    private val repo: AssetConfigRepository,
    private val files: FileRepository,
) {
    sealed interface Result {
        data object Unreadable : Result
        data object NothingNew : Result
        data class Fields(val fields: List<FieldCfg>) : Result
    }

    suspend operator fun invoke(uri: String): Result {
        val rows = files.readSheet(uri)
        val header = rows?.firstOrNull() ?: return Result.Unreadable
        val have = repo.config.value.fields.map { it.label.lowercase() }.toSet()
        val stamp = System.currentTimeMillis()
        val made = header.mapIndexedNotNull { c, h ->
            val label = h.trim()
            if (label.isEmpty() || label.lowercase() in have) return@mapIndexedNotNull null
            val vals = rows.drop(1).mapNotNull { it.getOrNull(c)?.trim()?.takeIf(String::isNotEmpty) }
            val distinct = vals.distinct()
            val kind = when {
                vals.isEmpty() -> FieldKind.TEXT
                vals.all { it.toDoubleOrNull() != null } -> FieldKind.NUMBER
                vals.all { Regex("^\\d{1,4}[-/.]\\d{1,2}[-/.]\\d{1,4}$").matches(it) } -> FieldKind.DATE
                vals.all { it.lowercase() in setOf("yes", "no", "true", "false", "y", "n") } -> FieldKind.YESNO
                vals.all { it.startsWith("http", ignoreCase = true) } -> FieldKind.URL
                distinct.size <= 8 && vals.size >= distinct.size * 2 -> FieldKind.DROPDOWN
                else -> FieldKind.TEXT
            }
            FieldCfg(
                "cf_${stamp}_$c", label, kind, FieldSection.MORE, custom = true,
                options = if (kind == FieldKind.DROPDOWN) distinct else emptyList(),
            )
        }
        return if (made.isEmpty()) Result.NothingNew else Result.Fields(made)
    }
}

/** Checks sheet: what validation looks for in the field. */
class SetFieldChecksUseCase @Inject constructor(private val repo: AssetConfigRepository, private val guard: PermissionGuard) {
    suspend operator fun invoke(key: String, flagEmpty: Boolean, rule: ValueRule, min: String, max: String) = repo.updateFields(guard) { l ->
        l.map { if (it.key == key) it.copy(flagEmpty = flagEmpty, rule = rule, min = min.trim(), max = max.trim()) else it }
    }
}

class SetFieldOptionsUseCase @Inject constructor(private val repo: AssetConfigRepository, private val guard: PermissionGuard) {
    suspend operator fun invoke(key: String, options: List<String>) {
        val clean = options.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        repo.updateFields(guard) { l -> l.map { if (it.key == key) it.copy(options = clean) else it } }
    }
}

/** How many photos of a kind are needed (0–9) and allowed (at least 1, at least the minimum). */
class SetPhotoLimitsUseCase @Inject constructor(private val repo: AssetConfigRepository, private val guard: PermissionGuard) {
    suspend operator fun invoke(id: String, min: Int, max: Int) = repo.updateConfig(guard) { c ->
        c.copy(photoReqs = c.photoReqs.map {
            if (it.id != id) it else {
                val mn = min.coerceIn(0, 9)
                it.copy(min = mn, max = max.coerceIn(maxOf(mn, 1), 9))
            }
        })
    }
}

/** Adds a kind of photo; false when it has no name. */
class AddPhotoReqUseCase @Inject constructor(private val repo: AssetConfigRepository, private val guard: PermissionGuard) {
    suspend operator fun invoke(name: String, hint: String): Boolean {
        if (name.isBlank()) return false
        repo.updateConfig(guard) { it.copy(photoReqs = it.photoReqs + PhotoReq("p" + System.currentTimeMillis(), name.trim(), hint.trim(), 0, 1)) }
        return true
    }
}

class RemovePhotoReqUseCase @Inject constructor(private val repo: AssetConfigRepository, private val guard: PermissionGuard) {
    suspend operator fun invoke(id: String) = repo.updateConfig(guard) { c -> c.copy(photoReqs = c.photoReqs.filter { it.id != id }) }
}

class SetGeoEnforceUseCase @Inject constructor(private val repo: AssetConfigRepository, private val guard: PermissionGuard) {
    suspend operator fun invoke(on: Boolean) = repo.updateConfig(guard) { it.copy(geoEnforce = on) }
}

/** The asset configuration use cases, for injecting as one. */
class AssetConfigUseCases @Inject constructor(
    val observe: ObserveAssetConfigUseCase,
    val toggleVisible: ToggleFieldVisibleUseCase,
    val toggleRequired: ToggleFieldRequiredUseCase,
    val addField: AddFieldUseCase,
    val removeField: RemoveFieldUseCase,
    val saveField: SaveFieldUseCase,
    val addFields: AddFieldsUseCase,
    val importFields: ImportFieldsUseCase,
    val setChecks: SetFieldChecksUseCase,
    val setOptions: SetFieldOptionsUseCase,
    val setPhotoLimits: SetPhotoLimitsUseCase,
    val addPhotoReq: AddPhotoReqUseCase,
    val removePhotoReq: RemovePhotoReqUseCase,
    val setGeoEnforce: SetGeoEnforceUseCase,
)
