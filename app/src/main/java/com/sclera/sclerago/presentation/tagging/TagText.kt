package com.sclera.sclerago.presentation.tagging

import com.sclera.sclerago.R
import com.sclera.sclerago.domain.model.OnlineException
import com.sclera.sclerago.domain.model.TaggingError
import com.sclera.sclerago.domain.model.TaggingException
import com.sclera.sclerago.presentation.common.UiText
import com.sclera.sclerago.presentation.common.toUiText

/** The words shown to the user for why a tag was not attached. */
fun Throwable.tagFailureText(holderName: ((String) -> String?)? = null): UiText = when (this) {
    is TaggingException -> {
        val holder = heldBy?.let { holderName?.invoke(it) }
        if (error == TaggingError.TAKEN_BY_SUBMITTED && holder != null) UiText.Res(R.string.tag_error_submitted_named, listOf(holder)) else error.toUiText()
    }
    is OnlineException -> error.toUiText()
    else -> UiText.Res(R.string.tag_error_generic)
}

fun TaggingError.toUiText(): UiText = UiText.Res(
    when (this) {
        TaggingError.EMPTY_TAG -> R.string.tag_error_empty
        TaggingError.BLANK_NFC -> R.string.tag_error_blank_nfc
        TaggingError.UNKNOWN_ID -> R.string.tag_error_unknown_id
        TaggingError.TAKEN_BY_VDMS -> R.string.tag_error_taken_by_vdms
        TaggingError.TAKEN_BY_OTHER_PROPERTY -> R.string.tag_error_other_property
        TaggingError.TAKEN_BY_SUBMITTED -> R.string.tag_error_submitted
        TaggingError.NEEDS_MOVE -> R.string.tag_error_taken_by_draft
        TaggingError.REGISTRATION_FAILED -> R.string.tag_error_registration
        TaggingError.CLAIMED_ELSEWHERE -> R.string.tag_error_claimed_elsewhere
        TaggingError.INVALID_RESPONSE -> R.string.tag_error_generic
        TaggingError.PHOTO_REQUIRED -> R.string.tag_error_photo_required
        TaggingError.NEEDS_CONNECTION -> R.string.tag_error_offline
    },
)
