package com.sclera.sclerago.domain.usecase.config

import com.sclera.sclerago.domain.model.FieldCfg
import com.sclera.sclerago.domain.model.FieldChecks
import com.sclera.sclerago.domain.model.FieldKind
import com.sclera.sclerago.domain.model.FieldSection
import com.sclera.sclerago.domain.model.MAX_TOOLTIP_LENGTH
import com.sclera.sclerago.domain.model.NO_INPUT_KINDS
import com.sclera.sclerago.domain.model.OPTION_KINDS
import com.sclera.sclerago.domain.model.OnlineError
import com.sclera.sclerago.domain.model.OnlineException
import com.sclera.sclerago.domain.model.Permission
import com.sclera.sclerago.domain.model.fieldNameKey
import com.sclera.sclerago.domain.model.optionColorOrNull
import com.sclera.sclerago.domain.repository.ConfigFieldsRepository
import com.sclera.sclerago.domain.repository.FileRepository
import com.sclera.sclerago.domain.usecase.access.PermissionGuard
import javax.inject.Inject

// Configuration → Asset fields: the property's standard fields at the server (as the web does it). Every call needs a
// connection and throws OnlineException; changes need the Configure permission. Photo rules, the geofence and AI in
// validation are in PropertyConfigUseCases.

/** Why a field set up in the field sheet can't be kept. */
enum class FieldProblem { NO_NAME, NO_OPTIONS, DUPLICATE }

/** True when [name] is already used by a field in [fields] other than [key] (names compare without case or spaces). */
fun nameTaken(fields: List<FieldCfg>, name: String, key: String): Boolean {
    val wanted = fieldNameKey(name)
    return wanted.isNotEmpty() && fields.any { it.key != key && fieldNameKey(it.label) == wanted }
}

/**
 * The field as it is kept: name, tooltip (at most [MAX_TOOLTIP_LENGTH]) and options trimmed, options without blanks or
 * repeats, a default that is still one of the options (multiple checkboxes: each of the comma-joined names), the checks
 * that fit its kind, and visible when mandatory (the locked name field is always mandatory and visible).
 */
fun FieldCfg.cleaned(): FieldCfg {
    val opts = options.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
    val default = defaultValue.trim().let { d ->
        when (kind) {
            FieldKind.DROPDOWN -> if (opts.isEmpty() || d in opts) d else ""
            FieldKind.MULTI -> d.split(',').map { it.trim() }.filter { it in opts }.distinct().joinToString(",")
            FieldKind.URL -> ""
            // Image, video, voice memo and key value fields have no default (as on the web).
            in NO_INPUT_KINDS -> ""
            else -> d
        }
    }
    // Colours are kept only for options still there, and only valid "#rrggbb" ones.
    val colors = optionColors.mapKeys { it.key.trim() }.filterKeys { it in opts }.mapNotNull { (k, v) -> optionColorOrNull(v)?.let { k to it } }.toMap()
    return copy(
        label = label.trim(), defaultValue = default, tooltip = tooltip.trim().take(MAX_TOOLTIP_LENGTH), options = opts, optionColors = colors,
    ).withChecks(cleanChecks(kind, checks, required))
        .let { if (it.locked) it.copy(required = true, visible = true) else it.copy(visible = it.visible || it.required) }
}

/** Why [f] can't be kept next to [others] (the other fields), or null. Custom dropdowns and multiple checkboxes need an option. */
fun fieldSetupProblem(f: FieldCfg, others: List<FieldCfg>): FieldProblem? = when {
    f.label.isBlank() -> FieldProblem.NO_NAME
    f.custom && f.kind in OPTION_KINDS && f.options.none { it.isNotBlank() } -> FieldProblem.NO_OPTIONS
    nameTaken(others, f.label, f.key) -> FieldProblem.DUPLICATE
    else -> null
}

class LoadConfigFieldsUseCase @Inject constructor(private val repo: ConfigFieldsRepository) {
    suspend operator fun invoke(propertyId: String): List<FieldCfg> = repo.load(propertyId)
}

/** The eye: shows or hides a field (`isActive`). Mandatory and locked fields stay visible (returned unchanged). */
class ToggleFieldVisibleUseCase @Inject constructor(private val repo: ConfigFieldsRepository, private val guard: PermissionGuard) {
    suspend operator fun invoke(propertyId: String, f: FieldCfg): FieldCfg {
        if (f.locked || (f.required && f.visible)) return f
        guard.requireOnline(Permission.CONFIGURE)
        return repo.update(propertyId, f, f.copy(visible = !f.visible))
    }
}

/** The tick: makes a field mandatory (and so visible) or optional. Locked fields stay mandatory. */
class ToggleFieldRequiredUseCase @Inject constructor(private val repo: ConfigFieldsRepository, private val guard: PermissionGuard) {
    suspend operator fun invoke(propertyId: String, f: FieldCfg): FieldCfg {
        if (f.locked) return f
        guard.requireOnline(Permission.CONFIGURE)
        return repo.update(propertyId, f, f.copy(required = !f.required, visible = f.visible || !f.required))
    }
}

/** Field sheet: saves the field ([before] as it was, [after] as edited) next to [all] the fields. */
class SaveFieldUseCase @Inject constructor(private val repo: ConfigFieldsRepository, private val guard: PermissionGuard) {
    sealed interface Result {
        data class Refused(val problem: FieldProblem) : Result
        data class Saved(val field: FieldCfg) : Result
    }

