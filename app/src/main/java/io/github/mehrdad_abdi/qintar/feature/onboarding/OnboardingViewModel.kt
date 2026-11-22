package io.github.mehrdad_abdi.qintar.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.mehrdad_abdi.qintar.domain.model.AppLanguage
import io.github.mehrdad_abdi.qintar.domain.model.LegacyMigrationState
import io.github.mehrdad_abdi.qintar.domain.model.Rank
import io.github.mehrdad_abdi.qintar.domain.model.UserPrefs
import io.github.mehrdad_abdi.qintar.domain.port.PrefsStore
import java.time.LocalTime
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the first launch shows: the full tour, or for someone whose v1 data was carried over one page. */
enum class OnboardingMode { FULL, WHATS_NEW }

enum class OnboardingPage { HADITH, RANKS, LANGUAGE, GOAL_AND_REMINDER, WHATS_NEW }

data class OnboardingUiState(
    val loading: Boolean = true,
    val mode: OnboardingMode = OnboardingMode.FULL,
    val pageIndex: Int = 0,
    val language: AppLanguage = AppLanguage.SYSTEM,
    val goal: Rank? = null,
    val reminderEnabled: Boolean = false,
    val reminderTime: LocalTime = UserPrefs().reminderTime,
    /** True once the tour has been finished or skipped; the screen then leaves. */
    val finished: Boolean = false,
) {
    val pages: List<OnboardingPage>
        // Both open with the hadith the ranks come from (owner, 2026-11-22).
        get() = if (mode == OnboardingMode.WHATS_NEW) {
            listOf(OnboardingPage.HADITH, OnboardingPage.WHATS_NEW)
        } else {
            listOf(OnboardingPage.HADITH, OnboardingPage.RANKS, OnboardingPage.LANGUAGE, OnboardingPage.GOAL_AND_REMINDER)
        }

    val page: OnboardingPage get() = pages[pageIndex.coerceIn(pages.indices)]
    val isLast: Boolean get() = pageIndex >= pages.lastIndex
}

/**
 * First-launch tour (§5.10). The choices (language, goal, reminder) are written as they are made, so
 * skipping keeps whatever was already chosen. Finishing or skipping records `onboardingDone`, after
 * which the tour is not shown again.
 */
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val prefs: PrefsStore,
) : ViewModel() {
    private val _state = MutableStateFlow(OnboardingUiState())
    val state: StateFlow<OnboardingUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            prefs.prefs.collect { p ->
                _state.update {
                    it.copy(
                        loading = false,
                        mode = if (p.legacyMigrationState.carriedOver()) OnboardingMode.WHATS_NEW else OnboardingMode.FULL,
                        language = p.language,
                        goal = p.goalRank,
                        reminderEnabled = p.reminderEnabled,
                        reminderTime = p.reminderTime,
                    )
                }
            }
        }
    }

    private fun LegacyMigrationState.carriedOver() = this == LegacyMigrationState.MIGRATED ||
        this == LegacyMigrationState.ACKNOWLEDGED || this == LegacyMigrationState.IN_PROGRESS

    fun next() = _state.update { if (it.isLast) it else it.copy(pageIndex = it.pageIndex + 1) }

    fun back() = _state.update { if (it.pageIndex > 0) it.copy(pageIndex = it.pageIndex - 1) else it }

    fun setLanguage(value: AppLanguage) = edit { it.copy(language = value) }

    fun setGoal(rank: Rank?) = edit { it.copy(goalRank = rank?.takeIf { r -> r != Rank.NONE }) }

    /** The screen asks for the notification permission first and calls this with `true` only when it is held. */
    fun setReminderEnabled(value: Boolean) = edit { it.copy(reminderEnabled = value) }

    fun setReminderTime(value: LocalTime) = edit { it.copy(reminderTime = value) }

    /** The last page's button, and "Skip" on any page. */
    fun finish() {
        viewModelScope.launch {
            prefs.update { it.copy(onboardingDone = true) }
            _state.update { it.copy(finished = true) }
        }
    }

    private fun edit(transform: (UserPrefs) -> UserPrefs) {
        viewModelScope.launch { prefs.update(transform) }
    }
}
