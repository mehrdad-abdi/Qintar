package io.github.mehrdad_abdi.quranbookmarks.domain.repository

import io.github.mehrdad_abdi.quranbookmarks.domain.model.Surah

interface SurahRepository {
    suspend fun getAllSurahs(): Result<List<Surah>>
    suspend fun getSurahByNumber(number: Int): Result<Surah?>
}