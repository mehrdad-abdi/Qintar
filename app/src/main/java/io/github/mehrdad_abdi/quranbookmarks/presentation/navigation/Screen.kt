package io.github.mehrdad_abdi.quranbookmarks.presentation.navigation

sealed class Screen(val route: String) {
    object TodayProgress : Screen("today_progress")
    object Bookmarks : Screen("bookmarks")
    object AddBookmark : Screen("add_bookmark")
    object EditBookmark : Screen("edit_bookmark/{bookmarkId}") {
        fun createRoute(bookmarkId: Long) = "edit_bookmark/$bookmarkId"
    }
    object BookmarkReading : Screen("bookmark_reading/{bookmarkId}") {
        fun createRoute(bookmarkId: Long) = "bookmark_reading/$bookmarkId"
    }
    object RandomReading : Screen("random_reading")
    object Statistics : Screen("statistics")
    object BadgeCalendar : Screen("badge_calendar")
    object Settings : Screen("settings")
    object ReciterSelection : Screen("reciter_selection")
}