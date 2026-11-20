package com.sclera.sclerago.testutil

import com.sclera.sclerago.domain.model.AccessState
import com.sclera.sclerago.domain.model.AssignResult
import com.sclera.sclerago.domain.model.FieldCfg
import com.sclera.sclerago.domain.model.FormEntry
import com.sclera.sclerago.domain.model.MasterFormat
import com.sclera.sclerago.domain.repository.AssetAssignmentRepository
import com.sclera.sclerago.domain.repository.AssetFormRepository
import com.sclera.sclerago.domain.usecase.asset.AssetFilterUseCases
import com.sclera.sclerago.domain.usecase.asset.BuildFilterOptionsUseCase
import com.sclera.sclerago.domain.usecase.asset.LoadAssigneeNamesUseCase
import com.sclera.sclerago.domain.usecase.asset.ObserveFormatsByIdUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
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
import com.sclera.sclerago.domain.model.PlaceName
import com.sclera.sclerago.domain.model.Property
import com.sclera.sclerago.domain.model.PropertyDownloads
import com.sclera.sclerago.domain.model.SignOffOutcome
import com.sclera.sclerago.domain.model.Status
import com.sclera.sclerago.domain.model.Town
import com.sclera.sclerago.domain.repository.AccessRepository
import com.sclera.sclerago.domain.repository.AddressLookupRepository
import com.sclera.sclerago.domain.repository.AssetRepository
import com.sclera.sclerago.domain.repository.AssetSignOffRepository
import com.sclera.sclerago.domain.repository.AuthRepository
import com.sclera.sclerago.domain.repository.ChangeRequestRepository
import com.sclera.sclerago.domain.repository.CurrentAccessRepository
import com.sclera.sclerago.domain.repository.FileRepository
import com.sclera.sclerago.domain.repository.LocalDataReloader
import com.sclera.sclerago.domain.repository.NetworkMonitor
import com.sclera.sclerago.domain.repository.OnboardingRepository
import com.sclera.sclerago.domain.repository.PropertyDownloadRepository
import com.sclera.sclerago.domain.repository.PropertyRepository
import com.sclera.sclerago.domain.usecase.access.PermissionGuard
import com.sclera.sclerago.domain.usecase.asset.ApplyChangeActionUseCase
import com.sclera.sclerago.domain.usecase.asset.ChangeRequestActionsUseCase
import com.sclera.sclerago.domain.usecase.asset.ChangeRequestUseCases
import com.sclera.sclerago.domain.usecase.asset.CloseChangeRequestUseCase
import com.sclera.sclerago.domain.usecase.asset.MarkAssetsValidatedUseCase
import com.sclera.sclerago.domain.usecase.asset.ReloadAssetsUseCase
import com.sclera.sclerago.domain.usecase.asset.RequestChangeUseCase
import com.sclera.sclerago.domain.usecase.asset.SetAssetStatusUseCase
import com.sclera.sclerago.domain.usecase.asset.SignOffActionUseCase
import com.sclera.sclerago.domain.usecase.auth.ObserveAuthStateUseCase
import com.sclera.sclerago.domain.usecase.property.AddPropertyUseCase
import com.sclera.sclerago.domain.usecase.property.AddressUseCases
import com.sclera.sclerago.domain.usecase.property.DeletePropertyUseCase
import com.sclera.sclerago.domain.usecase.property.FilterPropertySummariesUseCase
import com.sclera.sclerago.domain.usecase.property.GetPropertyUseCase
import com.sclera.sclerago.domain.usecase.property.IsDeviceOnlineUseCase
import com.sclera.sclerago.domain.usecase.property.ObserveCanManagePropertiesUseCase
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
import com.sclera.sclerago.domain.usecase.property.SuggestCitiesUseCase
import com.sclera.sclerago.domain.usecase.property.SuggestCountriesUseCase
import com.sclera.sclerago.domain.usecase.property.SuggestStatesUseCase
import com.sclera.sclerago.domain.usecase.property.UpdatePropertyUseCase
import com.sclera.sclerago.domain.usecase.property.ValidatePropertyUseCase
import com.sclera.sclerago.presentation.access.AccessViewModel
import com.sclera.sclerago.presentation.main.AssetListViewModel
import com.sclera.sclerago.presentation.property.PropertyViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

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
    override suspend fun get(id: String): Property = flow.value.first { it.id == id }
    override suspend fun update(property: Property): Property = property
    override suspend fun delete(id: String) = Unit
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
}

/** The asset catalog as the test sets it ([AssetCatalog.loaded] false while the property's data is being opened). */
class FakeCatalogRepository(initial: AssetCatalog = AssetCatalog()) : AssetRepository {
    override val catalog = MutableStateFlow(initial)
    override suspend fun add(assets: List<NewAsset>) = Unit
    override suspend fun setStatus(ids: Collection<String>, status: Status, photoRules: List<com.sclera.sclerago.domain.model.PhotoRule>): com.sclera.sclerago.domain.model.SubmitOutcome = com.sclera.sclerago.domain.model.SubmitOutcome()
    override suspend fun saveDetails(id: String, values: Map<String, String>, sources: Map<String, String>, place: LocValue?) = Unit
    override suspend fun delete(ids: Collection<String>) = Unit
    override suspend fun assignLocation(ids: Collection<String>, location: LocValue) = Unit
    override suspend fun attachMedia(id: String, media: List<Media>) = Unit
    override suspend fun removeMedia(assetId: String, mediaId: String) = Unit
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
    override suspend fun writeFile(uri: String, bytes: ByteArray) = false
    override suspend fun copyFile(from: String, to: String) = false
}

