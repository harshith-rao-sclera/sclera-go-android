package com.sclera.sclerago.presentation.common

import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.sclera.sclerago.R
import com.sclera.sclerago.domain.model.AuthError
import com.sclera.sclerago.domain.model.DataError

/** User-facing text that is resolved against the current locale at display time. */
@Immutable
sealed interface UiText {
    data class Res(@param:StringRes val id: Int, val args: List<Any> = emptyList()) : UiText

    /** A plural string: the form is picked by [count]; [args] fill its placeholders (just the count by default). */
    data class Plural(@param:PluralsRes val id: Int, val count: Int, val args: List<Any> = listOf(count)) : UiText

    /** Text that can't be localized on the device, e.g. a message returned by the server. */
    data class Raw(val text: String) : UiText
}

/** The text in the current locale; an argument that is itself a [UiText] is resolved too. */
@Composable
fun UiText.asString(): String = when (this) {
    is UiText.Res -> stringResource(id, *args.resolved())
    is UiText.Plural -> pluralStringResource(id, count, *args.resolved())
    is UiText.Raw -> text
}

@Composable
private fun List<Any>.resolved(): Array<Any> = map { if (it is UiText) it.asString() else it }.toTypedArray()

/** The localized message for each auth error (formerly carried on the error as messageRes/messageArgs). */
fun AuthError.toUiText(): UiText = when (this) {
    is AuthError.SessionExpired -> UiText.Res(R.string.auth_error_session_expired)
    is AuthError.MultipleSession -> UiText.Res(R.string.auth_error_multiple_session)
    is AuthError.UserBlocked -> UiText.Res(R.string.auth_error_user_blocked)
    is AuthError.UserNotFound -> UiText.Res(R.string.auth_error_user_not_found)
    is AuthError.Network -> UiText.Res(R.string.auth_error_network)
    is AuthError.Server -> UiText.Res(R.string.auth_error_server, listOf(detail))
}

/** The localized message for each reason a change to the offline data could not be made. */
fun DataError.toUiText(): UiText = when (this) {
    DataError.NOT_READY -> UiText.Res(R.string.data_error_not_ready)
    DataError.NAME_TAKEN -> UiText.Res(R.string.data_error_name_taken)
    DataError.IN_USE -> UiText.Res(R.string.data_error_in_use)
    DataError.FAILED -> UiText.Res(R.string.data_error_failed)
}
