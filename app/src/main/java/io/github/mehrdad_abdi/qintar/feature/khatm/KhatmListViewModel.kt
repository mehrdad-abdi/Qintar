package io.github.mehrdad_abdi.qintar.feature.khatm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.mehrdad_abdi.qintar.di.IoDispatcher
import io.github.mehrdad_abdi.qintar.domain.model.AyahId
import io.github.mehrdad_abdi.qintar.domain.model.SurahInfo
import io.github.mehrdad_abdi.qintar.domain.quran.QuranIndex
import io.github.mehrdad_abdi.qintar.domain.service.CoverageBar
import io.github.mehrdad_abdi.qintar.domain.service.KhatmProgress
import io.github.mehrdad_abdi.qintar.domain.service.KhatmSnapshot
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Provider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A khatm as the lists show it: where it stands and its bars (§4.6). Shared by the Read tab. */
data class KhatmListItem(
    val id: Long,
    val name: String,
    /** Page of the resume position. */
    val page: Int,
    val whole: CoverageBar,
    val surah: SurahInfo,
    val surahBar: CoverageBar,
    val juz: Int,
    val juzBar: CoverageBar,
    /** Null when the khatm has no daily goal. */
    val dailyPages: Float?,
    /** Only for completed khatms. */
    val completedDay: LocalDate? = null,
    val daysTaken: Int = 0,
) {
    val isCompleted: Boolean get() = completedDay != null
}

fun KhatmSnapshot.toListItem(index: QuranIndex) = KhatmListItem(
    id = khatm.id,
    name = khatm.name,
    page = index.pageOf(resume ?: AyahId.LAST),
    whole = wholeBar,
    surah = index.surah(currentSurah),
    surahBar = surahBar,
    juz = currentJuz,
    juzBar = juzBar,
    dailyPages = khatm.dailyPages,
    completedDay = if (khatm.completedAt != null) completion?.completedDay else null,
    daysTaken = completion?.daysTaken ?: 0,
)

data class KhatmListUiState(
    val loading: Boolean = true,
    /** Most recent activity first. */
    val active: List<KhatmListItem> = emptyList(),
    /** Newest completion first. */
    val completed: List<KhatmListItem> = emptyList(),
)

/** Every khatm: several may be in progress at once (R-KHT-2); finished ones are read-only history. */
@HiltViewModel
class KhatmListViewModel @Inject constructor(
    private val index: Provider<QuranIndex>,
    private val khatmProgress: Provider<KhatmProgress>,
    @IoDispatcher private val io: CoroutineDispatcher,
) : ViewModel() {
    private val _state = MutableStateFlow(KhatmListUiState())
    val state: StateFlow<KhatmListUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val (index, progress) = withContext(io) { index.get() to khatmProgress.get() }
            combine(progress.observeActive(), progress.observeCompleted()) { active, completed ->
                KhatmListUiState(
                    loading = false,
                    active = active.map { it.toListItem(index) },
                    completed = completed.map { it.toListItem(index) },
                )
            }.collect { computed -> _state.update { computed } }
        }
    }
}
