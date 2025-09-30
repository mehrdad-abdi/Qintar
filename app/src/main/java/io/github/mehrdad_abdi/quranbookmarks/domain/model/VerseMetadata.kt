package io.github.mehrdad_abdi.quranbookmarks.domain.model

data class VerseMetadata(
    val text: String,
    val surahName: String,
    val surahNameEn: String,
    val surahNumber: Int, // Surah number (1-114)
    val revelationType: String,
    val numberOfAyahs: Int,
    val hizbQuarter: Int,
    val rukuNumber: Int,
    val page: Int,
    val manzil: Int,
    val sajda: Boolean = false,
    val globalAyahNumber: Int, // Global ayah number (1-6236) for audio URLs
    val ayahInSurah: Int, // Ayah number within the surah
    val cachedAudioPath: String? = null, // Local file path for cached audio
    val cachedImagePath: String? = null // Local file path for cached image
)