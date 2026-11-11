package com.example.sclerago.ui

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.res.stringResource
import com.example.sclerago.auth.AuthError

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

fun AuthError.toUiText(): UiText = UiText.Res(messageRes, messageArgs)
