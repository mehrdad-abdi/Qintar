package io.github.mehrdad_abdi.quranbookmarks.util

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.util.Log
import io.github.mehrdad_abdi.quranbookmarks.domain.model.AppLanguage
import java.util.Locale

/**
 * Helper class for managing app locale and language changes.
 */
object LocaleHelper {

    private const val TAG = "LocaleHelper"

    /**
     * Set the app locale based on the given language.
     * This updates the configuration and returns a new context with the updated locale.
     */
    fun setLocale(context: Context, language: AppLanguage): Context {
        Log.d(TAG, "setLocale called with language: ${language.localeCode} (${language.nativeName})")

        val locale = Locale(language.localeCode)
        Locale.setDefault(locale)

        Log.d(TAG, "Locale.setDefault set to: ${Locale.getDefault()}")

        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)

        Log.d(TAG, "Configuration locale set to: ${config.locales[0]}")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            config.setLayoutDirection(locale)
            Log.d(TAG, "Layout direction set for RTL: ${config.layoutDirection}")
        }

        val newContext = context.createConfigurationContext(config)
        Log.d(TAG, "Created new context with locale: ${newContext.resources.configuration.locales[0]}")

        return newContext
    }

    /**
     * Apply locale to the given activity.
     * This should be called in Activity.attachBaseContext()
     */
    fun applyLocale(context: Context, language: AppLanguage): Context {
        return setLocale(context, language)
    }

    /**
     * Restart the activity to apply the new language.
     * This recreates the activity with the new locale.
     */
    fun restartActivity(activity: Activity) {
        val intent = activity.intent
        activity.finish()
        activity.startActivity(intent)
        activity.overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }

    /**
     * Get the current locale from the configuration.
     */
    fun getCurrentLocale(context: Context): Locale {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            context.resources.configuration.locales[0]
        } else {
            @Suppress("DEPRECATION")
            context.resources.configuration.locale
        }
    }

    /**
     * Check if the current locale is RTL.
     */
    fun isRtl(context: Context): Boolean {
        val config = context.resources.configuration
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            config.layoutDirection == android.view.View.LAYOUT_DIRECTION_RTL
        } else {
            false
        }
    }
}
