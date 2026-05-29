package com.pckeyboard.ime.service

import android.content.ClipboardManager
import android.content.Intent
import android.content.res.Configuration
import android.inputmethodservice.InputMethodService
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputMethodSubtype
import com.pckeyboard.ime.clipboard.ClipboardEditorActivity
import com.pckeyboard.ime.clipboard.ClipboardEditorBridge
import com.pckeyboard.ime.clipboard.ClipboardHistory
import com.pckeyboard.ime.layout.LayoutRegistry
import com.pckeyboard.ime.layout.LayoutSelector
import com.pckeyboard.ime.model.Key
import com.pckeyboard.ime.model.KeyType
import com.pckeyboard.ime.model.KeyboardLayout
import com.pckeyboard.ime.model.LayoutMode
import com.pckeyboard.ime.model.ModifierState
import com.pckeyboard.ime.settings.KeyboardPrefs
import com.pckeyboard.ime.settings.SettingsActivity
import com.pckeyboard.ime.theme.ThemeRepository
import com.pckeyboard.ime.updater.UpdateScheduler
import com.pckeyboard.ime.view.KeyboardView
import com.pckeyboard.ime.view.MenuAction

/** Compact UI label for a language pack id ("en_US" → "EN"). Shared with
 *  KeyboardView.showActionMenu so the locale row reads naturally. */
