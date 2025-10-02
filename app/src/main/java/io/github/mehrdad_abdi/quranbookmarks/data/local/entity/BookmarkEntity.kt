package io.github.mehrdad_abdi.quranbookmarks.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import io.github.mehrdad_abdi.quranbookmarks.data.local.converters.StringListConverter
import io.github.mehrdad_abdi.quranbookmarks.domain.model.Bookmark
import io.github.mehrdad_abdi.quranbookmarks.domain.model.BookmarkType

@Entity(tableName = "bookmarks")
@TypeConverters(StringListConverter::class)
data class BookmarkEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val type: String,
    val startSurah: Int,
    val startAyah: Int,
    val endSurah: Int,
    val endAyah: Int,
    val description: String,
    val tags: List<String>,
    val createdAt: Long,
    val updatedAt: Long
)

fun BookmarkEntity.toDomain(): Bookmark {
    return Bookmark(
        id = id,
        type = BookmarkType.valueOf(type),
        startSurah = startSurah,
        startAyah = startAyah,
        endSurah = endSurah,
        endAyah = endAyah,
        description = description,
        tags = tags,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}

fun Bookmark.toEntity(): BookmarkEntity {
    return BookmarkEntity(
        id = id,
        type = type.name,
        startSurah = startSurah,
        startAyah = startAyah,
        endSurah = endSurah,
        endAyah = endAyah,
        description = description,
        tags = tags,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}