package io.github.mehrdad_abdi.quranbookmarks.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import io.github.mehrdad_abdi.quranbookmarks.domain.model.KhatmProgress

@Entity(tableName = "khatm_progress")
data class KhatmProgressEntity(
    @PrimaryKey
    val id: Int = 1, // Single row table
    val lastPageRead: Int = 1, // Page number 1-604
    val updatedAt: Long = System.currentTimeMillis()
)

fun KhatmProgressEntity.toDomain(): KhatmProgress {
    return KhatmProgress(
        lastPageRead = lastPageRead,
        updatedAt = updatedAt
    )
}

fun KhatmProgress.toEntity(): KhatmProgressEntity {
    return KhatmProgressEntity(
        id = 1,
        lastPageRead = lastPageRead,
        updatedAt = updatedAt
    )
}
