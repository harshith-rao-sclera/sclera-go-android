package com.sclera.sclerago.data.sync

import com.sclera.sclerago.domain.repository.LocalDataReloader
import javax.inject.Inject

/** [LocalDataReloader] through the table invalidations: every observer reads its tables again, as after a sync pull. */
class TableReloader @Inject constructor(private val invalidations: TableInvalidations) : LocalDataReloader {
    override fun reload() = invalidations.emitAll()
}
