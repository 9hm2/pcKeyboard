package com.pckeyboard.ime.layout

import android.view.KeyEvent
import com.pckeyboard.ime.model.Key
import com.pckeyboard.ime.model.KeyType
import com.pckeyboard.ime.model.KeyboardLayout

/**
 * US English (ANSI QWERTY). Matches HK's default `donottranslate-keymap.xml`:
 *  - Number row: ` 1-0 - =
 *  - Top:        Tab + q-p + [ ] \
 *  - Home:       Caps + a-l + ; ' + Enter
 *  - Bottom:     Shift + (no LSGT) + z-m + , . / + ↑ + Shift
 *
 * ANSI doesn't have an LSGT key, so the bottom row uses a slightly wider
 * left Shift instead of squeezing in a < / > position.
 */
object EnglishLayout {

    private fun numberRow(): List<Key> = listOf(
        Key.char("`", "~", popup = "~`"),
        Key.char("1", "!", popup = "¹½¼"),
        Key.char("2", "@", popup = "²"),
        Key.char("3", "#", popup = "³¾"),
        Key.char("4", "$", popup = "€£¥₹₽"),
        Key.char("5", "%", popup = "‰"),
        Key.char("6", "^", popup = "°"),
        Key.char("7", "&", popup = "§"),
        Key.char("8", "*", popup = "•×★"),
        Key.char("9", "(", popup = "[{"),
        Key.char("0", ")", popup = "]}"),
        Key.char("-", "_", popup = "–—"),
        Key.char("=", "+", popup = "±≠≈"),
        Key.fn("⌫", KeyType.BACKSPACE, KeyEvent.KEYCODE_DEL, repeatable = true)
    )

    private fun topLetters(): List<Key> = listOf(
        Key.fn("Tab", KeyType.TAB, KeyEvent.KEYCODE_TAB),
        Key.letter("q"),
        Key.letter("w"),
        Key.letter("e", popup = "èéêëēėę"),
        Key.letter("r"),
        Key.letter("t", popup = "þ"),
        Key.letter("y", popup = "ÿýȳ"),
        Key.letter("u", popup = "üùúûūų"),
        Key.letter("i", popup = "ïìíîīįı"),
        Key.letter("o", popup = "öòóôõøōœ"),
        Key.letter("p"),
        Key.char("[", "{", popup = "「【〔"),
        Key.char("]", "}", popup = "」】〕"),
        Key.char("\\", "|", popup = "¦/")
    )

    private fun homeLetters(): List<Key> = listOf(
        Key.fn("Caps", KeyType.CAPS_LOCK, sticky = true, weight = 1.5f),
        Key.letter("a", popup = "àáâäãåæāąª"),
        Key.letter("s", popup = "ßśšșş"),
        Key.letter("d", popup = "ðď"),
        Key.letter("f"),
        Key.letter("g", popup = "ğ"),
        Key.letter("h"),
        Key.letter("j"),
        Key.letter("k"),
        Key.letter("l", popup = "łĺľļ"),
        Key.char(";", ":", popup = "·"),
        Key.char("'", "\"", popup = "‘’‚‛"),
        Key.fn("⏎", KeyType.ENTER, KeyEvent.KEYCODE_ENTER, weight = 1.5f)
    )

    // 14-weight row so the ▲ at column 89.3 % lines up with ▼ on the row
    // below. ANSI has no LSGT, so the left Shift takes its slot at weight 2.0.
    private fun bottomLetters(): List<Key> = listOf(
        Key.fn("⇧", KeyType.SHIFT, sticky = true, weight = 2.0f),
        Key.letter("z", popup = "žźż"),
        Key.letter("x"),
        Key.letter("c", popup = "çćč©"),
        Key.letter("v"),
        Key.letter("b"),
        Key.letter("n", popup = "ñńň"),
        Key.letter("m"),
        Key.char(",", "<", popup = "«‹„"),
        Key.char(".", ">", popup = "…»›"),
        Key.char("/", "?", popup = "¿÷"),
        Key.fn("▲", KeyType.ARROW_UP, KeyEvent.KEYCODE_DPAD_UP, repeatable = true),
        Key.fn("⇧", KeyType.SHIFT, sticky = true, weight = 1.0f)
    )

    fun main(): KeyboardLayout = LayoutBlocks.mainLayout(
        id = "en_US",
        displayName = "English (US)",
        numberRow = numberRow(),
        topLetters = topLetters(),
        homeLetters = homeLetters(),
        bottomLetters = bottomLetters()
    )

    fun symbols(): KeyboardLayout = LayoutBlocks.symbols()
    fun symbolsShift(): KeyboardLayout = LayoutBlocks.symbolsShift()
}
