package io.github.mehrdad_abdi.quranbookmarks.data.repository

import io.github.mehrdad_abdi.quranbookmarks.data.local.entities.SurahEntity
import io.github.mehrdad_abdi.quranbookmarks.data.local.entities.VerseEntity

interface OfflineVerseRepository {
    suspend fun getVerse(surahNumber: Int, ayahNumber: Int): VerseEntity?
    suspend fun getVersesBySurah(surahNumber: Int): List<VerseEntity>
    suspend fun getVersesByPage(pageNumber: Int): List<VerseEntity>
    suspend fun getAllSurahs(): List<SurahEntity>
    suspend fun getSurah(surahNumber: Int): SurahEntity?
}
