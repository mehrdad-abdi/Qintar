package io.github.mehrdad_abdi.quranbookmarks.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "surahs")
data class SurahEntity(
    @PrimaryKey val surahNumber: Int,
    val surahName: String,
    val surahNameEn: String,
    val revelationType: String,
    val numberOfAyahs: Int
)
