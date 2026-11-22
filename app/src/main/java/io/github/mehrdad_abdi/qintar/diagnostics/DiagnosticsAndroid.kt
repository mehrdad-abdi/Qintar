package io.github.mehrdad_abdi.qintar.diagnostics

import android.content.Context
import android.os.Build
import androidx.compose.runtime.staticCompositionLocalOf
import io.github.mehrdad_abdi.qintar.BuildConfig

/** The diagnostics of the running app, for composables that log (null in previews and tests). */
val LocalDiagnostics = staticCompositionLocalOf<Diagnostics?> { null }

/** The head of every report: versions, device and language. No user data. */
fun deviceInfo(context: Context): String = buildString {
    appendLine("App: Qintar ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) ${BuildConfig.BUILD_TYPE}")
    appendLine("Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
    appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
    append("Language: ${context.resources.configuration.locales[0].toLanguageTag()}")
}
