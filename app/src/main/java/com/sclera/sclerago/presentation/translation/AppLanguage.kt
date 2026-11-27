package com.sclera.sclerago.presentation.translation

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.util.Locale

/** The activity's context with other resources, so strings follow the chosen language while Hilt still finds the activity. */
private class ResourcesContext(base: Context, private val localized: Resources) : ContextWrapper(base) {
    override fun getResources(): Resources = localized
}

/** The locale of an app language code (the app's folder codes: `iw`, `in`, `zh-CN`, `zh-TW`, `pt-BR`). */
internal fun localeOf(code: String): Locale =
    Locale.forLanguageTag(when (code) { "iw" -> "he"; "in" -> "id"; else -> code })

/**
 * Shows [content] in the chosen language at once: the app's own strings come from the language's shipped resources, so
 * nothing waits for a download. Names and comments are translated apart (see [rememberTranslated]). Right-to-left
 * languages flip the layout.
 */
@Composable
fun AppLanguage(vm: TranslationViewModel = hiltViewModel(), content: @Composable () -> Unit) {
    val code by vm.target.collectAsStateWithLifecycle()
    val base = LocalContext.current
    val baseConfig = LocalConfiguration.current
    val localized = remember(code, base, baseConfig) {
        code?.let { c ->
            val locale = localeOf(c)
            val config = Configuration(baseConfig).apply { setLocale(locale); setLayoutDirection(locale) }
            ResourcesContext(base, base.createConfigurationContext(config).resources) to config
        }
    }
    val translation = remember(code, vm) { TextTranslation(code, vm::cached, vm::translate) }
    CompositionLocalProvider(LocalTextTranslation provides translation) {
        if (localized == null) content() else CompositionLocalProvider(
            LocalContext provides localized.first,
            LocalConfiguration provides localized.second,
            LocalLayoutDirection provides if (localized.second.layoutDirection == Configuration.SCREENLAYOUT_LAYOUTDIR_RTL) LayoutDirection.Rtl else LayoutDirection.Ltr,
            content = content,
        )
    }
}
