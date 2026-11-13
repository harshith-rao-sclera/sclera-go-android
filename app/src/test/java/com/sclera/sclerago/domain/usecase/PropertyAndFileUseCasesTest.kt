package com.sclera.sclerago.domain.usecase

import com.sclera.sclerago.data.repository.OnboardingRepositoryImpl
import com.sclera.sclerago.domain.model.OnboardingChannel
import com.sclera.sclerago.domain.model.Property
import com.sclera.sclerago.domain.usecase.media.DeleteOwnedMediaUseCase
import com.sclera.sclerago.domain.usecase.media.DescribePickedFilesUseCase
import com.sclera.sclerago.domain.usecase.media.DescribePickedMediaUseCase
import com.sclera.sclerago.domain.usecase.media.WritePropertyTemplateUseCase
import com.sclera.sclerago.domain.usecase.property.AddPropertyUseCase
import com.sclera.sclerago.domain.usecase.property.ObserveOnboardingUseCase
import com.sclera.sclerago.domain.usecase.property.ObservePropertiesUseCase
import com.sclera.sclerago.domain.usecase.property.SetOnboardingUseCase
import com.sclera.sclerago.domain.usecase.property.propertyProblem
import com.sclera.sclerago.domain.model.ImportedFile
import com.sclera.sclerago.domain.model.Media
import com.sclera.sclerago.testutil.FakeFileRepository
import com.sclera.sclerago.testutil.FakePropertyRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

class PropertyAndFileUseCasesTest {
    private val repo = FakePropertyRepository()

    @Test
    fun propertyProblem_explainsWhatIsWrong() {
        val existing = listOf(Property("p1", "Tower", false))
        assertEquals("Enter the property name.", propertyProblem(" ", "Office", existing))
        assertEquals("Choose the property type.", propertyProblem("A", "", existing))
        assertEquals("There is already a property called tower.", propertyProblem(" tower ", "Office", existing))
        assertNull(propertyProblem("New", "Office", existing))
    }

    @Test
    fun addProperty_addsAValidOneAndRejectsAnInvalidOne() {
        val add = AddPropertyUseCase(repo)
        val before = repo.properties.value.size
        val bad = add(Property("x", " ", true)) as AddPropertyUseCase.Result.Invalid
        assertEquals("Enter the property name.", bad.message)
        val ok = add(Property("x", " Depot ", true)) as AddPropertyUseCase.Result.Added
        assertEquals("Depot", ok.property.name)
        assertTrue(ok.property.id.startsWith("p"))
        assertEquals(false, ok.property.gps)
        assertEquals(before + 1, repo.properties.value.size)
        assertEquals(repo.properties, ObservePropertiesUseCase(repo)())
    }

    @Test
    fun onboarding_channelsCanBeSwitchedOffAndOn() {
        val onboarding = OnboardingRepositoryImpl()
        val set = SetOnboardingUseCase(onboarding)
        set("p1", OnboardingChannel.BLUETOOTH, false)
        assertEquals(setOf(OnboardingChannel.BLUETOOTH), ObserveOnboardingUseCase(onboarding)().value["p1"])
        set("p1", OnboardingChannel.BLUETOOTH, true)
        assertTrue(onboarding.disabled.value["p1"].orEmpty().isEmpty())
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
        WritePropertyTemplateUseCase(files)(ByteArrayOutputStream())
        assertTrue(files.templateWritten)
    }
}
