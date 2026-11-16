package io.github.mehrdad_abdi.qintar.feature.bookmarks

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.mehrdad_abdi.qintar.R
import io.github.mehrdad_abdi.qintar.ui.navigation.PlaceholderScreen
import io.github.mehrdad_abdi.qintar.ui.navigation.BookmarkEditorRoute

/**
 * Placeholder. The bookmarks feature replaces the body of this function (and may add a view model);
 * the signature is what the nav graph calls.
 *
 * @param onNavigate pass a route object from `ui/navigation/Routes.kt`.
 */
@Composable
fun BookmarkEditorScreen(route: BookmarkEditorRoute, onBack: () -> Unit, onNavigate: (route: Any) -> Unit) {
    PlaceholderScreen(title = stringResource(R.string.screen_bookmark_editor), detail = "id=${route.bookmarkId}", onBack = onBack)
}
