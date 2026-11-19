package com.sclera.sclerago.testutil

import com.sclera.sclerago.domain.model.AccessState
import com.sclera.sclerago.domain.model.AssetCatalog
import com.sclera.sclerago.domain.model.AssetDetails
import com.sclera.sclerago.domain.model.AuthState
import com.sclera.sclerago.domain.model.Group
import com.sclera.sclerago.domain.model.ImportedFile
import com.sclera.sclerago.domain.model.LocValue
import com.sclera.sclerago.domain.model.Media
import com.sclera.sclerago.domain.model.NewAsset
import com.sclera.sclerago.domain.model.OnboardingChannel
import com.sclera.sclerago.domain.model.Person
import com.sclera.sclerago.domain.model.Property
import com.sclera.sclerago.domain.model.PropertyDownloads
import com.sclera.sclerago.domain.model.Status
import com.sclera.sclerago.domain.repository.AccessRepository
import com.sclera.sclerago.domain.repository.AssetRepository
import com.sclera.sclerago.domain.repository.AuthRepository
import com.sclera.sclerago.domain.repository.CurrentAccessRepository
import com.sclera.sclerago.domain.repository.FileRepository
import com.sclera.sclerago.domain.repository.NetworkMonitor
import com.sclera.sclerago.domain.repository.OnboardingRepository
import com.sclera.sclerago.domain.repository.PropertyDownloadRepository
import com.sclera.sclerago.domain.repository.PropertyRepository
import com.sclera.sclerago.domain.usecase.access.PermissionGuard
import com.sclera.sclerago.domain.usecase.auth.ObserveAuthStateUseCase
import com.sclera.sclerago.domain.usecase.media.WritePropertyTemplateUseCase
import com.sclera.sclerago.domain.usecase.property.AddPropertyUseCase
import com.sclera.sclerago.domain.usecase.property.FilterPropertySummariesUseCase
import com.sclera.sclerago.domain.usecase.property.IsDeviceOnlineUseCase
import com.sclera.sclerago.domain.usecase.property.ObserveOnboardingUseCase
import com.sclera.sclerago.domain.usecase.property.ObservePropertiesUseCase
import com.sclera.sclerago.domain.usecase.property.ObservePropertyDownloadsUseCase
import com.sclera.sclerago.domain.usecase.property.ObservePropertySummariesUseCase
import com.sclera.sclerago.domain.usecase.property.OpenDownloadedPropertyUseCase
import com.sclera.sclerago.domain.usecase.property.PropertyDownloadUseCases
import com.sclera.sclerago.domain.usecase.property.PropertyUseCases
import com.sclera.sclerago.domain.usecase.property.RefreshPropertiesUseCase
import com.sclera.sclerago.domain.usecase.property.ResumePropertyDownloadUseCase
import com.sclera.sclerago.domain.usecase.property.SetOnboardingUseCase
import com.sclera.sclerago.domain.usecase.property.StartPropertyDownloadUseCase
import com.sclera.sclerago.domain.usecase.property.ValidateImportFileUseCase
import com.sclera.sclerago.domain.usecase.property.ValidatePropertyUseCase
import com.sclera.sclerago.presentation.access.AccessViewModel
import com.sclera.sclerago.presentation.main.AssetListViewModel
import com.sclera.sclerago.presentation.property.PropertyViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.OutputStream

// Fakes for the list-loading tests, written against the current repository interfaces. A "gated" repository's
// refresh() waits until the test finishes or fails it, so the state while the server read runs can be checked.

/** Lets everything through: what the user may do is still loading, so the server decides. */
class AllowAllAccess : CurrentAccessRepository {
    override val state: StateFlow<AccessState> = MutableStateFlow(AccessState.Loading)
}

fun allowAllGuard() = PermissionGuard(AllowAllAccess())

/** The outcome a gated refresh is released with. */
private class Release<T>(val value: T?, val error: Throwable?)

/** Properties whose refresh waits for [finish] (publishes the list, then returns) or [fail] (throws). */
class GatedPropertyRepository(initial: List<Property> = emptyList()) : PropertyRepository {
    private val flow = MutableStateFlow(initial)
    override val properties: StateFlow<List<Property>> = flow
    private val gate = CompletableDeferred<Release<List<Property>>>()
    var refreshes = 0
        private set

    override suspend fun refresh() {
        refreshes++
        val r = gate.await()
        r.error?.let { throw it }
        flow.value = r.value.orEmpty()
    }

    fun finish(list: List<Property>) = gate.complete(Release(list, null))
    fun fail(error: Throwable) = gate.complete(Release(null, error))

    override suspend fun add(property: Property): Property = property.also { flow.value = flow.value + it }
}

/** Groups and people whose refresh waits for [finish] or [fail], like [GatedPropertyRepository]. */
class GatedAccessRepository(groups: List<Group> = emptyList(), people: List<Person> = emptyList()) : AccessRepository {
    private val groupFlow = MutableStateFlow(groups)
    private val peopleFlow = MutableStateFlow(people)
    override val groups: StateFlow<List<Group>> = groupFlow
    override val people: StateFlow<List<Person>> = peopleFlow
    private val gate = CompletableDeferred<Release<Pair<List<Group>, List<Person>>>>()

