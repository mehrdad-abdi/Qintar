package io.github.mehrdad_abdi.qintar.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import io.github.mehrdad_abdi.qintar.feature.bookmarks.BookmarkEditorScreen
import io.github.mehrdad_abdi.qintar.feature.bookmarks.BookmarksScreen
import io.github.mehrdad_abdi.qintar.feature.bookmarks.GroupEditorScreen
import io.github.mehrdad_abdi.qintar.feature.insights.DayDetailScreen
import io.github.mehrdad_abdi.qintar.feature.insights.InsightsScreen
import io.github.mehrdad_abdi.qintar.feature.khatm.KhatmDetailScreen
import io.github.mehrdad_abdi.qintar.feature.onboarding.OnboardingScreen
import io.github.mehrdad_abdi.qintar.feature.read.ReadScreen
import io.github.mehrdad_abdi.qintar.feature.reader.ReaderScreen
import io.github.mehrdad_abdi.qintar.feature.settings.SettingsScreen
import io.github.mehrdad_abdi.qintar.feature.today.TodayScreen

/**
 * The whole navigation graph of the app, in one place.
 *
 * **Where features plug in:** every destination below calls exactly one screen composable that
 * lives in its feature package (`feature/<name>/`). A feature replaces the body of that
 * composable and leaves this file alone. A new destination needs a route in `Routes.kt` and
 * one `composable<…>` line here.
 *
 * Screens navigate by passing a route object to `onNavigate` and leave with `onBack`.
 */
@Composable
fun QintarNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    startDestination: Any = TodayRoute,
) {
    val onBack: () -> Unit = { navController.popBackStack() }
    val onNavigate: (Any) -> Unit = { route -> navController.navigate(route) }

    NavHost(navController = navController, startDestination = startDestination, modifier = modifier) {
        // ---- Bottom navigation tabs -------------------------------------------------------
        composable<TodayRoute> { TodayScreen(onNavigate) }               // feature/today
        composable<ReadRoute> { ReadScreen(onNavigate) }                 // feature/read
        composable<BookmarksRoute> { BookmarksScreen(onNavigate) }       // feature/bookmarks
        composable<InsightsRoute> { InsightsScreen(onNavigate) }         // feature/insights

        // ---- Full-screen destinations (no bottom bar) ---------------------------------------
        composable<ReaderRoute> { entry ->                               // feature/reader
            ReaderScreen(entry.toRoute<ReaderRoute>(), onBack, onNavigate)
        }
        composable<KhatmDetailRoute> { entry ->                          // feature/khatm
            KhatmDetailScreen(entry.toRoute<KhatmDetailRoute>(), onBack, onNavigate)
        }
        composable<BookmarkEditorRoute> { entry ->                       // feature/bookmarks
            BookmarkEditorScreen(entry.toRoute<BookmarkEditorRoute>(), onBack, onNavigate)
        }
        composable<GroupEditorRoute> { entry ->                          // feature/bookmarks
            GroupEditorScreen(entry.toRoute<GroupEditorRoute>(), onBack, onNavigate)
        }
        composable<DayDetailRoute> { entry ->                            // feature/insights
            DayDetailScreen(entry.toRoute<DayDetailRoute>(), onBack, onNavigate)
        }
        composable<SettingsRoute> { SettingsScreen(onBack, onNavigate) } // feature/settings
        composable<OnboardingRoute> {                                    // feature/onboarding
            OnboardingScreen(
                onFinished = {
                    navController.navigate(TodayRoute) { popUpTo<OnboardingRoute> { inclusive = true } }
                },
            )
        }
    }
}

/** Switches to a bottom-navigation tab, keeping one copy of each tab and restoring its state. */
fun NavHostController.navigateToTab(route: Any) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
