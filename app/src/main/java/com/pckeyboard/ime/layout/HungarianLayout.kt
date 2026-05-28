package com.pckeyboard.ime.layout

import android.view.KeyEvent
import com.pckeyboard.ime.model.Key
import com.pckeyboard.ime.model.KeyType
import com.pckeyboard.ime.model.KeyboardLayout

/**
 * Hungarian (HU). QWERTZ (Y and Z swapped) with the Hungarian vowels
 * surfaced through long-press alternates: á / é / í / ó ö ő / ú ü ű.
 */
object HungarianLayout {

    private fun topLetters(): List<Key> = listOf(
        Key.fn("Tab", KeyType.TAB, KeyEvent.KEYCODE_TAB, weight = 1.4f),
        Key.letter("q"),
        Key.letter("w"),
        Key.letter("e", popup = "éèêëē"),
        Key.letter("r"),
        Key.letter("t"),
        Key.letter("z", popup = "žźż"),                 // QWERTZ
        Key.letter("u", popup = "úüűùûū"),
        Key.letter("i", popup = "íìîï"),
        Key.letter("o", popup = "óöőòôõø"),
        Key.letter("p"),
        Key.char("ő", "Ő"),
        Key.char("ú", "Ú"),
        Key.char("\\", "|", weight = 1.2f)
    )

    // Mirror the HU ISO 105-key layout: home row ends with é, á, ű before
    // Enter just like a physical Hungarian keyboard.
    private fun homeLetters(): List<Key> = listOf(
        Key.fn("Caps", KeyType.CAPS_LOCK, sticky = true, weight = 1.6f),
        Key.letter("a", popup = "áàâäãåæ"),
        Key.letter("s", popup = "śš"),
        Key.letter("d"),
        Key.letter("f"),
        Key.letter("g"),
        Key.letter("h"),
        Key.letter("j"),
        Key.letter("k"),
        Key.letter("l"),
        Key.char("é", "É"),
        Key.char("á", "Á"),
        Key.char("ű", "Ű"),
        Key.fn("⏎", KeyType.ENTER, KeyEvent.KEYCODE_ENTER, weight = 2.0f)
    )

    private fun bottomLetters(): List<Key> = listOf(
        Key.fn("⇧", KeyType.SHIFT, sticky = true, weight = 1.6f),
        Key.char("í", "Í"),
        Key.letter("y", popup = "ÿý"),                   // QWERTZ
        Key.letter("x"),
        Key.letter("c", popup = "çć"),
        Key.letter("v"),
        Key.letter("b"),
        Key.letter("n", popup = "ñń"),
        Key.letter("m"),
        Key.char(",", "?", popup = "«‹„"),
        Key.char(".", ":", popup = "…»›"),
        Key.char("-", "_"),
        Key.fn("⇧", KeyType.SHIFT, sticky = true, weight = 1.6f)
    )

    fun main(): KeyboardLayout = LayoutBlocks.mainLayout(
        id = "hu_HU",
        displayName = "Hungarian",
        topLetters = topLetters(),
        homeLetters = homeLetters(),
        bottomLetters = bottomLetters()
    )
}
