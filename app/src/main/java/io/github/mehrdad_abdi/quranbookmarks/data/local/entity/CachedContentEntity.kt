package io.github.mehrdad_abdi.quranbookmarks.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import io.github.mehrdad_abdi.quranbookmarks.domain.model.CachedContent
import io.github.mehrdad_abdi.quranbookmarks.domain.model.VerseMetadata

@Entity(tableName = "cached_content")
data class CachedContentEntity(
    @PrimaryKey
    val id: String,
    val surah: Int,
    val ayah: Int,
    val imagePath: String?,
    val audioPath: String?,
    val metadataText: String?,
    val metadataSurahName: String?,
    val metadataSurahNameEn: String?,
    val metadataRevelationType: String?,
    val metadataNumberOfAyahs: Int?,
    val metadataHizbQuarter: Int?,
    val metadataRukuNumber: Int?,
    val metadataPage: Int?,
    val metadataManzil: Int?,
    val metadataSajda: Boolean?,
    val downloadedAt: Long
)

fun CachedContentEntity.toDomain(): CachedContent {
    val metadata = if (metadataText != null) {
        VerseMetadata(
            text = metadataText,
            surahName = metadataSurahName ?: "",
            surahNameEn = metadataSurahNameEn ?: "",
            surahNumber = surah, // Use the cached surah number
            revelationType = metadataRevelationType ?: "",
            numberOfAyahs = metadataNumberOfAyahs ?: 0,
            hizbQuarter = metadataHizbQuarter ?: 0,
            rukuNumber = metadataRukuNumber ?: 0,
            page = metadataPage ?: 0,
            manzil = metadataManzil ?: 0,
            sajda = metadataSajda ?: false,
            globalAyahNumber = 0, // TODO: Add to cache schema in future migration
            ayahInSurah = ayah // Use the cached ayah number as fallback
        )
    } else null

    return CachedContent(
        id = id,
        surah = surah,
        ayah = ayah,
        imagePath = imagePath,
        audioPath = audioPath,
        metadata = metadata,
        downloadedAt = downloadedAt
    )
}

fun CachedContent.toEntity(): CachedContentEntity {
    return CachedContentEntity(
        id = id,
        surah = surah,
        ayah = ayah,
        imagePath = imagePath,
        audioPath = audioPath,
        metadataText = metadata?.text,
        metadataSurahName = metadata?.surahName,
        metadataSurahNameEn = metadata?.surahNameEn,
        metadataRevelationType = metadata?.revelationType,
        metadataNumberOfAyahs = metadata?.numberOfAyahs,
        metadataHizbQuarter = metadata?.hizbQuarter,
        metadataRukuNumber = metadata?.rukuNumber,
        metadataPage = metadata?.page,
        metadataManzil = metadata?.manzil,
        metadataSajda = metadata?.sajda,
        downloadedAt = downloadedAt
    )
}