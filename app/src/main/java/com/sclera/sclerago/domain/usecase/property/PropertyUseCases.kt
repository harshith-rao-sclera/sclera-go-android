package com.sclera.sclerago.domain.usecase.property

import com.sclera.sclerago.domain.model.AuthError
import com.sclera.sclerago.domain.model.AuthState
import com.sclera.sclerago.domain.model.OnboardingChannel
import com.sclera.sclerago.domain.model.Property
import com.sclera.sclerago.domain.repository.AuthRepository
import com.sclera.sclerago.domain.repository.OnboardingRepository
import com.sclera.sclerago.domain.repository.PropertyRepository
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/** Why a property called [name] of [type] can't be added, or null when it can. */
fun propertyProblem(name: String, type: String, existing: List<Property>): String? = when {
    name.isBlank() -> "Enter the property name."
    type.isBlank() -> "Choose the property type."
    existing.any { it.name.equals(name.trim(), ignoreCase = true) } -> "There is already a property called ${name.trim()}."
    else -> null
}

class ObservePropertiesUseCase @Inject constructor(private val repo: PropertyRepository) {
    operator fun invoke(): StateFlow<List<Property>> = repo.properties
}

/** Adds a property from the Add New Property form. */
class AddPropertyUseCase @Inject constructor(private val repo: PropertyRepository) {
    sealed interface Result {
        data class Added(val property: Property) : Result
        data class Invalid(val message: String) : Result
    }

    operator fun invoke(draft: Property): Result {
        val clean = draft.name.trim()
        propertyProblem(clean, draft.type, repo.properties.value)?.let { return Result.Invalid(it) }
        val p = draft.copy(id = "p${System.currentTimeMillis()}", name = clean, gps = false)
        repo.add(p)
        return Result.Added(p)
    }
}

/**
 * Keeps the saved properties in step with the signed-in user's organisation: loads every page of its
 * properties whenever the organisation becomes known or changes, until the caller's scope ends. A failed
 * load (offline, server error) keeps the saved list; an error that ends the session has already signed out.
 */
class SyncPropertiesUseCase @Inject constructor(
    private val auth: AuthRepository,
    private val repo: PropertyRepository,
) {
    suspend operator fun invoke() {
        auth.state
            .map { (it as? AuthState.SignedIn)?.session?.organisationId }
            .distinctUntilChanged()
            .collectLatest { orgId ->
                if (orgId != null) {
                    try {
                        repo.refresh(orgId)
                    } catch (_: AuthError) {
                        // Keep what is saved; the next sign-in or app start tries again.
                    }
                }
            }
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
    val sync: SyncPropertiesUseCase,
    val observeOnboarding: ObserveOnboardingUseCase,
    val setOnboarding: SetOnboardingUseCase,
)