fun languageCode(id: String): String = when (id) {
    "en_US" -> "EN"
    "hu_HU" -> "HU"
    "de_DE" -> "DE"
    "es_ES" -> "ES"
    else -> "•"
}

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
    private lateinit var kbPrefs: KeyboardPrefs
    private var currentLayoutId: String = "en_US"
    private var currentMode: LayoutMode = LayoutMode.MAIN
    private var keyboardView: KeyboardView? = null

    private val clipListener = ClipboardManager.OnPrimaryClipChangedListener {
        val cm = getSystemService(CLIPBOARD_SERVICE) as? ClipboardManager ?: return@OnPrimaryClipChangedListener
        val clip = cm.primaryClip ?: return@OnPrimaryClipChangedListener
        if (clip.itemCount == 0) return@OnPrimaryClipChangedListener
        val text = clip.getItemAt(0)?.coerceToText(this)?.toString() ?: return@OnPrimaryClipChangedListener
        if (text.isNotBlank()) {
            ClipboardHistory(this).add(text)
            keyboardView?.refreshClipboard()
        }
    }

    override fun onCreate() {
        super.onCreate()
        themeRepo = ThemeRepository(this)
        kbPrefs = KeyboardPrefs(this)
        currentLayoutId = kbPrefs.currentLanguage
        UpdateScheduler.schedule(this)
        (getSystemService(CLIPBOARD_SERVICE) as? ClipboardManager)
            ?.addPrimaryClipChangedListener(clipListener)
    }

    override fun onDestroy() {
        (getSystemService(CLIPBOARD_SERVICE) as? ClipboardManager)
            ?.removePrimaryClipChangedListener(clipListener)
        super.onDestroy()
    }

    override fun onCurrentInputMethodSubtypeChanged(newSubtype: InputMethodSubtype?) {
        super.onCurrentInputMethodSubtypeChanged(newSubtype)
        // If the system did switch our subtype (e.g. the user picked one
        // from the IME picker dialog), honour it; otherwise our internal
        // cycle stays authoritative.
        val resolved = LayoutRegistry.resolveLocale(newSubtype?.locale)
        if (resolved != currentLayoutId) {
            currentLayoutId = resolved
            kbPrefs.currentLanguage = resolved
            currentMode = LayoutMode.MAIN
            bindCurrentLayout()
        }
    }

    /**
     * Globe tap cycle. Walks through the languages the user has enabled in
     * Settings *and* the emoji-picker state, so the typical "one language
     * + emoji" setup degenerates to a clean tap-to-toggle between language
     * and emoji — exactly what the user expects. With multiple languages
     * enabled it walks them in registry order, then emoji, then back to
     * the first language.
     */
    private fun cycleLanguage() {
        val view = keyboardView ?: return
        val enabled = kbPrefs.enabledLanguages
        val packs = LayoutRegistry.available.filter { it.id in enabled }
        if (packs.isEmpty()) return

        val states: List<String> = packs.map { it.id } + EMOJI_STATE
        val current = if (view.isEmojiOpen()) EMOJI_STATE else currentLayoutId
        val idx = states.indexOf(current).let { if (it < 0) states.size - 1 else it }
        val next = states[(idx + 1) % states.size]

        if (next == EMOJI_STATE) {
            view.showEmojiPicker()
        } else {
            view.hideEmojiPicker()
            currentLayoutId = next
            kbPrefs.currentLanguage = next
            currentMode = LayoutMode.MAIN
            bindCurrentLayout()
        }
    }

    companion object {
        private const val EMOJI_STATE = "__emoji__"
        /** How many lines Page Up / Page Down skips. */
        private const val PAGE_LINES = 10
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
        // If the user finished the clipboard editor with Send, commit the
        // edited text and persist the replacement into the history.
        ClipboardEditorBridge.consume()?.let { r ->
            if (r.edited.isNotEmpty()) currentInputConnection?.commitText(r.edited, 1)
            ClipboardHistory(this).replace(r.original, r.edited)
            keyboardView?.refreshClipboard()
        }
    }

    /**
     * When the IME is dismissed (focus lost, app backgrounded, etc.) reset
     * any transient mode — symbols/emoji/clipboard — so the next time it
     * opens we start clean on the main letters layout.
     */
    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        currentMode = LayoutMode.MAIN
        keyboardView?.hideEmojiPicker()
        keyboardView?.hideClipboard()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        bindCurrentLayout()
    }

    /**
     * Never enter the IME's fullscreen "extract" mode in landscape. The
     * default behaviour blanks the whole app with a single-line text editor
     * above the keyboard, so the user only sees the input field — chat
     * history, list rows, etc. all disappear. Returning false keeps the
     * focused app visible above our keyboard exactly like portrait does.
     */
    override fun onEvaluateFullscreenMode(): Boolean = false

    private fun bindCurrentLayout() {
        val view = keyboardView ?: return
        view.currentLanguageId = currentLayoutId
        val pack = LayoutRegistry.get(currentLayoutId)
        val base: KeyboardLayout = when (currentMode) {
            LayoutMode.SYMBOLS -> pack.symbols
            LayoutMode.SYMBOLS_SHIFT -> pack.symbolsShift
            else -> pack.main
        }
        val widthDp = (resources.displayMetrics.widthPixels / resources.displayMetrics.density).toInt()
        val variant = LayoutSelector.pick(widthDp)
        val finalLayout = withLanguageLabel(
            LayoutSelector.apply(base, variant),
            languageLabelFor(currentLayoutId)
        )
        view.bind(finalLayout, themeRepo.getSelectedTheme())
    }

    /** Rewrites the LANGUAGE_SWITCH key's label so the globe shows the
     *  current locale code (EN / HU / DE / ES). */
    private fun withLanguageLabel(layout: KeyboardLayout, label: String): KeyboardLayout {
        val newRows = layout.rows.map { row ->
            row.map { key ->
                if (key.type == KeyType.LANGUAGE_SWITCH) key.copy(label = label) else key
            }
        }
        return layout.copy(rows = newRows)
    }

    private fun languageLabelFor(id: String): String = languageCode(id)

    override fun onKey(key: Key, modifiers: ModifierState) {
        if (currentInputConnection == null) return
        when (key.type) {
            KeyType.SPACE -> commitChar(' ')
            KeyType.ENTER -> handleEnter(modifiers)
            KeyType.BACKSPACE -> sendKey(KeyEvent.KEYCODE_DEL, modifiers)
            KeyType.DELETE -> sendKey(KeyEvent.KEYCODE_FORWARD_DEL, modifiers)
            KeyType.TAB -> sendKey(KeyEvent.KEYCODE_TAB, modifiers)
            KeyType.ESC -> sendKey(KeyEvent.KEYCODE_ESCAPE, modifiers)
            // Arrow keys move the cursor via InputConnection.setSelection
            // rather than DPAD KeyEvents, otherwise the system / launcher /
            // foldable window manager may steal the event for navigation
            // (selecting the floating-window grab handle, moving focus to
            // a sibling Send button, etc.).
            KeyType.ARROW_LEFT -> moveCursor(-1, 0, modifiers)
            KeyType.ARROW_RIGHT -> moveCursor(1, 0, modifiers)
            KeyType.ARROW_UP -> moveCursor(0, -1, modifiers)
            KeyType.ARROW_DOWN -> moveCursor(0, 1, modifiers)
            KeyType.HOME -> moveCursor(Int.MIN_VALUE, 0, modifiers)
            KeyType.END -> moveCursor(Int.MAX_VALUE, 0, modifiers)
            KeyType.PAGE_UP -> moveCursor(0, -PAGE_LINES, modifiers)
            KeyType.PAGE_DOWN -> moveCursor(0, PAGE_LINES, modifiers)
            KeyType.INSERT -> { /* no-op — InputConnection has no insert mode */ }
            KeyType.FN -> if (key.keyCode != 0) sendKey(key.keyCode, modifiers)
            KeyType.SYMBOL_SWITCH -> switchSymbols()
            KeyType.LAYOUT_SWITCH -> switchSymbolsShift()
            KeyType.LANGUAGE_SWITCH -> cycleLanguage()
            KeyType.HIDE -> requestHideSelf(0)
            KeyType.LETTER, KeyType.CHAR -> {
                val c = pickChar(key, modifiers)
                val altAsAltGr = modifiers.isAltActive() && key.altLabel != null
                when {
                    altAsAltGr -> commitChar(c)        // Alt produces the layout's AltGr char
                    modifiers.shouldSendAsKeyEvent() -> {
                        val code = androidKeyCodeForChar(c)
                        if (code != 0) sendKey(code, modifiers) else commitChar(c)
                    }
                    else -> commitChar(c)
                }
            }
            else -> {}
        }
    }

    private fun pickChar(key: Key, modifiers: ModifierState): Char {
        // Alt acts as AltGr on char / letter keys that declare an altLabel
        // (the locale-specific AltGr glyph from the layout). Shift on top
        // capitalises the alt char where applicable.
        if (modifiers.isAltActive() && key.altLabel != null &&
            (key.type == KeyType.LETTER || key.type == KeyType.CHAR)) {
            val ch = key.altLabel[0]
            return if (modifiers.isShiftActive()) ch.uppercaseChar() else ch
        }
        return when {
            key.type == KeyType.LETTER && modifiers.isShiftActive() -> key.label.uppercase()[0]
            key.type == KeyType.LETTER -> key.label[0]
            key.type == KeyType.CHAR && modifiers.isShiftActive() && key.shiftLabel != null -> key.shiftLabel[0]
            else -> key.label[0]
        }
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

    /** Trackpad cursor motion — no modifier semantics. */
    override fun onCursorMove(dx: Int, dy: Int) {
        moveCursor(dx, dy, null)
    }

    override fun onMenuAction(action: MenuAction) {
        when (action) {
            is MenuAction.SwitchLanguage -> {
                currentLayoutId = action.packId
                kbPrefs.currentLanguage = action.packId
                currentMode = LayoutMode.MAIN
                keyboardView?.hideEmojiPicker()
                keyboardView?.hideClipboard()
                bindCurrentLayout()
            }
            MenuAction.OpenEmoji -> {
                keyboardView?.showEmojiPicker()
            }
            MenuAction.OpenClipboard -> {
                // Snapshot the system clip into history so it shows up as
                // the freshest card even if the listener missed the copy.
                val cm = getSystemService(CLIPBOARD_SERVICE) as? ClipboardManager
                cm?.primaryClip?.takeIf { it.itemCount > 0 }
                    ?.getItemAt(0)?.coerceToText(this)?.toString()
                    ?.takeIf { it.isNotBlank() }
                    ?.let { ClipboardHistory(this).add(it) }
                keyboardView?.showClipboard()
            }
            MenuAction.OpenSettings -> {
                val intent = Intent(this, SettingsActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
            }
        }
    }

    override fun onClipboardEdit(text: String) {
        val intent = Intent(this, ClipboardEditorActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra(ClipboardEditorActivity.EXTRA_TEXT, text)
        startActivity(intent)
    }

    /** Multi-codepoint commits (emoji, etc.) that don't fit through a single
     *  KeyEvent. Goes through commitText directly. */
    override fun onText(text: String) {
        if (text.isEmpty()) return
        currentInputConnection?.commitText(text, 1)
    }

    /**
     * Move the caret by [dx] characters and [dy] lines using
     * [InputConnection.setSelection] / [InputConnection.performEditorAction]
     * instead of DPAD KeyEvents. This keeps focus inside the editor — the
     * system / launcher / foldable window manager can't steal the event to
     * select the floating-window grab handle or jump to a sibling view.
     *
     * Modifier semantics:
     *  - Ctrl: word-boundary jump (only for horizontal motion).
     *  - Shift: extend selection instead of moving the caret.
     */
    private fun moveCursor(dx: Int, dy: Int, modifiers: ModifierState?) {
        val ic = currentInputConnection ?: return
        val req = ExtractedTextRequest().apply {
            hintMaxChars = 8192
            hintMaxLines = 512
        }
        val ext = ic.getExtractedText(req, 0) ?: return
        val text = ext.text?.toString() ?: return
        val anchor = ext.selectionStart
        val active = ext.selectionEnd
        if (anchor < 0 || active < 0) return

        val isCtrl = modifiers?.isCtrlActive() == true
        val isShift = modifiers?.isShiftActive() == true

        var newActive = active

        // Horizontal motion.
        when {
            dx == Int.MIN_VALUE -> {
                // Home: start of current line.
                newActive = text.lastIndexOf('\n', (newActive - 1).coerceAtLeast(0)) + 1
            }
            dx == Int.MAX_VALUE -> {
                // End: end of current line.
                val nl = text.indexOf('\n', newActive)
                newActive = if (nl == -1) text.length else nl
            }
            dx != 0 && isCtrl -> {
                newActive = wordJump(text, newActive, dx)
            }
            dx != 0 -> {
                newActive = (newActive + dx).coerceIn(0, text.length)
            }
        }

        // Vertical motion.
        if (dy != 0 && text.isNotEmpty()) {
            val currentLineStart =
                text.lastIndexOf('\n', (newActive - 1).coerceAtLeast(0)) + 1
            val column = newActive - currentLineStart
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
            newActive = lineStart + column.coerceAtMost(lineLen)
        }

        val newAnchor = if (isShift) anchor else newActive
        if (newActive != active || newAnchor != anchor) {
            ic.setSelection(newAnchor, newActive)
        }
    }

    private fun wordJump(text: String, from: Int, direction: Int): Int {
        if (text.isEmpty()) return from
        return if (direction > 0) {
            var i = from
            while (i < text.length && !text[i].isLetterOrDigit()) i++
            while (i < text.length && text[i].isLetterOrDigit()) i++
            i
        } else {
            var i = from - 1
            while (i >= 0 && !text[i].isLetterOrDigit()) i--
            while (i >= 0 && text[i].isLetterOrDigit()) i--
            i + 1
        }
    }

    /**
     * Enter semantics. Same reason as the arrow keys: a raw KEYCODE_ENTER
     * can be intercepted by the system as DPAD_CENTER and steal focus.
     * Use [InputConnection.performEditorAction] for the action the editor
     * declared (Send / Done / Search / Next / ...), or commit a literal
     * newline if the editor wants Enter to be a newline.
     */
    private fun handleEnter(modifiers: ModifierState) {
        val ic = currentInputConnection ?: return
        // Any modifier on Enter means "newline" rather than "submit form"
        // (Shift+Enter is the universal "newline in a chat field" combo).
        if (modifiers.isShiftActive() || modifiers.shouldSendAsKeyEvent()) {
            ic.commitText("\n", 1)
            return
        }
        val info = currentInputEditorInfo
        if (info == null) {
            ic.commitText("\n", 1)
            return
        }
        val opts = info.imeOptions
        val action = opts and EditorInfo.IME_MASK_ACTION
        val noEnterAction = opts and EditorInfo.IME_FLAG_NO_ENTER_ACTION != 0
        when {
            noEnterAction -> ic.commitText("\n", 1)
            action != EditorInfo.IME_ACTION_NONE &&
                action != EditorInfo.IME_ACTION_UNSPECIFIED ->
                ic.performEditorAction(action)
            else -> ic.commitText("\n", 1)
        }
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
