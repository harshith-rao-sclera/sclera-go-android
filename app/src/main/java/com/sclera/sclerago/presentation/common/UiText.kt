package com.sclera.sclerago.presentation.common

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.res.stringResource
import com.sclera.sclerago.R
import com.sclera.sclerago.domain.model.AuthError

/** User-facing text that is resolved against the current locale at display time. */
@Immutable
sealed interface UiText {
    data class Res(@param:StringRes val id: Int, val args: List<Any> = emptyList()) : UiText

    /** Text that can't be localized on the device, e.g. a message returned by the server. */
    data class Raw(val text: String) : UiText
}

@Composable
fun UiText.asString(): String = when (this) {
    is UiText.Res -> stringResource(id, *args.toTypedArray())
    is UiText.Raw -> text
}

/** The localized message for each auth error (formerly carried on the error as messageRes/messageArgs). */
fun AuthError.toUiText(): UiText = when (this) {
    is AuthError.SessionExpired -> UiText.Res(R.string.auth_error_session_expired)
    is AuthError.MultipleSession -> UiText.Res(R.string.auth_error_multiple_session)
    is AuthError.UserBlocked -> UiText.Res(R.string.auth_error_user_blocked)
    is AuthError.UserNotFound -> UiText.Res(R.string.auth_error_user_not_found)
    is AuthError.Network -> UiText.Res(R.string.auth_error_network)
    is AuthError.Server -> UiText.Res(R.string.auth_error_server, listOf(detail))
}
