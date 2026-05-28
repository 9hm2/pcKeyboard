package com.pckeyboard.ime.layout

import android.view.KeyEvent
import com.pckeyboard.ime.model.Key
import com.pckeyboard.ime.model.KeyType
import com.pckeyboard.ime.model.KeyboardLayout
import com.pckeyboard.ime.model.LayoutMode

/**
 * Full PC-style English (US) layout, including a row of function keys
 * and dedicated Ctrl/Alt/Meta/Tab/Esc keys.
 *
 * The layout deliberately follows the Samsung "PC-style" foldable keyboard
 * arrangement so users on Fold devices get the productivity bonus of a
 * full keyboard while phone users still get a comfortable QWERTY.
 */
object EnglishLayout {

    private fun fnRow(): List<Key> = listOf(
        Key.fn("Esc", KeyType.ESC, KeyEvent.KEYCODE_ESCAPE, weight = 1.2f),
        Key.fn("F1",  KeyType.FN, KeyEvent.KEYCODE_F1),
        Key.fn("F2",  KeyType.FN, KeyEvent.KEYCODE_F2),
        Key.fn("F3",  KeyType.FN, KeyEvent.KEYCODE_F3),
        Key.fn("F4",  KeyType.FN, KeyEvent.KEYCODE_F4),
        Key.fn("F5",  KeyType.FN, KeyEvent.KEYCODE_F5),
        Key.fn("F6",  KeyType.FN, KeyEvent.KEYCODE_F6),
        Key.fn("F7",  KeyType.FN, KeyEvent.KEYCODE_F7),
        Key.fn("F8",  KeyType.FN, KeyEvent.KEYCODE_F8),
        Key.fn("F9",  KeyType.FN, KeyEvent.KEYCODE_F9),
        Key.fn("F10", KeyType.FN, KeyEvent.KEYCODE_F10),
        Key.fn("F11", KeyType.FN, KeyEvent.KEYCODE_F11),
        Key.fn("F12", KeyType.FN, KeyEvent.KEYCODE_F12),
        Key.fn("Del", KeyType.DELETE, KeyEvent.KEYCODE_FORWARD_DEL, weight = 1.2f, repeatable = true)
    )

    private fun numberRow(): List<Key> = listOf(
        Key.char("`", "~"),
        Key.char("1", "!"),
        Key.char("2", "@"),
        Key.char("3", "#"),
        Key.char("4", "$"),
        Key.char("5", "%"),
        Key.char("6", "^"),
        Key.char("7", "&"),
        Key.char("8", "*"),
        Key.char("9", "("),
        Key.char("0", ")"),
        Key.char("-", "_"),
        Key.char("=", "+"),
        Key.fn("⌫", KeyType.BACKSPACE, KeyEvent.KEYCODE_DEL, weight = 1.8f, repeatable = true)
    )

    private fun topLetters(): List<Key> = listOf(
        Key.fn("Tab", KeyType.TAB, KeyEvent.KEYCODE_TAB, weight = 1.4f),
        Key.letter("q"), Key.letter("w"), Key.letter("e", popup = "èéêëē"),
        Key.letter("r"), Key.letter("t"), Key.letter("y", popup = "ÿý"),
        Key.letter("u", popup = "üùúûū"), Key.letter("i", popup = "ïìíîī"),
        Key.letter("o", popup = "öòóôõøō"), Key.letter("p"),
        Key.char("[", "{"),
        Key.char("]", "}"),
        Key.char("\\", "|", weight = 1.2f)
    )

    private fun homeLetters(): List<Key> = listOf(
        Key.fn("Caps", KeyType.CAPS_LOCK, sticky = true, weight = 1.6f),
        Key.letter("a", popup = "àáâäãåæā"),
        Key.letter("s", popup = "ßśš"),
        Key.letter("d"),
        Key.letter("f"),
        Key.letter("g"),
        Key.letter("h"),
        Key.letter("j"),
        Key.letter("k"),
        Key.letter("l"),
        Key.char(";", ":"),
        Key.char("'", "\""),
        Key.fn("⏎", KeyType.ENTER, KeyEvent.KEYCODE_ENTER, weight = 2.0f)
    )

