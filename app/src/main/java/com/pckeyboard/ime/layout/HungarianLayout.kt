package com.pckeyboard.ime.layout

import android.view.KeyEvent
import com.pckeyboard.ime.model.Key
import com.pckeyboard.ime.model.KeyType
import com.pckeyboard.ime.model.KeyboardLayout

/**
 * Hungarian (HU ISO 105-key, QWERTZ). AltGr glyphs taken from the xkb HU
 * map (matches HK's values-hu/donottranslate-altchars.xml) and exposed
 * both as the key's [Key.altLabel] (held-Alt commits this char) and at
 * the head of [Key.popupChars] so long-press still reveals them.
 *
 *  - Number row:    0 1-9 ö ü ó           (NOT EN-style ` 1-= — HU has 0
 *                                            on the left, ö ü ó on the right)
 *  - Top:           Tab + q-p ő ú + ű     (ű in the bksl slot)
 *  - Home:          Caps + a-l + é á + Enter
 *  - Bottom:        ⇧ + í + y-m + , . - + ↑ + ⇧
 */
object HungarianLayout {

    private fun numberRow(): List<Key> = listOf(
        Key.char("0", "~", alt = "<", popup = "<`"),
        Key.char("1", "'", alt = "~", popup = "~!¡¹"),
        Key.char("2", "\"", alt = "ˇ", popup = "ˇ@²"),
        Key.char("3", "+", alt = "^", popup = "^#³"),
        Key.char("4", "!", alt = "˘", popup = "˘$€"),
        Key.char("5", "%", alt = "°", popup = "°"),
        Key.char("6", "/", alt = "˛", popup = "˛&"),
        Key.char("7", "=", alt = "`", popup = "`§"),
        Key.char("8", "(", alt = "˙", popup = "˙*[{"),
        Key.char("9", ")", alt = "´", popup = "´*]}"),
        Key.char("ö", "Ö", alt = "0", popup = "0˝"),
        Key.char("ü", "Ü", alt = "-", popup = "-_¨"),
        Key.char("ó", "Ó", alt = ".", popup = ".¸=≈"),
        Key.fn("⌫", KeyType.BACKSPACE, KeyEvent.KEYCODE_DEL, repeatable = true)
    )

    // AltGr layer from the canonical Magyar billentyűzetkiosztás:
    //   q→\  w→|  e→Ä  r→¶  t→ŧ  z→@  u→€  o→[  p→]  ő→÷  ú→×  ű→¤
    // Letters where the spec leaves AltGr blank are left without alt= so
    // Alt+key passes through as a META_ALT_ON KeyEvent for app shortcuts
    // (Ctrl+Alt+key chords etc.). The popup chars start with the AltGr
    // glyph so it's also reachable via long-press.
    private fun topLetters(): List<Key> = listOf(
        Key.fn("Tab", KeyType.TAB, KeyEvent.KEYCODE_TAB),
        Key.letter("q", alt = "\\", popup = "\\"),
        Key.letter("w", alt = "|", popup = "|"),
        Key.letter("e", alt = "Ä", popup = "Ä€éèêëē"),
        Key.letter("r", alt = "¶", popup = "¶₹"),
        Key.letter("t", alt = "ŧ", popup = "ŧ₺þ"),
        Key.letter("z", alt = "@", popup = "@žźż"),
        Key.letter("u", alt = "€", popup = "€úüűùûū"),
        Key.letter("i", popup = "íìîïī"),
        Key.letter("o", alt = "[", popup = "[óöőòôõø"),
        Key.letter("p", alt = "]", popup = "]π₱§"),
        Key.char("ő", "Ő", alt = "÷", popup = "÷[{"),
        Key.char("ú", "Ú", alt = "×", popup = "×]}"),
        Key.char("ű", "Ű", alt = "¤", popup = "¤\\|")
    )

    private fun homeLetters(): List<Key> = listOf(
        Key.fn("Caps", KeyType.CAPS_LOCK, sticky = true, weight = 1.5f),
        Key.letter("a", alt = "đ", popup = "đáàâãåæä"),
        Key.letter("s", alt = "Đ", popup = "Đßš§"),
        Key.letter("d", alt = "[", popup = "["),
        Key.letter("f", alt = "]", popup = "]₣"),
        Key.letter("g", alt = "]", popup = "]"),
        Key.letter("h", alt = "ł", popup = "łħ"),
        Key.letter("j", alt = "Ł", popup = "Łĵ"),
        Key.letter("k", alt = "ł", popup = "ł"),
        Key.letter("l", alt = "$", popup = "$£₤λ"),
        Key.char("é", "É", alt = "$", popup = "$;:"),
        Key.char("á", "Á", alt = "ß", popup = "ß'\""),
        Key.fn("⏎", KeyType.ENTER, KeyEvent.KEYCODE_ENTER, weight = 1.5f)
    )

    private fun bottomLetters(): List<Key> = listOf(
        Key.fn("⇧", KeyType.SHIFT, sticky = true, weight = 1.0f),
        Key.char("í", "Í", alt = "<", popup = "<>"),
        Key.letter("y", alt = ">", popup = ">ÿý¥"),
        Key.letter("x", alt = "#", popup = "#"),
        Key.letter("c", alt = "&", popup = "&çčć¢"),
        Key.letter("v", alt = "@", popup = "@"),
        Key.letter("b", alt = "{", popup = "{đ"),
        Key.letter("n", alt = "}", popup = "}ñń"),
        Key.letter("m", alt = "<", popup = "<µ"),
        Key.char(",", "?", alt = ";", popup = ";«‹„"),
        Key.char(".", ":", alt = ">", popup = ">…»›"),
        Key.char("-", "_", alt = "*", popup = "*–—"),
        Key.fn("▲", KeyType.ARROW_UP, KeyEvent.KEYCODE_DPAD_UP, repeatable = true),
        Key.fn("⇧", KeyType.SHIFT, sticky = true, weight = 1.0f)
    )

    fun main(): KeyboardLayout = LayoutBlocks.mainLayout(
        id = "hu_HU",
        displayName = "Hungarian",
        numberRow = numberRow(),
        topLetters = topLetters(),
        homeLetters = homeLetters(),
        bottomLetters = bottomLetters()
    )
}
