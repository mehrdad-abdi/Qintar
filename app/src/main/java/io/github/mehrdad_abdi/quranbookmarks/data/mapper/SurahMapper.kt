package io.github.mehrdad_abdi.quranbookmarks.data.mapper

import io.github.mehrdad_abdi.quranbookmarks.data.local.entities.SurahEntity
import io.github.mehrdad_abdi.quranbookmarks.data.remote.dto.SurahDto
import io.github.mehrdad_abdi.quranbookmarks.domain.model.Surah

fun SurahDto.toDomain(): Surah {
    return Surah(
        number = number,
        name = name,
        englishName = englishName,
        englishNameTranslation = englishNameTranslation,
        numberOfAyahs = numberOfAyahs,
        revelationType = revelationType
    )
}

fun List<SurahDto>.toDomain(): List<Surah> {
    return map { it.toDomain() }
}

fun SurahEntity.toDomain(): Surah {
    return Surah(
        number = surahNumber,
        name = surahName,
        englishName = surahNameEn,
        englishNameTranslation = surahNameEn,
        numberOfAyahs = numberOfAyahs,
        revelationType = revelationType
    )
}

fun List<SurahEntity>.mapToDomain(): List<Surah> {
    return map { it.toDomain() }
}