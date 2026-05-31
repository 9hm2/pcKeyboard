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
 *
 * The AltGr layer is the US-International convention: vowels expose
 * their common accented forms (á / é / í / ó / ö / ü …), letter keys
 * pick up the trademark / copyright / euro / yen family (© ® ™ € ¥ £),
 * and the number row carries the typographic / fraction / sign glyphs
 * (¡ ² ³ ° § × ÷ {} – ≠). Every glyph appears on exactly one key so the
 * bottom-right hint never repeats.
 */
object EnglishLayout {

    private fun numberRow(): List<Key> = listOf(
        Key.char("`", "~", popup = "~`"),
        Key.char("1", "!", alt = "¡", popup = "¡¹½¼"),
        Key.char("2", "@", alt = "²", popup = "²"),
        Key.char("3", "#", alt = "³", popup = "³¾"),
        Key.char("4", "$", alt = "¥", popup = "¥£₹₽"),
        Key.char("5", "%", alt = "‰", popup = "‰"),
        Key.char("6", "^", alt = "°", popup = "°"),
        Key.char("7", "&", alt = "§", popup = "§"),
        Key.char("8", "*", alt = "×", popup = "×•★"),
        Key.char("9", "(", alt = "{", popup = "{["),
        Key.char("0", ")", alt = "}", popup = "}]"),
        Key.char("-", "_", alt = "–", popup = "–—"),
        Key.char("=", "+", alt = "≠", popup = "≠±≈"),
        Key.fn("⌫", KeyType.BACKSPACE, KeyEvent.KEYCODE_DEL, repeatable = true)
    )

    private fun topLetters(): List<Key> = listOf(
        Key.fn("Tab", KeyType.TAB, KeyEvent.KEYCODE_TAB),
        Key.letter("q", alt = "ä", popup = "ä"),
        Key.letter("w", alt = "å", popup = "å"),
        Key.letter("e", alt = "€", popup = "€èéêëēėę"),
        Key.letter("r", alt = "®", popup = "®"),
        Key.letter("t", alt = "™", popup = "™þ"),
        Key.letter("y", alt = "ÿ", popup = "ÿýȳ"),
        Key.letter("u", alt = "ü", popup = "üùúûūų"),
        Key.letter("i", alt = "í", popup = "íïìîīįı"),
        Key.letter("o", alt = "ö", popup = "öòóôõøōœ"),
        Key.letter("p", alt = "¶", popup = "¶"),
        Key.char("[", "{", alt = "«", popup = "«「【〔"),
        Key.char("]", "}", alt = "»", popup = "»」】〕"),
        Key.char("\\", "|", alt = "¦", popup = "¦/")
    )

    private fun homeLetters(): List<Key> = listOf(
        Key.fn("Caps", KeyType.CAPS_LOCK, sticky = true, weight = 1.5f),
        Key.letter("a", alt = "á", popup = "áàâäãåæāąª"),
        Key.letter("s", alt = "ß", popup = "ßśšșş"),
        Key.letter("d", alt = "ð", popup = "ðď"),
        Key.letter("f", alt = "ƒ", popup = "ƒ"),
        Key.letter("g", alt = "ğ", popup = "ğ"),
        Key.letter("h"),
        Key.letter("j"),
        Key.letter("k"),
        Key.letter("l", alt = "ł", popup = "łĺľļ"),
        Key.char(";", ":", alt = "·", popup = "·"),
        Key.char("'", "\"", alt = "‘", popup = "‘’‚‛"),
        Key.fn("⏎", KeyType.ENTER, KeyEvent.KEYCODE_ENTER, weight = 1.5f)
    )

    // 14-weight row so the ▲ at column 89.3 % lines up with ▼ on the row
    // below. ANSI has no LSGT, so the left Shift takes its slot at weight 2.0.
    private fun bottomLetters(): List<Key> = listOf(
        Key.fn("⇧", KeyType.SHIFT, sticky = true, weight = 2.0f),
        Key.letter("z", alt = "ž", popup = "žźż"),
        Key.letter("x", alt = "÷", popup = "÷"),
        Key.letter("c", alt = "©", popup = "©çćč"),
        Key.letter("v"),
        Key.letter("b"),
        Key.letter("n", alt = "ñ", popup = "ñńň"),
        Key.letter("m", alt = "µ", popup = "µ"),
        Key.char(",", "<", alt = "≤", popup = "≤«‹„"),
        Key.char(".", ">", alt = "≥", popup = "≥…»›"),
        Key.char("/", "?", alt = "¿", popup = "¿"),
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
