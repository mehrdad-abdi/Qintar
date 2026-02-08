package io.github.mehrdad_abdi.quranbookmarks.data.local.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "verses",
    foreignKeys = [
        ForeignKey(
            entity = SurahEntity::class,
            parentColumns = ["surahNumber"],
            childColumns = ["surahNumber"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["surahNumber"])]
)
data class VerseEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val surahNumber: Int,
    val ayahInSurah: Int,
    val text: String,
    val hizbQuarter: Int,
    val rukuNumber: Int,
    val page: Int,
    val manzil: Int,
    val sajda: Boolean,
    val globalAyahNumber: Int,
    val surahName: String,
    val surahNameEn: String,
    val revelationType: String,
    val numberOfAyahs: Int
)
