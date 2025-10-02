package io.github.mehrdad_abdi.quranbookmarks.presentation.ui.statistics

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.mehrdad_abdi.quranbookmarks.domain.model.BadgeLevel
import io.github.mehrdad_abdi.quranbookmarks.domain.usecase.reading.GetAllTimeStatsUseCase
import io.github.mehrdad_abdi.quranbookmarks.domain.usecase.reading.GetDailyHistoryUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class StatisticsUiState(
    val isLoading: Boolean = true,
    val error: String? = null,

    // 30-day chart data
    val last30Days: List<DayData> = emptyList(),
    val maxAyahsInPeriod: Int = 0, // For chart scaling

    // Summary stats
    val totalAyahs: Int = 0,
    val averagePerDay: Float = 0f,
    val daysRead: Int = 0, // Days with at least 1 ayah
    val bestDay: DayData? = null,

    // Badge distribution (how many days at each badge level)
    val badgeDistribution: Map<BadgeLevel, Int> = emptyMap()
)

data class DayData(
    val date: LocalDate,
    val ayahCount: Int,
    val badge: BadgeLevel
)

@HiltViewModel
class StatisticsViewModel @Inject constructor(
    private val getDailyHistoryUseCase: GetDailyHistoryUseCase,
    private val getAllTimeStatsUseCase: GetAllTimeStatsUseCase
) : ViewModel() {

    companion object {
        private const val TAG = "StatisticsVM"
    }

    private val _uiState = MutableStateFlow(StatisticsUiState())
    val uiState: StateFlow<StatisticsUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isLoading = true, error = null)

                // Load last 30 days
                val historyResult = getDailyHistoryUseCase.getLastNDays(30)
                if (historyResult.isSuccess) {
                    val history = historyResult.getOrThrow()
                    val dayDataList = history.map { day ->
                        DayData(
                            date = day.date,
                            ayahCount = day.ayahsRead,
                            badge = day.badgeLevel
                        )
                    }.reversed() // Oldest to newest for chart display

                    // Calculate statistics
                    val totalAyahs = dayDataList.sumOf { it.ayahCount }
                    val daysRead = dayDataList.count { it.ayahCount > 0 }
                    val averagePerDay = if (daysRead > 0) totalAyahs.toFloat() / daysRead else 0f
                    val maxAyahs = dayDataList.maxOfOrNull { it.ayahCount } ?: 0
                    val bestDay = dayDataList.maxByOrNull { it.ayahCount }

                    // Badge distribution
                    val badgeDistribution = dayDataList
                        .filter { it.ayahCount > 0 } // Only count days with reading
                        .groupBy { it.badge }
                        .mapValues { it.value.size }
                        .toSortedMap(compareBy { it.threshold }) // Sort by badge level

                    _uiState.value = _uiState.value.copy(
                        last30Days = dayDataList,
                        maxAyahsInPeriod = maxAyahs,
                        totalAyahs = totalAyahs,
                        averagePerDay = averagePerDay,
                        daysRead = daysRead,
                        bestDay = bestDay,
                        badgeDistribution = badgeDistribution,
                        isLoading = false
                    )

                    Log.d(TAG, "Loaded 30-day stats: total=$totalAyahs, days=$daysRead, avg=$averagePerDay")
                } else {
                    val error = historyResult.exceptionOrNull()
                    Log.e(TAG, "Failed to load history", error)
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "Failed to load statistics: ${error?.message}"
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading statistics", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Error loading statistics: ${e.message}"
                )
            }
        }
    }

    fun refresh() {
        loadData()
    }
}
