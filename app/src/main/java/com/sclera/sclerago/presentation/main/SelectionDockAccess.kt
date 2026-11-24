package com.sclera.sclerago.presentation.main

import com.sclera.sclerago.presentation.components.DockAccess
import com.sclera.sclerago.presentation.model.reason

/**
 * Why each action on the selection bar is greyed out for the signed-in user (the User Access Guide): Validate needs Check,
 * Edit and Submit need Collect, Assign needs Collect (and who the user is), Move needs Configure, and Delete needs Collect
 * and, for someone who isn't a reviewer, no submitted or signed-off asset among those ticked.
 */
internal fun AssetListState.dockAccess(): DockAccess = DockAccess(
    validate = blocks.check.reason(),
    edit = blocks.collect.reason(),
    assign = blocks.assign.reason(),
    submit = blocks.collect.reason(),
    move = blocks.configure.reason(),
    delete = blocks.collect.reason() ?: deleteLock.reason(),
)
