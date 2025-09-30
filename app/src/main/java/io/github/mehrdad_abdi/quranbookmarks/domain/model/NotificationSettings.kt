package io.github.mehrdad_abdi.quranbookmarks.domain.model

data class NotificationSettings(
    val enabled: Boolean = false,
    val schedule: NotificationSchedule = NotificationSchedule.DAILY,
    val time: String = "07:00", // HH:mm format
    val days: Set<Int> = setOf() // 1-7 for Monday-Sunday, empty for daily
)