package io.github.mehrdad_abdi.quranbookmarks.presentation.ui.random

import io.github.mehrdad_abdi.quranbookmarks.domain.model.ReadingActivity
import io.github.mehrdad_abdi.quranbookmarks.domain.model.VerseMetadata
import io.github.mehrdad_abdi.quranbookmarks.domain.repository.QuranRepository
import io.github.mehrdad_abdi.quranbookmarks.domain.repository.SettingsRepository
import io.github.mehrdad_abdi.quranbookmarks.domain.service.AudioPlaybackState
import io.github.mehrdad_abdi.quranbookmarks.domain.service.AudioService
import io.github.mehrdad_abdi.quranbookmarks.domain.usecase.random.GetRandomAyahUseCase
import io.github.mehrdad_abdi.quranbookmarks.domain.usecase.random.GetRandomPageUseCase
import io.github.mehrdad_abdi.quranbookmarks.domain.usecase.reading.GetTodayStatsUseCase
import io.github.mehrdad_abdi.quranbookmarks.domain.usecase.reading.ToggleAyahTrackingUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.verify
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class RandomReadingViewModelTest {

    @Mock
    private lateinit var getRandomAyahUseCase: GetRandomAyahUseCase

    @Mock
    private lateinit var getRandomPageUseCase: GetRandomPageUseCase

    @Mock
    private lateinit var quranRepository: QuranRepository

    @Mock
    private lateinit var settingsRepository: SettingsRepository

    @Mock
    private lateinit var audioService: AudioService

    @Mock
    private lateinit var getTodayStatsUseCase: GetTodayStatsUseCase

    @Mock
    private lateinit var toggleAyahTrackingUseCase: ToggleAyahTrackingUseCase

    private lateinit var viewModel: RandomReadingViewModel

    private val testDispatcher = UnconfinedTestDispatcher()

    private val testVerse = VerseMetadata(
        text = "Test ayah",
        surahName = "Al-Fatiha",
        surahNameEn = "The Opening",
        surahNumber = 1,
        revelationType = "Meccan",
        numberOfAyahs = 7,
        hizbQuarter = 1,
        rukuNumber = 1,
        page = 1,
        manzil = 1,
        sajda = false,
        globalAyahNumber = 1,
        ayahInSurah = 1
    )

    private val testActivity = ReadingActivity(
        date = LocalDate.now(),
        totalAyahsRead = 0,
        badgeLevel = io.github.mehrdad_abdi.quranbookmarks.domain.model.BadgeLevel.NONE,
        trackedAyahIds = emptySet()
    )

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        Dispatchers.setMain(testDispatcher)

        // Default mocks
        `when`(audioService.playbackState).thenReturn(MutableStateFlow(AudioPlaybackState()))
        `when`(getTodayStatsUseCase.invoke()).thenReturn(flowOf(testActivity))
        `when`(settingsRepository.getSettings()).thenReturn(
            flowOf(io.github.mehrdad_abdi.quranbookmarks.domain.model.AppSettings(reciterEdition = "ar.alafasy"))
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state loads random ayah by default`() = runTest {
        // Given
        `when`(getRandomAyahUseCase()).thenReturn(Result.success(testVerse))

        // When
        viewModel = RandomReadingViewModel(
            getRandomAyahUseCase,
            getRandomPageUseCase,
            quranRepository,
            settingsRepository,
            audioService,
            getTodayStatsUseCase,
            toggleAyahTrackingUseCase
        )

        // Then
        val state = viewModel.uiState.value
        assertEquals(RandomReadingMode.RANDOM_AYAH, state.mode)
        assertEquals(1, state.verses.size)
        assertEquals(testVerse, state.verses.first())
        assertFalse(state.isLoading)
    }

    @Test
    fun `switchMode to RANDOM_PAGE loads page verses`() = runTest {
        // Given
        val pageVerses = listOf(testVerse, testVerse.copy(globalAyahNumber = 2, ayahInSurah = 2))
        `when`(getRandomAyahUseCase()).thenReturn(Result.success(testVerse))
        `when`(getRandomPageUseCase()).thenReturn(Result.success(pageVerses))

        viewModel = RandomReadingViewModel(
            getRandomAyahUseCase,
            getRandomPageUseCase,
            quranRepository,
            settingsRepository,
            audioService,
            getTodayStatsUseCase,
            toggleAyahTrackingUseCase
        )

        // When
        viewModel.switchMode(RandomReadingMode.RANDOM_PAGE)

        // Then
        val state = viewModel.uiState.value
        assertEquals(RandomReadingMode.RANDOM_PAGE, state.mode)
        assertEquals(2, state.verses.size)
        assertFalse(state.isLoading)
    }

    @Test
    fun `switchMode to same mode does not reload`() = runTest {
        // Given
        `when`(getRandomAyahUseCase()).thenReturn(Result.success(testVerse))

        viewModel = RandomReadingViewModel(
            getRandomAyahUseCase,
            getRandomPageUseCase,
            quranRepository,
            settingsRepository,
            audioService,
            getTodayStatsUseCase,
            toggleAyahTrackingUseCase
        )

        // When
        viewModel.switchMode(RandomReadingMode.RANDOM_AYAH)

        // Then - should only be called once during init
        verify(getRandomAyahUseCase).invoke()
    }

    @Test
    fun `generateNewRandom loads new content`() = runTest {
        // Given
        val newVerse = testVerse.copy(globalAyahNumber = 100)
        `when`(getRandomAyahUseCase()).thenReturn(Result.success(testVerse))
            .thenReturn(Result.success(newVerse))

        viewModel = RandomReadingViewModel(
            getRandomAyahUseCase,
            getRandomPageUseCase,
            quranRepository,
            settingsRepository,
            audioService,
            getTodayStatsUseCase,
            toggleAyahTrackingUseCase
        )

        // When
        viewModel.generateNewRandom()

        // Then
        val state = viewModel.uiState.value
        assertEquals(newVerse, state.verses.first())
    }

    @Test
    fun `playAudio calls audioService with correct URL`() = runTest {
        // Given
        `when`(getRandomAyahUseCase()).thenReturn(Result.success(testVerse))
        `when`(quranRepository.getAudioUrl(any(), any())).thenReturn("https://test.com/audio.mp3")

        viewModel = RandomReadingViewModel(
            getRandomAyahUseCase,
            getRandomPageUseCase,
            quranRepository,
            settingsRepository,
            audioService,
            getTodayStatsUseCase,
            toggleAyahTrackingUseCase
        )

        // When
        viewModel.playAudio(0)

        // Then
        verify(audioService).playAudio("https://test.com/audio.mp3")
    }

    @Test
    fun `toggleReadStatus calls toggleAyahTrackingUseCase`() = runTest {
        // Given
        `when`(getRandomAyahUseCase()).thenReturn(Result.success(testVerse))

        viewModel = RandomReadingViewModel(
            getRandomAyahUseCase,
            getRandomPageUseCase,
            quranRepository,
            settingsRepository,
            audioService,
            getTodayStatsUseCase,
            toggleAyahTrackingUseCase
        )

        // When
        viewModel.toggleReadStatus(0)

        // Then
        verify(toggleAyahTrackingUseCase).invoke(
            ayahId = "random:${testVerse.surahNumber}:${testVerse.ayahInSurah}"
        )
    }

    @Test
    fun `error state is set when use case fails`() = runTest {
        // Given
        `when`(getRandomAyahUseCase()).thenReturn(Result.failure(Exception("Network error")))

        // When
        viewModel = RandomReadingViewModel(
            getRandomAyahUseCase,
            getRandomPageUseCase,
            quranRepository,
            settingsRepository,
            audioService,
            getTodayStatsUseCase,
            toggleAyahTrackingUseCase
        )

        // Then
        val state = viewModel.uiState.value
        assertTrue(state.error?.contains("Network error") == true)
        assertFalse(state.isLoading)
    }
}
