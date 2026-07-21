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
import android.text.InputType
import com.pckeyboard.ime.clipboard.ClipboardEditorActivity
import com.pckeyboard.ime.clipboard.ClipboardEditorBridge
import com.pckeyboard.ime.clipboard.ClipboardHistory
import com.pckeyboard.ime.dictionary.BigramStore
import com.pckeyboard.ime.dictionary.DictionaryStore
import com.pckeyboard.ime.dictionary.HunspellStore
import com.pckeyboard.ime.dictionary.PersonalErrorModel
import com.pckeyboard.ime.dictionary.RerankerStore
import com.pckeyboard.ime.dictionary.SuggestionEngine
import com.pckeyboard.ime.dictionary.TrigramStore
import com.pckeyboard.ime.dictionary.UserDictionary
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

    // --- Autocorrect / suggestion state ---------------------------------
    /** Per-editor verdict from [shouldSuggestFor] — false for passwords,
     *  terminals, URL bars etc. */
    private var suggestionsEnabled = false
    /** The (lowercase) original of the most recent auto-correction.
     *  If the very next committed word is this same word retyped, the
     *  user is insisting — it's kept, force-learned and vetoed instead
     *  of being corrected again. Deliberately survives Backspace and
     *  letter keys so the delete-and-retype flow reaches it. */
    private var pendingInsist: String? = null
    /** Words the user insisted on this session (retyped right after an
     *  auto-correction) — never corrected again until the IME restarts.
     *  (The insistence also force-learns the word into the persistent
     *  [UserDictionary], so the veto outlives the session too.) */
    private val autocorrectVetoes = mutableSetOf<String>()
    /** True while the previous input event was a plain letter commit —
     *  the state in which a sudden cursor jump means "the user tapped
     *  somewhere else mid-word" and the abandoned word deserves the same
     *  auto-correction a Space would have given it. */
    private var lastEventWasTyping = false
    /** Lazily-created per-language learning dictionaries. */
    private val userDicts = mutableMapOf<String, UserDictionary>()
    /** Lazily-created per-language personal typo models. */
    private val personalModels = mutableMapOf<String, PersonalErrorModel>()

    private fun userDict(): UserDictionary =
        userDicts.getOrPut(currentLayoutId) { UserDictionary(this, currentLayoutId) }

    private fun personalModel(): PersonalErrorModel =
        personalModels.getOrPut(currentLayoutId) { PersonalErrorModel(this, currentLayoutId) }

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
        /** How far left of the cursor [currentWord] looks for the start
         *  of the word being typed. */
        private const val MAX_WORD_LOOKBACK = 48
        /** Sentence punctuation that ends a word the same way Space does
         *  — typing "szó." auto-corrects just like "szó ". Digits and
         *  other symbols deliberately don't (ids, code, "abc1"). */
        private const val PUNCT_BOUNDARIES = ".,!?;:"

        /** Conjunctions that grammatically require a comma before them,
         *  per language (AkH. 248–260: tagmondathatár, értelmező,
         *  hasonlítás, illetve/valamint/avagy). */
        private val COMMA_BEFORE: Map<String, Set<String>> = mapOf(
            "hu_HU" to setOf(
                "hogy", "de", "mert", "hanem", "viszont", "ugyanis",
                "hiszen", "mivel", "ami", "aki", "amit", "akit",
                "amely", "amelyik", "ahol", "amikor", "ahogy", "amiért",
                "mint", "illetve", "valamint", "avagy", "azaz",
                "vagyis", "ámde", "mintha"
            ),
            "de_DE" to setOf(
                "dass", "weil", "aber", "sondern", "obwohl", "damit",
                "denn", "wenn", "falls", "während", "sodass"
            )
        )

        /** Paired coordinating conjunctions: standalone they take no
         *  comma ("ezt vagy azt"), but the SECOND occurrence within one
         *  sentence does ("vagy ezt, vagy azt" — AkH. paired-conjunction
         *  rule). */
        private val PAIRED_CONJUNCTIONS: Map<String, Set<String>> = mapOf(
            "hu_HU" to setOf("vagy", "akár", "hol", "mind", "sem", "se"),
            "de_DE" to setOf("oder", "entweder", "weder", "noch")
        )

        /** "több mint száz" style quantity comparisons take NO comma
         *  before "mint" — the exception to the always-comma rule. */
        private val MINT_NO_COMMA_PREV = setOf("több", "kevesebb", "jobb", "rosszabb")

        /** Words after which the comma is NOT inserted — coordinating
         *  conjunctions form comma-free chains ("és hogy", "vagy aki"),
         *  and stacking a conjunction on a conjunction never takes one. */
        private val NO_COMMA_AFTER: Map<String, Set<String>> = mapOf(
            "hu_HU" to (setOf(
                "és", "s", "vagy", "meg", "akár", "se", "sem"
            ) + COMMA_BEFORE.getValue("hu_HU")),
            "de_DE" to (setOf(
                "und", "oder", "sowie", "bzw", "beziehungsweise", "wie"
            ) + COMMA_BEFORE.getValue("de_DE"))
        )

        /** How much text the comma helper looks back for sentence
         *  context (paired-conjunction detection). */
        private const val COMMA_CONTEXT_LOOKBACK = 160
        /** How many next-word predictions the strip shows after a
         *  completed word. */
        private const val PREDICTION_COUNT = 5
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
        sessionTheme = TerminalThemeBridge.fromExtras(info?.extras, themeRepo.getSelectedTheme())
        // Refresh theme + sizing prefs on each session — user may have changed
        // them since the IME was last shown.
        keyboardView?.updateTheme(activeTheme())
        bindCurrentLayout()
        keyboardView?.applySizingPrefs()
        // Autocorrect: decide per editor whether the suggestion bar makes
        // sense, and warm up the dictionary for the active language.
        suggestionsEnabled = shouldSuggestFor(info)
        pendingInsist = null
        lastEventWasTyping = false
        val showBar = suggestionsEnabled &&
            kbPrefs.autocorrectMode != KeyboardPrefs.AUTOCORRECT_OFF
        keyboardView?.setSuggestionBarVisible(showBar)
        if (showBar) {
            DictionaryStore.preload(this, currentLayoutId)
            HunspellStore.preload(this, currentLayoutId)
            BigramStore.preload(this, currentLayoutId)
            TrigramStore.preload(this, currentLayoutId)
            RerankerStore.preload(this, currentLayoutId)
            keyboardView?.setSuggestions(emptyList())
        }
        // If the user finished the clipboard editor with Send, commit the
        // edited text and persist the replacement into the history.
        ClipboardEditorBridge.consume()?.let { r ->
            if (r.edited.isNotEmpty()) currentInputConnection?.commitText(r.edited, 1)
            ClipboardHistory(this).replace(r.original, r.edited)
            keyboardView?.refreshClipboard()
        }
    }

    /**
     * Re-read the terminal colours when the keyboard actually becomes visible.
     * A terminal host can restart input (which delivers its colours through the
     * editor's extras) right around the time the keyboard is first shown, so
     * onStartInputView may run before the colours are available — picking them
     * up here covers that race, which otherwise left the very first session
     * unthemed until the input was restarted again (e.g. a recents round-trip).
     */
    override fun onWindowShown() {
        super.onWindowShown()
        val derived = TerminalThemeBridge.fromExtras(
            currentInputEditorInfo?.extras, themeRepo.getSelectedTheme()
        ) ?: return
        sessionTheme = derived
        keyboardView?.updateTheme(activeTheme())
        bindCurrentLayout()
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
        // Keep the active language's dictionary warm so suggestions are
        // ready the moment the user starts typing after a switch.
        if (kbPrefs.autocorrectMode != KeyboardPrefs.AUTOCORRECT_OFF) {
            DictionaryStore.preload(this, currentLayoutId)
            HunspellStore.preload(this, currentLayoutId)
            BigramStore.preload(this, currentLayoutId)
            TrigramStore.preload(this, currentLayoutId)
            RerankerStore.preload(this, currentLayoutId)
        }
    }

    /** Rewrites the control-row "123" SYMBOL_SWITCH key into whatever the
     *  user picked for the slot to the right of Space (Settings or the
     *  key's own long-press chooser). The key is tagged with
     *  [Key.CODE_RIGHT_OF_SPACE] so the view can offer the chooser on
     *  long-press regardless of the slot's current assignment. */
    private fun withRightOfSpaceAction(
        layout: KeyboardLayout,
        action: String
    ): KeyboardLayout {
        val newRows = layout.rows.map { row ->
            row.map { key ->
                if (key.type == KeyType.SYMBOL_SWITCH && key.label == "123") {
                    when (action) {
                        KeyboardPrefs.RIGHT_OF_SPACE_EMOJI ->
                            Key.fn("😀", KeyType.EMOJI, weight = key.widthWeight)
                                .copy(code = Key.CODE_RIGHT_OF_SPACE)
                        KeyboardPrefs.RIGHT_OF_SPACE_ALT ->
                            Key.fn("Alt", KeyType.ALT, sticky = true, weight = key.widthWeight)
                                .copy(code = Key.CODE_RIGHT_OF_SPACE)
                        else -> key.copy(code = Key.CODE_RIGHT_OF_SPACE)
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
        if (key.type == KeyType.BACKSPACE) {
            sendKey(KeyEvent.KEYCODE_DEL, modifiers)
            updateSuggestions()
            return
        }
        lastEventWasTyping = false
        when (key.type) {
            KeyType.SPACE -> handleSpaceCommit()
            KeyType.ENTER -> {
                // Enter is a word boundary too — fix the word before the
                // newline / editor action goes through.
                maybeInsertCommaBefore()
                if (!maybeAutocorrect("")) learnCurrentWord()
                handleEnter(modifiers)
            }
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
                // Sentence punctuation ends the word: try the same
                // auto-correction as Space (committing "word." fixed),
                // otherwise count the word for the learning dictionary.
                var handled = false
                if (!altAsAltGr && !modifiers.shouldSendAsKeyEvent() &&
                    c in PUNCT_BOUNDARIES
                ) {
                    maybeInsertCommaBefore()
                    if (maybeAutocorrect(c.toString())) handled = true
                    else learnCurrentWord()
                } else if (!isWordChar(c)) {
                    learnCurrentWord()
                }
                if (!handled) when {
                    altAsAltGr -> commitChar(c)        // Alt produces the layout's AltGr char
                    modifiers.shouldSendAsKeyEvent() -> {
                        val code = androidKeyCodeForChar(c)
                        if (code != 0) sendKey(code, modifiers) else commitChar(c)
                    }
                    else -> commitChar(c)
                }
                if (isWordChar(c) && !modifiers.shouldSendAsKeyEvent()) {
                    lastEventWasTyping = true
                }
            }
            else -> {}
        }
        // Recompute the strip from the editor's actual text so it stays
        // honest across cursor motion, deletes and mode switches.
        updateSuggestions()
    }

    // --- Autocorrect ------------------------------------------------------

    /** Word-characters accepted into the tracked word. */
    private fun isWordChar(c: Char): Boolean = c.isLetter() || c == '\''

    /** The word fragment immediately left of the cursor, read from the
     *  editor itself (robust against cursor taps we never see). Empty
     *  when the cursor isn't at the end of a word or the fragment is
     *  implausibly long. */
    private fun currentWord(): String {
        val ic = currentInputConnection ?: return ""
        val before = ic.getTextBeforeCursor(MAX_WORD_LOOKBACK, 0) ?: return ""
        var i = before.length
        while (i > 0 && isWordChar(before[i - 1])) i--
        // A fragment that fills the whole lookback window has no visible
        // start — don't guess.
        if (i == 0 && before.length == MAX_WORD_LOOKBACK) return ""
        return before.subSequence(i, before.length).toString()
    }

    /**
     * The word fragment being typed plus the completed word before it
     * (bigram context). The previous word may be separated by spaces
     * and/or a comma; sentence punctuation (.!?;:) kills the context —
     * a new sentence starts fresh.
     */
    private fun currentWordWithContext(): Triple<String, String?, String?> {
        val ic = currentInputConnection ?: return Triple("", null, null)
        val before = ic.getTextBeforeCursor(MAX_WORD_LOOKBACK, 0)
            ?: return Triple("", null, null)
        var i = before.length
        while (i > 0 && isWordChar(before[i - 1])) i--
        if (i == 0 && before.length == MAX_WORD_LOOKBACK) return Triple("", null, null)
        val word = before.subSequence(i, before.length).toString()
        fun wordEndingAt(end: Int): Pair<String, Int>? {
            var k = end
            while (k > 0 && (before[k - 1] == ' ' || before[k - 1] == ',')) k--
            if (k == 0 || !isWordChar(before[k - 1])) return null
            var j = k
            while (j > 0 && isWordChar(before[j - 1])) j--
            return before.subSequence(j, k).toString() to j
        }
        val prev1 = wordEndingAt(i) ?: return Triple(word, null, null)
        val prev2 = wordEndingAt(prev1.second)
        return Triple(word, prev1.first, prev2?.first)
    }

    private fun suggestionsActive(): Boolean =
        suggestionsEnabled &&
            kbPrefs.autocorrectMode != KeyboardPrefs.AUTOCORRECT_OFF &&
            keyboardView != null

    /** Recomputes the suggestion strip for the word left of the cursor.
     *  With no word in progress the strip predicts the NEXT word from
     *  the bigram model instead ("milyen szavak jöhetnek"). */
    private fun updateSuggestions() {
        if (!suggestionsActive()) return
        val dict = DictionaryStore.peek(currentLayoutId)
        if (dict == null) {
            keyboardView?.setSuggestions(emptyList())
            return
        }
        val (word, prevWord, prev2Word) = currentWordWithContext()
        if (word.isEmpty()) {
            keyboardView?.setSuggestions(predictNext(dict, prevWord, prev2Word))
            return
        }
        keyboardView?.setSuggestions(
            newEngine(dict)
                .suggest(
                    word, com.pckeyboard.ime.view.SuggestionBarView.MAX_SUGGESTIONS,
                    prevWord, prev2Word
                )
                .suggestions
        )
    }

    /** Next-word prediction for the strip: trigram continuations first
     *  (two words of context), backfilled from the bigram model. */
    private fun predictNext(
        dict: com.pckeyboard.ime.dictionary.WordDictionary,
        prevWord: String?,
        prev2Word: String?
    ): List<String> {
        val prevRank = prevWord?.lowercase()?.let { dict.rankOf(it) } ?: return emptyList()
        if (prevRank < 0) return emptyList()
        val prev2Rank = prev2Word?.lowercase()?.let { dict.rankOf(it) } ?: -1
        val out = LinkedHashSet<String>()
        TrigramStore.peek(currentLayoutId)?.let { tri ->
            if (prev2Rank >= 0) {
                tri.topNext(prev2Rank, prevRank, PREDICTION_COUNT)
                    .forEach { out.add(dict.wordAt(it.first)) }
            }
        }
        BigramStore.peek(currentLayoutId)?.let { bi ->
            if (out.size < PREDICTION_COUNT) {
                bi.topNext(prevRank, PREDICTION_COUNT)
                    .forEach { if (out.size < PREDICTION_COUNT) out.add(dict.wordAt(it.first)) }
            }
        }
        return out.toList()
    }

    private fun newEngine(dict: com.pckeyboard.ime.dictionary.WordDictionary) =
        SuggestionEngine(
            dict, userDict(),
            HunspellStore.validatorFor(currentLayoutId),
            BigramStore.peek(currentLayoutId),
            TrigramStore.peek(currentLayoutId),
            RerankerStore.scorerFor(currentLayoutId),
            personalModel()
        )

    /**
     * Feeds the word left of the cursor into the per-language learning
     * dictionary. Called on every word boundary (Space, Enter,
     * punctuation). Only words the main dictionary doesn't know are
     * counted; after [UserDictionary.LEARN_THRESHOLD] uses they start
     * showing up in suggestions and are shielded from auto-correction.
     */
    private fun learnCurrentWord() {
        if (!suggestionsActive()) return
        val dict = DictionaryStore.peek(currentLayoutId) ?: return
        val word = currentWord()
        // Committing a different word closes the retype-to-keep window.
        if (word.isNotEmpty() && word.lowercase() != pendingInsist) pendingInsist = null
        if (word.length < 2 || word.length > 32) return
        val lower = word.lowercase()
        if (lower.any { !isWordChar(it) }) return
        if (dict.contains(lower)) return
        userDict().recordUse(lower)
    }

    /**
     * Space commit with optional auto-correction: in
     * [KeyboardPrefs.AUTOCORRECT_AUTO] mode a confidently-wrong word
     * left of the cursor is replaced before the space goes in.
     */
    private fun handleSpaceCommit() {
        maybeInsertCommaBefore()
        if (maybeAutocorrect(" ")) return
        // The word survived (or auto-correct is off) — that's a real use.
        learnCurrentWord()
        commitChar(' ')
    }

    /**
     * Grammar helper (Auto mode only): Hungarian — and similarly German
     * — requires a comma before subordinating / adversative
     * conjunctions ("azt mondta, hogy…", "szép, de drága"). When the
     * word just finished is such a conjunction and the text before it
     * ends in a plain word (no comma or sentence punctuation yet, and
     * not a coordinating word like "és" that forms comma-free chains),
     * the comma is inserted automatically.
     */
    private fun maybeInsertCommaBefore(): Boolean {
        if (!suggestionsActive() ||
            kbPrefs.autocorrectMode != KeyboardPrefs.AUTOCORRECT_AUTO
        ) return false
        val commaBefore = COMMA_BEFORE[currentLayoutId] ?: return false
        val paired = PAIRED_CONJUNCTIONS[currentLayoutId] ?: emptySet()
        val ic = currentInputConnection ?: return false
        val word = currentWord()
        val lower = word.lowercase()
        if (word.isEmpty() || (lower !in commaBefore && lower !in paired)) return false
        val before = ic.getTextBeforeCursor(COMMA_CONTEXT_LOOKBACK, 0)?.toString()
            ?: return false
        if (!before.endsWith(word)) return false
        val prefix = before.dropLast(word.length)
        var i = prefix.length
        while (i > 0 && prefix[i - 1] == ' ') i--
        val spaces = prefix.length - i
        // Needs a separating space AND a preceding word on the same
        // sentence: at text start / after punctuation there's no clause
        // boundary to mark.
        if (spaces == 0 || i == 0 || !isWordChar(prefix[i - 1])) return false
        var j = i
        while (j > 0 && isWordChar(prefix[j - 1])) j--
        val prevWord = prefix.substring(j, i).lowercase()
        if (prevWord in (NO_COMMA_AFTER[currentLayoutId] ?: emptySet())) return false

        val insert = when {
            lower in commaBefore ->
                // "több mint száz" style quantity comparison — no comma.
                !(lower == "mint" && prevWord in MINT_NO_COMMA_PREV)
            else ->
                // Paired conjunction: only the SECOND occurrence within
                // the current sentence takes the comma.
                sentenceContains(prefix, lower)
        }
        if (!insert) return false
        ic.beginBatchEdit()
        ic.deleteSurroundingText(word.length + spaces, 0)
        ic.commitText(", $word", 1)
        ic.endBatchEdit()
        return true
    }

    /** True when [word] occurs as a standalone word in the current
     *  sentence (scanning [prefix] back to the last sentence ender). */
    private fun sentenceContains(prefix: String, word: String): Boolean {
        val sentenceStart = prefix.indexOfLast { it == '.' || it == '!' || it == '?' } + 1
        val sentence = prefix.substring(sentenceStart).lowercase()
        var idx = 0
        while (idx < sentence.length) {
            while (idx < sentence.length && !isWordChar(sentence[idx])) idx++
            val start = idx
            while (idx < sentence.length && isWordChar(sentence[idx])) idx++
            if (idx > start && sentence.substring(start, idx) == word) return true
        }
        return false
    }

    /**
     * Tap-away correction: when the user taps somewhere else in the text
     * while mid-word, the abandoned word gets the same auto-correction a
     * Space would have applied. Detected here because a tap is the one
     * cursor move the IME never sees as a key event.
     */
    override fun onUpdateSelection(
        oldSelStart: Int, oldSelEnd: Int,
        newSelStart: Int, newSelEnd: Int,
        candidatesStart: Int, candidatesEnd: Int
    ) {
        super.onUpdateSelection(
            oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd
        )
        val jumped = newSelStart == newSelEnd &&
            kotlin.math.abs(newSelEnd - oldSelEnd) > 1
        if (!jumped) return
        if (lastEventWasTyping) {
            lastEventWasTyping = false
            maybeAutocorrectAt(oldSelEnd, newSelEnd)
        }
        // Whatever happened, the strip should describe the word at the
        // NEW cursor position, not the abandoned one.
        updateSuggestions()
    }

    /**
     * Retroactively corrects the word ending at absolute position [end]
     * while the caret already sits at [caret].
     *
     * The replacement goes through `setComposingRegion` + `commitText`
     * — the mechanism editors implement for IMEs — instead of a
     * select-and-commit dance: `setSelection` is applied asynchronously
     * by some editors (Compose text fields notably), which made the
     * commit land at the wrong spot and garble the text. If the editor
     * doesn't support composing regions we simply skip the correction —
     * a missed fix is better than mangled text. No undo window is armed:
     * Backspace at the new caret must keep deleting there.
     */
    private fun maybeAutocorrectAt(end: Int, caret: Int) {
        if (!suggestionsActive() ||
            kbPrefs.autocorrectMode != KeyboardPrefs.AUTOCORRECT_AUTO
        ) return
        val ic = currentInputConnection ?: return
        val dict = DictionaryStore.peek(currentLayoutId) ?: return
        val ext = ic.getExtractedText(ExtractedTextRequest(), 0) ?: return
        val text = ext.text?.toString() ?: return
        val off = ext.startOffset
        val endIdx = (end - off).coerceIn(0, text.length)
        // The old caret must have been sitting at the end of a word.
        if (endIdx == 0 || !isWordChar(text[endIdx - 1])) return
        if (endIdx < text.length && isWordChar(text[endIdx])) return
        var start = endIdx
        while (start > 0 && isWordChar(text[start - 1])) start--
        // Never touch the word the user tapped INTO — they went back to
        // edit it themselves.
        if (caret in (start + off)..(endIdx + off)) return
        val word = text.substring(start, endIdx)
        if (word.length < 2 || word.length > 32) return
        if (word.lowercase() in autocorrectVetoes) return
        if (consumeInsistIfRetyped(word)) return
        val replacement = newEngine(dict).suggest(word, deep = true).autoReplace ?: return
        if (replacement == word) return
        val delta = replacement.length - word.length
        ic.beginBatchEdit()
        if (!ic.setComposingRegion(start + off, endIdx + off)) {
            ic.endBatchEdit()
            return
        }
        ic.commitText(replacement, 1)          // replaces the composing region
        val target = (if (caret >= endIdx + off) caret + delta else caret).coerceAtLeast(0)
        ic.setSelection(target, target)
        ic.endBatchEdit()
        pendingInsist = word.lowercase()
    }

    /**
     * Attempts an auto-correction of the word left of the cursor at a
     * word boundary ([boundary] is the space / punctuation about to be
     * committed). On success commits "replacement + boundary" in one
     * batch and remembers the original: retyping it right away means
     * the user insists on it (see [consumeInsistIfRetyped]).
     */
    private fun maybeAutocorrect(boundary: String): Boolean {
        if (!suggestionsActive() ||
            kbPrefs.autocorrectMode != KeyboardPrefs.AUTOCORRECT_AUTO
        ) return false
        val ic = currentInputConnection ?: return false
        val dict = DictionaryStore.peek(currentLayoutId) ?: return false
        val (word, prevWord, prev2Word) = currentWordWithContext()
        if (word.isEmpty() || word.lowercase() in autocorrectVetoes) return false
        if (consumeInsistIfRetyped(word)) return false
        val replacement = newEngine(dict)
            .suggest(word, prevWord = prevWord, prev2Word = prev2Word, deep = true)
            .autoReplace
        if (replacement == null || replacement == word) return false
        ic.beginBatchEdit()
        ic.deleteSurroundingText(word.length, 0)
        ic.commitText(replacement + boundary, 1)
        ic.endBatchEdit()
        pendingInsist = word.lowercase()
        return true
    }

    /**
     * The delete-and-retype escape hatch: when the word about to be
     * committed is the same one the previous auto-correction replaced,
     * the user is insisting on their spelling. Keep it, learn it
     * permanently, and never correct it again. Returns true when the
     * insistence was consumed (the caller must then commit the word
     * exactly as typed).
     */
    private fun consumeInsistIfRetyped(word: String): Boolean {
        val lower = word.lowercase()
        if (lower != pendingInsist) return false
        pendingInsist = null
        autocorrectVetoes.add(lower)
        userDict().forceLearn(lower)
        // Whatever correction pair we learned for this word is wrong.
        personalModel().forget(lower)
        return true
    }

    /** True for editors where showing suggestions / correcting is
     *  appropriate: plain text fields — not passwords, URL bars, email
     *  address fields, terminals, or fields that opted out. */
    private fun shouldSuggestFor(info: EditorInfo?): Boolean {
        if (info == null) return false
        if (isTerminalLikeEditor()) return false
        val t = info.inputType
        if (t and InputType.TYPE_MASK_CLASS != InputType.TYPE_CLASS_TEXT) return false
        if (t and InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS != 0) return false
        when (t and InputType.TYPE_MASK_VARIATION) {
            InputType.TYPE_TEXT_VARIATION_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_URI,
            InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
            InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS,
            InputType.TYPE_TEXT_VARIATION_FILTER -> return false
        }
        return true
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
            low.contains("neoterm") ||
            low.contains(".x11") ||
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
        "com.termux.x11",
        "io.neoterm",
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
        // Terminal / X11 hosts (e.g. NeoTerm's embedded X server) read modifiers
        // from real Ctrl/Alt/Shift key-down/up events, not from a single event's
        // metaState — their native input bridge drops metaState entirely. So in
        // a terminal-like editor we frame the key with standalone modifier
        // presses, exactly the way a physical keyboard would, so combos like
        // Ctrl+C actually arrive as Ctrl+C. Normal Android editors keep the
        // plain metaState-on-one-event behaviour.
        //
        // Only Ctrl/Alt/Meta combos need this framing. Plain navigation is left
        // alone so that Caps Lock (which counts as "shift active") doesn't turn
        // every arrow into Shift+arrow.
        val needsRealModifiers =
            modifiers.isCtrlActive() || modifiers.isAltActive() || modifiers.isMetaActive()
        val wrapModifiers = needsRealModifiers && isTerminalLikeEditor()
        if (wrapModifiers) sendModifierKeys(modifiers, down = true, meta = meta)
        ic.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0, meta))
        ic.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0, meta))
        if (wrapModifiers) sendModifierKeys(modifiers, down = false, meta = meta)
    }

    /**
     * Emit standalone modifier key events for the currently active modifiers.
     * Pressed in a fixed order on the way down and released in reverse on the
     * way up so the host sees a well-formed modifier stack.
     */
    private fun sendModifierKeys(modifiers: ModifierState, down: Boolean, meta: Int) {
        val ic = currentInputConnection ?: return
        val action = if (down) KeyEvent.ACTION_DOWN else KeyEvent.ACTION_UP
        val now = System.currentTimeMillis()
        val codes = buildList {
            if (modifiers.isShiftActive()) add(KeyEvent.KEYCODE_SHIFT_LEFT)
            if (modifiers.isCtrlActive()) add(KeyEvent.KEYCODE_CTRL_LEFT)
            if (modifiers.isAltActive()) add(KeyEvent.KEYCODE_ALT_LEFT)
            if (modifiers.isMetaActive()) add(KeyEvent.KEYCODE_META_LEFT)
        }
        val ordered = if (down) codes else codes.asReversed()
        for (code in ordered) {
            ic.sendKeyEvent(KeyEvent(now, now, action, code, 0, meta))
        }
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
        // The cursor left the word we were watching; blank the strip and
        // let the next keypress recompute it (avoids an IPC per step).
        lastEventWasTyping = false
        keyboardView?.setSuggestions(emptyList())
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
            is MenuAction.SetRightOfSpace -> {
                kbPrefs.rightOfSpaceAction = action.action
                bindCurrentLayout()
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
        pendingInsist = null
        lastEventWasTyping = false
        updateSuggestions()
    }

    /** Suggestion-bar tap: replace the word being typed with the picked
     *  candidate and move on with a trailing space. Every tap is also a
     *  labelled example "typed -> meant" for the personal typo model. */
    override fun onSuggestionPicked(word: String) {
        val ic = currentInputConnection ?: return
        pendingInsist = null
        lastEventWasTyping = false
        val current = currentWord()
        ic.beginBatchEdit()
        if (current.isNotEmpty()) ic.deleteSurroundingText(current.length, 0)
        ic.commitText("$word ", 1)
        ic.endBatchEdit()
        if (current.isNotEmpty() && !current.equals(word, ignoreCase = true)) {
            personalModel().recordCorrection(current, word)
        }
        keyboardView?.setSuggestions(emptyList())
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
