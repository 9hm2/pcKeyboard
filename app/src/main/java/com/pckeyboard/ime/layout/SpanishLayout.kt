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
 *  - Home:       Caps + a-l + ñ ´ + Enter   (´ as the acute dead-key slot)
 *  - Bottom:     ⇧ + < + z-m + , . - + ↑ + ⇧   (< in the LSGT slot)
 */
object SpanishLayout {

    private fun numberRow(): List<Key> = listOf(
        Key.char("º", "ª", popup = "ºª"),
        // 1 → | removed — | already lives on AltGr+< on the LSGT key
        // (the xkb-canonical Spanish home for the pipe character).
        Key.char("1", "!", popup = "|¡"),
        Key.char("2", "\"", alt = "@", popup = "@"),
        Key.char("3", "·", alt = "#", popup = "#£"),
        Key.char("4", "$", alt = "~", popup = "~"),
        Key.char("5", "%", popup = "½"),
        Key.char("6", "&", alt = "¬", popup = "¬"),
        Key.char("7", "/", alt = "{", popup = "{"),
        Key.char("8", "(", alt = "[", popup = "["),
        Key.char("9", ")", alt = "]", popup = "]"),
        Key.char("0", "=", alt = "}", popup = "}°"),
        Key.char("'", "?", alt = "\\", popup = "\\¿"),
        Key.char("¡", "¿", popup = "~"),
        Key.fn("⌫", KeyType.BACKSPACE, KeyEvent.KEYCODE_DEL, repeatable = true)
    )

    // AltGr per HK values-es/donottranslate-altchars.xml: vowels expose
    // their accented equivalents directly under Alt; e also gets €.
    // The `, +, ç bracket alternatives ([, ], }) are dropped because
    // those bracket glyphs already live on AltGr+8 / 9 / 0; keeping
    // them on two keys at once would duplicate the visible hint.
    private fun topLetters(): List<Key> = listOf(
        Key.fn("Tab", KeyType.TAB, KeyEvent.KEYCODE_TAB),
        Key.letter("q"),
        Key.letter("w"),
        Key.letter("e", alt = "€", popup = "€éèêë"),
        Key.letter("r"),
        Key.letter("t"),
        Key.letter("y", alt = "ý", popup = "ýÿ"),
        Key.letter("u", alt = "ú", popup = "úüùûū"),
        Key.letter("i", alt = "í", popup = "íìîï"),
        Key.letter("o", alt = "ó", popup = "óòöôõ"),
        Key.letter("p"),
        Key.char("`", "^", popup = "[°"),
        Key.char("+", "*", popup = "]"),
        Key.char("ç", "Ç", popup = "}")
    )

    private fun homeLetters(): List<Key> = listOf(
        Key.fn("Caps", KeyType.CAPS_LOCK, sticky = true, weight = 1.5f),
        Key.letter("a", alt = "á", popup = "áàâäãå"),
        Key.letter("s", alt = "§", popup = "§ß"),
        Key.letter("d"),
        Key.letter("f"),
        Key.letter("g"),
        Key.letter("h"),
        Key.letter("j"),
        Key.letter("k"),
        Key.letter("l", alt = "£", popup = "£"),
        Key.char("ñ", "Ñ", popup = "~"),
        Key.char("´", "¨", popup = "`'¨"),
        Key.fn("⏎", KeyType.ENTER, KeyEvent.KEYCODE_ENTER, weight = 1.5f)
    )

    // The < key keeps | as its AltGr (its xkb-canonical home), and the
    // duplicate AltGr+1 → | up in the number row is therefore dropped.
    // n → ñ removed because ñ is its own base-character key on the home
    // row, so showing the hint here would duplicate it.
    private fun bottomLetters(): List<Key> = listOf(
        Key.fn("⇧", KeyType.SHIFT, sticky = true, weight = 1.0f),
        Key.char("<", ">", alt = "|", popup = "|≤≥«»"),
        Key.letter("z"),
        Key.letter("x"),
        Key.letter("c", alt = "ç", popup = "çčć¢"),
        Key.letter("v"),
        Key.letter("b"),
        Key.letter("n", popup = "ñń"),
        Key.letter("m", alt = "µ", popup = "µ"),
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
