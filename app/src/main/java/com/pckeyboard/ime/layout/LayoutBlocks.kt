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
 *  - Row 5 (Control):      Ctrl 🌐 Alt Space 123 ◀ ▼ ▶
 *
 * Every row totals weight 14 so a "1-unit" key is the same width in any
 * row. The control row's ▼ at cumulative weight 12.5 sits directly under
 * the bottom letter row's ▲ (also at 12.5), forming a real inverted-T
 * arrow cluster — same shape Hacker's Keyboard uses. There is no right
 * Ctrl: dropping it is what keeps the row at 14 instead of 15.
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
     * Bottom-bottom row. Total weight = 14 so columns line up with every
     * other row; the centre of ▼ at 12.5 / 14 sits directly under the
     * bottom letter row's ▲ at the same column, forming a real
     * inverted-T arrow cluster (same shape Hacker's Keyboard uses).
     *
     * No right Ctrl on this row — adding one would push the total to 15
     * and shift ▼ out from under ▲. Left Ctrl alone is enough to put a
     * Ctrl below the left Shift, which is what was requested.
     */
    fun controlRow(): List<Key> = listOf(
        // Win is intentionally absent — its weight is redistributed
        // equally to Ctrl, the language switcher and Alt so the cluster
        // grows into the vacated space instead of leaving a hole.
        Key.fn("Ctrl",  KeyType.CTRL, sticky = true, weight = 4f / 3f),
        Key.fn("🌐",   KeyType.LANGUAGE_SWITCH, weight = 4f / 3f),
        Key.fn("Alt",   KeyType.ALT,  sticky = true, weight = 4f / 3f),
        Key.fn("space", KeyType.SPACE, KeyEvent.KEYCODE_SPACE, weight = 6.0f),
        Key.fn("123",   KeyType.SYMBOL_SWITCH, weight = 1.0f),
        Key.fn("◀",    KeyType.ARROW_LEFT,  KeyEvent.KEYCODE_DPAD_LEFT,  repeatable = true),
        Key.fn("▼",    KeyType.ARROW_DOWN,  KeyEvent.KEYCODE_DPAD_DOWN,  repeatable = true),
        Key.fn("▶",    KeyType.ARROW_RIGHT, KeyEvent.KEYCODE_DPAD_RIGHT, repeatable = true)
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
