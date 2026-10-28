package com.mrjackspade.kairo.frontend

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class CatalogDescriptionTest {
    private fun layers(vararg records: String) = CatalogFieldLayers.merge(
        records.mapIndexed { index, record -> CatalogFieldLayers.Source("layer$index", JSONObject(record)) },
        { path -> path == listOf("descriptions") })

    private fun description(locale: Locale, vararg records: String): String? =
        layers(*records).let { LayeredGameCatalog.description(it.record, it, locale) }

    private val shipped = """{"description": "A maze game.", "descriptions": {"es": "Un juego de laberintos.", "pt-BR": "Um jogo de labirinto.", "id": "Permainan labirin."}}"""

    @Test
    fun theDeviceLanguageIsShownAndEnglishWhereTheGameHasNone() {
        assertEquals("Un juego de laberintos.", description(Locale.forLanguageTag("es-MX"), shipped))
        assertEquals("Um jogo de labirinto.", description(Locale.forLanguageTag("pt-BR"), shipped))
        assertEquals("A maze game.", description(Locale.forLanguageTag("pt-PT"), shipped))
        assertEquals("A maze game.", description(Locale.forLanguageTag("de-DE"), shipped))
        assertEquals("A maze game.", description(Locale.US, shipped))
    }

    @Test
    fun indonesianIsFoundUnderJavasOldLanguageCode() {
        assertEquals("Permainan labirin.", description(Locale("in", "ID"), shipped))
    }

    @Test
    fun aLayerAboveReplacesOneLanguageOrTheEnglishText() {
        val update = """{"descriptions": {"es": "Un laberinto."}}"""
        assertEquals("Un laberinto.", description(Locale.forLanguageTag("es"), shipped, update))
        assertEquals("Um jogo de labirinto.", description(Locale.forLanguageTag("pt-BR"), shipped, update))
        // An adult part with only its English text: that text, not the main translation below it.
        val adult = """{"description": "A frank maze game."}"""
        assertEquals("A frank maze game.", description(Locale.forLanguageTag("es"), shipped, adult))
        val translatedAdult = """{"description": "A frank maze game.", "descriptions": {"es": "Un laberinto franco."}}"""
        assertEquals("Un laberinto franco.", description(Locale.forLanguageTag("es"), shipped, translatedAdult))
    }
}