    suspend operator fun invoke(propertyId: String, before: FieldCfg, after: FieldCfg, all: List<FieldCfg>): Result {
        fieldSetupProblem(after, all)?.let { return Result.Refused(it) }
        guard.requireOnline(Permission.CONFIGURE)
        // The kind of a field never changes once it is made.
        val clean = after.cleaned().copy(kind = before.kind, id = before.id)
        return try {
            Result.Saved(repo.update(propertyId, before, clean))
        } catch (e: OnlineException) {
            if (e.error is OnlineError.Conflict) Result.Refused(FieldProblem.DUPLICATE) else throw e
        }
    }
}

/**
 * Add field sheet: adds the fields set up there together (all or none), skipping unnamed ones and names already
 * used. Returns the fields as the server made them (empty when there was nothing to add).
 */
class AddFieldsUseCase @Inject constructor(private val repo: ConfigFieldsRepository, private val guard: PermissionGuard) {
    suspend operator fun invoke(propertyId: String, existing: List<FieldCfg>, drafts: List<FieldCfg>): List<FieldCfg> {
        val kept = existing.toMutableList()
        val add = mutableListOf<FieldCfg>()
        drafts.forEach { f ->
            val draft = f.copy(section = FieldSection.MORE, custom = true)
            if (fieldSetupProblem(draft, kept) == null) {
                val clean = draft.cleaned()
                add += clean
                kept += clean
            }
        }
        if (add.isEmpty()) return emptyList()
        guard.requireOnline(Permission.CONFIGURE)
        return repo.create(propertyId, add)
    }
}

/** Deletes a custom field. Built-in fields can't be deleted, and the server refuses one that assets have answered. */
class DeleteFieldUseCase @Inject constructor(private val repo: ConfigFieldsRepository, private val guard: PermissionGuard) {
    enum class Result { DELETED, BUILT_IN, IN_USE }

    suspend operator fun invoke(propertyId: String, f: FieldCfg): Result {
        if (!f.custom) return Result.BUILT_IN
        guard.requireOnline(Permission.CONFIGURE)
        return try {
            repo.delete(propertyId, f.id)
            Result.DELETED
        } catch (e: OnlineException) {
            if (e.error is OnlineError.Conflict) Result.IN_USE else throw e
        }
    }
}

/** Checks sheet: what validation looks for in the field. Returns why the checks were refused, or the field as saved. */
class SetFieldChecksUseCase @Inject constructor(private val repo: ConfigFieldsRepository, private val guard: PermissionGuard) {
    sealed interface Result {
        data class Refused(val problem: ChecksProblem) : Result
        data class Saved(val field: FieldCfg) : Result
    }

    suspend operator fun invoke(propertyId: String, f: FieldCfg, checks: FieldChecks): Result {
        checksProblem(f.kind, checks)?.let { return Result.Refused(it) }
        guard.requireOnline(Permission.CONFIGURE)
        return Result.Saved(repo.update(propertyId, f, f.withChecks(cleanChecks(f.kind, checks, f.required))))
    }
}

/**
 * Import from Excel: one field per column. The header row names the fields; the values below pick
 * each field's kind (numbers, dates, yes/no, links, a short list → dropdown, else text). Columns that
 * match a field already here are skipped. The new fields are returned for review, not added.
 */
class ImportFieldsUseCase @Inject constructor(private val files: FileRepository) {
    sealed interface Result {
        data object Unreadable : Result
        data object NothingNew : Result
        data class Fields(val fields: List<FieldCfg>) : Result
    }

    suspend operator fun invoke(existing: List<FieldCfg>, uri: String): Result {
        val rows = files.readSheet(uri)
        val header = rows?.firstOrNull() ?: return Result.Unreadable
        val have = existing.map { fieldNameKey(it.label) }.toMutableSet()
        val stamp = System.currentTimeMillis()
        val made = header.mapIndexedNotNull { c, h ->
            val label = h.trim()
            if (label.isEmpty() || !have.add(fieldNameKey(label))) return@mapIndexedNotNull null
            val vals = rows.drop(1).mapNotNull { it.getOrNull(c)?.trim()?.takeIf(String::isNotEmpty) }
            val distinct = vals.distinct()
            val guessed = when {
                vals.isEmpty() -> FieldKind.TEXT
                vals.all { it.toDoubleOrNull() != null } -> FieldKind.NUMBER
                vals.all { Regex("^\\d{1,4}[-/.]\\d{1,2}[-/.]\\d{1,4}$").matches(it) } -> FieldKind.DATE
                vals.all { it.lowercase() in setOf("yes", "no", "true", "false", "y", "n") } -> FieldKind.YESNO
                vals.all { it.startsWith("http", ignoreCase = true) } -> FieldKind.URL
                distinct.size <= 8 && vals.size >= distinct.size * 2 -> FieldKind.DROPDOWN
                else -> FieldKind.TEXT
            }
            // Option names can't hold / , { } (the web's rule); a list with no usable option becomes text.
            val options = if (guessed == FieldKind.DROPDOWN) distinct.filter { o -> o.none { ch -> ch in "/,{}" } } else emptyList()
            val kind = if (guessed == FieldKind.DROPDOWN && options.isEmpty()) FieldKind.TEXT else guessed
            FieldCfg("cf_${stamp}_$c", label, kind, FieldSection.MORE, custom = true, options = options)
        }
        return if (made.isEmpty()) Result.NothingNew else Result.Fields(made)
    }
}

/** The Asset fields use cases, for injecting as one. */
class FieldsConfigUseCases @Inject constructor(
    val load: LoadConfigFieldsUseCase,
    val toggleVisible: ToggleFieldVisibleUseCase,
    val toggleRequired: ToggleFieldRequiredUseCase,
    val save: SaveFieldUseCase,
    val add: AddFieldsUseCase,
    val delete: DeleteFieldUseCase,
    val setChecks: SetFieldChecksUseCase,
    val importFields: ImportFieldsUseCase,
)
