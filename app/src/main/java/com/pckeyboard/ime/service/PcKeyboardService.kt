package com.pckeyboard.ime.service

import android.content.res.Configuration
import android.inputmethodservice.InputMethodService
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.ExtractedTextRequest
import com.pckeyboard.ime.layout.LayoutRegistry
import com.pckeyboard.ime.layout.LayoutSelector
import com.pckeyboard.ime.model.Key
import com.pckeyboard.ime.model.KeyType
import com.pckeyboard.ime.model.KeyboardLayout
import com.pckeyboard.ime.model.LayoutMode
import com.pckeyboard.ime.model.ModifierState
import com.pckeyboard.ime.theme.ThemeRepository
import com.pckeyboard.ime.view.KeyboardView

/**
 * The IME service.
 *
 * - Builds the keyboard view from the active LayoutPack + selected theme.
 * - Forwards key events to the input connection, expanding modifier state
 *   into Android KeyEvent meta flags so apps see real Ctrl/Alt combos.
 * - Reacts to configuration changes (fold / unfold, rotation) to swap
 *   between compact and full PC layouts.
 */
class PcKeyboardService : InputMethodService(), KeyboardView.Listener {

    private lateinit var themeRepo: ThemeRepository
    private var currentLayoutId: String = "en_US"
    private var currentMode: LayoutMode = LayoutMode.MAIN
    private var keyboardView: KeyboardView? = null

    override fun onCreate() {
        super.onCreate()
        themeRepo = ThemeRepository(this)
    }

    override fun onCreateInputView(): View {
        val view = KeyboardView(this)
        view.listener = this
        keyboardView = view
        bindCurrentLayout()
        return view
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        // Refresh theme + sizing prefs on each session — user may have changed
        // them since the IME was last shown.
        keyboardView?.updateTheme(themeRepo.getSelectedTheme())
        bindCurrentLayout()
        keyboardView?.applySizingPrefs()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        bindCurrentLayout()
    }

    private fun bindCurrentLayout() {
        val view = keyboardView ?: return
        val pack = LayoutRegistry.get(currentLayoutId)
        val base: KeyboardLayout = when (currentMode) {
            LayoutMode.SYMBOLS, LayoutMode.SYMBOLS_SHIFT -> pack.symbols
            else -> pack.main
        }
        val widthDp = (resources.displayMetrics.widthPixels / resources.displayMetrics.density).toInt()
        val variant = LayoutSelector.pick(widthDp)
        val finalLayout = LayoutSelector.apply(base, variant)
        view.bind(finalLayout, themeRepo.getSelectedTheme())
    }

    override fun onKey(key: Key, modifiers: ModifierState) {
        if (currentInputConnection == null) return
        when (key.type) {
            KeyType.SPACE -> commitChar(' ')
            KeyType.ENTER -> sendKey(KeyEvent.KEYCODE_ENTER, modifiers)
            KeyType.BACKSPACE -> sendKey(KeyEvent.KEYCODE_DEL, modifiers)
            KeyType.DELETE -> sendKey(KeyEvent.KEYCODE_FORWARD_DEL, modifiers)
            KeyType.TAB -> sendKey(KeyEvent.KEYCODE_TAB, modifiers)
            KeyType.ESC -> sendKey(KeyEvent.KEYCODE_ESCAPE, modifiers)
            KeyType.ARROW_LEFT -> sendKey(KeyEvent.KEYCODE_DPAD_LEFT, modifiers)
            KeyType.ARROW_RIGHT -> sendKey(KeyEvent.KEYCODE_DPAD_RIGHT, modifiers)
            KeyType.ARROW_UP -> sendKey(KeyEvent.KEYCODE_DPAD_UP, modifiers)
            KeyType.ARROW_DOWN -> sendKey(KeyEvent.KEYCODE_DPAD_DOWN, modifiers)
            KeyType.HOME -> sendKey(KeyEvent.KEYCODE_MOVE_HOME, modifiers)
            KeyType.END -> sendKey(KeyEvent.KEYCODE_MOVE_END, modifiers)
            KeyType.PAGE_UP -> sendKey(KeyEvent.KEYCODE_PAGE_UP, modifiers)
            KeyType.PAGE_DOWN -> sendKey(KeyEvent.KEYCODE_PAGE_DOWN, modifiers)
            KeyType.INSERT -> sendKey(KeyEvent.KEYCODE_INSERT, modifiers)
            KeyType.FN -> if (key.keyCode != 0) sendKey(key.keyCode, modifiers)
            KeyType.SYMBOL_SWITCH -> switchSymbols()
            KeyType.LAYOUT_SWITCH -> switchSymbolsShift()
            KeyType.LANGUAGE_SWITCH -> switchToNextInputMethod(false)
            KeyType.HIDE -> requestHideSelf(0)
            KeyType.LETTER, KeyType.CHAR -> {
                val c = pickChar(key, modifiers)
                if (modifiers.shouldSendAsKeyEvent()) {
                    val code = androidKeyCodeForChar(c)
                    if (code != 0) sendKey(code, modifiers) else commitChar(c)
                } else {
                    commitChar(c)
                }
            }
            else -> {}
        }
    }

