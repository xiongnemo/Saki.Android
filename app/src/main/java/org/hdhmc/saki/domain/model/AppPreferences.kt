package org.hdhmc.saki.domain.model

enum class TextScale(
    val storageKey: String,
    val label: String,
    val multiplier: Float,
) {
    EXTRA_SMALL(
        storageKey = "extra_small",
        label = "Extra small",
        multiplier = 0.8f,
    ),
    SMALL(
        storageKey = "small",
        label = "Small",
        multiplier = 0.92f,
    ),
    DEFAULT(
        storageKey = "default",
        label = "Default",
        multiplier = 1.0f,
    ),
    LARGE(
        storageKey = "large",
        label = "Large",
        multiplier = 1.12f,
    ),
    EXTRA_LARGE(
        storageKey = "extra_large",
        label = "Extra large",
        multiplier = 1.24f,
    );

    companion object {
        fun fromStorageKey(storageKey: String?): TextScale {
            return entries.firstOrNull { it.storageKey == storageKey } ?: DEFAULT
        }
    }
}

data class AppPreferences(
    val textScale: TextScale = TextScale.DEFAULT,
    val language: AppLanguage = AppLanguage.SYSTEM,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val albumViewMode: AlbumViewMode = AlbumViewMode.GRID,
)

enum class AppLanguage(val tag: String) {
    SYSTEM("system"),
    ENGLISH("en"),
    CHINESE("zh");

    companion object {
        fun fromTag(tag: String?): AppLanguage =
            entries.firstOrNull { it.tag == tag } ?: SYSTEM
    }
}

enum class ThemeMode(val storageKey: String) {
    SYSTEM("system"),
    LIGHT("light"),
    DARK("dark");

    companion object {
        fun fromStorageKey(storageKey: String?): ThemeMode =
            entries.firstOrNull { it.storageKey == storageKey } ?: SYSTEM
    }
}

enum class AlbumViewMode(val storageKey: String) {
    GRID("grid"),
    LIST("list");

    companion object {
        fun fromStorageKey(storageKey: String?): AlbumViewMode =
            entries.firstOrNull { it.storageKey == storageKey } ?: GRID
    }
}
