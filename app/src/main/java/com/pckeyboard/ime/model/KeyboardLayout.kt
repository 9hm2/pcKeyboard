package com.pckeyboard.ime.model

/**
 * A full keyboard layout, organised as rows of keys.
 * The layout is pluggable: a layout for another locale is just another
 * KeyboardLayout instance, so new locales can be added without touching
 * the renderer or service.
 */
data class KeyboardLayout(
    val id: String,
    val displayName: String,
    val rows: List<List<Key>>,
    val mode: LayoutMode = LayoutMode.MAIN
)

enum class LayoutMode {
    MAIN,
    SYMBOLS,
    SYMBOLS_SHIFT,
    NUMERIC,
    PHONE
}