    private fun pickChar(key: Key, modifiers: ModifierState): Char = when {
        key.type == KeyType.LETTER && modifiers.isShiftActive() -> key.label.uppercase()[0]
        key.type == KeyType.LETTER -> key.label[0]
        key.type == KeyType.CHAR && modifiers.isShiftActive() && key.shiftLabel != null -> key.shiftLabel[0]
        else -> key.label[0]
    }

    private fun commitChar(c: Char) {
        currentInputConnection?.commitText(c.toString(), 1)
    }

    private fun sendKey(keyCode: Int, modifiers: ModifierState) {
        val ic = currentInputConnection ?: return
        val meta = modifiers.toMetaState()
        val now = System.currentTimeMillis()
        ic.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0, meta))
        ic.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0, meta))
    }

    private fun switchSymbols() {
        currentMode = if (currentMode == LayoutMode.MAIN) LayoutMode.SYMBOLS else LayoutMode.MAIN
        bindCurrentLayout()
    }

    private fun switchSymbolsShift() {
        currentMode = if (currentMode == LayoutMode.SYMBOLS) LayoutMode.SYMBOLS_SHIFT else LayoutMode.SYMBOLS
        bindCurrentLayout()
    }

    /**
     * Trackpad cursor motion. Uses [InputConnection.setSelection] so we
     * never inject DPAD KeyEvents — those can move focus to a non-editable
     * sibling view (e.g. a chat-app Send button) which then fires
     * onFinishInput and tears down the IME, looking like the keyboard
     * vanished mid-gesture.
     */
    override fun onCursorMove(dx: Int, dy: Int) {
        val ic = currentInputConnection ?: return
        val req = ExtractedTextRequest().apply {
            hintMaxChars = 4096
            hintMaxLines = 256
        }
        val ext = ic.getExtractedText(req, 0) ?: return
        val text = ext.text?.toString() ?: return
        val origin = ext.selectionStart
        if (origin < 0) return

        var newPos = (origin + dx).coerceIn(0, text.length)

        if (dy != 0 && text.isNotEmpty()) {
            val currentLineStart =
                text.lastIndexOf('\n', (newPos - 1).coerceAtLeast(0)) + 1
            val column = newPos - currentLineStart

            var lineStart = currentLineStart
            if (dy > 0) {
                for (i in 0 until dy) {
                    val nl = text.indexOf('\n', lineStart)
                    if (nl == -1) { lineStart = text.length; break }
                    lineStart = nl + 1
                }
            } else {
                for (i in 0 until -dy) {
                    if (lineStart == 0) break
                    val prev = text.lastIndexOf('\n', lineStart - 2)
                    lineStart = if (prev == -1) 0 else prev + 1
                }
            }
            val nextNl = text.indexOf('\n', lineStart)
            val lineLen = if (nextNl == -1) text.length - lineStart else nextNl - lineStart
            newPos = lineStart + column.coerceAtMost(lineLen)
        }

        if (newPos != origin) ic.setSelection(newPos, newPos)
    }

    private fun androidKeyCodeForChar(c: Char): Int {
        if (c in 'a'..'z') return KeyEvent.KEYCODE_A + (c - 'a')
        if (c in 'A'..'Z') return KeyEvent.KEYCODE_A + (c - 'A')
        if (c in '0'..'9') return KeyEvent.KEYCODE_0 + (c - '0')
        return when (c) {
            ' ' -> KeyEvent.KEYCODE_SPACE
            '\n' -> KeyEvent.KEYCODE_ENTER
            '\t' -> KeyEvent.KEYCODE_TAB
            else -> 0
        }
    }
}
