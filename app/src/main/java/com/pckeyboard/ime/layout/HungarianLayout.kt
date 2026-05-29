package com.pckeyboard.ime.layout

import android.view.KeyEvent
import com.pckeyboard.ime.model.Key
import com.pckeyboard.ime.model.KeyType
import com.pckeyboard.ime.model.KeyboardLayout

/**
 * Hungarian (HU ISO 105-key, QWERTZ). AltGr glyphs taken from the xkb HU
 * map (matches HK's values-hu/donottranslate-altchars.xml) and exposed
 * both as the key's [Key.altLabel] (held-Alt commits this char) and at
 * the head of [Key.popupChars] so long-press still reveals them.
 *
 *  - Number row:    0 1-9 ö ü ó           (NOT EN-style ` 1-= — HU has 0
 *                                            on the left, ö ü ó on the right)
 *  - Top:           Tab + q-p ő ú + ű     (ű in the bksl slot)
 *  - Home:          Ctrl + a-l + é á + Enter
 *  - Bottom:        ⇧ + í + y-m + , . - + ↑ + ⇧
 */
object HungarianLayout {

    private fun numberRow(): List<Key> = listOf(
        Key.char("0", "~", popup = "`"),
        Key.char("1", "'", popup = "!¡¹"),
        Key.char("2", "\"", popup = "@²"),
        Key.char("3", "+", popup = "#³"),
        Key.char("4", "!", popup = "$€"),
        Key.char("5", "%", popup = "°"),
        Key.char("6", "/", popup = "&"),
        Key.char("7", "=", popup = "`§"),
        Key.char("8", "(", popup = "*[{"),
        Key.char("9", ")", popup = "*]}"),
        Key.char("ö", "Ö", popup = "˝"),
        Key.char("ü", "Ü", popup = "-_"),
        Key.char("ó", "Ó", popup = "=≈"),
        Key.fn("⌫", KeyType.BACKSPACE, KeyEvent.KEYCODE_DEL, weight = 1.5f, repeatable = true)
    )

    // AltGr glyphs aligned with HK's values-hu/donottranslate-altchars.xml:
    // q→\, w→|, e→€, z→ž, x→#, c→&, v→@, b→đ, n→}, m→μ, etc. Letters
    // without a known AltGr (r, t, i, o, p, j, h) are left without alt= so
    // Alt+key passes through as a META_ALT_ON KeyEvent for app shortcuts.
    private fun topLetters(): List<Key> = listOf(
        Key.fn("Tab", KeyType.TAB, KeyEvent.KEYCODE_TAB, weight = 1.5f),
        Key.letter("q", alt = "\\", popup = "\\"),
        Key.letter("w", alt = "|", popup = "|"),
        Key.letter("e", alt = "€", popup = "€éèêëē"),
        Key.letter("r", popup = "₹"),
        Key.letter("t", popup = "₺þ"),
        Key.letter("z", alt = "ž", popup = "žźż"),
        Key.letter("u", alt = "€", popup = "€úüűùûū"),
        Key.letter("i", popup = "íìîïī"),
        Key.letter("o", popup = "óöőòôõø"),
        Key.letter("p", popup = "π₱§"),
        Key.char("ő", "Ő", popup = "÷[{"),
        Key.char("ú", "Ú", popup = "×]}"),
        Key.char("ű", "Ű", popup = "\\|", weight = 1.5f)
    )

    private fun homeLetters(): List<Key> = listOf(
        Key.fn("Ctrl", KeyType.CTRL, sticky = true, weight = 1.5f),
        Key.letter("a", alt = "ä", popup = "äáàâãåæ"),
        Key.letter("s", alt = "§", popup = "§ßš"),
        Key.letter("d", alt = "đ", popup = "đĐ"),
        Key.letter("f", alt = "[", popup = "[₣"),
        Key.letter("g", alt = "]", popup = "]"),
        Key.letter("h", popup = "ħ"),
        Key.letter("j", popup = "ĵ"),
        Key.letter("k", alt = "ł", popup = "łŁ"),
        Key.letter("l", alt = "£", popup = "£₤λ"),
        Key.char("é", "É", popup = "$;:"),
        Key.char("á", "Á", popup = "ß'\""),
        Key.fn("⏎", KeyType.ENTER, KeyEvent.KEYCODE_ENTER, weight = 1.5f)
    )

    private fun bottomLetters(): List<Key> = listOf(
        Key.fn("⇧", KeyType.SHIFT, sticky = true, weight = 1.0f),
        Key.char("í", "Í", popup = "<>"),
        Key.letter("y", alt = ">", popup = ">ÿý¥"),
        Key.letter("x", alt = "#", popup = "#"),
        Key.letter("c", alt = "&", popup = "&çčć¢"),
        Key.letter("v", alt = "@", popup = "@"),
        Key.letter("b", alt = "đ", popup = "đ"),
        Key.letter("n", alt = "}", popup = "}ñń"),
        Key.letter("m", alt = "µ", popup = "µ"),
        Key.char(",", "?", popup = "«‹„"),
        Key.char(".", ":", popup = "…»›"),
        Key.char("-", "_", popup = "–—"),
        Key.fn("▲", KeyType.ARROW_UP, KeyEvent.KEYCODE_DPAD_UP, repeatable = true),
        Key.fn("⇧", KeyType.SHIFT, sticky = true, weight = 1.0f)
    )

    fun main(): KeyboardLayout = LayoutBlocks.mainLayout(
        id = "hu_HU",
        displayName = "Hungarian",
        numberRow = numberRow(),
        topLetters = topLetters(),
        homeLetters = homeLetters(),
        bottomLetters = bottomLetters()
    )
}
