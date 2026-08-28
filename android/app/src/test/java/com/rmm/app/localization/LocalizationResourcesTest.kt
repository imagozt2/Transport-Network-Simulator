package com.rmm.app.localization

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

class LocalizationResourcesTest {
    private val spanish = catalogue("app/src/main/res/values/strings.xml")
    private val english = catalogue("app/src/main/res/values-en/strings.xml")

    @Test
    fun englishCatalogueContainsEveryStringAndPlural() {
        assertEquals(spanish.keys, english.keys)
    }

    @Test
    fun translationsPreserveAllFormatArguments() {
        spanish.forEach { (key, source) ->
            assertEquals("Format arguments differ for $key", arguments(source), arguments(english.getValue(key)))
        }
    }

    @Test
    fun englishCatalogueDoesNotFallBackToSpanishInterfaceText() {
        val languageIndependent = setOf(
            "app_name",
            "ticket_wallet_single_trip",
            "ticket_purchase_total",
            "journey_history_duration",
            "journey_history_fare",
            "journeys_duration_minutes",
            "journeys_segment_duration",
        )

        spanish.forEach { (key, source) ->
            if (key !in languageIndependent) {
                assertFalse("$key is still written in Spanish", source == english.getValue(key))
            }
        }
        assertTrue(english.values.none { SPANISH_MARKERS.containsMatchIn(it) })
    }

    private fun catalogue(relativePath: String): Map<String, String> {
        val file = generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
            .flatMap { directory ->
                sequenceOf(
                    directory.resolve(relativePath),
                    directory.resolve("android/$relativePath"),
                )
            }
            .firstOrNull(File::isFile)
            ?: error("Resource file not found: $relativePath")
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val result = linkedMapOf<String, String>()
        val resources = document.documentElement

        for (index in 0 until resources.childNodes.length) {
            val node = resources.childNodes.item(index)
            if (node !is Element) continue
            when (node.tagName) {
                "string" -> result[node.getAttribute("name")] = node.textContent
                "plurals" -> for (itemIndex in 0 until node.childNodes.length) {
                    val item = node.childNodes.item(itemIndex)
                    if (item is Element && item.tagName == "item") {
                        result["${node.getAttribute("name")}.${item.getAttribute("quantity")}"] = item.textContent
                    }
                }
            }
        }
        return result
    }

    private fun arguments(value: String): List<String> = FORMAT_ARGUMENT
        .findAll(value)
        .map(MatchResult::value)
        .sorted()
        .toList()

    private companion object {
        val FORMAT_ARGUMENT = Regex("%\\d+\\$[a-zA-Z]")
        val SPANISH_MARKERS = Regex(
            "[¿¡]|\\b(billete|trayecto|estación|contraseña|recarga|viajes|días|selecciona|cargar|cerrar|cuenta)\\b",
            RegexOption.IGNORE_CASE,
        )
    }
}
