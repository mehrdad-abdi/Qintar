package io.github.mehrdad_abdi.quranbookmarks.domain.model

/**
 * Supported languages in the application.
 * Each language has a locale code and display name in its native script.
 */
enum class AppLanguage(
    val localeCode: String,
    val nativeName: String,
    val isRtl: Boolean
) {
    ENGLISH(
        localeCode = "en",
        nativeName = "English",
        isRtl = false
    ),
    ARABIC(
        localeCode = "ar",
        nativeName = "العربية",
        isRtl = true
    ),
    FARSI(
        localeCode = "fa",
        nativeName = "فارسی",
        isRtl = true
    );

    companion object {
        /**
         * Get AppLanguage from locale code.
         * Defaults to ENGLISH if not found.
         */
        fun fromLocaleCode(code: String): AppLanguage {
            return values().find { it.localeCode == code } ?: ENGLISH
        }

        /**
         * Get default language.
         */
        fun getDefault(): AppLanguage = ENGLISH
    }
}
