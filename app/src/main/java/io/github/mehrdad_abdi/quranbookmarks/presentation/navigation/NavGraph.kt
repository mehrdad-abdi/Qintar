package io.github.mehrdad_abdi.quranbookmarks.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import io.github.mehrdad_abdi.quranbookmarks.presentation.ui.about.AboutScreen
import io.github.mehrdad_abdi.quranbookmarks.presentation.ui.bookmark.AddBookmarkScreen
import io.github.mehrdad_abdi.quranbookmarks.presentation.ui.bookmark.EditBookmarkScreen
import io.github.mehrdad_abdi.quranbookmarks.presentation.ui.bookmarks.BookmarksScreen
import io.github.mehrdad_abdi.quranbookmarks.presentation.ui.calendar.BadgeCalendarScreen
import io.github.mehrdad_abdi.quranbookmarks.presentation.ui.hadith.HadithScreen
import io.github.mehrdad_abdi.quranbookmarks.presentation.ui.khatm.KhatmReadingScreen
import io.github.mehrdad_abdi.quranbookmarks.presentation.ui.random.RandomModeSelectionScreen
import io.github.mehrdad_abdi.quranbookmarks.presentation.ui.reading.BookmarkReadingScreen
import io.github.mehrdad_abdi.quranbookmarks.presentation.ui.settings.ReciterSelectionScreen
import io.github.mehrdad_abdi.quranbookmarks.presentation.ui.settings.SettingsScreen
import io.github.mehrdad_abdi.quranbookmarks.presentation.ui.settings.backup.BackupScreen
import io.github.mehrdad_abdi.quranbookmarks.presentation.ui.statistics.StatisticsScreen
import io.github.mehrdad_abdi.quranbookmarks.presentation.ui.today.TodayProgressScreen
import io.github.mehrdad_abdi.quranbookmarks.domain.model.SurahNames
import io.github.mehrdad_abdi.quranbookmarks.domain.model.Surah
import androidx.compose.runtime.remember

@Composable
fun NavGraph(
    navController: NavHostController
) {
    NavHost(
        navController = navController,
        startDestination = Screen.TodayProgress.route
    ) {
        // Today's Progress Screen (HOME SCREEN)
        composable(route = Screen.TodayProgress.route) {
            TodayProgressScreen(
                onNavigateToProfiles = {
                    navController.navigate(Screen.Bookmarks.route)
                },
                onNavigateToRandomReading = {
                    navController.navigate(Screen.RandomReading.route)
                },
                onNavigateToStatistics = {
                    navController.navigate(Screen.Statistics.route)
                },
                onNavigateToCalendar = {
                    navController.navigate(Screen.BadgeCalendar.route)
                },
                onNavigateToSettings = {
                    navController.navigate(Screen.Settings.route)
                },
                onNavigateToHadith = {
                    navController.navigate(Screen.Hadith.route)
                },
                onNavigateToKhatmReading = {
                    navController.navigate(Screen.KhatmReading.route)
                }
            )
        }

        // Bookmarks Screen (replaces ProfileList)
        composable(route = Screen.Bookmarks.route) {
            BookmarksScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToAddBookmark = {
                    navController.navigate(Screen.AddBookmark.route)
                },
                onNavigateToEditBookmark = { bookmarkId ->
                    navController.navigate(Screen.EditBookmark.createRoute(bookmarkId))
                }
            )
        }

        // Add Bookmark Screen
        composable(route = Screen.AddBookmark.route) {
            AddBookmarkScreen(
                groupId = 0L, // No longer used, but kept for compatibility
                onNavigateBack = {
                    navController.popBackStack()
                },
                onBookmarkCreated = {
                    navController.popBackStack()
                }
            )
        }

        composable(
            route = Screen.EditBookmark.route,
            arguments = listOf(
                navArgument("bookmarkId") {
                    type = NavType.LongType
                }
            )
        ) { backStackEntry ->
            val bookmarkId = backStackEntry.arguments?.getLong("bookmarkId") ?: 0L
            EditBookmarkScreen(
                bookmarkId = bookmarkId,
                onNavigateBack = {
                    navController.popBackStack()
                },
                onBookmarkUpdated = {
                    navController.popBackStack()
                }
            )
        }

        // Bookmark Reading Screen
        composable(
            route = Screen.BookmarkReading.route,
            arguments = listOf(
                navArgument("bookmarkId") {
                    type = NavType.LongType
                }
            )
        ) { backStackEntry ->
            val bookmarkId = backStackEntry.arguments?.getLong("bookmarkId") ?: 0L
            BookmarkReadingScreen(
                bookmarkId = bookmarkId,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        // Random Reading Mode Selection Screen
        composable(route = Screen.RandomReading.route) {
            RandomModeSelectionScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToReading = { bookmarkId ->
                    navController.navigate(Screen.BookmarkReading.createRoute(bookmarkId))
                }
            )
        }

        // Khatm Reading Screen
        composable(route = Screen.KhatmReading.route) {
            // Create surah list from SurahNames
            val surahs = remember {
                (1..114).map { number ->
                    Surah(
                        number = number,
                        name = SurahNames.getName(number),
                        englishName = SurahNames.getName(number),
                        englishNameTranslation = SurahNames.getName(number),
                        numberOfAyahs = SurahNames.getAyahCount(number),
                        revelationType = "" // Not needed for jump dialog
                    )
                }
            }

            KhatmReadingScreen(
                surahs = surahs,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        // Statistics Screen
        composable(route = Screen.Statistics.route) {
            StatisticsScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        // Badge Calendar Screen
        composable(route = Screen.BadgeCalendar.route) {
            BadgeCalendarScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        // Settings Screen
        composable(route = Screen.Settings.route) {
            SettingsScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToReciterSelection = {
                    navController.navigate(Screen.ReciterSelection.route)
                },
                onNavigateToBackup = {
                    navController.navigate(Screen.Backup.route)
                },
                onNavigateToAbout = {
                    navController.navigate(Screen.About.route)
                }
            )
        }

        // Reciter Selection Screen
        composable(route = Screen.ReciterSelection.route) {
            ReciterSelectionScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        // Backup & Restore Screen
        composable(route = Screen.Backup.route) {
            BackupScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        // Hadith Screen
        composable(route = Screen.Hadith.route) {
            HadithScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        // About Screen
        composable(route = Screen.About.route) {
            AboutScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}