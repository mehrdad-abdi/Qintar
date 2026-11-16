package io.github.mehrdad_abdi.qintar.feature.insights

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.mehrdad_abdi.qintar.R
import io.github.mehrdad_abdi.qintar.ui.navigation.PlaceholderScreen
import io.github.mehrdad_abdi.qintar.ui.navigation.DayDetailRoute

/**
 * Placeholder. The insights feature replaces the body of this function (and may add a view model);
 * the signature is what the nav graph calls.
 *
 * @param onNavigate pass a route object from `ui/navigation/Routes.kt`.
 */
@Composable
fun DayDetailScreen(route: DayDetailRoute, onBack: () -> Unit, onNavigate: (route: Any) -> Unit) {
    PlaceholderScreen(title = stringResource(R.string.screen_day_detail), detail = route.isoDate, onBack = onBack)
}
