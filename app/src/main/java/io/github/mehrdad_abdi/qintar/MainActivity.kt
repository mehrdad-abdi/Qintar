package io.github.mehrdad_abdi.qintar

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import io.github.mehrdad_abdi.qintar.data.legacy.LegacyV1Reader
import io.github.mehrdad_abdi.qintar.domain.model.AppLanguage as LanguagePref
import io.github.mehrdad_abdi.qintar.domain.model.LegacyMigrationState
import io.github.mehrdad_abdi.qintar.domain.model.ThemeMode
import io.github.mehrdad_abdi.qintar.domain.model.UserPrefs
import io.github.mehrdad_abdi.qintar.domain.port.PrefsStore
import io.github.mehrdad_abdi.qintar.playback.EXTRA_OPEN_PLAYER
import io.github.mehrdad_abdi.qintar.ui.designsystem.LocalNumeralStyle
import io.github.mehrdad_abdi.qintar.ui.designsystem.NumeralStyle
import io.github.mehrdad_abdi.qintar.ui.designsystem.QintarTheme
import io.github.mehrdad_abdi.qintar.ui.navigation.AppLanguage
import io.github.mehrdad_abdi.qintar.ui.navigation.OnboardingRoute
import io.github.mehrdad_abdi.qintar.ui.navigation.QintarAppShell
import io.github.mehrdad_abdi.qintar.ui.navigation.TodayRoute
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    @Inject
    lateinit var prefsStore: PrefsStore

    /** Counts taps on the playback notification; the shell opens the playing reader on each new value. */
    private val openPlayerRequests = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) noteOpenPlayer(intent)
        setContent {
            // Until the settings are known only the window background shows (the app's own colour,
            // see themes.xml), so the theme never flashes.
            val prefs by prefsStore.prefs.collectAsStateWithLifecycle(initialValue = null)
            val appearance = prefs?.let(::appearanceOf) ?: return@setContent
            // Decided once, when the migration has settled, so that a v1 user is not shown the full tour.
            val start by produceState<Any?>(null) {
                value = startDestinationOf(prefsStore, legacyDataPresent = getDatabasePath(LegacyV1Reader.DATABASE_NAME).isFile)
            }
            LaunchedEffect(appearance.languageTag) {
                // AppCompat recreates the activity when the language really changes.
                if (AppLanguage.current() != appearance.languageTag) AppLanguage.set(appearance.languageTag)
            }
            val dark = appearance.dark ?: isSystemInDarkTheme()
            // The system-bar icons follow the app's theme, which need not be the system's.
            DisposableEffect(dark) {
                val bars = if (dark) SystemBarStyle.dark(Color.TRANSPARENT) else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
                onDispose {}
            }
            QintarTheme(darkTheme = dark, pureBlack = appearance.pureBlack) {
                CompositionLocalProvider(LocalNumeralStyle provides appearance.numerals) {
                    val destination = start
                    if (destination == null) {
                        // The migration is still deciding: the app's background rather than a blank window.
                        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
                    } else {
                        QintarAppShell(
                            startDestination = destination,
                            player = { hiltViewModel() },
                            openPlayerRequest = openPlayerRequests.intValue,
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        noteOpenPlayer(intent)
    }

    private fun noteOpenPlayer(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_OPEN_PLAYER, false) == true) openPlayerRequests.intValue++
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

/** How long the first screen waits for the v1 migration to settle before deciding. */
private const val MIGRATION_WAIT_MILLIS = 3_000L

/**
 * Onboarding until it has been finished or skipped, Today after. A first launch of an upgraded v1
 * install (one where v1's database is on the device) waits (briefly) until the migration has decided, so that the tour can be the one-page
 * "what's new" for someone whose data was carried over.
 */
suspend fun startDestinationOf(prefsStore: PrefsStore, legacyDataPresent: Boolean = true): Any {
    // Without a v1 database there is nothing to wait for: the migration can only say "not needed".
    if (!legacyDataPresent) return if (prefsStore.prefs.first().onboardingDone) TodayRoute else OnboardingRoute
    val settled = withTimeoutOrNull(MIGRATION_WAIT_MILLIS) {
        prefsStore.prefs.first { it.onboardingDone || (it.legacyMigrationState != LegacyMigrationState.PENDING && it.legacyMigrationState != LegacyMigrationState.IN_PROGRESS) }
    } ?: prefsStore.prefs.first()
    return if (settled.onboardingDone) TodayRoute else OnboardingRoute
}
