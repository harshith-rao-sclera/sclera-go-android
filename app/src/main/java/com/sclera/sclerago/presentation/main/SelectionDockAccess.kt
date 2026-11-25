package com.sclera.sclerago.presentation.main

import com.sclera.sclerago.domain.model.AccessBlock
import com.sclera.sclerago.presentation.components.DockAccess
import com.sclera.sclerago.presentation.model.reason

/**
 * Why each action on the selection bar is greyed out for the signed-in user (the User Access Guide), for an action
 * [dockShown] keeps: Validate needs Check, Submit needs Collect, Assign needs Collect and a reviewer, Move needs
 * Configure. Edit needs Collect and, for someone who isn't a reviewer, at least one ticked asset that isn't submitted or
 * signed off (it changes only those, as the server); Delete needs Collect and, for them, no such asset among those ticked. What's left greyed out is a passing state (access still loading, away from a geofenced site) or the
 * ticked assets' own state.
 */
internal fun AssetListState.dockAccess(): DockAccess = DockAccess(
    validate = blocks.check.reason(),
    edit = blocks.collect.reason() ?: editLock.reason(),
    assign = blocks.assign.reason(),
    submit = blocks.collect.reason(),
    move = blocks.configure.reason(),
    delete = blocks.collect.reason() ?: deleteLock.reason(),
)

/**
 * Which selection bar actions the signed-in user's group allows at all: one it doesn't ([AccessBlock.lacksPermission])
 * is left off the bar instead of greyed out. Assign is a reviewer's only ("Assign to me" stands in for anyone else);
 * while the access is still being fetched it stays, greyed out.
 */
internal data class DockShown(
    val validate: Boolean = true,
    val edit: Boolean = true,
    val assign: Boolean = true,
    val take: Boolean = true,
    val submit: Boolean = true,
    val move: Boolean = true,
    val delete: Boolean = true,
)

internal fun AssetListState.dockShown(): DockShown {
    val collect = !blocks.collect.lacks
    return DockShown(
        validate = !blocks.check.lacks,
        edit = collect,
        assign = !blocks.assign.lacks && (access.reviewer || blocks.assign == AccessBlock.CHECKING),
        take = !blocks.assign.lacks,
        submit = collect,
        move = !blocks.configure.lacks,
        delete = collect,
    )
}

private val AccessBlock?.lacks: Boolean get() = this?.lacksPermission == true
