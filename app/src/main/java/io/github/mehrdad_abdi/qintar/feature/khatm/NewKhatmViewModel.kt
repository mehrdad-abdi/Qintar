package io.github.mehrdad_abdi.qintar.feature.khatm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.mehrdad_abdi.qintar.di.IoDispatcher
import io.github.mehrdad_abdi.qintar.domain.model.AyahId
import io.github.mehrdad_abdi.qintar.domain.model.AyahRef
import io.github.mehrdad_abdi.qintar.domain.model.DomainFailure
import io.github.mehrdad_abdi.qintar.domain.model.Quran
import io.github.mehrdad_abdi.qintar.domain.quran.QuranIndex
import io.github.mehrdad_abdi.qintar.domain.service.GoToParser
import io.github.mehrdad_abdi.qintar.domain.service.GoToResult
import io.github.mehrdad_abdi.qintar.domain.service.KhatmManager
import io.github.mehrdad_abdi.qintar.domain.time.DayClock
import io.github.mehrdad_abdi.qintar.ui.common.GoToProblem
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Provider
import kotlin.math.ceil
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class NewKhatmUiState(
    val loading: Boolean = true,
    /** "Khatm [ordinal]" is the default name; the screen localizes the word. */
    val ordinal: Int = 1,
    val name: String = "",
    val nameMissing: Boolean = false,
    /** What the user typed for the start position; empty = from the beginning. */
    val startText: String = "",
    val startProblem: GoToProblem? = null,
    /** The surahs a name typed into "Start from" may be. */
    val startSuggestions: List<io.github.mehrdad_abdi.qintar.domain.model.SurahInfo> = emptyList(),
    /** Where the khatm will start; null = the beginning. */
    val startFrom: AyahId? = null,
    val startRef: AyahRef? = null,
    /** What the user typed for pages a day; empty = no goal. */
    val dailyText: String = "",
    val dailyInvalid: Boolean = false,
    val dailyPages: Float? = null,
    /** Today + remaining pages ÷ goal, shown live; null without a goal. */
    val projectedFinish: LocalDate? = null,
    /** Set once the khatm exists. */
    val createdId: Long? = null,
)

/** The form that starts a khatm (§5.5). The goal is a guide, never strict. */
@HiltViewModel
class NewKhatmViewModel @Inject constructor(
    private val index: Provider<QuranIndex>,
    private val khatms: KhatmManager,
    private val clock: DayClock,
    @IoDispatcher private val io: CoroutineDispatcher,
) : ViewModel() {
    private val _state = MutableStateFlow(NewKhatmUiState())
    val state: StateFlow<NewKhatmUiState> = _state.asStateFlow()

    private var quran: QuranIndex? = null
    private var nameEdited = false

    init {
        viewModelScope.launch {
            quran = withContext(io) { index.get() }
            val ordinal = khatms.nextOrdinal()
            _state.update { it.copy(loading = false, ordinal = ordinal) }
        }
    }

    /** The screen offers the localized default name; it never overwrites one the user typed. */
    fun onDefaultName(name: String) {
        if (!nameEdited) _state.update { it.copy(name = name) }
    }

    fun onName(name: String) {
        nameEdited = true
        _state.update { it.copy(name = name, nameMissing = false) }
    }

    /** Accepts `2:255`, `36` or a surah's name (with an ayah number or not), `p300`, `j12`. */
    fun onStartText(text: String) {
        val idx = quran
        if (text.isBlank() || idx == null) {
            _state.update { it.copy(startText = text, startProblem = null, startFrom = null, startRef = null, startSuggestions = emptyList()).withProjection() }
            return
        }
        _state.update { it.copy(startSuggestions = GoToParser.suggestions(text, idx)) }
        when (val result = GoToParser.parse(text, idx)) {
            is GoToResult.Invalid -> _state.update {
                it.copy(startText = text, startProblem = GoToProblem.of(result.reason), startFrom = null, startRef = null).withProjection()
            }
            else -> _state.update {
                it.copy(startText = text, startProblem = null, startFrom = result.target, startRef = result.target?.let(idx::ref)).withProjection()
            }
        }
    }

    fun onDailyText(text: String) {
        val trimmed = text.trim()
        val parsed = parsePages(trimmed)
        _state.update {
            it.copy(
                dailyText = text,
                dailyInvalid = trimmed.isNotEmpty() && parsed == null,
                dailyPages = parsed,
            ).withProjection()
        }
    }

    fun onStart() {
        val current = _state.value
        if (current.name.isBlank()) {
            _state.update { it.copy(nameMissing = true) }
            return
        }
        if (current.dailyInvalid || current.startProblem != null) return
        viewModelScope.launch {
            try {
                val khatm = khatms.start(current.name, current.startFrom, current.dailyPages)
                _state.update { it.copy(createdId = khatm.id) }
            } catch (_: DomainFailure) {
            }
        }
    }

    private fun NewKhatmUiState.withProjection(): NewKhatmUiState {
        val idx = quran
        val goal = dailyPages
        if (idx == null || goal == null) return copy(projectedFinish = null)
        val first = startFrom?.value ?: 1
        var remaining = 0.0
        for (page in 1..Quran.PAGE_COUNT) {
            val range = idx.pageRange(page)
            val unread = range.end.value - maxOf(range.start.value, first) + 1
            if (unread > 0) remaining += unread.toDouble() / idx.ayahsOnPage(page)
        }
        return copy(projectedFinish = clock.today().plusDays(ceil(remaining / goal).toLong()))
    }

    companion object {
        /** "4", "2.5", "2,5", also in Persian or Arabic digits; null when not a number above zero. */
        fun parsePages(text: String): Float? {
            if (text.isEmpty()) return null
            val builder = StringBuilder()
            for (ch in text) {
                val digit = Character.digit(ch, 10)
                when {
                    digit >= 0 -> builder.append(digit)
                    ch == '.' || ch == ',' || ch == '٫' || ch == '٬' -> builder.append('.')
                    else -> return null
                }
            }
            val value = builder.toString().toFloatOrNull() ?: return null
            return value.takeIf { it > 0f && it <= MAX_PAGES_PER_DAY }
        }

        const val MAX_PAGES_PER_DAY = 604f
    }
}