    private fun bottomLetters(): List<Key> = listOf(
        Key.fn("⇧", KeyType.SHIFT, sticky = true, weight = 2.0f),
        Key.letter("z"), Key.letter("x"), Key.letter("c", popup = "çć"),
        Key.letter("v"), Key.letter("b"), Key.letter("n", popup = "ñń"),
        Key.letter("m"),
        Key.char(",", "<"),
        Key.char(".", ">"),
        Key.char("/", "?"),
        Key.fn("⇧", KeyType.SHIFT, sticky = true, weight = 2.0f)
    )

    private fun controlRow(): List<Key> = listOf(
        Key.fn("Ctrl",  KeyType.CTRL,  sticky = true, weight = 1.3f),
        Key.fn("Win",   KeyType.META,  sticky = true, weight = 1.1f),
        Key.fn("Alt",   KeyType.ALT,   sticky = true, weight = 1.1f),
        Key.fn("123",   KeyType.SYMBOL_SWITCH, weight = 1.2f),
        Key.fn("space", KeyType.SPACE, KeyEvent.KEYCODE_SPACE, weight = 5.5f),
        Key.fn("◀",    KeyType.ARROW_LEFT,  KeyEvent.KEYCODE_DPAD_LEFT,  repeatable = true),
        Key.fn("▲",    KeyType.ARROW_UP,    KeyEvent.KEYCODE_DPAD_UP,    repeatable = true),
        Key.fn("▼",    KeyType.ARROW_DOWN,  KeyEvent.KEYCODE_DPAD_DOWN,  repeatable = true),
        Key.fn("▶",    KeyType.ARROW_RIGHT, KeyEvent.KEYCODE_DPAD_RIGHT, repeatable = true),
        Key.fn("Alt",   KeyType.ALT,  sticky = true, weight = 1.1f),
        Key.fn("Ctrl",  KeyType.CTRL, sticky = true, weight = 1.3f)
    )

    fun main(): KeyboardLayout = KeyboardLayout(
        id = "en_us",
        displayName = "English (US)",
        rows = listOf(
            fnRow(),
            numberRow(),
            topLetters(),
            homeLetters(),
            bottomLetters(),
            controlRow()
        ),
        mode = LayoutMode.MAIN
    )

    fun symbols(): KeyboardLayout = KeyboardLayout(
        id = "en_us_sym",
        displayName = "Symbols",
        rows = listOf(
            listOf(
                Key.char("1"), Key.char("2"), Key.char("3"), Key.char("4"), Key.char("5"),
                Key.char("6"), Key.char("7"), Key.char("8"), Key.char("9"), Key.char("0"),
                Key.fn("⌫", KeyType.BACKSPACE, KeyEvent.KEYCODE_DEL, weight = 1.5f, repeatable = true)
            ),
            listOf(
                Key.char("@"), Key.char("#"), Key.char("$"), Key.char("_"), Key.char("&"),
                Key.char("-"), Key.char("+"), Key.char("("), Key.char(")"), Key.char("/"),
                Key.fn("⏎", KeyType.ENTER, KeyEvent.KEYCODE_ENTER, weight = 1.5f)
            ),
            listOf(
                Key.fn("=\\<", KeyType.LAYOUT_SWITCH, weight = 1.5f),
                Key.char("*"), Key.char("\""), Key.char("'"), Key.char(":"),
                Key.char(";"), Key.char("!"), Key.char("?"),
                Key.char(","), Key.char("."),
                Key.fn("⇧", KeyType.SHIFT, sticky = true, weight = 1.5f)
            ),
            listOf(
                Key.fn("ABC",   KeyType.SYMBOL_SWITCH, weight = 1.5f),
                Key.fn(",",     KeyType.CHAR, weight = 1f),
                Key.fn("space", KeyType.SPACE, KeyEvent.KEYCODE_SPACE, weight = 5f),
                Key.fn(".",     KeyType.CHAR, weight = 1f),
                Key.fn("⏎",    KeyType.ENTER, KeyEvent.KEYCODE_ENTER, weight = 1.5f)
            )
        ),
        mode = LayoutMode.SYMBOLS
    )
}
