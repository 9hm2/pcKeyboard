package com.pckeyboard.ime.layout

import android.view.KeyEvent
import com.pckeyboard.ime.model.Key
import com.pckeyboard.ime.model.KeyType
import com.pckeyboard.ime.model.KeyboardLayout

/**
 * German (DE ISO 105-key, QWERTZ). Matches HK's `values-de/...keymap.xml`:
 *  - Number row: ^ 1-0 ß '   (^ in tlde slot, ß ' as last two)
 *  - Top:        Tab + q-p + ü + #   (# in the bksl slot, + as ad12)
 *  - Home:       Caps + a-l + ö ä + Enter   (11 letter-positions)
 *  - Bottom:     ⇧ + < + y-m + , . - + ↑ + ⇧   (< in the LSGT slot)
 */
object GermanLayout {

    private fun numberRow(): List<Key> = listOf(
        Key.char("^", "°", popup = "^°"),
        Key.char("1", "!", popup = "¹¡"),
        Key.char("2", "\"", alt = "²", popup = "²"),
        Key.char("3", "§", alt = "³", popup = "³£"),
        Key.char("4", "$", popup = "¼¢"),
        Key.char("5", "%", popup = "½"),
        Key.char("6", "&", popup = "¬"),
        Key.char("7", "/", alt = "{", popup = "{"),
        Key.char("8", "(", alt = "[", popup = "["),
        Key.char("9", ")", alt = "]", popup = "]"),
        Key.char("0", "=", alt = "}", popup = "}°"),
        Key.char("ß", "?", alt = "\\", popup = "\\¿"),
        Key.char("'", "`", popup = "‘’"),
        Key.fn("⌫", KeyType.BACKSPACE, KeyEvent.KEYCODE_DEL, repeatable = true)
    )

    // AltGr per HK values-de/donottranslate-altchars.xml: q→@, e→€,
    // m→μ, plus the usual European accent popups on each vowel. The
    // a → ä / u → ü / o → ö entries the original table also had are
    // removed here because ä, ü, ö are already base characters on
    // their own dedicated keys, so showing them again on a / u / o
    // would duplicate the bottom-right hint across the layout.
    private fun topLetters(): List<Key> = listOf(
        Key.fn("Tab", KeyType.TAB, KeyEvent.KEYCODE_TAB),
        Key.letter("q", alt = "@", popup = "@"),
        Key.letter("w"),
        Key.letter("e", alt = "€", popup = "€éèêë"),
        Key.letter("r"),
        Key.letter("t"),
        Key.letter("z", alt = "ž", popup = "žźż"),
        Key.letter("u", popup = "üùúûū"),
        Key.letter("i", popup = "ïìíîī"),
        Key.letter("o", popup = "öòóôõøœ"),
        Key.letter("p"),
        Key.char("ü", "Ü"),
        Key.char("+", "*", alt = "~", popup = "~"),
        Key.char("#", "'", popup = "†‡")
    )

    private fun homeLetters(): List<Key> = listOf(
        Key.fn("Caps", KeyType.CAPS_LOCK, sticky = true, weight = 1.5f),
        Key.letter("a", popup = "äàáâãåæ"),
        Key.letter("s", alt = "§", popup = "§ßśš"),
        Key.letter("d"),
        Key.letter("f"),
        Key.letter("g"),
        Key.letter("h"),
        Key.letter("j"),
        Key.letter("k"),
        Key.letter("l", alt = "£", popup = "£₤"),
        Key.char("ö", "Ö"),
        Key.char("ä", "Ä"),
        Key.fn("⏎", KeyType.ENTER, KeyEvent.KEYCODE_ENTER, weight = 1.5f)
    )

    private fun bottomLetters(): List<Key> = listOf(
        Key.fn("⇧", KeyType.SHIFT, sticky = true, weight = 1.0f),
        Key.char("<", ">", alt = "|", popup = "|≤≥«»"),
        Key.letter("y", alt = "ý", popup = "ýÿ¥"),
        Key.letter("x"),
        Key.letter("c", alt = "ç", popup = "çčć¢"),
        Key.letter("v"),
        Key.letter("b"),
        Key.letter("n", alt = "ñ", popup = "ñń"),
        Key.letter("m", alt = "µ", popup = "µ"),
        Key.char(",", ";", popup = "«‹„·"),
        Key.char(".", ":", popup = "…»›"),
        Key.char("-", "_", popup = "–—"),
        Key.fn("▲", KeyType.ARROW_UP, KeyEvent.KEYCODE_DPAD_UP, repeatable = true),
        Key.fn("⇧", KeyType.SHIFT, sticky = true, weight = 1.0f)
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
