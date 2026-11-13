package com.sclera.sclerago.data.offline

import com.sclera.sclerago.data.mapper.DbJson
import com.sclera.sclerago.data.mapper.formatDbTime
import com.sclera.sclerago.data.mapper.stepKeysOf
import com.sclera.sclerago.data.sync.SyncCredentialsProvider
import com.sclera.sclerago.data.sync.AdcDatabase
import com.sclera.sclerago.domain.model.DataError
import com.sclera.sclerago.domain.model.DataException
import com.sclera.sclerago.domain.system.IdGenerator
import com.sclera.sclerago.domain.system.TimeProvider
import com.sclera.sclerago.domain.system.next
import kotlinx.coroutines.CoroutineDispatcher

/**
 * Writes the user's saved validations. A name is unique among them (as on the server), and a deleted validation is
 * marked removed while the jobs that ran it stay, no longer linked to it. Jobs that run on this device are not written.
 */
class ValidationWriter(
    private val db: AdcDatabase,
    private val io: CoroutineDispatcher,
    private val time: TimeProvider,
    private val ids: IdGenerator,
    private val user: SyncCredentialsProvider,
) {
    /** Adds a validation ([id] null) or updates one; returns its id. [ruleIds] are the app's rule ids; the server's steps of them are stored. Throws NAME_TAKEN when another validation has [name]. */
    suspend fun save(id: String?, name: String, description: String, ruleIds: List<String>): String = db.write(io) {
        val now = formatDbTime(time.nowMillis())
        val rules = DbJson.writeList(stepKeysOf(ruleIds))
        val text = description.trim().ifEmpty { null }
        if (id != null && !exists(live(VALIDATION_EXISTS, "validations"), listOf(id))) throw DataException(DataError.FAILED)
        if (exists(live(VALIDATION_NAME_TAKEN, "validations"), listOf(name, id.orEmpty()))) throw DataException(DataError.NAME_TAKEN)
        if (id == null) {
            val created = ids.next()
            execute(VALIDATION_INSERT, listOf(created, name, text, rules, user.userId(), now, now), setOf("validations"))
            created
        } else {
            execute(live(VALIDATION_UPDATE, "validations"), listOf(name, text, rules, now, id), setOf("validations"))
            id
        }
    }

    /** Removes the validation [id]. */
    suspend fun delete(id: String) = db.write(io) {
        removeRows("validations", "id = ?", listOf(id), formatDbTime(time.nowMillis()))
        if (hasTable("validation_jobs")) execute(JOBS_UNLINK, listOf(id), setOf("validation_jobs"))
    }
}
