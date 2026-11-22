package io.github.mehrdad_abdi.qintar.feature.insights

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.mehrdad_abdi.qintar.R
import io.github.mehrdad_abdi.qintar.di.IoDispatcher
import io.github.mehrdad_abdi.qintar.domain.model.AyahRange
import io.github.mehrdad_abdi.qintar.domain.model.Batch
import io.github.mehrdad_abdi.qintar.domain.model.DomainFailure
import io.github.mehrdad_abdi.qintar.domain.model.Quran
import io.github.mehrdad_abdi.qintar.domain.model.RangeMode
import io.github.mehrdad_abdi.qintar.domain.model.ReadContext
import io.github.mehrdad_abdi.qintar.domain.model.ReadMethod
import io.github.mehrdad_abdi.qintar.domain.model.SurahInfo
import io.github.mehrdad_abdi.qintar.domain.quran.PassageSummary
import io.github.mehrdad_abdi.qintar.domain.quran.QuranIndex
import io.github.mehrdad_abdi.qintar.domain.quran.ayahId
import io.github.mehrdad_abdi.qintar.domain.quran.pagesRange
import io.github.mehrdad_abdi.qintar.domain.quran.summary
import io.github.mehrdad_abdi.qintar.domain.service.ReadingTracker
import io.github.mehrdad_abdi.qintar.domain.time.DayClock
import io.github.mehrdad_abdi.qintar.ui.common.parseWholeNumber
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Provider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import io.github.mehrdad_abdi.qintar.domain.model.Tally as DomainTally

/** What the "Log reading" sheet adds (§5.8). */
enum class LogMode { PASSAGE, SURAH, PAGES, JUZ, NUMBER }

enum class LogField { FROM_AYAH, TO_AYAH, FROM_PAGE, TO_PAGE, JUZ, NUMBER }

/** Why a field was refused; the sheet shows [message] under it. */
enum class LogProblem(@StringRes val message: Int) {
    NUMBER_MISSING(R.string.bm_err_number),
    AYAH_OUT_OF_RANGE(R.string.bm_err_ayah),
    END_BEFORE_START(R.string.bm_err_end_before_start),
    PAGE_OUT_OF_RANGE(R.string.bm_err_page),
    JUZ_OUT_OF_RANGE(R.string.bm_err_juz),
    COUNT_OUT_OF_RANGE(R.string.log_err_count),
}

/** The domain refused the write. */
enum class LogRefusal(@StringRes val message: Int) {
    FUTURE_DAY(R.string.log_refusal_future),
    COUNT_OUT_OF_RANGE(R.string.log_err_count),
}

/** What a successful log wrote, kept for as long as its Undo is offered. */
sealed interface LogResult {
    val day: LocalDate

    /** The ayahs written by one bulk action. */
    data class Passage(val batch: Batch) : LogResult {
        override val day: LocalDate get() = batch.day
        val ayahs: Int get() = batch.added
    }

    /** A tally: a count with no ayah identity. */
    data class Number(val tally: DomainTally) : LogResult {
        override val day: LocalDate get() = tally.day
        val ayahs: Int get() = tally.count
    }

    val count: Int get() = when (this) {
        is Passage -> batch.added
        is Number -> tally.count
    }
}

