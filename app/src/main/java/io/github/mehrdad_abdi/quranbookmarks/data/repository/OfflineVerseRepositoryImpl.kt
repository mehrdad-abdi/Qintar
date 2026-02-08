package io.github.mehrdad_abdi.quranbookmarks.data.repository

import io.github.mehrdad_abdi.quranbookmarks.data.local.dao.QuranDao
import io.github.mehrdad_abdi.quranbookmarks.data.local.entities.SurahEntity
import io.github.mehrdad_abdi.quranbookmarks.data.local.entities.VerseEntity
import javax.inject.Inject

class OfflineVerseRepositoryImpl @Inject constructor(
    private val quranDao: QuranDao
) : OfflineVerseRepository {

    override suspend fun getVerse(surahNumber: Int, ayahNumber: Int): VerseEntity? {
        val globalAyahNumber = calculateGlobalAyahNumber(surahNumber, ayahNumber)
        return quranDao.getVerseByGlobalAyahNumber(globalAyahNumber)
    }

    override suspend fun getVersesBySurah(surahNumber: Int): List<VerseEntity> {
        return quranDao.getVersesBySurah(surahNumber)
    }

    override suspend fun getVersesByPage(pageNumber: Int): List<VerseEntity> {
        return quranDao.getVersesByPage(pageNumber)
    }

    override suspend fun getAllSurahs(): List<SurahEntity> {
        return quranDao.getAllSurahs()
    }

    override suspend fun getSurah(surahNumber: Int): SurahEntity? {
        return quranDao.getSurahByNumber(surahNumber)
    }

    private fun calculateGlobalAyahNumber(surahNumber: Int, ayahNumber: Int): Int {
        val surahAyahCounts = listOf(
            7, 286, 200, 176, 120, 165, 206, 75, 129, 109, 123, 111, 43, 52, 99, 128, 111, 110,
            98, 135, 112, 78, 118, 64, 77, 227, 93, 88, 69, 60, 34, 30, 73, 54, 45, 83, 182, 88,
            75, 85, 54, 53, 40, 87, 55, 78, 96, 109, 123, 64, 52, 57, 35, 41, 44, 28, 28, 27, 25,
            22, 17, 19, 15, 13, 14, 11, 11, 11, 8, 5, 6, 4, 4, 3, 5, 4, 5, 6, 3
        )
        return surahAyahCounts.take(surahNumber - 1).sum() + ayahNumber
    }
}
