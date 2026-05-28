package com.pckeyboard.ime.layout

import android.view.KeyEvent
import com.pckeyboard.ime.model.Key
import com.pckeyboard.ime.model.KeyType
import com.pckeyboard.ime.model.KeyboardLayout

/**
 * Hungarian (HU ISO 105-key, QWERTZ). Matches HK's `values-hu/...keymap.xml`:
 *  - Number row: 0 1-9 ö ü ó   (NOT EN-style ` 1-= — HU has 0 on the left
 *                                and ö ü ó where -, = sit on ANSI)
 *  - Top:        Tab + q-p + ő ú ű    (ű in the bksl slot)
 *  - Home:       Ctrl + a-l + é á + Enter   (11 letter-positions)
 *  - Bottom:     ⇧ + í + y-m + , . - + ↑ + ⇧   (í in the LSGT slot)
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

    private fun topLetters(): List<Key> = listOf(
        Key.fn("Tab", KeyType.TAB, KeyEvent.KEYCODE_TAB, weight = 1.5f),
        Key.letter("q"),
        Key.letter("w"),
        Key.letter("e", popup = "éèêëē"),
        Key.letter("r"),
        Key.letter("t"),
        Key.letter("z", popup = "žźż"),
        Key.letter("u", popup = "úüűùûū"),
        Key.letter("i", popup = "íìîï"),
        Key.letter("o", popup = "óöőòôõø"),
        Key.letter("p"),
        Key.char("ő", "Ő", popup = "÷[{"),
        Key.char("ú", "Ú", popup = "×]}"),
        Key.char("ű", "Ű", popup = "\\|", weight = 1.5f)
    )

    private fun homeLetters(): List<Key> = listOf(
        Key.fn("Ctrl", KeyType.CTRL, sticky = true, weight = 1.5f),
        Key.letter("a", popup = "áàâäãåæ"),
        Key.letter("s", popup = "śš"),
        Key.letter("d"),
        Key.letter("f"),
        Key.letter("g"),
        Key.letter("h"),
        Key.letter("j"),
        Key.letter("k"),
        Key.letter("l"),
        Key.char("é", "É", popup = "$;:"),
        Key.char("á", "Á", popup = "ß'\""),
        Key.fn("⏎", KeyType.ENTER, KeyEvent.KEYCODE_ENTER, weight = 1.5f)
    )

    private fun bottomLetters(): List<Key> = listOf(
        Key.fn("⇧", KeyType.SHIFT, sticky = true, weight = 1.5f),
        Key.char("í", "Í", popup = "<>"),
        Key.letter("y", popup = "ÿý"),
        Key.letter("x"),
        Key.letter("c", popup = "çć"),
        Key.letter("v"),
        Key.letter("b"),
        Key.letter("n", popup = "ñń"),
        Key.letter("m"),
        Key.char(",", "?", popup = "«‹„"),
        Key.char(".", ":", popup = "…»›"),
        Key.char("-", "_", popup = "–—"),
        Key.fn("▲", KeyType.ARROW_UP, KeyEvent.KEYCODE_DPAD_UP, repeatable = true),
        Key.fn("⇧", KeyType.SHIFT, sticky = true, weight = 1.5f)
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
