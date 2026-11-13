package com.sclera.sclerago.domain.repository

import com.sclera.sclerago.domain.model.Asset
import com.sclera.sclerago.domain.validation.CheckInput
import com.sclera.sclerago.domain.validation.Rule
import com.sclera.sclerago.domain.validation.SavedValidation
import com.sclera.sclerago.domain.validation.ValJob
import kotlinx.coroutines.flow.StateFlow

/** Validation jobs that ran (or are running) and the user's saved validations. */
interface ValidationRepository {
    /** Every job, all properties, newest first. */
    val jobs: StateFlow<List<ValJob>>

    /** The user's own validations (the built-in ones are the templates). */
    val saved: StateFlow<List<SavedValidation>>

    /** A template's or saved validation's name, by id. */
    fun sourceName(id: String): String

    /** A template's or saved validation's rules, by id. */
    fun sourceRules(id: String): List<Rule>

    /**
     * Starts a job running [sourceId]'s rules on the assets [ids] of [prop]. It runs in the background
     * (or at once when [instant]); [inputs] is read when it finishes, so it checks the assets as they
     * are then, and [onDone] gets the finished job.
     */
    fun start(
        sourceId: String,
        ids: List<String>,
        label: String,
        prop: String,
        inputs: () -> List<Pair<Asset, CheckInput>>,
        startedAt: Long,
        instant: Boolean,
        onDone: (ValJob) -> Unit,
    )

    /** Saves a new validation ([id] null) or updates one; returns its id, or null if it isn't complete. */
    fun save(id: String?, name: String, description: String, ruleIds: List<String>): String?

    fun delete(id: String)
}
