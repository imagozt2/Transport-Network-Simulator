package com.rmm.app.core.preferences

import org.junit.Assert.assertEquals
import org.junit.Test

class DisplayPreferencesContractTest {
    @Test
    fun defaultsAreSpanishAndLightWithoutPersistedSelection() {
        assertEquals(
            DisplayPreferences(AppLanguage.SPANISH, AppTheme.LIGHT),
            DisplayPreferences(),
        )
    }

    @Test
    fun everySupportedLanguageCanBeCombinedWithEveryTheme() {
        val combinations = AppLanguage.entries.flatMap { language ->
            AppTheme.entries.map { theme -> DisplayPreferences(language, theme) }
        }

        assertEquals(AppLanguage.entries.size * AppTheme.entries.size, combinations.distinct().size)
        assertEquals(setOf("es", "en"), combinations.map { it.language.languageTag }.toSet())
        assertEquals(setOf(AppTheme.LIGHT, AppTheme.DARK), combinations.map { it.theme }.toSet())
    }
}
