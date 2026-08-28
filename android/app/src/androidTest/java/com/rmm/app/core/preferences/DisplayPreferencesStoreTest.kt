package com.rmm.app.core.preferences

import android.content.res.Configuration
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.rmm.app.R
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DisplayPreferencesStoreTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val store = DisplayPreferencesStore.get(context)

    @Test
    fun languageAndThemePersistTogether() {
        val original = store.load()
        try {
            val expected = DisplayPreferences(AppLanguage.ENGLISH, AppTheme.DARK)
            store.save(expected)
            assertEquals(expected, store.load())
        } finally {
            store.save(original)
        }
    }

    @Test
    fun localizedContextsResolveSpanishAndEnglishResources() {
        assertEquals("Mi cuenta", localizedString(AppLanguage.SPANISH, R.string.account_title))
        assertEquals("My account", localizedString(AppLanguage.ENGLISH, R.string.account_title))
        assertEquals("Modo de color", localizedString(AppLanguage.SPANISH, R.string.settings_theme))
        assertEquals("Colour mode", localizedString(AppLanguage.ENGLISH, R.string.settings_theme))
    }

    private fun localizedString(language: AppLanguage, resource: Int): String {
        val configuration = Configuration(context.resources.configuration).apply {
            setLocale(Locale.forLanguageTag(language.languageTag))
        }
        return context.createConfigurationContext(configuration).getString(resource)
    }
}
