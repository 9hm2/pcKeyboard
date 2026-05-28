package com.pckeyboard.ime.layout

import android.view.KeyEvent
import com.pckeyboard.ime.model.Key
import com.pckeyboard.ime.model.KeyType
import com.pckeyboard.ime.model.KeyboardLayout

/**
 * Spanish (ES ISO 105-key, QWERTY). Matches HK's `values-es/...keymap.xml`:
 *  - Number row: º 1-0 ' ¡   (º in tlde slot, ' ¡ as last two)
 *  - Top:        Tab + q-p + ` + + ç   (ç in the bksl slot, ` as ad11,
 *                                       + as ad12)
 *  - Home:       Ctrl + a-l + ñ ´ + Enter   (´ as the acute dead-key slot)
 *  - Bottom:     ⇧ + < + z-m + , . - + ↑ + ⇧   (< in the LSGT slot)
 */
object SpanishLayout {

    private fun numberRow(): List<Key> = listOf(
        Key.char("º", "ª", popup = "ºª"),
        Key.char("1", "!", popup = "|¡"),
        Key.char("2", "\"", popup = "@"),
        Key.char("3", "·", popup = "#£"),
        Key.char("4", "$", popup = "~"),
        Key.char("5", "%", popup = "½"),
        Key.char("6", "&", popup = "¬"),
        Key.char("7", "/", popup = "{"),
        Key.char("8", "(", popup = "["),
        Key.char("9", ")", popup = "]"),
        Key.char("0", "=", popup = "}°"),
        Key.char("'", "?", popup = "\\¿"),
        Key.char("¡", "¿", popup = "~"),
        Key.fn("⌫", KeyType.BACKSPACE, KeyEvent.KEYCODE_DEL, weight = 1.5f, repeatable = true)
    )

    private fun topLetters(): List<Key> = listOf(
        Key.fn("Tab", KeyType.TAB, KeyEvent.KEYCODE_TAB, weight = 1.5f),
        Key.letter("q"),
        Key.letter("w"),
        Key.letter("e", popup = "éèêë"),
        Key.letter("r"),
        Key.letter("t"),
        Key.letter("y", popup = "ÿý"),
        Key.letter("u", popup = "úüùûū"),
        Key.letter("i", popup = "íìîï"),
        Key.letter("o", popup = "óòöôõ"),
        Key.letter("p"),
        Key.char("`", "^", popup = "[°"),
        Key.char("+", "*", popup = "]"),
        Key.char("ç", "Ç", popup = "}", weight = 1.5f)
    )

    private fun homeLetters(): List<Key> = listOf(
        Key.fn("Ctrl", KeyType.CTRL, sticky = true, weight = 1.5f),
        Key.letter("a", popup = "áàâäãå"),
        Key.letter("s"),
        Key.letter("d"),
        Key.letter("f"),
        Key.letter("g"),
        Key.letter("h"),
        Key.letter("j"),
        Key.letter("k"),
        Key.letter("l"),
        Key.char("ñ", "Ñ", popup = "~"),
        Key.char("´", "¨", popup = "`'¨"),
        Key.fn("⏎", KeyType.ENTER, KeyEvent.KEYCODE_ENTER, weight = 1.5f)
    )

    private fun bottomLetters(): List<Key> = listOf(
        Key.fn("⇧", KeyType.SHIFT, sticky = true, weight = 1.0f),
        Key.char("<", ">", popup = "|≤≥«»"),
        Key.letter("z"),
        Key.letter("x"),
        Key.letter("c", popup = "çć"),
        Key.letter("v"),
        Key.letter("b"),
        Key.letter("n", popup = "ñń"),
        Key.letter("m"),
        Key.char(",", ";", popup = "«‹„"),
        Key.char(".", ":", popup = "…»›"),
        Key.char("-", "_", popup = "–—"),
        Key.fn("▲", KeyType.ARROW_UP, KeyEvent.KEYCODE_DPAD_UP, repeatable = true),
        Key.fn("⇧", KeyType.SHIFT, sticky = true, weight = 1.0f)
    )

    fun main(): KeyboardLayout = LayoutBlocks.mainLayout(
        id = "es_ES",
        displayName = "Spanish",
        numberRow = numberRow(),
        topLetters = topLetters(),
        homeLetters = homeLetters(),
        bottomLetters = bottomLetters()
    )
}
