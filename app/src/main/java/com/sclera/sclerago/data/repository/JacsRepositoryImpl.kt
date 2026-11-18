package com.sclera.sclerago.data.repository

import com.sclera.sclerago.data.remote.adc.AdcClassifyApi
import com.sclera.sclerago.domain.model.JacsCode
import com.sclera.sclerago.domain.model.JacsPage
import com.sclera.sclerago.domain.model.JacsType
import com.sclera.sclerago.domain.model.OnlineError
import com.sclera.sclerago.domain.model.OnlineException
import com.sclera.sclerago.domain.repository.JacsRepository
import com.sclera.sclerago.domain.repository.PropertyDownloadRepository
import javax.inject.Inject

/** The JACS catalog (`/jacs-codes`) and the current property's type for a code (`/properties/{id}/jacs-codes/{code}/master-format`). */
class JacsRepositoryImpl @Inject constructor(
    private val api: AdcClassifyApi,
    private val downloads: PropertyDownloadRepository,
) : JacsRepository {
    override suspend fun search(query: String, cursor: String?): JacsPage {
        val page = api.jacsCodes(query.ifBlank { null }, cursor, PAGE_SIZE)
        return JacsPage(
            codes = page.data.filter { it.code.isNotBlank() }.map { JacsCode(it.code, it.title?.trim()?.takeIf { t -> t.isNotEmpty() }) },
            next = page.nextCursor?.takeIf { page.hasMore },
        )
    }

    override suspend fun typeFor(code: String): JacsType? {
        val property = downloads.downloads.value.currentId ?: throw OnlineException(OnlineError.NotFound)
        val mf = api.jacsMasterFormat(property, code).masterFormat ?: return null
        return JacsType(mf.id, mf.systemType, mf.assetType?.takeIf { it.isNotBlank() }, mf.subtype?.takeIf { it.isNotBlank() })
    }

    private companion object {
        const val PAGE_SIZE = 50
    }
}
