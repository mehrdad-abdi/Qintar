package io.github.mehrdad_abdi.quranbookmarks.data.local.entities

import io.github.mehrdad_abdi.quranbookmarks.domain.model.VerseMetadata

fun VerseEntity.toVerseMetadata(): VerseMetadata {
    return VerseMetadata(
        text = text,
        surahName = surahName,
        surahNameEn = surahNameEn,
        surahNumber = surahNumber,
        revelationType = revelationType,
        numberOfAyahs = numberOfAyahs,
        hizbQuarter = hizbQuarter,
        rukuNumber = rukuNumber,
        page = page,
        manzil = manzil,
        sajda = sajda,
        globalAyahNumber = globalAyahNumber,
        ayahInSurah = ayahInSurah
    )
}
