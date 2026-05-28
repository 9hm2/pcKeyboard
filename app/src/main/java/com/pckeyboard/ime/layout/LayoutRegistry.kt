package com.pckeyboard.ime.layout

import com.pckeyboard.ime.model.KeyboardLayout

/**
 * Registry of available keyboard layouts. Adding a new language is a matter
 * of dropping in a new Layout object and registering it here.
 */
object LayoutRegistry {

    data class LayoutPack(
        val id: String,
        val displayName: String,
        val main: KeyboardLayout,
        val symbols: KeyboardLayout
    )

    private val packs: MutableMap<String, LayoutPack> = mutableMapOf(
        "en_US" to LayoutPack(
            id = "en_US",
            displayName = "English (US)",
            main = EnglishLayout.main(),
            symbols = EnglishLayout.symbols()
        )
    )

    val available: List<LayoutPack> get() = packs.values.toList()

    fun get(id: String): LayoutPack = packs[id] ?: packs.getValue("en_US")

    fun register(pack: LayoutPack) { packs[pack.id] = pack }
}
