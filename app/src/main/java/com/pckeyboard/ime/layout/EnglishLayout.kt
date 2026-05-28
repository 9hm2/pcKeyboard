package com.pckeyboard.ime.layout

import android.view.KeyEvent
import com.pckeyboard.ime.model.Key
import com.pckeyboard.ime.model.KeyType
import com.pckeyboard.ime.model.KeyboardLayout

/**
 * US English (QWERTY). Letter rows define the locale; shared Fn / number /
 * control / symbol rows come from [LayoutBlocks].
 */
object EnglishLayout {

    private fun topLetters(): List<Key> = listOf(
        Key.fn("Tab", KeyType.TAB, KeyEvent.KEYCODE_TAB, weight = 1.4f),
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
        Key.char("\\", "|", popup = "¦/", weight = 1.2f)
    )

    private fun homeLetters(): List<Key> = listOf(
        Key.fn("Caps", KeyType.CAPS_LOCK, sticky = true, weight = 1.6f),
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
        Key.fn("⏎", KeyType.ENTER, KeyEvent.KEYCODE_ENTER, weight = 2.0f)
    )

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
        Key.fn("⇧", KeyType.SHIFT, sticky = true, weight = 2.0f)
    )

    fun main(): KeyboardLayout = LayoutBlocks.mainLayout(
        id = "en_US",
        displayName = "English (US)",
        topLetters = topLetters(),
        homeLetters = homeLetters(),
        bottomLetters = bottomLetters()
    )

    fun symbols(): KeyboardLayout = LayoutBlocks.symbols()
    fun symbolsShift(): KeyboardLayout = LayoutBlocks.symbolsShift()
}
