package com.sclera.sclerago.domain.usecase.config

import com.sclera.sclerago.domain.model.Fence
import com.sclera.sclerago.domain.model.Geofence
import com.sclera.sclerago.domain.model.MAX_FENCES
import com.sclera.sclerago.domain.model.MAX_FENCE_POINTS
import com.sclera.sclerago.domain.model.MAX_PHOTOS_PER_ASSET
import com.sclera.sclerago.domain.model.MIN_FENCE_POINTS
import com.sclera.sclerago.domain.model.OnlineError
import com.sclera.sclerago.domain.model.OnlineException
import com.sclera.sclerago.domain.model.Permission
import com.sclera.sclerago.domain.model.PhotoKind
import com.sclera.sclerago.domain.model.PhotoRule
import com.sclera.sclerago.domain.model.PropertySettings
import com.sclera.sclerago.domain.repository.GeofenceRepository
import com.sclera.sclerago.domain.repository.PropertySettingsRepository
import com.sclera.sclerago.domain.usecase.access.PermissionGuard
import javax.inject.Inject

// Configuration's property settings at the server: photo rules (Asset fields), AI in validation (Settings) and the
// geofence (Geofencing). Every call needs a connection and throws OnlineException; changes need the Configure permission.

class LoadPropertySettingsUseCase @Inject constructor(private val repo: PropertySettingsRepository) {
    suspend operator fun invoke(propertyId: String): PropertySettings = repo.load(propertyId)
}

/** Which number of a photo rule a stepper changes. */
enum class PhotoBound { MIN, MAX }

/** Why a photo rule stepper can't move. */
enum class PhotoStepBlock {
    /** The minimum is already 0. */
    NONE_REQUIRED,

    /** The minimum can't pass the maximum: raise the maximum first. */
    RAISE_MAX_FIRST,

    /** The maximum is already 0. */
    NONE_ALLOWED,

    /** The maximum can't go below the minimum. */
    BELOW_MIN,

    /** All kinds together already allow [MAX_PHOTOS_PER_ASSET]. */
    TOTAL_LIMIT,
}

/** Why the [bound] of [kind]'s rule can't go [up] (or down) one, or null when it can. */
fun photoStepBlock(rules: List<PhotoRule>, kind: PhotoKind, bound: PhotoBound, up: Boolean): PhotoStepBlock? {
    val r = rules.firstOrNull { it.kind == kind } ?: return null
    return when (bound) {
        PhotoBound.MIN -> when {
            !up && r.min <= 0 -> PhotoStepBlock.NONE_REQUIRED
            up && r.min >= r.max -> PhotoStepBlock.RAISE_MAX_FIRST
            else -> null
        }
        PhotoBound.MAX -> when {
            !up && r.max <= 0 -> PhotoStepBlock.NONE_ALLOWED
            !up && r.max - 1 < r.min -> PhotoStepBlock.BELOW_MIN
            up && rules.sumOf { it.max } >= MAX_PHOTOS_PER_ASSET -> PhotoStepBlock.TOTAL_LIMIT
            else -> null
        }
    }
}

/** [rules] with the [bound] of [kind] one [up] (or down), or null when that is blocked ([photoStepBlock]). */
fun stepPhotoRule(rules: List<PhotoRule>, kind: PhotoKind, bound: PhotoBound, up: Boolean): List<PhotoRule>? {
    if (photoStepBlock(rules, kind, bound, up) != null) return null
    val d = if (up) 1 else -1
    return rules.map { r ->
        when {
            r.kind != kind -> r
            bound == PhotoBound.MIN -> r.copy(min = r.min + d)
            else -> r.copy(max = r.max + d)
        }
    }
}

/** True when [rules] are ones the server accepts: each 0 ≤ min ≤ max, and every maximum together at most [MAX_PHOTOS_PER_ASSET]. */
fun photoRulesValid(rules: List<PhotoRule>): Boolean =
    rules.all { it.min in 0..it.max } && rules.sumOf { it.max } <= MAX_PHOTOS_PER_ASSET

/** Saves the photo rules (every kind); returns the settings the server kept. */
class SavePhotoRulesUseCase @Inject constructor(private val repo: PropertySettingsRepository, private val guard: PermissionGuard) {
    suspend operator fun invoke(propertyId: String, rules: List<PhotoRule>): PropertySettings {
        guard.requireOnline(Permission.CONFIGURE)
        if (!photoRulesValid(rules)) throw OnlineException(OnlineError.Invalid)
        return repo.savePhotoRules(propertyId, rules)
    }
}