data class LogReadingUiState(
    val ready: Boolean = false,
    val day: LocalDate = LocalDate.MIN,
    /** The latest day that can be chosen. */
    val today: LocalDate = LocalDate.MIN,
    val surahs: List<SurahInfo> = emptyList(),
    val mode: LogMode = LogMode.PASSAGE,
    val fromSurah: Int = 1,
    val fromAyahText: String = "1",
    val toSurah: Int = 1,
    val toAyahText: String = "1",
    val surah: Int = 1,
    val fromPageText: String = "1",
    val toPageText: String = "1",
    val juzText: String = "1",
    val numberText: String = "",
    val note: String = "",
    /** The passage the position stands for; null while a number is invalid or in [LogMode.NUMBER]. */
    val range: AyahRange? = null,
    val summary: PassageSummary? = null,
    /** How many ayahs the button would add; 0 while the form is not valid. */
    val addCount: Int = 0,
    val errors: Map<LogField, LogProblem> = emptyMap(),
    val refusal: LogRefusal? = null,
    /** Set when the write succeeded; the sheet reports it and closes. */
    val result: LogResult? = null,
) {
    val canSubmit: Boolean get() = ready && addCount > 0 && errors.isEmpty()
    val isToday: Boolean get() = day == today
    val isYesterday: Boolean get() = day == today.minusDays(1)
}

/**
 * The manual log (R-TRK-6, §5.8): a day (today by default, never the future) and either a passage
 * (ayah range, surah, page range, juz) or a plain number of ayahs with an optional note. A passage is
 * written as `Log` context readings by method `MANUAL`; a number is a tally.
 *
 * The sheet calls [start] each time it opens; the view model is shared by whatever screen hosts it.
 */
