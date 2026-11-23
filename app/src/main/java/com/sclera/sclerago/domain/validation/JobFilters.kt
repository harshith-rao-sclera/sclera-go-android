package com.sclera.sclerago.domain.validation

// Filtering Recent jobs (the web's jobFilters): who ran it, how it stands, and which check it was. The list holds the
// newest runs (the API's most, 100); the filters narrow those, and the choices offered come from them too.

/** How many Recent jobs rows show at first; "Show more" adds as many again. */
const val JOBS_SHOWN = 20

/** [JobFilters.by] for the signed-in user ("Me"). */
const val JOBS_BY_ME = "me"

/** How a run stands, as Recent jobs filters it: Running covers a run still waiting its turn (queued). */
enum class JobStatusFilter {
    RUNNING, COMPLETED, FAILED, READY;

    /** [run] stands this way. */
    fun keeps(run: ValRun): Boolean = when (this) {
        RUNNING -> run.status.active
        COMPLETED -> run.status == RunStatus.COMPLETED
        FAILED -> run.status == RunStatus.FAILED
        READY -> run.status == RunStatus.READY
    }
}

/** The Recent jobs filters; null is any. [by] is a user id, or [JOBS_BY_ME]. */
data class JobFilters(val by: String? = null, val status: JobStatusFilter? = null, val check: String? = null) {
    val any: Boolean get() = by != null || status != null || check != null
}

/** Someone who started one of the runs: their user id and name (null when the server doesn't say: "Someone"). */
data class JobPerson(val id: String, val name: String?)

/**
 * The choices for "Run by" and "Check", from the runs in the list: [mine] when one of them is the signed-in user's
 * (offered first, as "Me"), the other people who started one (by name), and the checks' names (A to Z).
 */
data class JobFilterOptions(val mine: Boolean, val people: List<JobPerson>, val checks: List<String>)

/**
 * The [JobFilterOptions] of [runs] for the signed-in user [myId]. A run nobody started (the app did) isn't offered by
 * person.
 */
fun jobFilterOptions(runs: List<ValRun>, myId: String?): JobFilterOptions {
    val people = LinkedHashMap<String, String?>()
    val checks = LinkedHashSet<String>()
    var mine = false
    runs.forEach { run ->
        if (run.name.isNotBlank()) checks += run.name
        val by = run.createdBy?.takeIf { it.isNotBlank() } ?: return@forEach
        if (myId != null && by == myId) mine = true
        else if (by !in people) people[by] = run.startedBy
    }
    return JobFilterOptions(
        mine = mine,
        people = people.map { (id, name) -> JobPerson(id, name) }
            .sortedWith(compareBy<JobPerson, String?>(nullsLast(String.CASE_INSENSITIVE_ORDER)) { it.name }),
        checks = checks.sortedWith(String.CASE_INSENSITIVE_ORDER),
    )
}

/** The runs [filters] keep, for the signed-in user [myId]. */
fun List<ValRun>.filteredBy(filters: JobFilters, myId: String?): List<ValRun> = filter { run ->
    (filters.by == null || run.createdBy == (if (filters.by == JOBS_BY_ME) myId else filters.by)) &&
        (filters.status?.keeps(run) ?: true) &&
        (filters.check == null || run.name == filters.check)
}

/**
 * [this] without a choice that is no longer offered (its last run dropped off the list, or the property changed), so
 * the list is never emptied by a filter nobody can see.
 */
fun JobFilters.keptIn(options: JobFilterOptions): JobFilters = copy(
    by = by?.takeIf { if (it == JOBS_BY_ME) options.mine else options.people.any { p -> p.id == it } },
    check = check?.takeIf { it in options.checks },
)
