package io.github.mehrdad_abdi.quranbookmarks.presentation.navigation

sealed class Screen(val route: String) {
    object ProfileList : Screen("profile_list")
    object AddProfile : Screen("add_profile")
    object EditProfile : Screen("edit_profile/{profileId}") {
        fun createRoute(profileId: Long) = "edit_profile/$profileId"
    }
    object ProfileDetail : Screen("profile_detail/{profileId}") {
        fun createRoute(profileId: Long) = "profile_detail/$profileId"
    }
    object AddBookmark : Screen("add_bookmark/{profileId}") {
        fun createRoute(profileId: Long) = "add_bookmark/$profileId"
    }
    object EditBookmark : Screen("edit_bookmark/{bookmarkId}") {
        fun createRoute(bookmarkId: Long) = "edit_bookmark/$bookmarkId"
    }
}