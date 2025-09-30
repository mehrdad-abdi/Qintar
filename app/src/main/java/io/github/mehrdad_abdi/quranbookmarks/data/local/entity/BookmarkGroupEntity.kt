package io.github.mehrdad_abdi.quranbookmarks.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import io.github.mehrdad_abdi.quranbookmarks.domain.model.BookmarkGroup
import io.github.mehrdad_abdi.quranbookmarks.domain.model.NotificationSchedule
import io.github.mehrdad_abdi.quranbookmarks.domain.model.NotificationSettings

@Entity(tableName = "bookmark_groups")
data class BookmarkGroupEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val description: String,
    val color: Int,
    val reciterEdition: String,
    val notificationEnabled: Boolean,
    val notificationSchedule: String,
    val notificationTime: String,
    val notificationDays: String, // JSON string of Set<Int>
    val createdAt: Long,
    val updatedAt: Long
)

fun BookmarkGroupEntity.toDomain(): BookmarkGroup {
    val notificationDaysSet = if (notificationDays.isNotBlank()) {
        notificationDays.split(",").mapNotNull { it.toIntOrNull() }.toSet()
    } else {
        emptySet()
    }

    return BookmarkGroup(
        id = id,
        name = name,
        description = description,
        color = color,
        reciterEdition = reciterEdition,
        notificationSettings = NotificationSettings(
            enabled = notificationEnabled,
            schedule = NotificationSchedule.valueOf(notificationSchedule),
            time = notificationTime,
            days = notificationDaysSet
        ),
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}

fun BookmarkGroup.toEntity(): BookmarkGroupEntity {
    return BookmarkGroupEntity(
        id = id,
        name = name,
        description = description,
        color = color,
        reciterEdition = reciterEdition,
        notificationEnabled = notificationSettings.enabled,
        notificationSchedule = notificationSettings.schedule.name,
        notificationTime = notificationSettings.time,
        notificationDays = notificationSettings.days.joinToString(","),
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}