package io.github.mehrdad_abdi.quranbookmarks.domain.usecase.cache

import io.github.mehrdad_abdi.quranbookmarks.data.cache.FileDownloadManager
import io.github.mehrdad_abdi.quranbookmarks.domain.model.CachedContent
import io.github.mehrdad_abdi.quranbookmarks.domain.model.VerseMetadata
import io.github.mehrdad_abdi.quranbookmarks.domain.repository.QuranRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.*

class CacheAyahContentUseCaseTest {

    private lateinit var quranRepository: QuranRepository
    private lateinit var fileDownloadManager: FileDownloadManager
    private lateinit var useCase: CacheAyahContentUseCase

    private val testMetadata = VerseMetadata(
        text = "بِسۡمِ ٱللَّهِ ٱلرَّحۡمَٰنِ ٱلرَّحِيمِ",
        surahName = "الفاتحة",
        surahNameEn = "Al-Fatihah",
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

    @Before
    fun setup() {
        quranRepository = mock()
        fileDownloadManager = mock()
        useCase = CacheAyahContentUseCase(quranRepository, fileDownloadManager)
    }

    @Test
    fun `cache ayah content successfully when not cached`() = runTest {
        // Given
        val ayahRef = AyahReference(surah = 1, ayah = 1, globalAyahNumber = 1)
        whenever(quranRepository.getCachedContentById("1:1")).thenReturn(null)
        whenever(quranRepository.getVerseMetadata(1)).thenReturn(Result.success(testMetadata))
        whenever(fileDownloadManager.downloadImageFile(any(), any(), any())).thenAnswer { "/cache/image_1_1.png" }
        whenever(fileDownloadManager.downloadAudioFile(any(), any(), any())).thenAnswer { "/cache/audio_1_1.mp3" }

        // When
        val result = useCase(ayahRef)

        // Then
        assertTrue(result.isSuccess)
        verify(quranRepository).saveCachedContent(any())
        verify(quranRepository).getVerseMetadata(1)
    }

    @Test
    fun `return existing cache when already cached and not forcing refresh`() = runTest {
        // Given
        val ayahRef = AyahReference(surah = 1, ayah = 1, globalAyahNumber = 1)
        val existingCache = CachedContent(
            id = "1:1",
            surah = 1,
            ayah = 1,
            metadata = testMetadata
        )
        whenever(quranRepository.getCachedContentById("1:1")).thenReturn(existingCache)

        // When
        val result = useCase(ayahRef, reciterEdition = null, forceRefresh = false)

        // Then
        assertTrue(result.isSuccess)
        assertEquals(existingCache, result.getOrNull())
        verify(quranRepository, never()).saveCachedContent(any())
        verify(quranRepository, never()).getVerseMetadata(any())
    }

    @Test
    fun `force refresh fetches new data even when cached`() = runTest {
        // Given
        val ayahRef = AyahReference(surah = 1, ayah = 1, globalAyahNumber = 1)
        val existingCache = CachedContent(
            id = "1:1",
            surah = 1,
            ayah = 1,
            metadata = testMetadata
        )
        whenever(quranRepository.getCachedContentById("1:1")).thenReturn(existingCache)
        whenever(quranRepository.getVerseMetadata(1)).thenReturn(Result.success(testMetadata))

        // When
        val result = useCase(ayahRef, reciterEdition = null, forceRefresh = true)

        // Then
        assertTrue(result.isSuccess)
        verify(quranRepository).getVerseMetadata(1)
        verify(quranRepository).saveCachedContent(any())
    }

    @Test
    fun `handle API failure gracefully`() = runTest {
        // Given
        val ayahRef = AyahReference(surah = 1, ayah = 1, globalAyahNumber = 1)
        whenever(quranRepository.getCachedContentById("1:1")).thenReturn(null)
        whenever(quranRepository.getVerseMetadata(1)).thenReturn(
            Result.failure(Exception("Network error"))
        )

        // When
        val result = useCase(ayahRef)

        // Then
        assertTrue(result.isFailure)
        verify(quranRepository, never()).saveCachedContent(any())
    }

    @Test
    fun `cache multiple ayahs with deduplication`() = runTest {
        // Given - duplicate ayah references
        val ayahs = listOf(
            AyahReference(surah = 1, ayah = 1, globalAyahNumber = 1),
            AyahReference(surah = 1, ayah = 1, globalAyahNumber = 1), // Duplicate
            AyahReference(surah = 1, ayah = 2, globalAyahNumber = 2),
            AyahReference(surah = 1, ayah = 2, globalAyahNumber = 2)  // Duplicate
        )

        whenever(quranRepository.getCachedContentById(any())).thenReturn(null)
        whenever(quranRepository.getVerseMetadata(any())).thenReturn(Result.success(testMetadata))

        // When
        val result = useCase.cacheMultiple(ayahs)

        // Then
        assertTrue(result.isSuccess)
        assertEquals(2, result.getOrThrow().size) // Only 2 unique ayahs
        verify(quranRepository, times(2)).saveCachedContent(any())
    }

    @Test
    fun `cache multiple ayahs handles partial failures`() = runTest {
        // Given
        val ayahs = listOf(
            AyahReference(surah = 1, ayah = 1, globalAyahNumber = 1),
            AyahReference(surah = 1, ayah = 2, globalAyahNumber = 2)
        )

        whenever(quranRepository.getCachedContentById(any())).thenReturn(null)
        whenever(quranRepository.getVerseMetadata(1)).thenReturn(Result.success(testMetadata))
        whenever(quranRepository.getVerseMetadata(2)).thenReturn(
            Result.failure(Exception("Network error"))
        )

        // When
        val result = useCase.cacheMultiple(ayahs)

        // Then
        assertTrue(result.isSuccess)
        assertEquals(1, result.getOrThrow().size) // Only the successful one
        verify(quranRepository, times(1)).saveCachedContent(any())
    }

    @Test
    fun `use specified reciter edition when provided`() = runTest {
        // Given
        val ayahRef = AyahReference(surah = 1, ayah = 1, globalAyahNumber = 1)
        whenever(quranRepository.getCachedContentById("1:1")).thenReturn(null)
        whenever(quranRepository.getVerseMetadata(1)).thenReturn(Result.success(testMetadata))

        // When
        val result = useCase(ayahRef, reciterEdition = "ar.abdulbasit", forceRefresh = false)

        // Then
        assertTrue(result.isSuccess)
        verify(quranRepository).getAudioUrl("ar.abdulbasit", 1, "64")
    }

    @Test
    fun `use default reciter when reciter is none`() = runTest {
        // Given
        val ayahRef = AyahReference(surah = 1, ayah = 1, globalAyahNumber = 1)
        whenever(quranRepository.getCachedContentById("1:1")).thenReturn(null)
        whenever(quranRepository.getVerseMetadata(1)).thenReturn(Result.success(testMetadata))

        // When
        val result = useCase(ayahRef, reciterEdition = "none", forceRefresh = false)

        // Then
        assertTrue(result.isSuccess)
        verify(quranRepository).getAudioUrl("ar.alafasy", 1, "64")
    }

    @Test
    fun `cache id matches ayah reference format`() = runTest {
        // Given
        val ayahRef = AyahReference(surah = 2, ayah = 255, globalAyahNumber = 262)
        whenever(quranRepository.getCachedContentById("2:255")).thenReturn(null)
        whenever(quranRepository.getVerseMetadata(262)).thenReturn(Result.success(testMetadata))

        // When
        val result = useCase(ayahRef)

        // Then
        assertTrue(result.isSuccess)
        val cached = result.getOrThrow()
        assertEquals("2:255", cached.id)
        assertEquals(2, cached.surah)
        assertEquals(255, cached.ayah)
    }

    @Test
    fun `empty list of ayahs returns success with empty list`() = runTest {
        // Given
        val ayahs = emptyList<AyahReference>()

        // When
        val result = useCase.cacheMultiple(ayahs)

        // Then
        assertTrue(result.isSuccess)
        assertEquals(0, result.getOrThrow().size)
        verify(quranRepository, never()).saveCachedContent(any())
    }
}