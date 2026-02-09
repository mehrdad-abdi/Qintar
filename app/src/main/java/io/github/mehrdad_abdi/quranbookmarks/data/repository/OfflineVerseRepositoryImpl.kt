package io.github.mehrdad_abdi.quranbookmarks.data.repository

import io.github.mehrdad_abdi.quranbookmarks.data.local.dao.QuranDao
import io.github.mehrdad_abdi.quranbookmarks.data.local.entities.SurahEntity
import io.github.mehrdad_abdi.quranbookmarks.data.local.entities.VerseEntity
import javax.inject.Inject

class OfflineVerseRepositoryImpl @Inject constructor(
    private val quranDao: QuranDao
) : OfflineVerseRepository {

    override suspend fun getVerse(surahNumber: Int, ayahNumber: Int): VerseEntity? {
        return quranDao.getVerseBySurahAndAyah(surahNumber, ayahNumber)
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
}
