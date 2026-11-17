package com.sclera.sclerago.domain.usecase.property

import com.sclera.sclerago.domain.model.OnboardingChannel
import com.sclera.sclerago.domain.model.OnlineError
import com.sclera.sclerago.domain.model.OnlineException
import com.sclera.sclerago.domain.model.Permission
import com.sclera.sclerago.domain.model.Property
import com.sclera.sclerago.domain.repository.OnboardingRepository
import com.sclera.sclerago.domain.repository.PropertyRepository
import com.sclera.sclerago.domain.usecase.access.PermissionGuard
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/** Why a property can't be added, as the web's Add New Property says it. */
enum class PropertyProblem { REQUIRED_FIELDS, EMAIL_INVALID, PHONE_INVALID }

// The web's email and phone checks (components/helper/validateEmail.js, validatePhone.js).
private val EMAIL = Regex("""^([A-Za-z0-9_\-.])+@([A-Za-z0-9_\-.])+\.([A-Za-z]{2,4})$""")
private val PHONE = Regex("""(\+\d{1,3}\s?)?((\(\d{3}\)\s?)|(\d{3})(\s|-?))(\d{3}(\s|-?))(\d{4})(\s?(([E|e]xt[:|.|]?)|x|X)(\s?\d+))?""")

/**
 * Why [draft] can't be added, or null when it can: a name and a type are required, and a contact email or phone
 * that is given must look valid (as the web checks them; the server checks the rest).
 */
fun propertyProblem(draft: Property): PropertyProblem? {
    val email = draft.contact.email.trim()
    val phone = draft.contact.phone.trim()
    return when {
        draft.name.isBlank() || draft.type.isBlank() -> PropertyProblem.REQUIRED_FIELDS
        email.isNotEmpty() && !EMAIL.matches(email) -> PropertyProblem.EMAIL_INVALID
        phone.isNotEmpty() && PHONE.find(phone) == null -> PropertyProblem.PHONE_INVALID
        else -> null
    }
}

class ObservePropertiesUseCase @Inject constructor(private val repo: PropertyRepository) {
    operator fun invoke(): StateFlow<List<Property>> = repo.properties
}

/** Loads the properties from the server again (what is shown stays when it fails; throws [OnlineException]). */
class RefreshPropertiesUseCase @Inject constructor(private val repo: PropertyRepository) {
    suspend operator fun invoke() = repo.refresh()
}

/** Why [draft] can't be added as it is, or null when it can (the Add form's checks). */
class ValidatePropertyUseCase @Inject constructor() {
    operator fun invoke(draft: Property): PropertyProblem? = propertyProblem(draft)
}

/** Adds a property from the Add New Property form (online only). */
class AddPropertyUseCase @Inject constructor(private val repo: PropertyRepository, private val guard: PermissionGuard) {
    sealed interface Result {
        data class Added(val property: Property) : Result
        data class Invalid(val problem: PropertyProblem) : Result
        data class Failed(val error: OnlineError) : Result
    }

    suspend operator fun invoke(draft: Property): Result {
        val clean = draft.name.trim()
        propertyProblem(draft)?.let { return Result.Invalid(it) }
        if (!guard.allows(Permission.MANAGE_PROPERTIES)) return Result.Failed(OnlineError.Forbidden)
        return try {
            Result.Added(repo.add(draft.copy(name = clean, gps = false)))
        } catch (e: OnlineException) {
            Result.Failed(e.error)
        }
    }
}

/** Whether the user may add and import properties (Manage properties), as the web shows "+ Property" and Import only then. */
class ObserveCanManagePropertiesUseCase @Inject constructor(private val guard: PermissionGuard) {
    operator fun invoke(): Flow<Boolean> = guard.observe(Permission.MANAGE_PROPERTIES)
}

/** Property Info's Edit: the property as the server has it now. Throws [OnlineException]. */
class GetPropertyUseCase @Inject constructor(private val repo: PropertyRepository) {
    suspend operator fun invoke(id: String): Property = repo.get(id)
}

/**
 * Saves an edited property (Property Info → Edit → Save), with the same checks as adding one; returns why it can't be
 * saved, or null when it was. Throws [OnlineException].
 */
class UpdatePropertyUseCase @Inject constructor(private val repo: PropertyRepository) {
    suspend operator fun invoke(property: Property): PropertyProblem? {
        propertyProblem(property)?.let { return it }
        repo.update(property.copy(name = property.name.trim()))
        return null
    }
}

/** Property Info's Delete (after its confirmation). Throws [OnlineException]. */
class DeletePropertyUseCase @Inject constructor(private val repo: PropertyRepository) {
    suspend operator fun invoke(id: String) = repo.delete(id)
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
    val canManage: ObserveCanManagePropertiesUseCase,
    val get: GetPropertyUseCase,
    val update: UpdatePropertyUseCase,
    val delete: DeletePropertyUseCase,
)
