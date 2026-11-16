package com.sclera.sclerago.data.repository

import com.sclera.sclerago.data.online.PropertyStore
import com.sclera.sclerago.data.remote.adc.PhotoRulesDto
import com.sclera.sclerago.domain.model.DEFAULT_PHOTO_RULES
import com.sclera.sclerago.domain.model.PhotoKind
import com.sclera.sclerago.domain.model.PhotoRule
import com.sclera.sclerago.domain.repository.PhotoRulesRepository
import com.sclera.sclerago.domain.repository.PropertyDownloadRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject

/** The current property's `settings.photoRules` from the property list the app keeps (encrypted) from the server. */
class PhotoRulesRepositoryImpl @Inject constructor(
    private val store: PropertyStore,
    private val downloads: PropertyDownloadRepository,
) : PhotoRulesRepository {
    override fun rules(): Flow<List<PhotoRule>> = combine(store.photoRules, downloads.downloads) { all, d ->
        d.currentId?.let(all::get)?.toRules() ?: DEFAULT_PHOTO_RULES
    }

    private fun PhotoRulesDto.toRules() = listOf(
        PhotoRule(PhotoKind.NAMEPLATE, nameplate.min, nameplate.max),
        PhotoRule(PhotoKind.ASSET, asset.min, asset.max),
        PhotoRule(PhotoKind.LABEL_ID, labelId.min, labelId.max),
    )
}
