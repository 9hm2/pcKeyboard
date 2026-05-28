package com.pckeyboard.ime.layout

import android.view.KeyEvent
import com.pckeyboard.ime.model.Key
import com.pckeyboard.ime.model.KeyType
import com.pckeyboard.ime.model.KeyboardLayout

/**
 * German (DE ISO 105-key, QWERTZ). Matches HK's `values-de/...keymap.xml`:
 *  - Number row: ^ 1-0 ß '   (^ in tlde slot, ß ' as last two)
 *  - Top:        Tab + q-p + ü + #   (# in the bksl slot, + as ad12)
 *  - Home:       Ctrl + a-l + ö ä + Enter   (11 letter-positions)
 *  - Bottom:     ⇧ + < + y-m + , . - + ↑ + ⇧   (< in the LSGT slot)
 */
object GermanLayout {

    private fun numberRow(): List<Key> = listOf(
        Key.char("^", "°", popup = "^°"),
        Key.char("1", "!", popup = "¹¡"),
        Key.char("2", "\"", popup = "²"),
        Key.char("3", "§", popup = "³£"),
        Key.char("4", "$", popup = "¼¢"),
        Key.char("5", "%", popup = "½"),
        Key.char("6", "&", popup = "¬"),
        Key.char("7", "/", popup = "{"),
        Key.char("8", "(", popup = "["),
        Key.char("9", ")", popup = "]"),
        Key.char("0", "=", popup = "}°"),
        Key.char("ß", "?", popup = "\\¿"),
        Key.char("'", "`", popup = "‘’"),
        Key.fn("⌫", KeyType.BACKSPACE, KeyEvent.KEYCODE_DEL, weight = 1.5f, repeatable = true)
    )

    private fun topLetters(): List<Key> = listOf(
        Key.fn("Tab", KeyType.TAB, KeyEvent.KEYCODE_TAB, weight = 1.5f),
        Key.letter("q"),
        Key.letter("w"),
        Key.letter("e", popup = "éèêë"),
        Key.letter("r"),
        Key.letter("t"),
        Key.letter("z", popup = "žźż"),
        Key.letter("u", popup = "üùúûū"),
        Key.letter("i", popup = "ïìíîī"),
        Key.letter("o", popup = "öòóôõøœ"),
        Key.letter("p"),
        Key.char("ü", "Ü"),
        Key.char("+", "*", popup = "~"),
        Key.char("#", "'", popup = "†‡", weight = 1.5f)
    )

    private fun homeLetters(): List<Key> = listOf(
        Key.fn("Ctrl", KeyType.CTRL, sticky = true, weight = 1.5f),
        Key.letter("a", popup = "äàáâãåæ"),
        Key.letter("s", popup = "ßśš"),
        Key.letter("d"),
        Key.letter("f"),
        Key.letter("g"),
        Key.letter("h"),
        Key.letter("j"),
        Key.letter("k"),
        Key.letter("l"),
        Key.char("ö", "Ö"),
        Key.char("ä", "Ä"),
        Key.fn("⏎", KeyType.ENTER, KeyEvent.KEYCODE_ENTER, weight = 1.5f)
    )

    private fun bottomLetters(): List<Key> = listOf(
        Key.fn("⇧", KeyType.SHIFT, sticky = true, weight = 1.5f),
        Key.char("<", ">", popup = "|≤≥«»"),
        Key.letter("y", popup = "ÿý"),
        Key.letter("x"),
        Key.letter("c", popup = "çć"),
        Key.letter("v"),
        Key.letter("b"),
        Key.letter("n", popup = "ñń"),
        Key.letter("m"),
        Key.char(",", ";", popup = "«‹„·"),
        Key.char(".", ":", popup = "…»›"),
        Key.char("-", "_", popup = "–—"),
        Key.fn("▲", KeyType.ARROW_UP, KeyEvent.KEYCODE_DPAD_UP, repeatable = true),
        Key.fn("⇧", KeyType.SHIFT, sticky = true, weight = 1.5f)
    )

    fun main(): KeyboardLayout = LayoutBlocks.mainLayout(
        id = "de_DE",
        displayName = "German",
        numberRow = numberRow(),
        topLetters = topLetters(),
        homeLetters = homeLetters(),
        bottomLetters = bottomLetters()
    )
}
