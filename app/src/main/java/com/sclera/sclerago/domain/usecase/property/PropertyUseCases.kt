package com.sclera.sclerago.domain.usecase.property

import com.sclera.sclerago.domain.model.OnboardingChannel
import com.sclera.sclerago.domain.model.Property
import com.sclera.sclerago.domain.repository.OnboardingRepository
import com.sclera.sclerago.domain.repository.PropertyRepository
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

/** Adds a property from the Add New Property form. */
class AddPropertyUseCase @Inject constructor(private val repo: PropertyRepository) {
    sealed interface Result {
        data class Added(val property: Property) : Result
        data class Invalid(val problem: PropertyProblem, val name: String) : Result
    }

    operator fun invoke(draft: Property): Result {
        val clean = draft.name.trim()
        propertyProblem(clean, draft.type, repo.properties.value)?.let { return Result.Invalid(it, clean) }
        val p = draft.copy(id = "p${System.currentTimeMillis()}", name = clean, gps = false)
        repo.add(p)
        return Result.Added(p)
    }
}

class ObserveOnboardingUseCase @Inject constructor(private val repo: OnboardingRepository) {
    /** Property id → ways of adding assets switched off there. */
    operator fun invoke(): StateFlow<Map<String, Set<OnboardingChannel>>> = repo.disabled
}

/** Property Settings: switches Bluetooth or Wi-Fi onboarding on or off for a property. */
class SetOnboardingUseCase @Inject constructor(private val repo: OnboardingRepository) {
    operator fun invoke(prop: String, channel: OnboardingChannel, enabled: Boolean) = repo.setEnabled(prop, channel, enabled)
}

/** The property use cases, for injecting as one. */
class PropertyUseCases @Inject constructor(
    val observe: ObservePropertiesUseCase,
    val add: AddPropertyUseCase,
    val observeOnboarding: ObserveOnboardingUseCase,
    val setOnboarding: SetOnboardingUseCase,
    val summaries: ObservePropertySummariesUseCase,
    val filter: FilterPropertySummariesUseCase,
    val validateImport: ValidateImportFileUseCase,
)
