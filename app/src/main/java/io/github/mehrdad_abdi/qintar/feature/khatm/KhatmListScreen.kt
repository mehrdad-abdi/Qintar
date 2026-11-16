package io.github.mehrdad_abdi.qintar.feature.khatm

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.mehrdad_abdi.qintar.R
import io.github.mehrdad_abdi.qintar.ui.common.ScreenTopBar
import io.github.mehrdad_abdi.qintar.ui.common.dateText
import io.github.mehrdad_abdi.qintar.ui.common.pagesText
import io.github.mehrdad_abdi.qintar.ui.common.surahName
import io.github.mehrdad_abdi.qintar.ui.designsystem.EmptyState
import io.github.mehrdad_abdi.qintar.ui.designsystem.QCard
import io.github.mehrdad_abdi.qintar.ui.designsystem.QintarType
import io.github.mehrdad_abdi.qintar.ui.designsystem.SectionHeader
import io.github.mehrdad_abdi.qintar.ui.designsystem.Sizes
import io.github.mehrdad_abdi.qintar.ui.designsystem.Spacing
import io.github.mehrdad_abdi.qintar.ui.designsystem.StackedProgressRow
import io.github.mehrdad_abdi.qintar.ui.designsystem.formatNumber
import io.github.mehrdad_abdi.qintar.ui.navigation.KhatmDetailRoute
import io.github.mehrdad_abdi.qintar.ui.navigation.NewKhatmRoute
import io.github.mehrdad_abdi.qintar.ui.navigation.ReaderRoute

/** Test tag of the khatm list. */
const val KHATM_LIST_TAG = "khatm-list"

/**
 * Every khatm (§5.5): those in progress as cards (name, goal, bars, Continue), finished ones below as
 * read-only rows.
 *
 * @param onNavigate pass a route object from `ui/navigation/Routes.kt`.
 */
@Composable
fun KhatmListScreen(onBack: () -> Unit, onNavigate: (route: Any) -> Unit, viewModel: KhatmListViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).windowInsetsPadding(WindowInsets.safeDrawing)) {
        ScreenTopBar(stringResource(R.string.khatm_list_title), onBack)
        if (state.loading) return@Column
        LazyColumn(
            modifier = Modifier.fillMaxSize().testTag(KHATM_LIST_TAG),
            contentPadding = PaddingValues(horizontal = Spacing.screen, vertical = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.betweenCards),
        ) {
            item {
                SectionHeader(
                    stringResource(R.string.khatm_section_active),
                    actionLabel = stringResource(R.string.read_new_khatm),
                    onAction = { onNavigate(NewKhatmRoute) },
                )
            }
            if (state.active.isEmpty()) {
                item {
                    QCard(Modifier.fillMaxWidth()) {
                        EmptyState(
                            title = stringResource(R.string.today_no_khatm_title),
                            message = stringResource(R.string.khatm_none_message),
                            icon = Icons.Outlined.AutoStories,
                            actionLabel = stringResource(R.string.today_start_khatm),
                            onAction = { onNavigate(NewKhatmRoute) },
                        )
                    }
                }
            }
            items(state.active, key = { "active-${it.id}" }) { item ->
                KhatmSummaryCard(
                    item = item,
                    onOpen = { onNavigate(KhatmDetailRoute(item.id)) },
                    onContinue = { onNavigate(ReaderRoute.khatm(item.id)) },
                )
            }
            if (state.completed.isNotEmpty()) {
                item { Spacer(Modifier.height(Spacing.sm)) }
                item { SectionHeader(stringResource(R.string.khatm_section_completed)) }
                items(state.completed, key = { "done-${it.id}" }) { item ->
                    QCard(Modifier.fillMaxWidth(), onClick = { onNavigate(KhatmDetailRoute(item.id)) }) {
                        Text(item.name, style = QintarType.BodyStrong, color = MaterialTheme.colorScheme.onSurface)
                        item.completedDay?.let { day ->
                            Text(
                                stringResource(R.string.khatm_completed_row, dateText(day), formatNumber(item.daysTaken)),
                                style = QintarType.Label,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * One khatm as a card: name and goal, its bars, and Continue. Tapping the card opens the khatm detail.
 * Reused by the Read tab with [compact], which keeps only the name and the whole-Quran bar.
 */
@Composable
fun KhatmSummaryCard(
    item: KhatmListItem,
    onOpen: () -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    QCard(modifier.fillMaxWidth(), onClick = onOpen) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(item.name, style = QintarType.BodyStrong, color = colors.onSurface, modifier = Modifier.weight(1f))
            Text(stringResource(R.string.page_n, formatNumber(item.page)), style = QintarType.Numeric, color = colors.onSurfaceVariant)
        }
        item.dailyPages?.let { goal ->
            Text(
                stringResource(R.string.khatm_goal_pages_per_day, pagesText(goal.toDouble())),
                style = QintarType.Label,
                color = colors.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(Spacing.md))
        if (!compact) {
            StackedProgressRow(surahName(item.surah), percentText(item.surahBar.fraction), item.surahBar.fraction)
            Spacer(Modifier.height(Spacing.sm))
            StackedProgressRow(
                stringResource(R.string.juz_n, formatNumber(item.juz)),
                percentText(item.juzBar.fraction),
                item.juzBar.fraction,
            )
            Spacer(Modifier.height(Spacing.sm))
        }
        StackedProgressRow(stringResource(R.string.khatm_whole_quran), percentText(item.whole.fraction), item.whole.fraction)
        Spacer(Modifier.height(Spacing.md))
        OutlinedButton(
            onClick = onContinue,
            modifier = Modifier.align(Alignment.End).heightIn(min = Sizes.touchTarget),
            contentPadding = PaddingValues(horizontal = Spacing.xxl),
        ) { Text(stringResource(R.string.action_continue), style = QintarType.Label) }
    }
}

@Composable
internal fun percentText(fraction: Float): String = stringResource(R.string.percent, formatNumber((fraction * 100).toInt()))
