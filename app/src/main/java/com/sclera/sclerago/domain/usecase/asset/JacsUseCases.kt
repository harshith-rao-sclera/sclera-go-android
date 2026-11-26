package com.sclera.sclerago.domain.usecase.asset

import kotlinx.coroutines.flow.StateFlow
import com.sclera.sclerago.domain.model.sameJacsNumber
import com.sclera.sclerago.domain.model.JacsGroup
import com.sclera.sclerago.domain.model.MasterFormat
import com.sclera.sclerago.domain.model.ClassifyDetails
import com.sclera.sclerago.domain.model.JacsPage
import com.sclera.sclerago.domain.model.JacsSuggestion
import com.sclera.sclerago.domain.model.JacsType
import com.sclera.sclerago.domain.model.OnlineError
import com.sclera.sclerago.domain.model.OnlineException
import com.sclera.sclerago.domain.model.Permission
import com.sclera.sclerago.domain.repository.ClassificationRepository
import com.sclera.sclerago.domain.repository.JacsRepository
import com.sclera.sclerago.domain.usecase.access.PermissionGuard
import com.sclera.sclerago.domain.validation.isServerId
import javax.inject.Inject

/** Searches the JACS catalog for the asset's JACS code (the catalogue kept on the phone, else `GET /jacs-codes`), as anyone who can collect may. */
class SearchJacsCodesUseCase @Inject constructor(private val repo: JacsRepository, private val guard: PermissionGuard) {
    suspend operator fun invoke(query: String, cursor: String? = null): JacsPage {
        guard.requireOnline(Permission.COLLECT)
        return repo.search(query.trim(), cursor)
    }
}

/**
 * The type a picked JACS [code] gives the asset, as the backend asks of the phone (it works offline): none when the
 * asset's type [current] has its own code under the same number (the type stays); else the type in [formats] whose own
 * code it is; else the type named by one of the code's catalogue groups; else, online, the server's match (it also
 * reads the type library's numbers). Null: the type stays. Nothing is saved.
 */
class JacsTypeUseCase @Inject constructor(private val repo: JacsRepository, private val guard: PermissionGuard) {
    suspend operator fun invoke(code: String, formats: List<MasterFormat>, current: MasterFormat?): JacsType? {
        val value = code.trim()
        if (value.isEmpty()) return null
        if (current?.jacsCode?.let { sameJacsNumber(it, value) } == true) return null
        formats.firstOrNull { it.jacsCode?.trim() == value }?.let { return it.toJacsType() }
        val groups = repo.catalog.value.firstOrNull { it.code == value }?.groups.orEmpty()
        formats.firstOrNull { f -> groups.any { g -> g.names(f) } }?.let { return it.toJacsType() }
        return try {
            guard.requireOnline(Permission.COLLECT)
            repo.typeFor(value)
        } catch (_: OnlineException) {
            null
        }
    }

    private fun JacsGroup.names(f: MasterFormat): Boolean =
        f.systemType.equals(systemType, true) && f.assetType.orEmpty().equals(assetType.orEmpty(), true) &&
            f.subtype.orEmpty().equals(subtype.orEmpty(), true)

    private fun MasterFormat.toJacsType() = JacsType(id, systemType, assetType, subtype)
}

/** The titles of the JACS codes (code → title), from the catalogue kept on the phone, to show a code with its name. */
class ObserveJacsTitlesUseCase @Inject constructor(private val repo: JacsRepository) {
    operator fun invoke(): StateFlow<Map<String, String>> = repo.titles
}

/** The JACS use cases, for injecting as one. */
class JacsUseCases @Inject constructor(
    val search: SearchJacsCodesUseCase,
    val typeFor: JacsTypeUseCase,
    val titles: ObserveJacsTitlesUseCase,
)
