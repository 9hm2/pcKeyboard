package com.pckeyboard.ime.layout

import android.view.KeyEvent
import com.pckeyboard.ime.model.Key
import com.pckeyboard.ime.model.KeyType
import com.pckeyboard.ime.model.KeyboardLayout
import com.pckeyboard.ime.model.LayoutMode

/**
 * Rows + symbol pages that are shared across every locale (function keys,
 * the number row, the bottom control row, the two symbol pages). Locale
 * layouts only need to supply their own letter rows.
 */
internal object LayoutBlocks {

    fun fnRow(): List<Key> = listOf(
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

    fun numberRow(): List<Key> = listOf(
        Key.char("`", "~", popup = "~`"),
        Key.char("1", "!", popup = "¹½¼"),
        Key.char("2", "@", popup = "²"),
        Key.char("3", "#", popup = "³¾"),
        Key.char("4", "$", popup = "€£¥₹₽"),
        Key.char("5", "%", popup = "‰"),
        Key.char("6", "^", popup = "°"),
        Key.char("7", "&", popup = "§"),
        Key.char("8", "*", popup = "•×★"),
        Key.char("9", "(", popup = "[{<"),
        Key.char("0", ")", popup = "]}>"),
        Key.char("-", "_", popup = "–—·"),
        Key.char("=", "+", popup = "±≠≈"),
        Key.fn("⌫", KeyType.BACKSPACE, KeyEvent.KEYCODE_DEL, weight = 1.8f, repeatable = true)
    )

    /**
     * Bottom row. The Win key has been replaced with a globe — long-press
     * the space bar to use the trackpad, tap the globe to cycle locales.
     */
    fun controlRow(): List<Key> = listOf(
        Key.fn("Ctrl", KeyType.CTRL, sticky = true, weight = 1.3f),
        Key.fn("🌐",  KeyType.LANGUAGE_SWITCH, weight = 1.1f),
        Key.fn("Alt",  KeyType.ALT,  sticky = true, weight = 1.1f),
        Key.fn("123",  KeyType.SYMBOL_SWITCH, weight = 1.2f),
        Key.fn("space", KeyType.SPACE, KeyEvent.KEYCODE_SPACE, weight = 5.5f),
        Key.fn("◀",   KeyType.ARROW_LEFT,  KeyEvent.KEYCODE_DPAD_LEFT,  repeatable = true),
        Key.fn("▲",   KeyType.ARROW_UP,    KeyEvent.KEYCODE_DPAD_UP,    repeatable = true),
        Key.fn("▼",   KeyType.ARROW_DOWN,  KeyEvent.KEYCODE_DPAD_DOWN,  repeatable = true),
        Key.fn("▶",   KeyType.ARROW_RIGHT, KeyEvent.KEYCODE_DPAD_RIGHT, repeatable = true),
        Key.fn("Alt",  KeyType.ALT,  sticky = true, weight = 1.1f),
        Key.fn("Ctrl", KeyType.CTRL, sticky = true, weight = 1.3f)
    )

    fun symbols(): KeyboardLayout = KeyboardLayout(
        id = "sym",
        displayName = "Symbols",
        rows = listOf(
            listOf(
                Key.char("1"), Key.char("2"), Key.char("3"), Key.char("4"), Key.char("5"),
                Key.char("6"), Key.char("7"), Key.char("8"), Key.char("9"), Key.char("0")
            ),
            listOf(
                Key.char("@"), Key.char("#"), Key.char("$"), Key.char("_"), Key.char("&"),
                Key.char("-"), Key.char("+"), Key.char("("), Key.char(")"), Key.char("/")
            ),
            listOf(
                Key.fn("=\\<", KeyType.LAYOUT_SWITCH, weight = 1.5f),
                Key.char("*"), Key.char("\""), Key.char("'"), Key.char(":"),
                Key.char(";"), Key.char("!"), Key.char("?"),
                Key.fn("⌫", KeyType.BACKSPACE, KeyEvent.KEYCODE_DEL, weight = 1.5f, repeatable = true)
            ),
            listOf(
                Key.fn("ABC", KeyType.SYMBOL_SWITCH, weight = 1.5f),
                Key.char(","),
                Key.fn("space", KeyType.SPACE, KeyEvent.KEYCODE_SPACE, weight = 5f),
                Key.char("."),
                Key.fn("⏎", KeyType.ENTER, KeyEvent.KEYCODE_ENTER, weight = 1.5f)
            )
        ),
        mode = LayoutMode.SYMBOLS
    )

    fun symbolsShift(): KeyboardLayout = KeyboardLayout(
        id = "sym_shift",
        displayName = "Symbols 2",
        rows = listOf(
            listOf(
                Key.char("~"), Key.char("`"), Key.char("|"), Key.char("•"), Key.char("°"),
                Key.char("¶"), Key.char("§"), Key.char("©"), Key.char("®"), Key.char("™")
            ),
            listOf(
                Key.char("€"), Key.char("£"), Key.char("¥"), Key.char("¢"), Key.char("^"),
                Key.char("="), Key.char("÷"), Key.char("×"), Key.char("±"), Key.char("¬")
            ),
            listOf(
                Key.fn("123", KeyType.LAYOUT_SWITCH, weight = 1.5f),
                Key.char("<"), Key.char(">"), Key.char("{"), Key.char("}"),
                Key.char("["), Key.char("]"), Key.char("\\"),
                Key.fn("⌫", KeyType.BACKSPACE, KeyEvent.KEYCODE_DEL, weight = 1.5f, repeatable = true)
            ),
            listOf(
                Key.fn("ABC", KeyType.SYMBOL_SWITCH, weight = 1.5f),
                Key.char("?"),
                Key.fn("space", KeyType.SPACE, KeyEvent.KEYCODE_SPACE, weight = 5f),
                Key.char("!"),
                Key.fn("⏎", KeyType.ENTER, KeyEvent.KEYCODE_ENTER, weight = 1.5f)
            )
        ),
        mode = LayoutMode.SYMBOLS_SHIFT
    )

    /**
     * Assemble a complete main layout from the shared Fn / number / control
     * rows plus the three locale-specific letter rows.
     */
    fun mainLayout(
        id: String,
        displayName: String,
        topLetters: List<Key>,
        homeLetters: List<Key>,
        bottomLetters: List<Key>
    ): KeyboardLayout = KeyboardLayout(
        id = id,
        displayName = displayName,
        rows = listOf(
            fnRow(),
            numberRow(),
            topLetters,
            homeLetters,
            bottomLetters,
            controlRow()
        ),
        mode = LayoutMode.MAIN
    )
}
