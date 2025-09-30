package io.github.mehrdad_abdi.quranbookmarks.domain.usecase.cache

import io.github.mehrdad_abdi.quranbookmarks.domain.model.Bookmark
import io.github.mehrdad_abdi.quranbookmarks.domain.model.BookmarkType
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class GetAyahsFromBookmarkUseCaseTest {

    private lateinit var useCase: GetAyahsFromBookmarkUseCase

    @Before
    fun setup() {
        useCase = GetAyahsFromBookmarkUseCase()
    }

    @Test
    fun `single ayah bookmark returns one ayah reference`() {
        // Given
        val bookmark = Bookmark(
            id = 1,
            groupId = 1,
            type = BookmarkType.AYAH,
            startSurah = 1,
            startAyah = 1
        )

        // When
        val result = useCase(bookmark)

        // Then
        assertEquals(1, result.size)
        assertEquals(1, result[0].surah)
        assertEquals(1, result[0].ayah)
        assertEquals("1:1", result[0].getCacheId())
    }

    @Test
    fun `range bookmark returns multiple ayah references`() {
        // Given
        val bookmark = Bookmark(
            id = 1,
            groupId = 1,
            type = BookmarkType.RANGE,
            startSurah = 2,
            startAyah = 1,
            endAyah = 5
        )

        // When
        val result = useCase(bookmark)

        // Then
        assertEquals(5, result.size)
        assertEquals(2, result[0].surah)
        assertEquals(1, result[0].ayah)
        assertEquals(2, result[4].surah)
        assertEquals(5, result[4].ayah)
    }

    @Test
    fun `surah bookmark returns all ayahs in surah`() {
        // Given - Al-Fatiha has 7 ayahs
        val bookmark = Bookmark(
            id = 1,
            groupId = 1,
            type = BookmarkType.SURAH,
            startSurah = 1,
            startAyah = 1
        )

        // When
        val result = useCase(bookmark)

        // Then
        assertEquals(7, result.size)
        assertEquals(1, result[0].surah)
        assertEquals(1, result[0].ayah)
        assertEquals(1, result[6].surah)
        assertEquals(7, result[6].ayah)
    }

    @Test
    fun `page bookmark returns empty list`() {
        // Given - Page bookmarks are not yet supported for caching
        val bookmark = Bookmark(
            id = 1,
            groupId = 1,
            type = BookmarkType.PAGE,
            startSurah = 1,
            startAyah = 1  // page number
        )

        // When
        val result = useCase(bookmark)

        // Then
        assertTrue(result.isEmpty())
    }

    @Test
    fun `ayah references have correct global ayah numbers`() {
        // Given - Test with Surah 2 (Al-Baqarah) which starts at global ayah 8
        val bookmark = Bookmark(
            id = 1,
            groupId = 1,
            type = BookmarkType.AYAH,
            startSurah = 2,
            startAyah = 1
        )

        // When
        val result = useCase(bookmark)

        // Then
        assertEquals(1, result.size)
        assertEquals(8, result[0].globalAyahNumber) // 7 ayahs from Al-Fatiha + ayah 1 = 8
    }

    @Test
    fun `cache ids are correctly formatted`() {
        // Given
        val bookmark = Bookmark(
            id = 1,
            groupId = 1,
            type = BookmarkType.RANGE,
            startSurah = 3,
            startAyah = 10,
            endAyah = 12
        )

        // When
        val result = useCase(bookmark)

        // Then
        assertEquals("3:10", result[0].getCacheId())
        assertEquals("3:11", result[1].getCacheId())
        assertEquals("3:12", result[2].getCacheId())
    }

    @Test
    fun `large surah bookmark returns correct ayah count`() {
        // Given - Al-Baqarah (Surah 2) has 286 ayahs
        val bookmark = Bookmark(
            id = 1,
            groupId = 1,
            type = BookmarkType.SURAH,
            startSurah = 2,
            startAyah = 1
        )

        // When
        val result = useCase(bookmark)

        // Then
        assertEquals(286, result.size)
        assertEquals(2, result[0].surah)
        assertEquals(1, result[0].ayah)
        assertEquals(2, result[285].surah)
        assertEquals(286, result[285].ayah)
    }

    @Test
    fun `small surah bookmark returns correct ayah count`() {
        // Given - Al-Ikhlas (Surah 112) has 4 ayahs
        val bookmark = Bookmark(
            id = 1,
            groupId = 1,
            type = BookmarkType.SURAH,
            startSurah = 112,
            startAyah = 1
        )

        // When
        val result = useCase(bookmark)

        // Then
        assertEquals(4, result.size)
        assertEquals(112, result[0].surah)
        assertEquals(1, result[0].ayah)
        assertEquals(112, result[3].surah)
        assertEquals(4, result[3].ayah)
    }

    @Test
    fun `range within same surah has correct surah number for all ayahs`() {
        // Given
        val bookmark = Bookmark(
            id = 1,
            groupId = 1,
            type = BookmarkType.RANGE,
            startSurah = 5,
            startAyah = 3,
            endAyah = 8
        )

        // When
        val result = useCase(bookmark)

        // Then
        assertEquals(6, result.size)
        result.forEach { ayahRef ->
            assertEquals(5, ayahRef.surah)
        }
    }
}