    override suspend fun refresh() {
        val r = gate.await()
        r.error?.let { throw it }
        r.value?.let { (g, p) ->
            groupFlow.value = g
            peopleFlow.value = p
        }
    }

    fun finish(groups: List<Group>, people: List<Person>) = gate.complete(Release(groups to people, null))
    fun fail(error: Throwable) = gate.complete(Release(null, error))

    override suspend fun saveGroup(group: Group): Group = group
    override suspend fun deleteGroup(id: String) = Unit
    override suspend fun savePerson(person: Person): Person = person
    override suspend fun removePerson(id: String) = Unit
}

/** The asset catalog as the test sets it ([AssetCatalog.loaded] false while the property's data is being opened). */
class FakeCatalogRepository(initial: AssetCatalog = AssetCatalog()) : AssetRepository {
    override val catalog = MutableStateFlow(initial)
    override suspend fun add(assets: List<NewAsset>) = Unit
    override suspend fun setStatus(ids: Collection<String>, status: Status, photoRules: List<com.sclera.sclerago.domain.model.PhotoRule>): com.sclera.sclerago.domain.model.SubmitOutcome = com.sclera.sclerago.domain.model.SubmitOutcome()
    override suspend fun saveDetails(id: String, values: Map<String, String>) = Unit
    override suspend fun delete(ids: Collection<String>) = Unit
    override suspend fun assignLocation(ids: Collection<String>, location: LocValue) = Unit
    override suspend fun attachMedia(id: String, media: List<Media>) = Unit
    override suspend fun details(id: String): AssetDetails? = null
    override suspend fun detailsFor(ids: Collection<String>): Map<String, AssetDetails> = emptyMap()
}

class NoOnboarding : OnboardingRepository {
    override val disabled: StateFlow<Map<String, Set<OnboardingChannel>>> = MutableStateFlow(emptyMap())
    override suspend fun setEnabled(prop: String, channel: OnboardingChannel, enabled: Boolean) = Unit
}

class NoFiles : FileRepository {
    override suspend fun describeMedia(uris: List<String>): List<Media> = emptyList()
    override suspend fun describeFiles(uris: List<String>): List<ImportedFile> = emptyList()
    override fun deleteOwned(media: List<Media>) = Unit
    override suspend fun readSheet(uri: String): List<List<String>>? = null
    override fun writePropertyImportTemplate(out: OutputStream) = Unit
}

class IdleDownloads : PropertyDownloadRepository {
    override val downloads: StateFlow<PropertyDownloads> = MutableStateFlow(PropertyDownloads(restored = true))
    override fun start(propertyId: String) = Unit
    override fun open(propertyId: String) = Unit
    override fun resume() = Unit
}

object AlwaysOnline : NetworkMonitor {
    override fun isOnline(): Boolean = true
}

class SignedOutAuth : AuthRepository {
    override val state: StateFlow<AuthState> = MutableStateFlow(AuthState.SignedOut())
    override suspend fun completeLogin(enteredEmail: String) = Unit
    override fun clearLoginCookies() = Unit
    override fun onForeground() = Unit
    override fun logout() = Unit
}

/** The Property list's view model over [properties] and [assets], with every other dependency idle. */
fun propertyListViewModel(properties: PropertyRepository, assets: AssetRepository = FakeCatalogRepository()): PropertyViewModel {
    val guard = allowAllGuard()
    val onboarding = NoOnboarding()
    val downloads = IdleDownloads()
    return PropertyViewModel(
        PropertyUseCases(
            ObservePropertiesUseCase(properties), AddPropertyUseCase(properties, guard), ValidatePropertyUseCase(properties),
            RefreshPropertiesUseCase(properties), ObserveOnboardingUseCase(onboarding), SetOnboardingUseCase(onboarding, guard),
            ObservePropertySummariesUseCase(properties, assets), FilterPropertySummariesUseCase(), ValidateImportFileUseCase(),
        ),
        WritePropertyTemplateUseCase(NoFiles()),
        PropertyDownloadUseCases(
            ObservePropertyDownloadsUseCase(downloads), StartPropertyDownloadUseCase(downloads, AlwaysOnline),
            OpenDownloadedPropertyUseCase(downloads), ResumePropertyDownloadUseCase(downloads), IsDeviceOnlineUseCase(AlwaysOnline),
        ),
    )
}

/** People and access's view model over [repo]. */
fun accessViewModel(repo: AccessRepository): AccessViewModel =
    AccessViewModel(accessUseCases(repo), ObserveAuthStateUseCase(SignedOutAuth()), { 0L })

/** The asset lists' view model over [repo] (see [assetUseCases] for the rest of the wiring). */
fun assetListViewModel(repo: AssetRepository): AssetListViewModel = AssetListViewModel(assetUseCases(repo))
