package com.sclera.sclerago.domain.usecase.asset

import com.sclera.sclerago.domain.model.ModelManuals
import com.sclera.sclerago.domain.model.FieldCfg
import com.sclera.sclerago.domain.model.FormEntry
import com.sclera.sclerago.domain.model.LocNode
import com.sclera.sclerago.domain.model.MasterFormat
import com.sclera.sclerago.domain.model.PhotoRule
import com.sclera.sclerago.domain.model.PlaceNames
import com.sclera.sclerago.domain.model.SpecLookup
import com.sclera.sclerago.domain.model.placeNames
import com.sclera.sclerago.domain.model.reclassify
import com.sclera.sclerago.domain.model.Permission
import com.sclera.sclerago.domain.model.Media
import com.sclera.sclerago.domain.model.MAX_CLASSIFY_PHOTOS
import com.sclera.sclerago.domain.model.readablePhotos
import com.sclera.sclerago.domain.repository.AssetFormRepository
import com.sclera.sclerago.domain.repository.AssetValidationRepository
import com.sclera.sclerago.domain.repository.PhotoRulesRepository
import com.sclera.sclerago.domain.repository.SpecLookupRepository
import com.sclera.sclerago.domain.usecase.access.PermissionGuard
import com.sclera.sclerago.domain.usecase.location.ObserveLocationTreeUseCase
import com.sclera.sclerago.domain.validation.AssetValidation
import com.sclera.sclerago.domain.validation.combineValidation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/** The fields of an asset (the standard ones, then its own), again whenever they change. */
class ObserveAssetFieldsUseCase @Inject constructor(private val repo: AssetFormRepository) {
    operator fun invoke(assetId: String): Flow<List<FieldCfg>> = repo.fields(assetId)
}

/** The rows of an asset's fields in their order, its photo, video and voice memo fields included. */
class ObserveAssetFieldRowsUseCase @Inject constructor(private val repo: AssetFormRepository) {
    operator fun invoke(assetId: String): Flow<List<FormEntry>> = repo.entries(assetId)
}

/** The active asset type catalogue the System details dropdowns offer, again whenever it changes. */
class ObserveMasterFormatsUseCase @Inject constructor(private val repo: AssetFormRepository) {
    operator fun invoke(): Flow<List<MasterFormat>> = repo.formats()
}

/** The building, floor and location names of an asset's place, looked up in the property's place tree. */
class GetPlaceNamesUseCase @Inject constructor() {
    operator fun invoke(tree: List<LocNode>, buildingId: String?, floorId: String?, locationId: String?): PlaceNames =
        placeNames(tree, locationId, floorId, buildingId)
}

/** The classification values to set when the system, asset or sub type changes (see [reclassify]). */
class ReclassifyUseCase @Inject constructor() {
    operator fun invoke(formats: List<MasterFormat>, values: Map<String, String>, key: String, value: String): Map<String, String> =
        formats.reclassify(values, key, value)
}

/** The asset's validation as the server builds it from its runs (see [combineValidation]); null before any run. */
class ObserveAssetValidationUseCase @Inject constructor(private val repo: AssetValidationRepository) {
    operator fun invoke(assetId: String): Flow<AssetValidation?> =
        repo.runs(assetId).map { (records, issues) -> combineValidation(records, issues) }
}

/**
 * Validates an asset again at the server, as anyone with check may (validate includes it; else
 * [com.sclera.sclerago.domain.model.OnlineException] Forbidden): from its first step to look at, or every step ([fromStart]).
 */
class RevalidateAssetUseCase @Inject constructor(private val repo: AssetValidationRepository, private val guard: PermissionGuard) {
    /** The signed-in user may validate assets again (the Validate buttons show only then). */
    fun allowed(): Boolean = guard.allows(Permission.CHECK)

    suspend operator fun invoke(assetId: String, fromStart: Boolean) {
        guard.requireOnline(Permission.CHECK)
        repo.revalidate(assetId, fromStart)
    }
}

/** The current property's photo rules, which the Media tab's photo cards follow. */
class ObservePhotoRulesUseCase @Inject constructor(private val repo: PhotoRulesRepository) {
    operator fun invoke(): Flow<List<PhotoRule>> = repo.rules()
}

/**
 * The web's AI suggestions: the asset's newest look-up, starting one (as anyone who can collect may), following it until
 * it has finished, and telling the server its suggestions were shown.
 */
class SpecLookupUseCase @Inject constructor(private val repo: SpecLookupRepository, private val guard: PermissionGuard) {
    suspend fun latest(assetId: String): SpecLookup? = repo.latest(assetId)

    suspend fun start(assetId: String, typed: Map<String, String>): SpecLookup {
        guard.requireOnline(Permission.COLLECT)
        return repo.start(assetId, typed)
    }

    /** Asks again every [POLL_MS] until the look-up has finished; the server gives up on one after ten minutes. */
    suspend fun follow(assetId: String, lookup: SpecLookup): SpecLookup {
        var now = lookup
        while (now.status.waiting) {
            delay(POLL_MS)
            now = repo.get(assetId, now.id)
        }
        return now
    }

    suspend fun seen(lookupId: String) = repo.seen(lookupId)

    /** The manuals of the asset's model found so far, and whether the search for them still runs. */
    suspend fun manuals(assetId: String): ModelManuals = repo.manuals(assetId)

    /** The photos the phone's look-up sends, as the server reads them (a few spare in case one can't be read here). */
    fun photosToRead(media: List<Media>): List<Media> = readablePhotos(media, MAX_CLASSIFY_PHOTOS * 2)

    /** The phone's look-up from [photos] and the form's [typed] values (POST /properties/{id}/lookup). */
    suspend fun lookUpOnPhone(assetId: String, photos: List<Media>, typed: Map<String, String>, masterFormatId: String?): SpecLookup {
        guard.requireOnline(Permission.COLLECT)
        return repo.lookUpOnPhone(assetId, photos, typed, masterFormatId)
    }

    private companion object {
        const val POLL_MS = 2_500L
    }
}

/** What the asset details screen reads from the property's data besides the asset itself, bundled for injection. */
class AssetFormUseCases @Inject constructor(
    val fields: ObserveAssetFieldsUseCase,
    val fieldRows: ObserveAssetFieldRowsUseCase,
    val formats: ObserveMasterFormatsUseCase,
    val placeTree: ObserveLocationTreeUseCase,
    val placeNames: GetPlaceNamesUseCase,
    val reclassify: ReclassifyUseCase,
    val validation: ObserveAssetValidationUseCase,
    val revalidate: RevalidateAssetUseCase,
    val photoRules: ObservePhotoRulesUseCase,
    val lookups: SpecLookupUseCase,
    val voiceFill: FillByVoiceUseCase,
)
