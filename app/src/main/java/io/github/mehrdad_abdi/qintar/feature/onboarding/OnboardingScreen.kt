package io.github.mehrdad_abdi.qintar.feature.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.mehrdad_abdi.qintar.R
import io.github.mehrdad_abdi.qintar.domain.model.AppLanguage
import io.github.mehrdad_abdi.qintar.domain.model.Rank
import io.github.mehrdad_abdi.qintar.feature.settings.GoalOption
import io.github.mehrdad_abdi.qintar.ui.common.TimeButton
import io.github.mehrdad_abdi.qintar.ui.common.number
import io.github.mehrdad_abdi.qintar.ui.common.rememberNotificationGate
import io.github.mehrdad_abdi.qintar.ui.designsystem.QintarType
import io.github.mehrdad_abdi.qintar.ui.designsystem.RankLadder
import io.github.mehrdad_abdi.qintar.ui.designsystem.RankMark
import io.github.mehrdad_abdi.qintar.ui.designsystem.Sizes
import io.github.mehrdad_abdi.qintar.ui.designsystem.Spacing
import io.github.mehrdad_abdi.qintar.ui.designsystem.formatNumber
import io.github.mehrdad_abdi.qintar.ui.designsystem.rankName

object OnboardingTags {
    const val SKIP = "onboarding-skip"
    const val NEXT = "onboarding-next"
    const val BACK = "onboarding-back"
    const val DONE = "onboarding-done"
    const val LANGUAGE_PREFIX = "onboarding-language-"
    const val REMINDER = "onboarding-reminder"
    const val HADITH = "onboarding-hadith"
}

/**
 * First-launch tour (§5.10): what is tracked and the ranks, the language, an optional goal and
 * reminder. Skippable at every step; ends on Today.
 */
@Composable
fun OnboardingScreen(onFinished: () -> Unit, viewModel: OnboardingViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.finished) { if (state.finished) onFinished() }
    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize().background(colors.background).windowInsetsPadding(WindowInsets.safeDrawing)) {
        if (state.loading) return@Column
        Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.sm, vertical = Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (state.mode == OnboardingMode.WHATS_NEW) stringResource(R.string.onb_whats_new_title) else stringResource(R.string.screen_onboarding),
                style = QintarType.Title,
                modifier = Modifier.weight(1f).padding(start = Spacing.md),
            )
            if (!(state.isLast && state.mode == OnboardingMode.WHATS_NEW)) {
                TextButton(onClick = viewModel::finish, modifier = Modifier.heightIn(min = Sizes.touchTarget).testTag(OnboardingTags.SKIP)) {
                    Text(stringResource(R.string.onb_skip), style = QintarType.Label)
                }
            }
        }
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.screen, vertical = Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            when (state.page) {
                OnboardingPage.HADITH -> io.github.mehrdad_abdi.qintar.ui.common.HadithText(Modifier.testTag(OnboardingTags.HADITH))
                OnboardingPage.RANKS -> RanksPage()
                OnboardingPage.LANGUAGE -> LanguagePage(state.language, viewModel::setLanguage)
                OnboardingPage.GOAL_AND_REMINDER -> GoalAndReminderPage(state, viewModel)
                OnboardingPage.WHATS_NEW -> WhatsNewPage()
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.screen, vertical = Spacing.md), verticalAlignment = Alignment.CenterVertically) {
            if (state.pageIndex > 0) {
                TextButton(onClick = viewModel::back, modifier = Modifier.heightIn(min = Sizes.touchTarget).testTag(OnboardingTags.BACK)) {
                    Text(stringResource(R.string.onb_back), style = QintarType.Label)
                }
            }
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.Center) {
                if (state.pages.size > 1) {
                    state.pages.indices.forEach { i ->
                        Box(
                            Modifier
                                .padding(horizontal = Spacing.xs)
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (i == state.pageIndex) colors.primary else colors.outlineVariant),
                        )
                    }
                }
            }
            if (state.isLast) {
                Button(onClick = viewModel::finish, modifier = Modifier.heightIn(min = Sizes.touchTarget).testTag(OnboardingTags.DONE)) {
                    Text(stringResource(R.string.onb_start), style = QintarType.Label)
                }
            } else {
                Button(onClick = viewModel::next, modifier = Modifier.heightIn(min = Sizes.touchTarget).testTag(OnboardingTags.NEXT)) {
                    Text(stringResource(R.string.onb_next), style = QintarType.Label)
                }
            }
        }
    }
}

