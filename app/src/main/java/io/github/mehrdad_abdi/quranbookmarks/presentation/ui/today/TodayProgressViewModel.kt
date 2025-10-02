package io.github.mehrdad_abdi.quranbookmarks.presentation.ui.today

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.mehrdad_abdi.quranbookmarks.domain.model.BadgeLevel
import io.github.mehrdad_abdi.quranbookmarks.domain.usecase.reading.GetAllTimeStatsUseCase
import io.github.mehrdad_abdi.quranbookmarks.domain.usecase.reading.GetDailyHistoryUseCase
import io.github.mehrdad_abdi.quranbookmarks.domain.usecase.reading.GetStreaksUseCase
import io.github.mehrdad_abdi.quranbookmarks.domain.usecase.reading.GetTodayStatsUseCase
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class TodayProgressUiState(
    val isLoading: Boolean = true,
    val error: String? = null,

    // Today's stats
    val todayAyahCount: Int = 0,
    val currentBadge: BadgeLevel = BadgeLevel.NONE,
    val nextBadge: BadgeLevel? = null,
    val ayahsToNextBadge: Int? = null,
    val progressToNextBadge: Float = 0f, // 0.0 to 1.0

    // Streaks
    val overallStreak: Int = 0,
    val topBadgeStreaks: List<Pair<BadgeLevel, Int>> = emptyList(),

    // Last 7 days
    val last7Days: List<DayBadge> = emptyList(),

    // All-time stats
    val totalAyahsAllTime: Int = 0,
    val bestDayAyahs: Int = 0,
    val bestDayDate: LocalDate? = null
)

data class DayBadge(
    val date: LocalDate,
    val ayahCount: Int,
    val badge: BadgeLevel,
    val emoji: String
)

@HiltViewModel
class TodayProgressViewModel @Inject constructor(
    private val getTodayStatsUseCase: GetTodayStatsUseCase,
    private val getStreaksUseCase: GetStreaksUseCase,
    private val getAllTimeStatsUseCase: GetAllTimeStatsUseCase,
    private val getDailyHistoryUseCase: GetDailyHistoryUseCase
) : ViewModel() {

    companion object {
        private const val TAG = "TodayProgressVM"
    }

    private val _uiState = MutableStateFlow(TodayProgressUiState())
    val uiState: StateFlow<TodayProgressUiState> = _uiState.asStateFlow()

    init {
        loadAllData()
    }

    private fun loadAllData() {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isLoading = true, error = null)

                // Collect today's stats reactively
                getTodayStatsUseCase()
                    .catch { error ->
                        Log.e(TAG, "Error loading today's stats", error)
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            error = "Failed to load today's stats: ${error.message}"
                        )
                    }
                    .collect { todayActivity ->
                        // Calculate progress to next badge
                        val nextBadge = BadgeLevel.getNextLevel(todayActivity.badgeLevel)
                        val ayahsToNext = todayActivity.getAyahsToNextLevel()
                        val progress = if (nextBadge != null && ayahsToNext != null) {
                            val currentThreshold = todayActivity.badgeLevel.threshold
                            val nextThreshold = nextBadge.threshold
                            val range = nextThreshold - currentThreshold
                            val achieved = todayActivity.totalAyahsRead - currentThreshold
                            (achieved.toFloat() / range.toFloat()).coerceIn(0f, 1f)
                        } else if (todayActivity.badgeLevel == BadgeLevel.SAHIB_QANTAR) {
                            1f // At max level
                        } else {
                            0f
                        }

                        _uiState.value = _uiState.value.copy(
                            todayAyahCount = todayActivity.totalAyahsRead,
                            currentBadge = todayActivity.badgeLevel,
                            nextBadge = nextBadge,
                            ayahsToNextBadge = ayahsToNext,
                            progressToNextBadge = progress
                        )

                        // Load other data after today's stats
                        loadStreaks()
                        loadLast7Days()
                        loadAllTimeStats()
                    }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading data", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Error loading data: ${e.message}"
                )
            }
        }
    }

    private suspend fun loadStreaks() {
        try {
            val result = getStreaksUseCase()
            if (result.isSuccess) {
                val streaks = result.getOrThrow()
                _uiState.value = _uiState.value.copy(
                    overallStreak = streaks.overallStreak,
                    topBadgeStreaks = streaks.getTopStreaks(3), // Top 3 badge streaks
                    isLoading = false
                )
                Log.d(TAG, "Loaded streaks: overall=${streaks.overallStreak}, top=${streaks.getTopStreaks(3)}")
            } else {
                Log.e(TAG, "Failed to load streaks", result.exceptionOrNull())
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading streaks", e)
        }
    }

    private suspend fun loadLast7Days() {
        try {
            val result = getDailyHistoryUseCase.getLastNDays(7)
            if (result.isSuccess) {
                val history = result.getOrThrow()
                val dayBadges = history.map { day ->
                    DayBadge(
                        date = day.date,
                        ayahCount = day.ayahsRead,
                        badge = day.badgeLevel,
                        emoji = day.badgeEmoji
                    )
                }
                _uiState.value = _uiState.value.copy(
                    last7Days = dayBadges.reversed() // Show oldest to newest
                )
                Log.d(TAG, "Loaded last 7 days: ${dayBadges.size} days")
            } else {
                Log.e(TAG, "Failed to load last 7 days", result.exceptionOrNull())
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading last 7 days", e)
        }
    }

    private suspend fun loadAllTimeStats() {
        try {
            val result = getAllTimeStatsUseCase()
            if (result.isSuccess) {
                val stats = result.getOrThrow()
                _uiState.value = _uiState.value.copy(
                    totalAyahsAllTime = stats.totalAyahsRead,
                    bestDayAyahs = stats.maxAyahsInDay,
                    bestDayDate = stats.bestDay?.date
                )
                Log.d(TAG, "Loaded all-time stats: total=${stats.totalAyahsRead}, best=${stats.maxAyahsInDay}")
            } else {
                Log.e(TAG, "Failed to load all-time stats", result.exceptionOrNull())
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading all-time stats", e)
        }
    }

    fun refresh() {
        loadAllData()
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
