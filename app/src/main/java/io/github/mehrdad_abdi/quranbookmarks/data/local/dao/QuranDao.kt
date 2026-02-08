package io.github.mehrdad_abdi.quranbookmarks.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import io.github.mehrdad_abdi.quranbookmarks.data.local.entities.SurahEntity
import io.github.mehrdad_abdi.quranbookmarks.data.local.entities.VerseEntity

@Dao
interface QuranDao {

    @Query("SELECT * FROM surahs ORDER BY surahNumber ASC")
    suspend fun getAllSurahs(): List<SurahEntity>

    @Query("SELECT * FROM surahs WHERE surahNumber = :surahNumber")
    suspend fun getSurahByNumber(surahNumber: Int): SurahEntity?

    @Query("SELECT * FROM verses WHERE globalAyahNumber = :globalAyahNumber")
    suspend fun getVerseByGlobalAyahNumber(globalAyahNumber: Int): VerseEntity?

    @Query("SELECT * FROM verses WHERE surahNumber = :surahNumber ORDER BY ayahInSurah ASC")
    suspend fun getVersesBySurah(surahNumber: Int): List<VerseEntity>

    @Query("SELECT * FROM verses WHERE page = :pageNumber ORDER BY surahNumber ASC, ayahInSurah ASC")
    suspend fun getVersesByPage(pageNumber: Int): List<VerseEntity>

    @Query("SELECT * FROM verses WHERE surahNumber = :surahNumber AND ayahInSurah BETWEEN :startAyah AND :endAyah ORDER BY ayahInSurah ASC")
    suspend fun getVersesInAyahRange(surahNumber: Int, startAyah: Int, endAyah: Int): List<VerseEntity>

}
