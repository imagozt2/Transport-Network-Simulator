package com.rmm.app.core.preferences

import android.content.Context

enum class AppLanguage(val languageTag: String) {
    SPANISH("es"),
    ENGLISH("en"),
}

enum class AppTheme {
    LIGHT,
    DARK,
}

data class DisplayPreferences(
    val language: AppLanguage = AppLanguage.SPANISH,
    val theme: AppTheme = AppTheme.LIGHT,
)

class DisplayPreferencesStore private constructor(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        FILE_NAME,
        Context.MODE_PRIVATE,
    )

    fun load(): DisplayPreferences = DisplayPreferences(
        language = preferences.getString(KEY_LANGUAGE, null)
            ?.let { stored -> AppLanguage.entries.firstOrNull { it.name == stored } }
            ?: AppLanguage.SPANISH,
        theme = preferences.getString(KEY_THEME, null)
            ?.let { stored -> AppTheme.entries.firstOrNull { it.name == stored } }
            ?: AppTheme.LIGHT,
    )

    fun save(displayPreferences: DisplayPreferences) {
        preferences.edit()
            .putString(KEY_LANGUAGE, displayPreferences.language.name)
            .putString(KEY_THEME, displayPreferences.theme.name)
            .apply()
    }

    companion object {
        private const val FILE_NAME = "rmm-display-preferences"
        private const val KEY_LANGUAGE = "language"
        private const val KEY_THEME = "theme"

        @Volatile
        private var instance: DisplayPreferencesStore? = null

        fun get(context: Context): DisplayPreferencesStore = instance ?: synchronized(this) {
            instance ?: DisplayPreferencesStore(context).also { instance = it }
        }
    }
}
