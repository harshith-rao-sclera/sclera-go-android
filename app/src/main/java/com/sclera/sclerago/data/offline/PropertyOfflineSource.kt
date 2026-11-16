package com.sclera.sclerago.data.offline

import com.sclera.sclerago.data.mapper.toProperty
import com.sclera.sclerago.data.sync.AdcDatabase
import com.sclera.sclerago.domain.model.Property
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

private const val PROPERTIES = "SELECT * FROM properties WHERE %LIVE% ORDER BY name, id"

/**
 * The properties (sites) the user can open. The `properties` table is expected in the offline database later;
 * until the sync brings it, there are none and every call returns an empty list. Reads only.
 */
class PropertyOfflineSource(private val db: AdcDatabase, private val io: CoroutineDispatcher) {

    suspend fun properties(): List<Property> = withContext(io) { load() }

    fun propertiesFlow(): Flow<List<Property>> = db.watch(io, TABLE) { load() }

    private fun load(): List<Property> = db.whenTable(TABLE, emptyList()) {
        db.query(PROPERTIES.replace("%LIVE%", db.notRemoved(TABLE))).map { it.toProperty() }
    }

    private companion object {
        const val TABLE = "properties"
    }
}
