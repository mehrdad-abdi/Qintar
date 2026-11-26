package io.github.mehrdad_abdi.qintar.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import io.github.mehrdad_abdi.qintar.playback.origin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import io.github.mehrdad_abdi.qintar.R
import androidx.compose.foundation.layout.Column
import io.github.mehrdad_abdi.qintar.feature.player.PlayerBar
import io.github.mehrdad_abdi.qintar.feature.player.PlayerBarViewModel
import io.github.mehrdad_abdi.qintar.ui.designsystem.Spacing
import io.github.mehrdad_abdi.qintar.ui.designsystem.QintarType
import io.github.mehrdad_abdi.qintar.ui.designsystem.Sizes
import kotlin.reflect.KClass

/** The four bottom-navigation destinations of §5.1, in order. */
enum class TopLevelDestination(
    val route: Any,
    val routeClass: KClass<*>,
    @StringRes val label: Int,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
) {
    Today(TodayRoute, TodayRoute::class, R.string.tab_today, Icons.Outlined.WbSunny, Icons.Rounded.WbSunny),
    Read(ReadRoute, ReadRoute::class, R.string.tab_read, Icons.AutoMirrored.Outlined.MenuBook, Icons.AutoMirrored.Rounded.MenuBook),
    Bookmarks(BookmarksRoute, BookmarksRoute::class, R.string.tab_bookmarks, Icons.Outlined.BookmarkBorder, Icons.Rounded.Bookmark),
    Insights(InsightsRoute, InsightsRoute::class, R.string.tab_insights, Icons.Outlined.BarChart, Icons.Rounded.BarChart),
}

private fun NavDestination?.topLevel(): TopLevelDestination? =
    TopLevelDestination.entries.firstOrNull { this?.hasRoute(it.routeClass) == true }

/**
 * Single-activity shell: the nav host, plus the bottom bar on the four tabs. Full-screen
 * destinations (reader, editors, settings…) hide the bar and own the whole window, so each
 * screen applies its own system-bar insets (the activity is edge-to-edge).
 */
@Composable
fun QintarAppShell(
    navController: NavHostController = rememberNavController(),
    startDestination: Any = TodayRoute,
    player: (@Composable () -> PlayerBarViewModel)? = null,
    openPlayerRequest: Int = 0,
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentTab = backStackEntry?.destination.topLevel()
    // The canary records each screen opened (its route, which holds ids only).
    val diagnostics = io.github.mehrdad_abdi.qintar.diagnostics.LocalDiagnostics.current
    val screen = backStackEntry?.destination?.route?.substringAfterLast('.').orEmpty()
    LaunchedEffect(screen) { if (screen.isNotEmpty()) diagnostics?.log("screen", screen) }

    // The playback notification was tapped: go to the reader the audio came from, unless it is the one on screen.
    if (player != null && openPlayerRequest > 0) {
        val playerViewModel = player()
        LaunchedEffect(openPlayerRequest) {
            val playing = withTimeoutOrNull(OPEN_PLAYER_WAIT_MILLIS) { playerViewModel.state.first { it.visible } } ?: return@LaunchedEffect
            val target = playing.returnRoute ?: return@LaunchedEffect
            if (!showsReaderOf(navController.currentBackStackEntry, target)) navController.navigate(target) { launchSingleTop = true }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (currentTab != null) {
                Column {
                    // While something plays, the mini player stands above the tabs; a tap goes back to its reader.
                    player?.let {
                        PlayerBar(
                            it(),
                            onOpen = { route -> navController.navigate(route) },
                            modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                        )
                    }
                    QintarBottomBar(currentTab) { navController.navigateToTab(it.route) }
                }
            }
        },
    ) { padding ->
        // Content keeps to 640dp, centred, on tablets and in landscape (§6.3).
        Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding), contentAlignment = Alignment.TopCenter) {
            QintarNavHost(
                navController = navController,
                startDestination = startDestination,
                player = player,
                modifier = Modifier.widthIn(max = Sizes.contentMaxWidth).fillMaxSize(),
            )
            diagnostics?.let {
                io.github.mehrdad_abdi.qintar.diagnostics.DiagnosticsMarker(it, screen, Modifier.align(Alignment.CenterEnd))
            }
        }
    }
}

/** How long a tap on the notification waits for the player to report what it plays (a cold start). */
private const val OPEN_PLAYER_WAIT_MILLIS = 3_000L

/** Whether [entry] is the reader that [target] would open (the same scope, wherever it is scrolled to). */
internal fun showsReaderOf(entry: NavBackStackEntry?, target: ReaderRoute): Boolean =
    entry?.destination?.hasRoute(ReaderRoute::class) == true && entry.toRoute<ReaderRoute>().origin() == target.origin()

@Composable
fun QintarBottomBar(
    current: TopLevelDestination,
    modifier: Modifier = Modifier,
    onSelect: (TopLevelDestination) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    NavigationBar(modifier = modifier, containerColor = colors.surface, contentColor = colors.onSurfaceVariant) {
        TopLevelDestination.entries.forEach { destination ->
            val selected = destination == current
            NavigationBarItem(
                selected = selected,
                onClick = { onSelect(destination) },
                icon = { Icon(if (selected) destination.selectedIcon else destination.icon, contentDescription = null) },
                label = { Text(stringResource(destination.label), style = QintarType.Caption, maxLines = 1) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = colors.onPrimaryContainer,
                    selectedTextColor = colors.onSurface,
                    indicatorColor = colors.primaryContainer,
                    unselectedIconColor = colors.onSurfaceVariant,
                    unselectedTextColor = colors.onSurfaceVariant,
                ),
            )
        }
    }
}
