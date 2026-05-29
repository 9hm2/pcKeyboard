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
        // 0's AltGr `<` removed — duplicate of í's AltGr; í is the
        // canonical home of `<` on Hungarian ISO.
        Key.char("0", "~", popup = "`"),
        Key.char("1", "'", alt = "~", popup = "~!¡¹"),
        Key.char("2", "\"", alt = "ˇ", popup = "ˇ@²"),
        Key.char("3", "+", alt = "^", popup = "^#³"),
        Key.char("4", "!", alt = "˘", popup = "˘$€"),
        Key.char("5", "%", alt = "°", popup = "°"),
        Key.char("6", "/", alt = "˛", popup = "˛&"),
        Key.char("7", "=", alt = "`", popup = "`§"),
        Key.char("8", "(", alt = "˙", popup = "˙*[{"),
        Key.char("9", ")", alt = "´", popup = "´*]}"),
        // ö / ü / ó use their dead-key AltGr glyphs (xkb hu(basic)) rather
        // than the spec's `0 - .` — those would duplicate base characters
        // that already have their own keys on the layout.
        Key.char("ö", "Ö", alt = "˝", popup = "˝"),
        Key.char("ü", "Ü", alt = "¨", popup = "¨"),
        Key.char("ó", "Ó", alt = "¸", popup = "¸=≈"),
        Key.fn("⌫", KeyType.BACKSPACE, KeyEvent.KEYCODE_DEL, repeatable = true)
    )

    // AltGr layer from the canonical Magyar billentyűzetkiosztás, with
    // every glyph appearing on exactly one key so the bottom-right hint
    // in KeyView never repeats a character across the layout. Where the
    // spec listed the same glyph on multiple keys we keep it on the
    // xkb-standard position (e.g. [ on d, ] on g, @ on v, $ on é,
    // ł on k, < on í, > on y) and drop the duplicate from the others.
    private fun topLetters(): List<Key> = listOf(
        Key.fn("Tab", KeyType.TAB, KeyEvent.KEYCODE_TAB),
        Key.letter("q", alt = "\\", popup = "\\"),
        Key.letter("w", alt = "|", popup = "|"),
        Key.letter("e", alt = "Ä", popup = "Ä€éèêëē"),
        Key.letter("r", alt = "¶", popup = "¶₹"),
        Key.letter("t", alt = "ŧ", popup = "ŧ₺þ"),
        // z's `@` removed — duplicate of v.
        Key.letter("z", popup = "žźż"),
        Key.letter("u", alt = "€", popup = "€úüűùûū"),
        Key.letter("i", popup = "íìîïī"),
        // o's `[` removed — duplicate of d.
        Key.letter("o", popup = "óöőòôõø"),
        // p's `]` removed — duplicate of g.
        Key.letter("p", popup = "π₱§"),
        Key.char("ő", "Ő", alt = "÷", popup = "÷[{"),
        Key.char("ú", "Ú", alt = "×", popup = "×]}"),
        Key.char("ű", "Ű", alt = "¤", popup = "¤\\|")
    )

    private fun homeLetters(): List<Key> = listOf(
        Key.fn("Caps", KeyType.CAPS_LOCK, sticky = true, weight = 1.5f),
        Key.letter("a", alt = "đ", popup = "đáàâãåæä"),
        Key.letter("s", alt = "Đ", popup = "Đßš§"),
        // d's `[` removed — moved one slot right to f, the xkb-standard
        // position. Now d has no AltGr glyph.
        Key.letter("d", popup = "đĐ"),
        Key.letter("f", alt = "[", popup = "[₣"),
        Key.letter("g", alt = "]", popup = "]"),
        // h's `ł` removed — duplicate of k.
        Key.letter("h", popup = "ħ"),
        Key.letter("j", alt = "Ł", popup = "Łĵ"),
        Key.letter("k", alt = "ł", popup = "ł"),
        // l's `$` removed — duplicate of é.
        Key.letter("l", popup = "£₤λ"),
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
        // m's `<` removed — duplicate of í.
        Key.letter("m", popup = "µ"),
        Key.char(",", "?", alt = ";", popup = ";«‹„"),
        // . `>` removed — duplicate of y.
        Key.char(".", ":", popup = "…»›"),
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
