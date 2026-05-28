package com.pckeyboard.ime.layout

import android.view.KeyEvent
import com.pckeyboard.ime.model.Key
import com.pckeyboard.ime.model.KeyType
import com.pckeyboard.ime.model.KeyboardLayout

/**
 * Spanish (ES). QWERTY with ñ as a dedicated key between l and the
 * punctuation on the home row; ¿ / ¡ surfaced via long-press on / and !.
 */
object SpanishLayout {

    private fun topLetters(): List<Key> = listOf(
        Key.fn("Tab", KeyType.TAB, KeyEvent.KEYCODE_TAB, weight = 1.4f),
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
        Key.char("[", "{"),
        Key.char("]", "}"),
        Key.char("\\", "|", weight = 1.2f)
    )

    // Mirror the ES ISO 105-key layout: home row ends with ñ, ´, ç. The
    // physical ´ is a dead key on PC — we commit it literally on tap
    // (with `¨ ` ' ´` available as long-press alternates), since long-press
    // on the vowels already gives accented letters directly.
    private fun homeLetters(): List<Key> = listOf(
        Key.fn("Caps", KeyType.CAPS_LOCK, sticky = true, weight = 1.6f),
        Key.letter("a", popup = "áàâäãå"),
        Key.letter("s"),
        Key.letter("d"),
        Key.letter("f"),
        Key.letter("g"),
        Key.letter("h"),
        Key.letter("j"),
        Key.letter("k"),
        Key.letter("l"),
        Key.char("ñ", "Ñ"),
        Key.char("´", "¨", popup = "`'¨"),
        Key.char("ç", "Ç"),
        Key.fn("⏎", KeyType.ENTER, KeyEvent.KEYCODE_ENTER, weight = 2.0f)
    )

    private fun bottomLetters(): List<Key> = listOf(
        Key.fn("⇧", KeyType.SHIFT, sticky = true, weight = 2.0f),
        Key.letter("z"),
        Key.letter("x"),
        Key.letter("c", popup = "çć"),
        Key.letter("v"),
        Key.letter("b"),
        Key.letter("n", popup = "ñń"),
        Key.letter("m"),
        Key.char(",", "<", popup = "«‹„"),
        Key.char(".", ">", popup = "…»›"),
        Key.char("/", "?", popup = "¿"),
        Key.fn("⇧", KeyType.SHIFT, sticky = true, weight = 2.0f)
    )

    fun main(): KeyboardLayout = LayoutBlocks.mainLayout(
        id = "es_ES",
        displayName = "Spanish",
        topLetters = topLetters(),
        homeLetters = homeLetters(),
        bottomLetters = bottomLetters()
    )
}
