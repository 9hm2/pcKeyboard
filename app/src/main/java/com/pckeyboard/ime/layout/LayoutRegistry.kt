package com.pckeyboard.ime.layout

import com.pckeyboard.ime.model.KeyboardLayout

/**
 * Registry of available keyboard layouts. Adding a new language is a matter
 * of dropping in a new Layout object + registering it here + adding the
 * matching <subtype> in res/xml/method.xml.
 */
object LayoutRegistry {

    data class LayoutPack(
        val id: String,
        val displayName: String,
        val main: KeyboardLayout,
        val symbols: KeyboardLayout,
        val symbolsShift: KeyboardLayout
    )

    private val packs: MutableMap<String, LayoutPack> = mutableMapOf(
        "en_US" to LayoutPack(
            id = "en_US",
            displayName = "English (US)",
            main = EnglishLayout.main(),
            symbols = EnglishLayout.symbols(),
            symbolsShift = EnglishLayout.symbolsShift()
        ),
        "hu_HU" to LayoutPack(
            id = "hu_HU",
            displayName = "Hungarian",
            main = HungarianLayout.main(),
            symbols = LayoutBlocks.symbols(),
            symbolsShift = LayoutBlocks.symbolsShift()
        ),
        "de_DE" to LayoutPack(
            id = "de_DE",
            displayName = "German",
            main = GermanLayout.main(),
            symbols = LayoutBlocks.symbols(),
            symbolsShift = LayoutBlocks.symbolsShift()
        ),
        "es_ES" to LayoutPack(
            id = "es_ES",
            displayName = "Spanish",
            main = SpanishLayout.main(),
            symbols = LayoutBlocks.symbols(),
            symbolsShift = LayoutBlocks.symbolsShift()
        )
    )

    val available: List<LayoutPack> get() = packs.values.toList()

    fun get(id: String): LayoutPack = packs[id] ?: packs.getValue("en_US")

    /** Resolve an arbitrary locale string (e.g. "hu", "hu_HU") to a pack id. */
    fun resolveLocale(locale: String?): String {
        val normalized = locale?.lowercase() ?: return "en_US"
        return when {
            normalized.startsWith("hu") -> "hu_HU"
            normalized.startsWith("de") -> "de_DE"
            normalized.startsWith("es") -> "es_ES"
            else -> "en_US"
        }
    }

    fun register(pack: LayoutPack) { packs[pack.id] = pack }
}
