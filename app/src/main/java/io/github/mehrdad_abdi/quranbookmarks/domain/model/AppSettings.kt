package io.github.mehrdad_abdi.quranbookmarks.domain.model

data class AppSettings(
    val reciterEdition: String = "ar.alafasy",
    val reciterBitrate: String = "64", // Audio bitrate for selected reciter
    val notificationSettings: NotificationSettings = NotificationSettings(),
    val theme: AppTheme = AppTheme.SYSTEM,
    val primaryColorHex: String = "#00796B", // Default Teal color
    val playbackSpeed: Float = 1.0f // Playback speed (0.5x to 2x)
)

enum class AppTheme {
    LIGHT,
    DARK,
    SYSTEM
}

enum class ThemeColor(val hex: String, val displayName: String) {
    GOLD("#C5A05C", "Gold"),
    BLUE("#1976D2", "Blue"),
    GREEN("#388E3C", "Green"),
    PURPLE("#7B1FA2", "Purple"),
    TEAL("#00796B", "Teal"),
    ORANGE("#F57C00", "Orange"),
    RED("#D32F2F", "Red"),
    PINK("#C2185B", "Pink")
}