@HiltViewModel
class LogReadingViewModel @Inject constructor(
    private val index: Provider<QuranIndex>,
    private val tracker: ReadingTracker,
    private val clock: DayClock,
    @IoDispatcher private val io: CoroutineDispatcher,
) : ViewModel() {

    private val _state = MutableStateFlow(LogReadingUiState())
    val state: StateFlow<LogReadingUiState> = _state.asStateFlow()

    private var quran: QuranIndex? = null

    /** Opens a fresh form for [day] (null = today). */
    fun start(day: LocalDate? = null) {
        viewModelScope.launch {
            val q = quran ?: withContext(io) { index.get() }.also { quran = it }
            val today = clock.today()
            _state.value = LogReadingUiState(ready = true, day = day?.takeIf { it <= today } ?: today, today = today, surahs = q.surahs)
                .resolved(q)
        }
    }

    fun onDay(day: LocalDate) = _state.update { it.copy(day = day, refusal = null) }

    fun onMode(mode: LogMode) = change { copy(mode = mode) }

    fun onFromSurah(number: Int) = change { copy(fromSurah = number, fromAyahText = clampAyah(number, fromAyahText)) }

    fun onFromAyahText(text: String) = change { copy(fromAyahText = text) }

    fun onToSurah(number: Int) = change { copy(toSurah = number, toAyahText = clampAyah(number, toAyahText)) }

    fun onToAyahText(text: String) = change { copy(toAyahText = text) }

    fun onSurah(number: Int) = change { copy(surah = number) }

    fun onFromPageText(text: String) = change { copy(fromPageText = text) }

    fun onToPageText(text: String) = change { copy(toPageText = text) }

    fun onJuzText(text: String) = change { copy(juzText = text) }

    fun onNumberText(text: String) = change { copy(numberText = text) }

    fun onNote(text: String) = _state.update { it.copy(note = text) }

    private fun clampAyah(surah: Int, text: String): String {
        val n = parseWholeNumber(text)
        return if (n != null && n in 1..quran!!.surah(surah).ayahCount) text else "1"
    }

    private inline fun change(block: LogReadingUiState.() -> LogReadingUiState) {
        val q = quran ?: return
        _state.update { it.block().copy(refusal = null).resolved(q) }
    }

    private fun LogReadingUiState.resolved(q: QuranIndex): LogReadingUiState {
        val problems = HashMap<LogField, LogProblem>()

        fun ayah(surah: Int, text: String, field: LogField) = parseWholeNumber(text).let { n ->
            when {
                n == null -> null.also { problems[field] = LogProblem.NUMBER_MISSING }
                else -> q.ayahId(surah, n) ?: null.also { problems[field] = LogProblem.AYAH_OUT_OF_RANGE }
            }
        }

        fun page(text: String, field: LogField): Int? {
            val n = parseWholeNumber(text)
            return when {
                n == null -> null.also { problems[field] = LogProblem.NUMBER_MISSING }
                n !in 1..Quran.PAGE_COUNT -> null.also { problems[field] = LogProblem.PAGE_OUT_OF_RANGE }
                else -> n
            }
        }

        var number = 0
        val range: AyahRange? = when (mode) {
            LogMode.PASSAGE -> {
                val from = ayah(fromSurah, fromAyahText, LogField.FROM_AYAH)
                val to = ayah(toSurah, toAyahText, LogField.TO_AYAH)
                when {
                    from == null || to == null -> null
                    to < from -> null.also { problems[LogField.TO_AYAH] = LogProblem.END_BEFORE_START }
                    else -> AyahRange(from, to)
                }
            }
            LogMode.SURAH -> q.surahRange(surah)
            LogMode.PAGES -> {
                val from = page(fromPageText, LogField.FROM_PAGE)
                val to = page(toPageText, LogField.TO_PAGE)
                when {
                    from == null || to == null -> null
                    to < from -> null.also { problems[LogField.TO_PAGE] = LogProblem.END_BEFORE_START }
                    else -> q.pagesRange(from, to)
                }
            }
            LogMode.JUZ -> {
                val juz = parseWholeNumber(juzText)
                when {
                    juz == null -> null.also { problems[LogField.JUZ] = LogProblem.NUMBER_MISSING }
                    juz !in 1..Quran.JUZ_COUNT -> null.also { problems[LogField.JUZ] = LogProblem.JUZ_OUT_OF_RANGE }
                    else -> q.juzRange(juz)
                }
            }
            LogMode.NUMBER -> {
                val n = parseWholeNumber(numberText)
                when {
                    numberText.isBlank() -> Unit // nothing typed yet: no error, just nothing to add
                    n == null -> problems[LogField.NUMBER] = LogProblem.NUMBER_MISSING
                    n !in 1..MAX_NUMBER -> problems[LogField.NUMBER] = LogProblem.COUNT_OUT_OF_RANGE
                    else -> number = n
                }
                null
            }
        }
        return copy(
            range = range,
            summary = range?.let(q::summary),
            addCount = if (mode == LogMode.NUMBER) number else range?.size ?: 0,
            errors = problems,
        )
    }

    /** Writes the reading. On success [LogReadingUiState.result] is set; a refusal sets [LogReadingUiState.refusal]. */
    fun onSubmit() {
        val s = _state.value
        if (!s.canSubmit) return
        viewModelScope.launch {
            try {
                val result: LogResult = if (s.mode == LogMode.NUMBER) {
                    LogResult.Number(tracker.addTally(s.day, s.addCount, s.note))
                } else {
                    val range = checkNotNull(s.range)
                    LogResult.Passage(tracker.markRange(range, ReadContext.Log, ReadMethod.MANUAL, RangeMode.ADD, 1, s.day))
                }
                _state.update { it.copy(result = result, refusal = null) }
            } catch (_: DomainFailure.FutureDay) {
                _state.update { it.copy(refusal = LogRefusal.FUTURE_DAY) }
            } catch (_: DomainFailure.CountOutOfRange) {
                _state.update { it.copy(refusal = LogRefusal.COUNT_OUT_OF_RANGE) }
            }
        }
    }

    /** The host has reported [LogReadingUiState.result] (snackbar, closing the sheet). */
    fun onResultHandled() = _state.update { it.copy(result = null) }

    /** Takes back what [result] wrote. */
    fun onUndo(result: LogResult) {
        viewModelScope.launch {
            when (result) {
                is LogResult.Passage -> tracker.undo(result.batch)
                is LogResult.Number -> tracker.deleteTally(result.tally.id)
            }
        }
    }

    companion object {
        /** The most a single number can add; the tracker refuses more. */
        const val MAX_NUMBER = 100_000
    }
}
