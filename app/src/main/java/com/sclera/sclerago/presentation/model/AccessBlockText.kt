package com.sclera.sclerago.presentation.model

import androidx.annotation.StringRes
import com.sclera.sclerago.R
import com.sclera.sclerago.domain.model.AccessBlock
import com.sclera.sclerago.domain.model.AssetLock
import com.sclera.sclerago.presentation.common.UiText

/** What a tap on a button greyed out by [AccessBlock] says. */
@get:StringRes
val AccessBlock.message: Int
    get() = when (this) {
        AccessBlock.CHECKING -> R.string.access_block_checking
        AccessBlock.NO_GROUP -> R.string.access_block_no_group
        AccessBlock.NEEDS_VIEW -> R.string.access_block_view
        AccessBlock.NEEDS_COLLECT -> R.string.access_block_collect
        AccessBlock.NEEDS_CHECK -> R.string.access_block_check
        AccessBlock.NEEDS_VALIDATE -> R.string.access_block_validate
        AccessBlock.NEEDS_CONFIGURE -> R.string.access_block_configure
        AccessBlock.NEEDS_MANAGE_PROPERTIES -> R.string.access_block_manage_properties
        AccessBlock.NEEDS_MANAGE_ACCESS -> R.string.access_block_manage_access
        AccessBlock.NEEDS_ALL_PROPERTIES -> R.string.access_block_all_properties
        AccessBlock.OFF_PREMISE -> R.string.access_block_off_premise
    }

/** What a tap on a button greyed out by the asset's [AssetLock] says. */
@get:StringRes
val AssetLock.message: Int
    get() = when (this) {
        AssetLock.NOT_ASSIGNED -> R.string.asset_lock_not_assigned
        AssetLock.SUBMITTED -> R.string.access_block_submitted
        AssetLock.MANAGED -> R.string.asset_lock_managed
        AssetLock.NO_PERMISSION -> R.string.access_block_collect
        AssetLock.OFF_PREMISE -> R.string.access_block_off_premise
    }

/** [AccessBlock.message] as [UiText], null when nothing blocks. */
fun AccessBlock?.reason(): UiText? = this?.let { UiText.Res(it.message) }

/** [AssetLock.message] as [UiText], null when nothing locks. */
fun AssetLock?.reason(): UiText? = this?.let { UiText.Res(it.message) }
