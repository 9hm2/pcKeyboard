package com.pckeyboard.ime.layout

import android.view.KeyEvent
import com.pckeyboard.ime.model.Key
import com.pckeyboard.ime.model.KeyType
import com.pckeyboard.ime.model.KeyboardLayout
import com.pckeyboard.ime.model.LayoutMode

/**
 * Rows + symbol pages that are shared across every locale.
 *
 * Structure follows the AOSP "Hacker's Keyboard" kbd_full.xml conventions
 * for a desktop-faithful 6-row layout:
 *  - Row 0 (Fn extension): Esc + F1-F11 + Home + End
 *  - Row 1 (Numbers):      ` 1-0 - = ⌫
 *  - Row 2 (Top letters):  Tab + q-p + [ ] \             (locale-specific)
 *  - Row 3 (Home letters): Caps + a-l + ... + Enter      (locale-specific)
 *  - Row 4 (Bottom):       ⇧ + < + z-m + , . / + ↑ + ⇧  (locale-specific)
 *  - Row 5 (Control):      Ctrl 🌐 Win Alt 123 Space ◀ ▼ ▶ Ctrl
 *
 * Caps Lock on the home-row left and Ctrl on the bottom-bottom row matches
 * the physical layout of every consumer PC keyboard. The bottom-row
 * up-arrow + control-row left/down/right form the inverted-T arrow cluster
 * of a real desktop keyboard.
 */
internal object LayoutBlocks {

    fun fnRow(): List<Key> = listOf(
        Key.fn("Esc",  KeyType.ESC, KeyEvent.KEYCODE_ESCAPE),
        Key.fn("F1",   KeyType.FN, KeyEvent.KEYCODE_F1),
        Key.fn("F2",   KeyType.FN, KeyEvent.KEYCODE_F2),
        Key.fn("F3",   KeyType.FN, KeyEvent.KEYCODE_F3),
        Key.fn("F4",   KeyType.FN, KeyEvent.KEYCODE_F4),
        Key.fn("F5",   KeyType.FN, KeyEvent.KEYCODE_F5),
        Key.fn("F6",   KeyType.FN, KeyEvent.KEYCODE_F6),
        Key.fn("F7",   KeyType.FN, KeyEvent.KEYCODE_F7),
        Key.fn("F8",   KeyType.FN, KeyEvent.KEYCODE_F8),
        Key.fn("F9",   KeyType.FN, KeyEvent.KEYCODE_F9),
        Key.fn("F10",  KeyType.FN, KeyEvent.KEYCODE_F10),
        Key.fn("F11",  KeyType.FN, KeyEvent.KEYCODE_F11),
        Key.fn("Home", KeyType.HOME),
        Key.fn("End",  KeyType.END)
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
     * Bottom-bottom row. Total weight = 14 so the columns line up across
     * the whole keyboard; the centre of ▼ sits at 89.3 % of the row width,
     * which is exactly the column of ▲ at the right end of the bottom
     * letter row, forming a real inverted-T arrow cluster.
     *
     * Ctrl sits at both ends — directly beneath the left and right Shift
     * keys of the bottom letter row — mirroring a real desktop keyboard.
     */
    fun controlRow(): List<Key> = listOf(
        Key.fn("Ctrl",  KeyType.CTRL, sticky = true, weight = 1.0f),
        Key.fn("🌐",   KeyType.LANGUAGE_SWITCH, weight = 1.0f),
        Key.fn("Win",   KeyType.META, sticky = true, weight = 1.0f),
        Key.fn("Alt",   KeyType.ALT,  sticky = true, weight = 1.0f),
        Key.fn("123",   KeyType.SYMBOL_SWITCH, weight = 1.0f),
        Key.fn("space", KeyType.SPACE, KeyEvent.KEYCODE_SPACE, weight = 6.0f),
        Key.fn("◀",    KeyType.ARROW_LEFT,  KeyEvent.KEYCODE_DPAD_LEFT,  repeatable = true),
        Key.fn("▼",    KeyType.ARROW_DOWN,  KeyEvent.KEYCODE_DPAD_DOWN,  repeatable = true),
        Key.fn("▶",    KeyType.ARROW_RIGHT, KeyEvent.KEYCODE_DPAD_RIGHT, repeatable = true),
        Key.fn("Ctrl",  KeyType.CTRL, sticky = true, weight = 1.0f)
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
     * Assemble a complete main layout from the shared Fn / control rows
     * plus the four locale-specific rows (number, top, home, bottom). The
     * number row is locale-specific because the row's shift glyphs and the
     * far-right keys differ per ISO keyboard (HU has 0 then ö ü ó, DE has
     * ß ', ES has ' ¡, etc.).
     */
    fun mainLayout(
        id: String,
        displayName: String,
        numberRow: List<Key>,
        topLetters: List<Key>,
        homeLetters: List<Key>,
        bottomLetters: List<Key>
    ): KeyboardLayout = KeyboardLayout(
        id = id,
        displayName = displayName,
        rows = listOf(
            fnRow(),
            numberRow,
            topLetters,
            homeLetters,
            bottomLetters,
            controlRow()
        ),
        mode = LayoutMode.MAIN
    )
}
