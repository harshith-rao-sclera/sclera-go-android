package com.sclera.sclerago.domain.usecase.asset

import com.sclera.sclerago.domain.model.ClassificationResult
import com.sclera.sclerago.domain.model.ClassifyDetails
import com.sclera.sclerago.domain.model.FieldCfg
import com.sclera.sclerago.domain.model.FieldSection
import com.sclera.sclerago.domain.model.MAX_CLASSIFY_PHOTOS
import com.sclera.sclerago.domain.model.Media
import com.sclera.sclerago.domain.model.OnlineError
import com.sclera.sclerago.domain.model.OnlineException
import com.sclera.sclerago.domain.model.Permission
import com.sclera.sclerago.domain.model.PhotoKind
import com.sclera.sclerago.domain.model.TypeField
import com.sclera.sclerago.domain.model.fieldKindOf
import com.sclera.sclerago.domain.model.typeFieldKey
import com.sclera.sclerago.domain.repository.ClassificationRepository
import com.sclera.sclerago.domain.usecase.access.PermissionGuard
import javax.inject.Inject

/**
 * Asks the server what an asset is, as anyone who can collect may: from its photos (nameplate first, then label, then
 * equipment photos, at most three) and what is typed, or just the type the person picked ([masterFormatId]) for its JACS codes.
 * Throws [OnlineException]: Forbidden without collect, Invalid when there is no photo and nothing typed.
 */
class SuggestAssetTypeUseCase @Inject constructor(private val repo: ClassificationRepository, private val guard: PermissionGuard) {
    suspend operator fun invoke(details: ClassifyDetails, media: List<Media>, masterFormatId: String? = null): ClassificationResult {
        guard.requireOnline(Permission.COLLECT)
        val photos = if (masterFormatId != null) emptyList() else classifyPhotos(media)
        if (masterFormatId == null && photos.isEmpty() && details.isEmpty) throw OnlineException(OnlineError.Invalid)
        return repo.suggest(details, photos, masterFormatId)
    }

    /** The photos worth reading, best first: nameplate, label, equipment, then any other photo that answers no field. */
    private fun classifyPhotos(media: List<Media>): List<Media> {
        val photos = media.filter { !it.video && !it.isDoc && !it.audio && it.fieldId == null }
        val order = listOf(PhotoKind.NAMEPLATE, PhotoKind.LABEL_ID, PhotoKind.ASSET, null)
        return photos.sortedWith(compareBy({ order.indexOf(it.category) }, { -it.createdAt })).take(MAX_CLASSIFY_PHOTOS * 2)
    }
}

/**
 * The fields of the type [masterFormatId] an asset doesn't have yet, as form fields: never mandatory, named by
 * [typeFieldKey] until the asset is saved and they are made. A field whose name is already on the asset ([taken], any case) is left out.
 */
class GetTypeFieldsUseCase @Inject constructor(private val repo: ClassificationRepository) {
    suspend operator fun invoke(masterFormatId: String, taken: Collection<String>): List<FieldCfg> {
        val names = taken.map { it.trim().lowercase() }.toSet()
        return repo.typeFields(masterFormatId).sortedBy { it.position }
            .distinctBy { it.name.lowercase() }
            .filter { it.name.isNotBlank() && it.name.lowercase() !in names }
            .mapNotNull { it.toFormField() }
    }

    private fun TypeField.toFormField(): FieldCfg? {
        val kind = fieldKindOf(type) ?: return null
        return FieldCfg(
            key = typeFieldKey(name), label = name, kind = kind, section = FieldSection.MORE, required = false, custom = true,
            options = options, tooltip = helpText.orEmpty(),
        )
    }
}

/** Keeps every type's fields of the current property on the device, for picking a type and saving assets offline. */
class RefreshTypeFieldsUseCase @Inject constructor(private val repo: ClassificationRepository) {
    suspend operator fun invoke() = repo.refresh()
}

/** Classifying an asset, bundled for injection. */
class ClassifyAssetUseCases @Inject constructor(
    val suggest: SuggestAssetTypeUseCase,
    val typeFields: GetTypeFieldsUseCase,
    val refreshTypeFields: RefreshTypeFieldsUseCase,
)