@Composable
private fun PageTitle(text: String) = Text(text, style = QintarType.Headline, color = MaterialTheme.colorScheme.onSurface)

@Composable
private fun RanksPage() {
    PageTitle(stringResource(R.string.onb_ranks_title))
    Text(stringResource(R.string.onb_ranks_body), style = QintarType.Body, color = MaterialTheme.colorScheme.onSurfaceVariant)
    RankLadder(currentRank = 0, modifier = Modifier.fillMaxWidth(), markSize = Sizes.rankMark)
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Rank.ladder.forEach { rank ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                RankMark(rank = rank.number, size = Sizes.rankMark, contentDescription = null)
                Text(rankName(rank.number), style = QintarType.Body, modifier = Modifier.weight(1f).padding(horizontal = Spacing.md))
                Text(stringResource(R.string.settings_goal_threshold, formatNumber(rank.threshold)), style = QintarType.Caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun LanguagePage(selected: AppLanguage, onSelect: (AppLanguage) -> Unit) {
    PageTitle(stringResource(R.string.onb_language_title))
    Column {
        AppLanguage.entries.forEach { language ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = Sizes.touchTarget)
                    .selectable(language == selected, role = Role.RadioButton) { onSelect(language) }
                    .testTag(OnboardingTags.LANGUAGE_PREFIX + language.name),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = language == selected, onClick = null)
                Text(
                    when (language) {
                        AppLanguage.SYSTEM -> stringResource(R.string.settings_system_default)
                        AppLanguage.ENGLISH -> stringResource(R.string.language_name_en)
                        AppLanguage.ARABIC -> stringResource(R.string.language_name_ar)
                        AppLanguage.PERSIAN -> stringResource(R.string.language_name_fa)
                    },
                    style = QintarType.Body,
                    modifier = Modifier.padding(start = Spacing.md),
                )
            }
        }
    }
}

@Composable
private fun GoalAndReminderPage(state: OnboardingUiState, vm: OnboardingViewModel) {
    var blocked by remember { mutableStateOf(false) }
    val gate = rememberNotificationGate { granted ->
        blocked = !granted
        if (granted) vm.setReminderEnabled(true)
    }
    PageTitle(stringResource(R.string.onb_goal_title))
    Text(stringResource(R.string.onb_goal_body), style = QintarType.Body, color = MaterialTheme.colorScheme.onSurfaceVariant)
    RankLadder(currentRank = state.goal?.number ?: 0, goalRank = state.goal?.number, modifier = Modifier.fillMaxWidth())
    Column {
        GoalOption(null, selected = state.goal == null, onSelect = { vm.setGoal(null) })
        Rank.ladder.forEach { rank -> GoalOption(rank, selected = state.goal == rank, onSelect = { vm.setGoal(rank) }) }
    }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = Sizes.touchTarget)
            .toggleable(state.reminderEnabled, role = Role.Switch) { on -> if (on) gate() else vm.setReminderEnabled(false) }
            .testTag(OnboardingTags.REMINDER),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(R.string.onb_reminder), style = QintarType.Body, modifier = Modifier.weight(1f))
        Switch(checked = state.reminderEnabled, onCheckedChange = null)
    }
    if (state.reminderEnabled) TimeButton(stringResource(R.string.settings_reminder_time), state.reminderTime, vm::setReminderTime, Modifier.fillMaxWidth())
    if (blocked) Text(stringResource(R.string.settings_notifications_blocked), style = QintarType.Caption, color = MaterialTheme.colorScheme.error)
    Spacer(Modifier.height(Spacing.sm))
}

@Composable
private fun WhatsNewPage() {
    PageTitle(stringResource(R.string.onb_whats_new_headline))
    Text(stringResource(R.string.onb_whats_new_body), style = QintarType.Body, color = MaterialTheme.colorScheme.onSurfaceVariant)
    RankLadder(currentRank = 0, modifier = Modifier.fillMaxWidth())
}
