package com.sclera.sclerago.presentation.model

import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import com.sclera.sclerago.R
import com.sclera.sclerago.domain.model.ValidationMark
import com.sclera.sclerago.presentation.icons.Ico
import com.sclera.sclerago.presentation.icons.Icons
import com.sclera.sclerago.presentation.theme.C

/** The chip's name: "Failed validation" or "Needs a look", as the web words them. */
@get:StringRes
val ValidationMark.labelRes: Int
    get() = when (this) {
        ValidationMark.FAILED -> R.string.dash_chip_validation_failed
        ValidationMark.NEEDS_LOOK -> R.string.dash_chip_validation_look
    }

/** Its icon: a stop sign for failed, a magnifier for needs a look. */
val ValidationMark.icon: Ico
    get() = when (this) {
        ValidationMark.FAILED -> Icons.AlertOctagon
        ValidationMark.NEEDS_LOOK -> Icons.SearchAlert
    }

/** Its colour, as the Validation tab colours the outcomes: red for failed, orange for needs a look. */
val ValidationMark.color: Color
    get() = when (this) {
        ValidationMark.FAILED -> C.Red
        ValidationMark.NEEDS_LOOK -> C.Orange
    }

/** What a screen reader says for the row's mark: how many steps need attention. */
@get:PluralsRes
val ValidationMark.problemsRes: Int
    get() = when (this) {
        ValidationMark.FAILED -> R.plurals.asset_validation_failed
        ValidationMark.NEEDS_LOOK -> R.plurals.asset_validation_look
    }