/** Turns the property's AI switch on or off (everything AI, validation's AI checks too); returns the settings the server kept. */
class SetAiValidationUseCase @Inject constructor(private val repo: PropertySettingsRepository, private val guard: PermissionGuard) {
    suspend operator fun invoke(propertyId: String, on: Boolean): PropertySettings {
        guard.requireOnline(Permission.CONFIGURE)
        return repo.setAiValidation(propertyId, on)
    }
}

/**
 * Puts the property's type fields in metric or US units (the web's Units setting); returns the settings the server kept.
 * Only assets classified from then on get the renamed fields; no answer is converted.
 */
class SetMetricUnitsUseCase @Inject constructor(private val repo: PropertySettingsRepository, private val guard: PermissionGuard) {
    suspend operator fun invoke(propertyId: String, metric: Boolean): PropertySettings {
        guard.requireOnline(Permission.CONFIGURE)
        return repo.setMetricUnits(propertyId, metric)
    }
}

// ---- Geofence

class LoadGeofenceUseCase @Inject constructor(private val repo: GeofenceRepository) {
    suspend operator fun invoke(propertyId: String): Geofence = repo.load(propertyId)
}

/** Why fences can't be saved. */
enum class FenceProblem { NO_FENCES, TOO_MANY_FENCES, TOO_FEW_POINTS, TOO_MANY_POINTS, BAD_POINT }

/** Why [fences] can't be saved, as the server checks them, or null when they can. */
fun fenceProblem(fences: List<Fence>): FenceProblem? = when {
    fences.isEmpty() -> FenceProblem.NO_FENCES
    fences.size > MAX_FENCES -> FenceProblem.TOO_MANY_FENCES
    fences.any { f -> f.rings.isEmpty() || f.rings.any { r -> r.distinct().size < MIN_FENCE_POINTS } } -> FenceProblem.TOO_FEW_POINTS
    // The server adds the closing point, so a ring may hold one point fewer than its limit.
    fences.any { f -> f.rings.any { r -> r.size >= MAX_FENCE_POINTS } } -> FenceProblem.TOO_MANY_POINTS
    fences.any { f ->
        f.rings.any { r -> r.any { p -> !p.lat.isFinite() || !p.lng.isFinite() || p.lat !in -90.0..90.0 || p.lng !in -180.0..180.0 } }
    } -> FenceProblem.BAD_POINT
    else -> null
}

/**
 * Save on the Geofencing map: replaces every fence with [fences], keeping enforcement as it was. Returns why they
 * were refused before sending, or the geofence the server kept.
 */
class SaveGeofenceUseCase @Inject constructor(private val repo: GeofenceRepository, private val guard: PermissionGuard) {
    sealed interface Result {
        data class Refused(val problem: FenceProblem) : Result
        data class Saved(val geofence: Geofence) : Result
    }

    suspend operator fun invoke(propertyId: String, fences: List<Fence>, enforced: Boolean): Result {
        fenceProblem(fences)?.let { return Result.Refused(it) }
        guard.requireOnline(Permission.CONFIGURE)
        return Result.Saved(repo.save(propertyId, Geofence(enforced, fences)))
    }
}

/**
 * The enforce switch: turns enforcement on or off with the saved [fences] (sent again, as the server replaces both).
 * Enforcing needs a saved fence. Returns the geofence the server kept, or null when there is no fence to enforce.
 */
class SetGeofenceEnforcedUseCase @Inject constructor(private val repo: GeofenceRepository, private val guard: PermissionGuard) {
    suspend operator fun invoke(propertyId: String, fences: List<Fence>, enforced: Boolean): Geofence? {
        if (enforced && fences.isEmpty()) return null
        guard.requireOnline(Permission.CONFIGURE)
        return repo.save(propertyId, Geofence(enforced, fences))
    }
}

/** The property settings and geofence use cases, for injecting as one. */
class PropertyConfigUseCases @Inject constructor(
    val loadSettings: LoadPropertySettingsUseCase,
    val savePhotoRules: SavePhotoRulesUseCase,
    val setAiValidation: SetAiValidationUseCase,
    val setMetricUnits: SetMetricUnitsUseCase,
    val loadGeofence: LoadGeofenceUseCase,
    val saveGeofence: SaveGeofenceUseCase,
    val setGeofenceEnforced: SetGeofenceEnforcedUseCase,
    val configureBlock: ObserveConfigureBlockUseCase,
)
