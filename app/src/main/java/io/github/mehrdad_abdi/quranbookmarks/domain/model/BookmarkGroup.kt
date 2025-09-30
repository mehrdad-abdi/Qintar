package io.github.mehrdad_abdi.quranbookmarks.domain.model

data class BookmarkGroup(
    val id: Long = 0,
    val name: String,
    val description: String = "",
    val color: Int,
    val reciterEdition: String = "ar.alafasy",
    val notificationSettings: NotificationSettings = NotificationSettings(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)