class IdleDownloads : PropertyDownloadRepository {
    override val downloads: StateFlow<PropertyDownloads> = MutableStateFlow(PropertyDownloads(restored = true))
    override fun start(propertyId: String) = Unit
    override fun open(propertyId: String) = Unit
    override fun resume() = Unit
}

/** No address suggestions (the property form's country, state and city lists stay empty). */
class NoAddresses : AddressLookupRepository {
    override suspend fun countries(): List<PlaceName> = emptyList()
    override suspend fun states(country: String): List<PlaceName> = emptyList()
    override suspend fun cities(country: String, state: String?, query: String, limit: Int): List<Town> = emptyList()
}

/** Reloading the asset list from the database does nothing here (the catalog is the test's). */
class NoReload : LocalDataReloader {
    override fun reload() = Unit
}

/** Change requests that are only recorded, never sent. */
class RecordingChangeRequests : ChangeRequestRepository {
    val calls = mutableListOf<String>()
    override suspend fun request(assetId: String, note: String?) {
        calls += "request $assetId"
    }
    override suspend fun close(assetId: String, declined: Boolean) {
        calls += "close $assetId"
    }
}

/** An admin's sign-off that marks nothing (the catalog is the test's). */
class NoSignOff : AssetSignOffRepository {
    override suspend fun markValidated(ids: Collection<String>) = SignOffOutcome()
}

/** The change-request use cases over [assets], wired as Hilt does ([guard] lets everything through by default). */
fun changeRequestUseCases(assets: AssetRepository, guard: PermissionGuard = allowAllGuard()): ChangeRequestUseCases {
    val requests = RecordingChangeRequests()
    val request = RequestChangeUseCase(requests, assets, guard)
    val close = CloseChangeRequestUseCase(requests, guard)
    val markValidated = MarkAssetsValidatedUseCase(NoSignOff(), guard)
    return ChangeRequestUseCases(
        request, close, ChangeRequestActionsUseCase(guard), SignOffActionUseCase(guard), markValidated,
        ApplyChangeActionUseCase(request, close, SetAssetStatusUseCase(assets, guard, FakePhotoRulesRepository()), markValidated),
        guard, InMemoryAccessRepository(),
    )
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
    val addresses = NoAddresses()
    return PropertyViewModel(
        PropertyUseCases(
            ObservePropertiesUseCase(properties), AddPropertyUseCase(properties, guard), ValidatePropertyUseCase(),
            RefreshPropertiesUseCase(properties), ObserveOnboardingUseCase(onboarding), SetOnboardingUseCase(onboarding, guard),
            ObservePropertySummariesUseCase(properties, assets), FilterPropertySummariesUseCase(), ObserveCanManagePropertiesUseCase(guard),
            GetPropertyUseCase(properties), UpdatePropertyUseCase(properties), DeletePropertyUseCase(properties),
        ),
        PropertyDownloadUseCases(
            ObservePropertyDownloadsUseCase(downloads), StartPropertyDownloadUseCase(downloads, AlwaysOnline),
            OpenDownloadedPropertyUseCase(downloads), ResumePropertyDownloadUseCase(downloads), IsDeviceOnlineUseCase(AlwaysOnline),
        ),
        AddressUseCases(SuggestCountriesUseCase(addresses), SuggestStatesUseCase(addresses), SuggestCitiesUseCase(addresses)),
    )
}

/** People and access's view model over [repo]. */
fun accessViewModel(repo: AccessRepository): AccessViewModel =
    AccessViewModel(accessUseCases(repo), ObserveAuthStateUseCase(SignedOutAuth()), { 0L })

/** The asset lists' view model over [repo] (see [assetUseCases] for the rest of the wiring). */
fun assetListViewModel(repo: AssetRepository): AssetListViewModel =
    AssetListViewModel(assetUseCases(repo), ReloadAssetsUseCase(NoReload()), changeRequestUseCases(repo), assetFilterUseCases())

/** The list filter's use cases with nobody's names from a server and an empty type catalogue. */
fun assetFilterUseCases(guard: PermissionGuard = allowAllGuard()): AssetFilterUseCases = AssetFilterUseCases(
    BuildFilterOptionsUseCase(),
    LoadAssigneeNamesUseCase(InMemoryAccessRepository(), NoAssignees, SignedOutAuth(), guard),
    ObserveFormatsByIdUseCase(NoTypeCatalogue),
    guard,
)

/** No server: nobody's names are known; assigning isn't used by the lists. */
private object NoAssignees : AssetAssignmentRepository {
    override suspend fun assign(propertyId: String, assetIds: Collection<String>, email: String?): AssignResult = error("not used")
    override suspend fun assigneeNames(propertyId: String): Map<String, String> = emptyMap()
}

/** An empty asset type catalogue and no fields. */
private object NoTypeCatalogue : AssetFormRepository {
    override fun fields(assetId: String): Flow<List<FieldCfg>> = flowOf(emptyList())
    override fun entries(assetId: String): Flow<List<FormEntry>> = flowOf(emptyList())
    override fun formats(): Flow<List<MasterFormat>> = flowOf(emptyList())
    override fun formatsById(): Flow<Map<String, MasterFormat>> = flowOf(emptyMap())
}
