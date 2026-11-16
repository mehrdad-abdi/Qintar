package io.github.mehrdad_abdi.qintar

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import io.github.mehrdad_abdi.qintar.domain.model.AppLanguage as LanguagePref
import io.github.mehrdad_abdi.qintar.domain.model.ThemeMode
import io.github.mehrdad_abdi.qintar.domain.model.UserPrefs
import io.github.mehrdad_abdi.qintar.domain.port.PrefsStore
import io.github.mehrdad_abdi.qintar.ui.designsystem.LocalNumeralStyle
import io.github.mehrdad_abdi.qintar.ui.designsystem.NumeralStyle
import io.github.mehrdad_abdi.qintar.ui.designsystem.QintarTheme
import io.github.mehrdad_abdi.qintar.ui.navigation.AppLanguage
import io.github.mehrdad_abdi.qintar.ui.navigation.QintarAppShell
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    @Inject
    lateinit var prefsStore: PrefsStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            // Nothing is drawn until the settings are known, so the theme never flashes.
            val prefs by prefsStore.prefs.collectAsStateWithLifecycle(initialValue = null)
            val appearance = prefs?.let(::appearanceOf) ?: return@setContent
            LaunchedEffect(appearance.languageTag) {
                // AppCompat recreates the activity when the language really changes.
                if (AppLanguage.current() != appearance.languageTag) AppLanguage.set(appearance.languageTag)
            }
            QintarTheme(
                darkTheme = appearance.dark ?: isSystemInDarkTheme(),
                pureBlack = appearance.pureBlack,
            ) {
                CompositionLocalProvider(LocalNumeralStyle provides appearance.numerals) { QintarAppShell() }
            }
        }
    }
}

/** The appearance settings of §5.9 in the terms the design system takes them. */
data class Appearance(
    /** null = follow the system. */
    val dark: Boolean?,
    val pureBlack: Boolean,
    val numerals: NumeralStyle,
    /** null = follow the system language. */
    val languageTag: String?,
)

fun appearanceOf(prefs: UserPrefs) = Appearance(
    dark = when (prefs.theme) {
        ThemeMode.SYSTEM -> null
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    },
    pureBlack = prefs.pureBlack,
    numerals = if (prefs.latinNumerals) NumeralStyle.Latin else NumeralStyle.FollowLanguage,
    languageTag = when (prefs.language) {
        LanguagePref.SYSTEM -> null
        LanguagePref.ENGLISH -> "en"
        LanguagePref.ARABIC -> "ar"
        LanguagePref.PERSIAN -> "fa"
    },
)
