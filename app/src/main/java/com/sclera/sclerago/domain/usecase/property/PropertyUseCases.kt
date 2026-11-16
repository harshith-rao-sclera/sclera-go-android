package com.sclera.sclerago.domain.usecase.property

import com.sclera.sclerago.domain.model.OnboardingChannel
import com.sclera.sclerago.domain.model.OnlineError
import com.sclera.sclerago.domain.model.OnlineException
import com.sclera.sclerago.domain.model.Permission
import com.sclera.sclerago.domain.model.Property
import com.sclera.sclerago.domain.repository.OnboardingRepository
import com.sclera.sclerago.domain.repository.PropertyRepository
import com.sclera.sclerago.domain.usecase.access.PermissionGuard
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/** Why a property can't be added. */
enum class PropertyProblem { NAME_REQUIRED, TYPE_REQUIRED, DUPLICATE }

/** Why a property called [name] of [type] can't be added, or null when it can. */
fun propertyProblem(name: String, type: String, existing: List<Property>): PropertyProblem? = when {
    name.isBlank() -> PropertyProblem.NAME_REQUIRED
    type.isBlank() -> PropertyProblem.TYPE_REQUIRED
    existing.any { it.name.equals(name.trim(), ignoreCase = true) } -> PropertyProblem.DUPLICATE
    else -> null
}

class ObservePropertiesUseCase @Inject constructor(private val repo: PropertyRepository) {
    operator fun invoke(): StateFlow<List<Property>> = repo.properties
}

/** Loads the properties from the server again (what is shown stays when it fails; throws [OnlineException]). */
class RefreshPropertiesUseCase @Inject constructor(private val repo: PropertyRepository) {
    suspend operator fun invoke() = repo.refresh()
}

/** Why [draft] can't be added as it is, or null when it can (the Add form's checks, against the properties as they are now). */
class ValidatePropertyUseCase @Inject constructor(private val repo: PropertyRepository) {
    operator fun invoke(draft: Property): PropertyProblem? = propertyProblem(draft.name.trim(), draft.type, repo.properties.value)
}

/** Adds a property from the Add New Property form (online only). */
class AddPropertyUseCase @Inject constructor(private val repo: PropertyRepository, private val guard: PermissionGuard) {
    sealed interface Result {
        data class Added(val property: Property) : Result
        data class Invalid(val problem: PropertyProblem, val name: String) : Result
        data class Failed(val error: OnlineError) : Result
    }

    suspend operator fun invoke(draft: Property): Result {
        val clean = draft.name.trim()
        propertyProblem(clean, draft.type, repo.properties.value)?.let { return Result.Invalid(it, clean) }
        if (!guard.allows(Permission.MANAGE_PROPERTIES)) return Result.Failed(OnlineError.Forbidden)
        return try {
            Result.Added(repo.add(draft.copy(name = clean, gps = false)))
        } catch (e: OnlineException) {
            Result.Failed(e.error)
        }
    }
}

class ObserveOnboardingUseCase @Inject constructor(private val repo: OnboardingRepository) {
    /** Property id → ways of adding assets switched off there. */
    operator fun invoke(): StateFlow<Map<String, Set<OnboardingChannel>>> = repo.disabled
}

/** Property Settings: switches Bluetooth or Wi-Fi onboarding on or off for a property. */
class SetOnboardingUseCase @Inject constructor(private val repo: OnboardingRepository, private val guard: PermissionGuard) {
    /** Throws [OnlineException] (the switch lives on the server). */
    suspend operator fun invoke(prop: String, channel: OnboardingChannel, enabled: Boolean) {
        guard.requireOnline(Permission.CONFIGURE)
        repo.setEnabled(prop, channel, enabled)
    }
}

/** The property use cases, for injecting as one. */
class PropertyUseCases @Inject constructor(
    val observe: ObservePropertiesUseCase,
    val add: AddPropertyUseCase,
    val validate: ValidatePropertyUseCase,
    val refresh: RefreshPropertiesUseCase,
    val observeOnboarding: ObserveOnboardingUseCase,
    val setOnboarding: SetOnboardingUseCase,
    val summaries: ObservePropertySummariesUseCase,
    val filter: FilterPropertySummariesUseCase,
    val validateImport: ValidateImportFileUseCase,
)
