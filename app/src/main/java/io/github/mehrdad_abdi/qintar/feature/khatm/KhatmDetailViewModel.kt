package io.github.mehrdad_abdi.qintar.feature.khatm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.mehrdad_abdi.qintar.di.IoDispatcher
import io.github.mehrdad_abdi.qintar.domain.model.AyahId
import io.github.mehrdad_abdi.qintar.domain.model.DomainFailure
import io.github.mehrdad_abdi.qintar.domain.model.SurahInfo
import io.github.mehrdad_abdi.qintar.domain.quran.QuranIndex
import io.github.mehrdad_abdi.qintar.domain.service.KhatmCompletion
import io.github.mehrdad_abdi.qintar.domain.service.KhatmDay
import io.github.mehrdad_abdi.qintar.domain.service.KhatmManager
import io.github.mehrdad_abdi.qintar.domain.service.KhatmProgress
import io.github.mehrdad_abdi.qintar.domain.service.KhatmSnapshot
import io.github.mehrdad_abdi.qintar.ui.navigation.KhatmDetailRoute
import java.time.LocalDate
import javax.inject.Provider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class KhatmDetailPhase { LOADING, READY, NOT_FOUND }

/** A dialog the detail screen is showing. */
enum class KhatmDetailDialog { RENAME, DAILY_GOAL, DELETE }

data class KhatmDetailUiState(
    val phase: KhatmDetailPhase = KhatmDetailPhase.LOADING,
    val snapshot: KhatmSnapshot? = null,
    /** Page of the resume position. */
    val page: Int = 1,
    val resume: AyahId? = null,
    /** The surah of the resume position. */
    val currentSurah: SurahInfo? = null,
    /** Non-null only when it is somewhere other than [resume] (§4.6). */
    val firstUnread: AyahId? = null,
    /** Most recent first, at most [KhatmDetailViewModel.RECENT_DAYS]. */
    val recentDays: List<KhatmDay> = emptyList(),
    val dialog: KhatmDetailDialog? = null,
    /** The goal dialog's text could not be read as a number above zero. */
    val goalInvalid: Boolean = false,
    /** The rename dialog's text was empty. */
    val nameMissing: Boolean = false,
) {
    val name: String get() = snapshot?.khatm?.name.orEmpty()
    val dailyPages: Float? get() = snapshot?.khatm?.dailyPages
    val completion: KhatmCompletion? get() = snapshot?.completion
    val isComplete: Boolean get() = snapshot?.isComplete == true

    /** The "today" bar exists only for a khatm with a goal, and only while it is in progress. */
    val showTodayBar: Boolean get() = dailyPages != null && !isComplete
}

/** One khatm in detail (§5.5): bars, juz strip, recent days, pace, and the edits. */
@HiltViewModel(assistedFactory = KhatmDetailViewModel.Factory::class)
class KhatmDetailViewModel @AssistedInject constructor(
    @Assisted private val route: KhatmDetailRoute,
    private val index: Provider<QuranIndex>,
    private val khatmProgress: Provider<KhatmProgress>,
    private val khatms: KhatmManager,
    @IoDispatcher private val io: CoroutineDispatcher,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(route: KhatmDetailRoute): KhatmDetailViewModel
    }

    private val _state = MutableStateFlow(KhatmDetailUiState())
    val state: StateFlow<KhatmDetailUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val (idx, progress) = withContext(io) { index.get() to khatmProgress.get() }
            progress.observe(route.khatmId).collect { snapshot ->
                if (snapshot == null) {
                    _state.update { it.copy(phase = KhatmDetailPhase.NOT_FOUND, snapshot = null, dialog = null) }
                } else {
                    _state.update {
                        it.copy(
                            phase = KhatmDetailPhase.READY,
                            snapshot = snapshot,
                            page = idx.pageOf(snapshot.resume ?: AyahId.LAST),
                            resume = snapshot.resume,
                            currentSurah = idx.surah(snapshot.currentSurah),
                            firstUnread = snapshot.firstUnread.takeIf { snapshot.firstUnreadDiffersFromResume },
                            recentDays = snapshot.days.asReversed().take(RECENT_DAYS),
                        )
                    }
                }
            }
        }
    }

    fun onDialog(dialog: KhatmDetailDialog?) {
        _state.update { it.copy(dialog = dialog, goalInvalid = false, nameMissing = false) }
    }

    fun onRename(name: String) {
        if (name.isBlank()) {
            _state.update { it.copy(nameMissing = true) }
            return
        }
        act { khatms.rename(route.khatmId, name) }
        onDialog(null)
    }

    /** [text] empty removes the goal; Persian and Arabic digits are accepted. */
    fun onSetDailyPages(text: String) {
        val trimmed = text.trim()
        val pages = if (trimmed.isEmpty()) null else NewKhatmViewModel.parsePages(trimmed)
        if (trimmed.isNotEmpty() && pages == null) {
            _state.update { it.copy(goalInvalid = true) }
            return
        }
        act { khatms.setDailyPages(route.khatmId, pages) }
        onDialog(null)
    }

    /** Removes the khatm only; the readings stay in the log. The screen leaves when the khatm is gone. */
    fun onDelete() {
        onDialog(null)
        act { khatms.delete(route.khatmId) }
    }

    private fun act(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (_: DomainFailure) {
            }
        }
    }

    companion object {
        const val RECENT_DAYS = 14
    }
}

/** Date helper kept next to the model: the day a khatm started. */
val KhatmDetailUiState.startedDay: LocalDate? get() = snapshot?.khatm?.startedDay
