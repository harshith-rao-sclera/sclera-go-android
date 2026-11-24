package com.sclera.sclerago.domain.usecase.config

import com.sclera.sclerago.domain.model.Currency
import com.sclera.sclerago.domain.model.FieldCfg
import com.sclera.sclerago.domain.model.FieldChecks
import com.sclera.sclerago.domain.model.FieldKind
import com.sclera.sclerago.domain.model.FieldSection
import com.sclera.sclerago.domain.model.FieldsRead
import com.sclera.sclerago.domain.model.MAX_TOOLTIP_LENGTH
import com.sclera.sclerago.domain.model.NO_INPUT_KINDS
import com.sclera.sclerago.domain.model.OPTION_KINDS
import com.sclera.sclerago.domain.model.OnlineError
import com.sclera.sclerago.domain.model.OnlineException
import com.sclera.sclerago.domain.model.Permission
import com.sclera.sclerago.domain.model.fieldNameKey
import com.sclera.sclerago.domain.model.optionColorOrNull
import com.sclera.sclerago.domain.repository.ConfigFieldsRepository
import com.sclera.sclerago.domain.repository.CurrencyRepository
import com.sclera.sclerago.domain.repository.FileRepository
import com.sclera.sclerago.domain.usecase.access.PermissionGuard
import javax.inject.Inject

// Configuration → Asset fields: the property's standard fields at the server (as the web does it). Every call needs a
// connection and throws OnlineException; changes need the Configure permission. Photo rules, the geofence and AI in
// validation are in PropertyConfigUseCases.

/** Why a field set up in the field sheet can't be kept. [NO_CURRENCY]: a currency field needs its currency. */
enum class FieldProblem { NO_NAME, NO_OPTIONS, NO_CURRENCY, DUPLICATE }

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
        unit = unit.trim(),
    ).withChecks(cleanChecks(kind, checks, required))
        .let { if (it.locked) it.copy(required = true, visible = true) else it.copy(visible = it.visible || it.required) }
}

/**
 * Why [f] can't be kept next to [others] (the other fields), or null. Custom dropdowns and multiple checkboxes need an
 * option, and a currency field its currency (as the web's "Select a currency").
 */
fun fieldSetupProblem(f: FieldCfg, others: List<FieldCfg>): FieldProblem? = when {
    f.label.isBlank() -> FieldProblem.NO_NAME
    f.custom && f.kind in OPTION_KINDS && f.options.none { it.isNotBlank() } -> FieldProblem.NO_OPTIONS
    f.kind == FieldKind.CURRENCY && f.unit.isBlank() -> FieldProblem.NO_CURRENCY
    nameTaken(others, f.label, f.key) -> FieldProblem.DUPLICATE
    else -> null
}

class LoadConfigFieldsUseCase @Inject constructor(private val repo: ConfigFieldsRepository) {
    suspend operator fun invoke(propertyId: String): List<FieldCfg> = repo.load(propertyId)
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
 * Import from Excel, as the web: the server reads the .xlsx [file] (a content URI; one field per row with its type,
 * options, mandatory, active, default value and help text, columns matched by heading) and returns the new fields to
 * review in the Add field sheet; nothing is added yet. Names the property already has and photo, video or voice memo
 * rows come back as skipped. Throws OnlineException, or SheetRejectedException when a row is wrong.
 */
class ImportFieldsUseCase @Inject constructor(private val repo: ConfigFieldsRepository, private val guard: PermissionGuard) {
    suspend operator fun invoke(propertyId: String, file: String): FieldsRead {
        guard.requireOnline(Permission.CONFIGURE)
        return repo.readSheet(propertyId, file)
    }
}

/** Import from Excel's Sample Format: saves the server's sample sheet to [destination]; false when it can't be written. Throws OnlineException. */
class SaveFieldsSampleUseCase @Inject constructor(private val repo: ConfigFieldsRepository, private val files: FileRepository) {
    suspend operator fun invoke(propertyId: String, destination: String): Boolean = files.writeFile(destination, repo.importSample(propertyId))
}

/** The currencies a currency field can be in (its unit), by name, for picking one. Throws OnlineException. */
class GetCurrenciesUseCase @Inject constructor(private val repo: CurrencyRepository) {
    suspend operator fun invoke(): List<Currency> = repo.currencies()
}

/** The Asset fields use cases, for injecting as one. */
class FieldsConfigUseCases @Inject constructor(
    val load: LoadConfigFieldsUseCase,
    val toggleRequired: ToggleFieldRequiredUseCase,
    val save: SaveFieldUseCase,
    val add: AddFieldsUseCase,
    val delete: DeleteFieldUseCase,
    val setChecks: SetFieldChecksUseCase,
    val importFields: ImportFieldsUseCase,
    val saveImportSample: SaveFieldsSampleUseCase,
    val currencies: GetCurrenciesUseCase,
)
