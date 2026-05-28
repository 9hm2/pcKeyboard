package com.pckeyboard.ime.layout

import android.view.KeyEvent
import com.pckeyboard.ime.model.Key
import com.pckeyboard.ime.model.KeyType
import com.pckeyboard.ime.model.KeyboardLayout

/**
 * German (DE). QWERTZ with ä / ö / ü / ß as dedicated keys on the home /
 * top rows, matching desktop DE layouts; long-press exposes more accented
 * variants.
 */
object GermanLayout {

    private fun topLetters(): List<Key> = listOf(
        Key.fn("Tab", KeyType.TAB, KeyEvent.KEYCODE_TAB, weight = 1.4f),
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
        Key.char("ö", "Ö"),
        Key.char("\\", "|", weight = 1.2f)
    )

    private fun homeLetters(): List<Key> = listOf(
        Key.fn("Caps", KeyType.CAPS_LOCK, sticky = true, weight = 1.6f),
        Key.letter("a", popup = "äàáâãåæ"),
        Key.letter("s", popup = "ßśš"),
        Key.letter("d"),
        Key.letter("f"),
        Key.letter("g"),
        Key.letter("h"),
        Key.letter("j"),
        Key.letter("k"),
        Key.letter("l"),
        Key.char("ä", "Ä"),
        Key.char("ß", "ẞ"),
        Key.fn("⏎", KeyType.ENTER, KeyEvent.KEYCODE_ENTER, weight = 2.0f)
    )

    private fun bottomLetters(): List<Key> = listOf(
        Key.fn("⇧", KeyType.SHIFT, sticky = true, weight = 2.0f),
        Key.letter("y", popup = "ÿý"),
        Key.letter("x"),
        Key.letter("c", popup = "çć"),
        Key.letter("v"),
        Key.letter("b"),
        Key.letter("n", popup = "ñń"),
        Key.letter("m"),
        Key.char(",", ";", popup = "«‹„"),
        Key.char(".", ":", popup = "…»›"),
        Key.char("-", "_", popup = "–—"),
        Key.fn("⇧", KeyType.SHIFT, sticky = true, weight = 2.0f)
    )

    fun main(): KeyboardLayout = LayoutBlocks.mainLayout(
        id = "de_DE",
        displayName = "German",
        topLetters = topLetters(),
        homeLetters = homeLetters(),
        bottomLetters = bottomLetters()
    )
}
