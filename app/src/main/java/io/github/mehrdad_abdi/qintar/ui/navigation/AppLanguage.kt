package io.github.mehrdad_abdi.qintar.ui.navigation

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

/**
 * Per-app language through the AndroidX API (§6.6): no context wrapping. AppCompat stores the
 * choice (see `AppLocalesMetadataHolderService` in the manifest) and recreates the activity.
 */
object AppLanguage {
    /** The languages listed in `res/xml/locales_config.xml`. */
    val supported = listOf("en", "fa", "ar")

    /** @param languageTag one of [supported], or null to follow the system language. */
    fun set(languageTag: String?) {
        val locales = if (languageTag == null) {
            LocaleListCompat.getEmptyLocaleList()
        } else {
            LocaleListCompat.forLanguageTags(languageTag)
        }
        AppCompatDelegate.setApplicationLocales(locales)
    }

    /** The chosen language tag, or null when following the system. */
    fun current(): String? = AppCompatDelegate.getApplicationLocales().takeIf { !it.isEmpty }?.get(0)?.language
}
