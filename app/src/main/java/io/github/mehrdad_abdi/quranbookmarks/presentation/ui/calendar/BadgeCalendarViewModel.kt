package io.github.mehrdad_abdi.quranbookmarks.presentation.ui.calendar

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.mehrdad_abdi.quranbookmarks.domain.model.BadgeLevel
import io.github.mehrdad_abdi.quranbookmarks.domain.usecase.reading.GetDailyHistoryUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import javax.inject.Inject

data class BadgeCalendarUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val currentMonth: YearMonth = YearMonth.now(),
    val calendarDays: List<CalendarDay> = emptyList(),
    val selectedDay: CalendarDay? = null
)

data class CalendarDay(
    val date: LocalDate,
    val ayahCount: Int,
    val badge: BadgeLevel,
    val isInCurrentMonth: Boolean
)

@HiltViewModel
class BadgeCalendarViewModel @Inject constructor(
    private val getDailyHistoryUseCase: GetDailyHistoryUseCase
) : ViewModel() {

    companion object {
        private const val TAG = "BadgeCalendarVM"
    }

    private val _uiState = MutableStateFlow(BadgeCalendarUiState())
    val uiState: StateFlow<BadgeCalendarUiState> = _uiState.asStateFlow()

    init {
        loadMonth(YearMonth.now())
    }

    fun loadMonth(yearMonth: YearMonth) {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(
                    isLoading = true,
                    error = null,
                    currentMonth = yearMonth
                )

                // Get first and last day of the month
                val firstDayOfMonth = yearMonth.atDay(1)
                val lastDayOfMonth = yearMonth.atEndOfMonth()

                // Get first day of the calendar (might be from previous month)
                val firstDayOfWeek = firstDayOfMonth.dayOfWeek.value % 7 // 0 = Sunday
                val calendarStart = firstDayOfMonth.minusDays(firstDayOfWeek.toLong())

                // Get last day of the calendar (might be from next month)
                val lastDayOfWeek = lastDayOfMonth.dayOfWeek.value % 7
                val daysToAdd = if (lastDayOfWeek == 6) 0 else (6 - lastDayOfWeek)
                val calendarEnd = lastDayOfMonth.plusDays(daysToAdd.toLong())

                // Load reading data for this range
                val result = getDailyHistoryUseCase.getForDateRange(calendarStart, calendarEnd)

                if (result.isSuccess) {
                    val history = result.getOrThrow()
                    val historyMap = history.associateBy { it.date }

                    // Build calendar days - fill in all days even if no data
                    val calendarDays = mutableListOf<CalendarDay>()
                    var currentDate = calendarStart
                    while (!currentDate.isAfter(calendarEnd)) {
                        val dayHistory = historyMap[currentDate]
                        calendarDays.add(
                            CalendarDay(
                                date = currentDate,
                                ayahCount = dayHistory?.ayahsRead ?: 0,
                                badge = dayHistory?.badgeLevel ?: BadgeLevel.NONE,
                                isInCurrentMonth = currentDate.month == yearMonth.month
                            )
                        )
                        currentDate = currentDate.plusDays(1)
                    }

                    _uiState.value = _uiState.value.copy(
                        calendarDays = calendarDays,
                        isLoading = false
                    )

                    Log.d(TAG, "Loaded calendar for $yearMonth: ${calendarDays.size} days")
                } else {
                    val error = result.exceptionOrNull()
                    Log.e(TAG, "Failed to load calendar", error)
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "Failed to load calendar: ${error?.message}"
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading calendar", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Error loading calendar: ${e.message}"
                )
            }
        }
    }

    fun goToPreviousMonth() {
        val previousMonth = _uiState.value.currentMonth.minusMonths(1)
        loadMonth(previousMonth)
    }

    fun goToNextMonth() {
        val nextMonth = _uiState.value.currentMonth.plusMonths(1)
        loadMonth(nextMonth)
    }

    fun goToCurrentMonth() {
        loadMonth(YearMonth.now())
    }

    fun selectDay(day: CalendarDay?) {
        _uiState.value = _uiState.value.copy(selectedDay = day)
    }

    fun refresh() {
        loadMonth(_uiState.value.currentMonth)
    }
}
