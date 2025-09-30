package io.github.mehrdad_abdi.quranbookmarks.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import io.github.mehrdad_abdi.quranbookmarks.presentation.ui.bookmark.AddBookmarkScreen
import io.github.mehrdad_abdi.quranbookmarks.presentation.ui.bookmark.EditBookmarkScreen
import io.github.mehrdad_abdi.quranbookmarks.presentation.ui.reading.ReadingScreen
import io.github.mehrdad_abdi.quranbookmarks.presentation.ui.playlist.ContinuousPlaybackScreen
import io.github.mehrdad_abdi.quranbookmarks.presentation.ui.profile.AddProfileScreen
import io.github.mehrdad_abdi.quranbookmarks.presentation.ui.profile.EditProfileScreen
import io.github.mehrdad_abdi.quranbookmarks.presentation.ui.profile.ProfileDetailScreen
import io.github.mehrdad_abdi.quranbookmarks.presentation.ui.profile.ProfileListScreen

@Composable
fun NavGraph(
    navController: NavHostController
) {
    NavHost(
        navController = navController,
        startDestination = Screen.ProfileList.route
    ) {
        composable(route = Screen.ProfileList.route) {
            ProfileListScreen(
                onNavigateToAddProfile = {
                    navController.navigate(Screen.AddProfile.route)
                },
                onNavigateToProfile = { profileId ->
                    navController.navigate(Screen.ProfileDetail.createRoute(profileId))
                },
                onNavigateToEditProfile = { profileId ->
                    navController.navigate(Screen.EditProfile.createRoute(profileId))
                }
            )
        }

        composable(route = Screen.AddProfile.route) {
            AddProfileScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onProfileCreated = { profileId ->
                    navController.popBackStack()
                    // Navigate back to main screen - the new profile will appear in the list
                }
            )
        }

        composable(
            route = Screen.EditProfile.route,
            arguments = listOf(
                navArgument("profileId") {
                    type = NavType.LongType
                }
            )
        ) { backStackEntry ->
            val profileId = backStackEntry.arguments?.getLong("profileId") ?: 0L
            EditProfileScreen(
                profileId = profileId,
                onNavigateBack = {
                    navController.popBackStack()
                },
                onProfileUpdated = {
                    navController.popBackStack()
                }
            )
        }

        composable(
            route = Screen.ProfileDetail.route,
            arguments = listOf(
                navArgument("profileId") {
                    type = NavType.LongType
                }
            )
        ) { backStackEntry ->
            val profileId = backStackEntry.arguments?.getLong("profileId") ?: 0L
            ProfileDetailScreen(
                profileId = profileId,
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToAddBookmark = { profileId ->
                    navController.navigate(Screen.AddBookmark.createRoute(profileId))
                },
                onNavigateToEditProfile = { profileId ->
                    navController.navigate(Screen.EditProfile.createRoute(profileId))
                },
                onNavigateToEditBookmark = { bookmarkId ->
                    navController.navigate(Screen.EditBookmark.createRoute(bookmarkId))
                },
                onNavigateToBookmark = { bookmarkId ->
                    navController.navigate(Screen.Reading.createRoute(bookmarkId))
                },
                onNavigateToContinuousPlayback = { profileId ->
                    navController.navigate(Screen.ContinuousPlayback.createRoute(profileId))
                }
            )
        }

        composable(
            route = Screen.AddBookmark.route,
            arguments = listOf(
                navArgument("profileId") {
                    type = NavType.LongType
                }
            )
        ) { backStackEntry ->
            val profileId = backStackEntry.arguments?.getLong("profileId") ?: 0L
            AddBookmarkScreen(
                groupId = profileId,
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

        composable(
            route = Screen.Reading.route,
            arguments = listOf(
                navArgument("bookmarkId") {
                    type = NavType.LongType
                }
            )
        ) { backStackEntry ->
            val bookmarkId = backStackEntry.arguments?.getLong("bookmarkId") ?: 0L
            ReadingScreen(
                bookmarkId = bookmarkId,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(
            route = Screen.ContinuousPlayback.route,
            arguments = listOf(
                navArgument("profileId") {
                    type = NavType.LongType
                }
            )
        ) { backStackEntry ->
            val profileId = backStackEntry.arguments?.getLong("profileId") ?: 0L
            ContinuousPlaybackScreen(
                profileId = profileId,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}