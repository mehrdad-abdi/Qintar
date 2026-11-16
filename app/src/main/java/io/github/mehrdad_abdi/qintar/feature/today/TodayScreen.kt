package io.github.mehrdad_abdi.qintar.feature.today

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.mehrdad_abdi.qintar.R
import io.github.mehrdad_abdi.qintar.ui.navigation.PlaceholderScreen

/**
 * Placeholder. The today feature replaces the body of this function (and may add a view model);
 * the signature is what the nav graph calls.
 *
 * @param onNavigate pass a route object from `ui/navigation/Routes.kt`.
 */
@Composable
fun TodayScreen(onNavigate: (route: Any) -> Unit) {
    PlaceholderScreen(title = stringResource(R.string.tab_today))
}
