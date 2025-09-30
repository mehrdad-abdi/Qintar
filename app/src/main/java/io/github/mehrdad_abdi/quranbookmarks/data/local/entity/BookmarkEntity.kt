package io.github.mehrdad_abdi.quranbookmarks.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import io.github.mehrdad_abdi.quranbookmarks.domain.model.Bookmark
import io.github.mehrdad_abdi.quranbookmarks.domain.model.BookmarkType

@Entity(
    tableName = "bookmarks",
    foreignKeys = [
        ForeignKey(
            entity = BookmarkGroupEntity::class,
            parentColumns = ["id"],
            childColumns = ["groupId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["groupId"])
    ]
)
data class BookmarkEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val groupId: Long,
    val type: String,
    val startSurah: Int,
    val startAyah: Int,
    val endSurah: Int,
    val endAyah: Int,
    val description: String,
    val createdAt: Long,
    val updatedAt: Long
)

fun BookmarkEntity.toDomain(): Bookmark {
    return Bookmark(
        id = id,
        groupId = groupId,
        type = BookmarkType.valueOf(type),
        startSurah = startSurah,
        startAyah = startAyah,
        endSurah = endSurah,
        endAyah = endAyah,
        description = description,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}

fun Bookmark.toEntity(): BookmarkEntity {
    return BookmarkEntity(
        id = id,
        groupId = groupId,
        type = type.name,
        startSurah = startSurah,
        startAyah = startAyah,
        endSurah = endSurah,
        endAyah = endAyah,
        description = description,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}