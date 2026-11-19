package com.sclera.sclerago.domain.usecase

import com.sclera.sclerago.domain.model.Contact
import com.sclera.sclerago.domain.model.ImportedFile
import com.sclera.sclerago.domain.model.Media
import com.sclera.sclerago.domain.model.OnboardingChannel
import com.sclera.sclerago.domain.model.OnlineError
import com.sclera.sclerago.domain.model.OnlineException
import com.sclera.sclerago.domain.model.Property
import com.sclera.sclerago.domain.repository.OnboardingRepository
import com.sclera.sclerago.domain.usecase.media.DeleteOwnedMediaUseCase
import com.sclera.sclerago.domain.usecase.media.DescribePickedFilesUseCase
import com.sclera.sclerago.domain.usecase.media.DescribePickedMediaUseCase
import com.sclera.sclerago.domain.usecase.property.AddPropertyUseCase
import com.sclera.sclerago.domain.usecase.property.ObserveOnboardingUseCase
import com.sclera.sclerago.domain.usecase.property.ObservePropertiesUseCase
import com.sclera.sclerago.domain.usecase.property.PropertyProblem
import com.sclera.sclerago.domain.usecase.property.RefreshPropertiesUseCase
import com.sclera.sclerago.domain.usecase.property.SetOnboardingUseCase
import com.sclera.sclerago.domain.usecase.property.ValidatePropertyUseCase
import com.sclera.sclerago.domain.usecase.property.propertyProblem
import com.sclera.sclerago.testutil.FakeFileRepository
import com.sclera.sclerago.testutil.FakePropertyRepository
import com.sclera.sclerago.testutil.allowAllGuard
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class PropertyAndFileUseCasesTest {
    private val repo = FakePropertyRepository(listOf(Property("p1", "Tower", false)))
    private val guard = allowAllGuard()

    /** Property Settings kept the way the server keeps them: property id to the channels switched off. */
    private class InMemoryOnboarding : OnboardingRepository {
        private val flow = MutableStateFlow(emptyMap<String, Set<OnboardingChannel>>())
        override val disabled: StateFlow<Map<String, Set<OnboardingChannel>>> = flow
        override suspend fun setEnabled(prop: String, channel: OnboardingChannel, enabled: Boolean) {
            val now = flow.value[prop].orEmpty()
            flow.value = flow.value + (prop to if (enabled) now - channel else now + channel)
        }
    }

    @Test
    fun propertyProblem_explainsWhatIsWrong() {
        assertEquals(PropertyProblem.REQUIRED_FIELDS, propertyProblem(Property("", " ", false)))
        assertEquals(PropertyProblem.REQUIRED_FIELDS, propertyProblem(Property("", "A", false, type = "")))
        assertEquals(PropertyProblem.EMAIL_INVALID, propertyProblem(Property("", "A", false, contact = Contact(email = "not-an-email"))))
        assertNull(propertyProblem(Property("", "New", false)))
    }

    @Test
    fun validateProperty_usesTheFormsChecks() {
        val validate = ValidatePropertyUseCase()
        assertEquals(PropertyProblem.REQUIRED_FIELDS, validate(Property("", " ", false)))
        assertNull(validate(Property("", "Depot", false)))
    }

    @Test
    fun refreshProperties_asksTheRepository() = runTest {
        RefreshPropertiesUseCase(repo)()
        assertEquals(1, repo.refreshes)
    }

    @Test
    fun addProperty_addsAValidOneAndRejectsAnInvalidOne() = runTest {
        val add = AddPropertyUseCase(repo, guard)
        val bad = add(Property("", " ", true)) as AddPropertyUseCase.Result.Invalid
        assertEquals(PropertyProblem.REQUIRED_FIELDS, bad.problem)
        val ok = add(Property("", " Depot ", true)) as AddPropertyUseCase.Result.Added
        assertEquals("Depot", ok.property.name)
        assertTrue(ok.property.id.isNotEmpty())
        assertEquals(false, ok.property.gps) // GPS is set on the property later, never by the form
        assertEquals(2, repo.properties.value.size)
        assertEquals(repo.properties, ObservePropertiesUseCase(repo)())
    }

    @Test
    fun addProperty_serverFailure_isReturnedNotThrown() = runTest {
        repo.failure = OnlineException(OnlineError.Offline)
        val failed = AddPropertyUseCase(repo, guard)(Property("", "Depot", false)) as AddPropertyUseCase.Result.Failed
        assertEquals(OnlineError.Offline, failed.error)
        assertEquals(1, repo.properties.value.size)
    }

    @Test
    fun onboarding_channelsCanBeSwitchedOffAndOn() = runTest {
        val onboarding = InMemoryOnboarding()
        val set = SetOnboardingUseCase(onboarding, guard)
        set("p1", OnboardingChannel.BLUETOOTH, false)
        assertEquals(setOf(OnboardingChannel.BLUETOOTH), ObserveOnboardingUseCase(onboarding)().value["p1"])
        set("p1", OnboardingChannel.BLUETOOTH, true)
        assertTrue(onboarding.disabled.value["p1"].orEmpty().isEmpty())
    }

    @Test
    fun onboarding_withoutConfigurePermission_isRefused() = runTest {
        val onboarding = InMemoryOnboarding()
        val noConfigure = com.sclera.sclerago.domain.usecase.access.PermissionGuard(NoGroupAccess)
        try {
            SetOnboardingUseCase(onboarding, noConfigure)("p1", OnboardingChannel.NETWORK, false)
            fail("expected Forbidden")
        } catch (e: OnlineException) {
            assertEquals(OnlineError.Forbidden, e.error)
        }
        assertTrue(onboarding.disabled.value.isEmpty())
    }

    @Test
    fun fileUseCases_delegateToTheRepository() = runTest {
        val files = FakeFileRepository()
        val m = Media("m", "content://m", false, owned = true)
        files.media = listOf(m)
        files.files = listOf(ImportedFile("f", "content://f", "a.csv", 1, null))
        assertEquals(listOf(m), DescribePickedMediaUseCase(files)(listOf("u")))
        assertEquals("a.csv", DescribePickedFilesUseCase(files)(listOf("u")).single().name)
        DeleteOwnedMediaUseCase(files)(listOf(m, m.copy(owned = false)))
        assertEquals(listOf(m), files.deleted)
    }
}

/** A signed-in user with no group: the app refuses every change for them. */
private object NoGroupAccess : com.sclera.sclerago.domain.repository.CurrentAccessRepository {
    override val state: StateFlow<com.sclera.sclerago.domain.model.AccessState> = MutableStateFlow(
        com.sclera.sclerago.domain.model.AccessState.NoGroup(
            com.sclera.sclerago.domain.model.CurrentAccess(
                userId = "u", email = "u@x.com", name = null, role = null, organisationId = "org",
                groupId = null, groupName = null, permissions = emptySet(), allProperties = false, canManageProperties = false,
            ),
        ),
    )
}
