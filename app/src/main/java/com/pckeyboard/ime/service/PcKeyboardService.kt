package com.pckeyboard.ime.service

import android.content.ClipboardManager
import android.content.Intent
import android.content.res.Configuration
import android.inputmethodservice.InputMethodService
import android.inputmethodservice.InputMethodService.Insets
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
import com.pckeyboard.ime.theme.KeyboardTheme
import com.pckeyboard.ime.theme.TerminalThemeBridge
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

    // When the keyboard is opened by a co-operating terminal (NeoTerm) it
    // advertises its colours through EditorInfo.extras; we derive a theme from
    // them and use it for the lifetime of that input session instead of the
    // user's persisted theme. Null = no terminal colours, use the saved theme.
    private var sessionTheme: KeyboardTheme? = null

    /** Theme to render with: the terminal-derived one if present, else the saved theme. */
    private fun activeTheme(): KeyboardTheme = sessionTheme ?: themeRepo.getSelectedTheme()

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
        // WorkManager initialises through credential-encrypted storage,
        // which is unreachable during Direct Boot (the window between
        // device boot and the first user unlock when the IME also has
        // to be usable). Skip the auto-update scheduling in that window
        // — it'll get scheduled on the next IME create after unlock.
        val um = getSystemService(USER_SERVICE) as? android.os.UserManager
        if (um == null ||
            android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.N ||
            um.isUserUnlocked
        ) {
            UpdateScheduler.schedule(this)
        }
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
        // Drop the IME window's own background so the see-through gap
        // we draw in side-split mode actually reveals the app behind us.
        // Doesn't affect non-split modes because the KeyboardView itself
        // is then opaque.
        window?.window?.setBackgroundDrawable(
            android.graphics.drawable.ColorDrawable(0)
        )
        bindCurrentLayout()
        return view
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        // Re-read the persisted language id on every session. The IME
        // may have first started during Direct Boot (lock screen), at
        // which point credential-encrypted storage was empty and we
        // defaulted to en_US — once the user unlocks, the real saved
        // language becomes readable and we pick it up here.
        val stored = kbPrefs.currentLanguage
        if (stored != currentLayoutId && LayoutRegistry.get(stored).id == stored) {
            currentLayoutId = stored
        }
        // If the host is a terminal that handed us its colours, derive a
        // matching theme for this session; otherwise fall back to the user's
        // saved theme. Recomputed on every session so it tracks the terminal's
        // current colour scheme.
        sessionTheme = TerminalThemeBridge.fromExtras(info?.extras)
        // Refresh theme + sizing prefs on each session — user may have changed
        // them since the IME was last shown.
        keyboardView?.updateTheme(activeTheme())
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
        // Drop any terminal-derived theme so the next (possibly non-terminal)
        // session starts from the user's saved theme.
        sessionTheme = null
        currentMode = LayoutMode.MAIN
        keyboardView?.hideEmojiPicker()
        keyboardView?.hideEmojiSearchHeader()
        keyboardView?.hideClipboard()
        keyboardView?.hideVoiceInput()
        // Drop any sticky / locked modifier state — Shift, Ctrl, Alt,
        // Caps Lock, etc. — so reopening the keyboard never lands the
        // user in some "still locked from last session" surprise.
        keyboardView?.resetModifiers()
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

    /**
     * When the user has enabled side-split, carve the middle gap out of
     * the IME's touchable region so taps in the gap fall through to the
     * app behind the keyboard. The visible / content insets stay at
     * their defaults so the app still resizes above the IME — the gap
     * is purely a touch-through pocket.
     */
    override fun onComputeInsets(outInsets: Insets) {
        super.onComputeInsets(outInsets)
        val view = keyboardView ?: return
        // The KeyboardView is intentionally taller than the visible
        // keyboard — KeyboardView.POPUP_ZONE_DP at the top is reserved
        // as a transparent area where long-press popups can draw. Tell
        // the system the IME's "content" starts only at the top of the
        // keyboard rows, so the app keeps fitting above the keys and
        // not above the empty zone above them.
        val popupZonePx = (resources.displayMetrics.density *
            com.pckeyboard.ime.view.KeyboardView.POPUP_ZONE_DP).toInt()
        outInsets.contentTopInsets = popupZonePx
        outInsets.visibleTopInsets = popupZonePx

        // Side-split mode: carve the middle gap out of the touchable
        // region so taps in it fall through to whatever app is behind.
        val gap = view.sideSplitGapBounds()
        if (gap != null) {
            outInsets.touchableInsets = Insets.TOUCHABLE_INSETS_REGION
            val region = outInsets.touchableRegion
            region.setEmpty()
            region.union(android.graphics.Rect(0, 0, gap.left, view.height))
            region.union(android.graphics.Rect(gap.right, 0, view.width, view.height))
            if (gap.top > 0) {
                region.union(android.graphics.Rect(0, 0, view.width, gap.top))
            }
        }
    }

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
        // If the user has flipped "show function row" in the globe menu,
        // keep the full PC layout even on narrow phones that would
        // otherwise drop the F-key row.
        val variant = if (KeyboardPrefs(this).showFunctionRow) com.pckeyboard.ime.layout.LayoutVariant.FULL
                      else LayoutSelector.pick(widthDp)
        val finalLayout = withRightOfSpaceAction(
            withLanguageLabel(
                LayoutSelector.apply(base, variant),
                languageLabelFor(currentLayoutId)
            ),
            kbPrefs.rightOfSpaceAction
        )
        view.bind(finalLayout, activeTheme())
    }

    /** Rewrites the control-row "123" SYMBOL_SWITCH key into whatever the
     *  user picked in Settings for the slot to the right of Space. */
    private fun withRightOfSpaceAction(
        layout: KeyboardLayout,
        action: String
    ): KeyboardLayout {
        if (action == KeyboardPrefs.RIGHT_OF_SPACE_SYMBOLS) return layout
        val newRows = layout.rows.map { row ->
            row.map { key ->
                if (key.type == KeyType.SYMBOL_SWITCH && key.label == "123") {
                    when (action) {
                        KeyboardPrefs.RIGHT_OF_SPACE_EMOJI ->
                            Key.fn("😀", KeyType.EMOJI, weight = key.widthWeight)
                        else -> key
                    }
                } else key
            }
        }
        return layout.copy(rows = newRows)
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
            // Arrow / Home / End / PgUp / PgDn normally move the cursor
            // via InputConnection.setSelection (so the system / launcher
            // / foldable window manager can't steal a DPAD event for
            // navigation: floating-window grab handle, sibling Send
            // button, etc.). Terminal emulators (Termux, ConnectBot,
            // etc.) declare TYPE_NULL because they have no Android-side
            // text-edit model — for those we hand the DPAD KeyEvent
            // through directly so the terminal can translate it into
            // its own escape sequence (ESC [ A, …).
            KeyType.ARROW_LEFT ->
                if (isTerminalLikeEditor()) sendKey(KeyEvent.KEYCODE_DPAD_LEFT, modifiers)
                else moveCursor(-1, 0, modifiers)
            KeyType.ARROW_RIGHT ->
                if (isTerminalLikeEditor()) sendKey(KeyEvent.KEYCODE_DPAD_RIGHT, modifiers)
                else moveCursor(1, 0, modifiers)
            KeyType.ARROW_UP ->
                if (isTerminalLikeEditor()) sendKey(KeyEvent.KEYCODE_DPAD_UP, modifiers)
                else moveCursor(0, -1, modifiers)
            KeyType.ARROW_DOWN ->
                if (isTerminalLikeEditor()) sendKey(KeyEvent.KEYCODE_DPAD_DOWN, modifiers)
                else moveCursor(0, 1, modifiers)
            KeyType.HOME ->
                if (isTerminalLikeEditor()) sendKey(KeyEvent.KEYCODE_MOVE_HOME, modifiers)
                else moveCursor(Int.MIN_VALUE, 0, modifiers)
            KeyType.END ->
                if (isTerminalLikeEditor()) sendKey(KeyEvent.KEYCODE_MOVE_END, modifiers)
                else moveCursor(Int.MAX_VALUE, 0, modifiers)
            KeyType.PAGE_UP ->
                if (isTerminalLikeEditor()) sendKey(KeyEvent.KEYCODE_PAGE_UP, modifiers)
                else moveCursor(0, -PAGE_LINES, modifiers)
            KeyType.PAGE_DOWN ->
                if (isTerminalLikeEditor()) sendKey(KeyEvent.KEYCODE_PAGE_DOWN, modifiers)
                else moveCursor(0, PAGE_LINES, modifiers)
            KeyType.INSERT -> { /* no-op — InputConnection has no insert mode */ }
            KeyType.FN -> if (key.keyCode != 0) sendKey(key.keyCode, modifiers)
            KeyType.SYMBOL_SWITCH -> switchSymbols()
            KeyType.LAYOUT_SWITCH -> switchSymbolsShift()
            KeyType.LANGUAGE_SWITCH -> cycleLanguage()
            KeyType.EMOJI -> keyboardView?.showEmojiPicker()
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

    /** True when the focused editor looks like a terminal / shell / SSH
     *  client — i.e. an editor that has no Android text-edit model and
     *  reads raw KeyEvents to translate them into ANSI escape sequences.
     *
     *  Detection layers (any single hit wins):
     *   1. `inputType == 0` / `TYPE_NULL` — the standard Termux pattern.
     *   2. Known terminal-app package ids in [KNOWN_TERMINAL_PACKAGES].
     *   3. Fuzzy substring match against the package id — NetHunter Term
     *      forks, console / shell / connectbot / juicessh variants etc.
     *      tend to have one of these tokens in their package name even
     *      when they declare a non-zero inputType.
     */
    private fun isTerminalLikeEditor(): Boolean {
        val info = currentInputEditorInfo ?: return false
        if (info.inputType == 0) return true
        val pkg = info.packageName ?: return false
        if (pkg in KNOWN_TERMINAL_PACKAGES) return true
        val low = pkg.lowercase()
        return low.contains("termux") ||
            low.contains("nethunter") ||
            low.contains("connectbot") ||
            low.contains("juicessh") ||
            low.endsWith(".term") ||
            low.endsWith(".nhterm") ||
            low.contains(".terminal") ||
            low.contains(".shell")
    }

    private val KNOWN_TERMINAL_PACKAGES: Set<String> = setOf(
        "com.termux",
        "com.termux.window",
        "com.termoneplus",
        "jackpal.androidterm",
        "com.spartacusrex.spartacuside",
        "com.offsec.nhterm",
        "com.kali.nethunter.term",
        "com.kpym.terminalemulator"
    )

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
            MenuAction.ToggleFunctionRow -> {
                kbPrefs.showFunctionRow = !kbPrefs.showFunctionRow
                bindCurrentLayout()
            }
            MenuAction.ToggleSideSplit -> {
                kbPrefs.sideSplitEnabled = !kbPrefs.sideSplitEnabled
                bindCurrentLayout()
            }
            MenuAction.OpenVoiceInput -> {
                keyboardView?.showVoiceInput(recognitionLocaleFor(currentLayoutId))
            }
        }
    }

    /** Maps a `LayoutPack` id (e.g. "hu_HU") to the BCP-47 language tag
     *  used by [android.speech.RecognizerIntent.EXTRA_LANGUAGE]. */
    private fun recognitionLocaleFor(id: String): String = when (id) {
        "en_US" -> "en-US"
        "hu_HU" -> "hu-HU"
        "de_DE" -> "de-DE"
        "es_ES" -> "es-ES"
        else    -> "en-US"
    }

    override fun onOpenAppSettings() {
        // Deep-link straight to this app's "App info" page so the user
        // can grant RECORD_AUDIO from there — IMEs can't trigger the
        // runtime permission dialog directly.
        try {
            startActivity(
                Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    android.net.Uri.parse("package:$packageName"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (_: Throwable) { /* fall through */ }
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
     *
     * Terminal emulators (Termux, ConnectBot, …) declare `TYPE_NULL` and
     * read raw KeyEvents instead — for those we always send a real
     * KEYCODE_ENTER so the terminal's read loop sees it (the "press
     * Enter to restart" prompt at the end of a Termux session, for
     * example, needs a real key event to dismiss).
     */
    private fun handleEnter(modifiers: ModifierState) {
        val ic = currentInputConnection ?: return
        if (isTerminalLikeEditor()) {
            sendKey(KeyEvent.KEYCODE_ENTER, modifiers)
            return
        }
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
