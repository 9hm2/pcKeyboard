package com.pckeyboard.ime.view

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.util.TypedValue
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.pckeyboard.ime.model.Key
import com.pckeyboard.ime.model.KeyType
import com.pckeyboard.ime.model.KeyboardLayout
import com.pckeyboard.ime.model.ModifierState
import com.pckeyboard.ime.settings.KeyboardPrefs
import com.pckeyboard.ime.theme.KeyboardTheme

/**
 * Renders a KeyboardLayout. A vertical [rowsContainer] holds the rows of
 * keys. On top of that we can mount a [TrackpadView] overlay for the
 * Space-long-press trackpad gesture, or a [KeyPopupView] anchored above
 * any other long-pressed key for alternate-character selection.
 */
class KeyboardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs), KeyView.Listener {

    var listener: Listener? = null
    private var layoutData: KeyboardLayout? = null
    private var theme: KeyboardTheme? = null
    private val modifiers = ModifierState()
    private val handler = Handler(Looper.getMainLooper())
    private val repeatRunnables = mutableMapOf<KeyView, Runnable>()
    private val prefs = KeyboardPrefs(context)

    private val rowsContainer = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
    }

    /** Vertical stack holding [emojiSearchHeader] (optional) and
     *  [rowsContainer]. Wraps both so the header can push the keyboard
     *  rows down without overlapping them while emoji search is active. */
    private val mainContainer = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
    }

    // Long-press popup. Rendered as a child of this FrameLayout, but the
    // KeyboardView is intentionally taller than the visible keyboard:
    // [popupZone] reserves dp(POPUP_ZONE_DP) of transparent space above
    // the rows so popups can draw there without being clipped, while
    // PcKeyboardService.onComputeInsets reports a smaller contentTopInset
    // so the app keeps fitting above the keys (not above the empty zone).
    // This is the same trick FlorisBoard uses.
    private var popupView: KeyPopupView? = null

    /** Transparent reserved area at the top of [mainContainer] where the
     *  long-press popup can draw without being clipped — the IME's
     *  inset is reported smaller so the app doesn't waste space above
     *  this zone (it shows through). */
    private val popupZone = View(context)

    // Globe long-press: vertical action menu, same touch routing pattern.
    private var actionMenuView: ActionMenuView? = null

    // Emoji picker overlay shown via the action menu "Emoji" item.
    private var emojiView: EmojiView? = null

    // Voice input overlay shown via the action menu "Voice input" item.
    private var voiceInputView: VoiceInputView? = null

    // Emoji search slim header — when present, sits inside mainContainer
    // above the keyboard rows; KeyboardView funnels char/letter/space/
    // backspace presses into its query while it's mounted.
    private var emojiSearchHeader: EmojiSearchHeaderView? = null

    // Autocorrect suggestion strip — when present, sits inside
    // mainContainer directly above the keyboard rows. Mounted only for
    // editors where suggestions make sense (the IME service decides).
    private var suggestionBar: SuggestionBarView? = null

    // Clipboard manager overlay.
    private var clipboardView: com.pckeyboard.ime.clipboard.ClipboardView? = null
    private val clipHistory = com.pckeyboard.ime.clipboard.ClipboardHistory(context)

    // Space-trackpad state. After the user slides their finger to the
    // indicator and "arms" the trackpad. On release the TrackpadView
    // transitions into a free-touchpad phase where each finger-down on
    // its surface sets a new origin and subsequent moves emit relative
    // pixel deltas — handleTrackpadDelta converts those to character /
    // line steps and forwards them to the IME service.
    private var trackpadView: TrackpadView? = null
    private var trackpadActive: Boolean = false
    private var trackpadArmed: Boolean = false
    private var trackpadAccumX: Float = 0f
    private var trackpadAccumY: Float = 0f
    /** Sensitivity multiplier captured at arming time so the cursor speed
     *  stays consistent across a single drag even if the user changes the
     *  preference in another session. */
    private var trackpadSensitivity: Float = 1f

    init {
        mainContainer.addView(
            popupZone,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(POPUP_ZONE_DP)
            )
        )
        mainContainer.addView(
            rowsContainer,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
            )
        )
        addView(
            mainContainer,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        )
    }

    fun bind(layout: KeyboardLayout, theme: KeyboardTheme) {
        this.layoutData = layout
        this.theme = theme
        // KeyboardView stays transparent so [popupZone] reveals the app
        // behind the IME. The opaque keyboard background is moved to
        // rowsContainer so the keys still have a solid backdrop and the
        // gaps between keys aren't see-through.
        setBackgroundColor(0)
        rowsContainer.setBackgroundColor(
            if (prefs.sideSplitEnabled) 0 else theme.backgroundColor
        )
        rebuild()
        requestLayout()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val mode = MeasureSpec.getMode(heightMeasureSpec)
        if (mode == MeasureSpec.UNSPECIFIED || mode == MeasureSpec.AT_MOST) {
            val rows = layoutData?.rows?.size ?: 5
            val widthDp = resources.displayMetrics.widthPixels / resources.displayMetrics.density
            val perRowDp = if (widthDp >= 600f) 46f else 52f
            val base = dp(perRowDp) * rows + dp((theme?.keySpacingDp ?: 3).toFloat()) * (rows + 1)
            // The IME view is always taller than the visible keyboard:
            //  + dp(POPUP_ZONE_DP) reserved at the top as a transparent
            //    zone where long-press popups can draw without being
            //    clipped (the matching contentTopInsets stays smaller
            //    so the app doesn't push above this empty zone).
            //  + dp(SEARCH_HEADER_DP) when the emoji search bar is up,
            //    so the keyboard rows keep their normal size.
            val extra = dp(POPUP_ZONE_DP) +
                (if (emojiSearchHeader != null) dp(SEARCH_HEADER_DP) else 0) +
                (if (suggestionBar != null) dp(SuggestionBarView.BAR_DP) else 0)
            val targetHeight = (base * prefs.heightScale).toInt() + extra
            val resolved = if (mode == MeasureSpec.AT_MOST) {
                minOf(MeasureSpec.getSize(heightMeasureSpec), targetHeight)
            } else targetHeight
            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(resolved, MeasureSpec.EXACTLY))
        } else {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        }
    }

    /** Re-applies sizing prefs (height scale, padding, split). */
    fun applySizingPrefs() {
        rebuild()
        requestLayout()
    }

    fun updateTheme(theme: KeyboardTheme) {
        this.theme = theme
        setBackgroundColor(theme.backgroundColor)
        forEachKeyView { kv ->
            kv.theme = theme
            kv.invalidate()
        }
    }

    /** Clears any ONCE / LOCKED state on every modifier (Shift, Ctrl,
     *  Alt, Caps, …). Called from the IME service on input dismiss so
     *  the next session starts fresh. */
    fun resetModifiers() {
        modifiers.reset()
        refresh()
    }

    fun refresh() {
        forEachKeyView { it.invalidate() }
    }

    private fun rebuild() {
        rowsContainer.removeAllViews()
        val layout = layoutData ?: return
        val theme = theme ?: return
        val spacing = dp(theme.keySpacingDp.toFloat())

        val widthDp = (resources.displayMetrics.widthPixels /
                resources.displayMetrics.density).toInt()
        val side = (resources.displayMetrics.widthPixels * prefs.horizontalPadding).toInt()
        rowsContainer.setPadding(side, 0, side, 0)

        if (prefs.sideSplitEnabled) {
            buildSideSplitRows(layout, theme)
            return
        }

        val splitGap = if (prefs.splitEnabled && widthDp >= KeyboardPrefs.SPLIT_MIN_WIDTH_DP)
            prefs.splitGapWeight else 0f

        for (row in layout.rows) {
            val rowView = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
                )
                // Row carries no margin of its own; each KeyView's internal
                // padding (spacing / 2 on every side) produces a uniform
                // "spacing"-sized gap between every pair of adjacent keys,
                // both within a row AND between rows. The first / last
                // keys on each edge get the same spacing / 2 inset.
            }
            val spaceIndex = row.indexOfFirst { it.type == KeyType.SPACE }
            val isSpaceRow = splitGap > 0f && spaceIndex >= 0
            // In rows that contain Space, don't carve a gap into the row —
            // instead stretch the Space key by `splitGap` so it bridges the
            // visual split and can be hit by either thumb. Other rows get the
            // normal centre spacer.
            val splitIndex = if (splitGap > 0f && !isSpaceRow) findSplitIndex(row) else -1
            val totalWeight = row.sumOf { it.widthWeight.toDouble() }.toFloat() + splitGap
            for ((index, key) in row.withIndex()) {
                val effectiveWeight = if (isSpaceRow && index == spaceIndex)
                    key.widthWeight + splitGap else key.widthWeight
                val kv = KeyView(context, key, theme, modifiers).apply {
                    listener = this@KeyboardView
                    layoutParams = LinearLayout.LayoutParams(
                        0, LinearLayout.LayoutParams.MATCH_PARENT,
                        effectiveWeight / totalWeight
                    )
                }
                rowView.addView(kv)
                if (index == splitIndex) {
                    val spacer = View(context).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            0, LinearLayout.LayoutParams.MATCH_PARENT,
                            splitGap / totalWeight
                        )
                    }
                    rowView.addView(spacer)
                }
            }
            rowsContainer.addView(rowView)
        }
    }

    /**
     * Renders the layout in "side-split" mode: every row gets sliced at
     * its weight midpoint and rebuilt as [left keys][big gap][right keys]
     * so both thumbs can reach the keys on a landscape phone without
     * stretching. When the midpoint falls inside a key (typically Space),
     * the key is duplicated as a left half + a right half so each thumb
     * has its own Space.
     *
     * The gap weight is the same on every row, which preserves
     * row-to-row alignment (▲ stays directly above ▼, etc.). The middle
     * gap is just a [View] with no listener; PcKeyboardService.onComputeInsets
     * carves it out of the touchable region so taps in the gap fall
     * through to whatever app is behind the keyboard.
     */
    private fun buildSideSplitRows(layout: KeyboardLayout, theme: KeyboardTheme) {
        val gap = KeyboardPrefs.SIDE_SPLIT_GAP_WEIGHT
        for (row in layout.rows) {
            val rowView = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
                )
            }
            val originalTotal = row.sumOf { it.widthWeight.toDouble() }.toFloat()
            val totalWeight = originalTotal + gap
            val midpoint = originalTotal / 2f
            var cumulative = 0f
            var gapInserted = false
            for (key in row) {
                val keyStart = cumulative
                val keyEnd = cumulative + key.widthWeight
                cumulative = keyEnd
                if (gapInserted) {
                    rowView.addView(makeKeyView(key, theme, key.widthWeight / totalWeight))
                    continue
                }
                if (keyStart >= midpoint) {
                    // Gap goes BEFORE this key — keys split cleanly here.
                    rowView.addView(makeGapView(gap / totalWeight))
                    rowView.addView(makeKeyView(key, theme, key.widthWeight / totalWeight))
                    gapInserted = true
                } else if (keyEnd > midpoint) {
                    // Midpoint is strictly inside this key — split the key
                    // into a left and right half so both halves remain
                    // tappable (this is how Space ends up as two reachable
                    // half-Space keys).
                    val leftW = midpoint - keyStart
                    val rightW = keyEnd - midpoint
                    if (leftW > 0.001f) {
                        rowView.addView(makeKeyView(key, theme, leftW / totalWeight))
                    }
                    rowView.addView(makeGapView(gap / totalWeight))
                    if (rightW > 0.001f) {
                        rowView.addView(makeKeyView(key, theme, rightW / totalWeight))
                    }
                    gapInserted = true
                } else {
                    rowView.addView(makeKeyView(key, theme, key.widthWeight / totalWeight))
                }
            }
            // Edge case: a row with all keys on the left of the midpoint —
            // still emit a trailing gap so the row width is consistent.
            if (!gapInserted) rowView.addView(makeGapView(gap / totalWeight))
            rowsContainer.addView(rowView)
        }
    }

    private fun makeKeyView(key: Key, theme: KeyboardTheme, weight: Float): KeyView =
        KeyView(context, key, theme, modifiers).apply {
            listener = this@KeyboardView
            layoutParams = LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.MATCH_PARENT, weight
            )
        }

    private fun makeGapView(weight: Float): View = View(context).apply {
        layoutParams = LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.MATCH_PARENT, weight
        )
    }

    /**
     * Bounds (in KeyboardView coords) of the empty middle column when
     * side-split is active. Returns `null` when side-split is off or the
     * view hasn't been laid out yet. The IME service uses this to carve
     * the middle out of its touchable region so taps in the gap fall
     * through to whatever app is behind the keyboard.
     */
    fun sideSplitGapBounds(): android.graphics.Rect? {
        if (!prefs.sideSplitEnabled) return null
        val layout = layoutData ?: return null
        if (layout.rows.isEmpty()) return null
        if (width == 0 || height == 0) return null

        val firstRow = layout.rows.first()
        val originalTotal = firstRow.sumOf { it.widthWeight.toDouble() }.toFloat()
        val gapWeight = KeyboardPrefs.SIDE_SPLIT_GAP_WEIGHT
        val totalWeight = originalTotal + gapWeight

        val side = (resources.displayMetrics.widthPixels * prefs.horizontalPadding).toInt()
        val innerLeft = side
        val innerWidth = width - 2 * side

        val leftKeysWidth = (originalTotal / 2f / totalWeight * innerWidth).toInt()
        val gapWidth = (gapWeight / totalWeight * innerWidth).toInt()
        val gapLeft = innerLeft + leftKeysWidth
        val gapRight = gapLeft + gapWidth

        // Only the rows area is the gap — the optional search header
        // above the rows stays opaque + touchable.
        val gapTop = rowsContainer.top
        val gapBottom = rowsContainer.bottom.takeIf { it > gapTop } ?: height
        return android.graphics.Rect(gapLeft, gapTop, gapRight, gapBottom)
    }

    private fun findSplitIndex(row: List<Key>): Int {
        if (row.size < 4) return -1
        val total = row.sumOf { it.widthWeight.toDouble() }.toFloat()
        var acc = 0f
        for ((i, k) in row.withIndex()) {
            acc += k.widthWeight
            if (acc >= total / 2f) return i
        }
        return row.size / 2
    }

    override fun onKeyDown(view: KeyView) {
        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        if (view.key.repeatable) startRepeat(view)
    }

    override fun onKeyUp(view: KeyView) {
        stopRepeat(view)
        handleKey(view.key)
    }

    override fun onKeyCancel(view: KeyView) {
        stopRepeat(view)
    }

    override fun onKeyLongPress(view: KeyView) {
        stopRepeat(view)
        // The configurable right-of-Space slot opens its chooser no matter
        // what it's currently assigned to (123 / emoji / Alt).
        if (view.key.code == Key.CODE_RIGHT_OF_SPACE) {
            showRightOfSpaceMenu(view)
            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            return
        }
        when (view.key.type) {
            KeyType.SPACE -> {
                showTrackpad()
                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            }
            KeyType.LANGUAGE_SWITCH -> {
                showActionMenu(view)
                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            }
            else -> {
                val chars = view.key.popupChars ?: return
                showPopup(view, chars)
                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            }
        }
    }

    override fun onKeyPopupMove(view: KeyView, rawX: Float, rawY: Float) {
        if (trackpadActive) {
            handleTrackpadMove(rawX, rawY)
            return
        }
        // Action menu supports slide-to-select: as the finger moves up onto
        // the menu, highlight whichever row is under it.
        actionMenuView?.let {
            it.highlightAt(rawX, rawY)
            return
        }
        val popup = popupView ?: return
        val loc = IntArray(2)
        popup.getLocationOnScreen(loc)
        popup.selectedIndex = popup.findIndexForX(rawX - loc[0])
    }

    override fun onKeyPopupRelease(view: KeyView) {
        if (trackpadActive) {
            // Don't commit Space whether the user armed the trackpad or
            // not — long-press alone is intent enough to suppress the
            // tap. If armed, hand off to free touchpad mode so the user
            // can do real cursor work; if not armed (released without
            // reaching the indicator), dismiss the trackpad.
            val tp = trackpadView
            if (trackpadArmed && tp != null) {
                tp.phase = TrackpadView.Phase.FREE
            } else {
                endTrackpad()
            }
            return
        }
        actionMenuView?.let { menu ->
            // If the user slid onto a row, fire it and close. If they
            // released still on the globe key (no hover), leave the menu
            // up so it can be tapped instead.
            menu.commitHover()
            return
        }
        val selected = popupView?.let { p ->
            p.selectedIndex.takeIf { it in p.chars.indices }?.let { p.chars[it] }
        }
        dismissPopup()
        if (selected != null) {
            val charKey = Key.char(selected)
            listener?.onKey(charKey, modifiers)
            modifiers.consumeAfterChar()
            refresh()
        }
        // else: silently dismiss — the user slid off and released, no commit.
    }

    override fun onKeyPopupCancel(view: KeyView) {
        if (trackpadActive) { endTrackpad(); return }
        if (actionMenuView != null) { dismissActionMenu(); return }
        dismissPopup()
    }

    // --- Globe action menu ------------------------------------------------

    var currentLanguageId: String = "en_US"

    private fun showActionMenu(anchor: KeyView) {
        dismissActionMenu()
        val prefs = com.pckeyboard.ime.settings.KeyboardPrefs(context)
        val enabled = prefs.enabledLanguages
        val langItems = com.pckeyboard.ime.layout.LayoutRegistry.available
            .filter { it.id in enabled }
            .map { pack ->
                MenuItem(
                    icon = "🌐",
                    label = "${com.pckeyboard.ime.service.languageCode(pack.id)} — ${pack.displayName}",
                    action = MenuAction.SwitchLanguage(pack.id),
                    isCurrent = pack.id == currentLanguageId && !isEmojiOpen()
                )
            }
        val fnRowIcon = if (prefs.showFunctionRow) "☑" else "☐"
        // Side-split menu entry temporarily hidden — rendering bugs need
        // fixing before we re-expose the toggle. The MenuAction itself
        // stays in the sealed class so the wiring can be re-enabled
        // without a manifest change.
        val items = langItems + listOf(
            MenuItem(fnRowIcon, "Function row (Esc, F1…)", MenuAction.ToggleFunctionRow),
            MenuItem("🎤", "Voice input",       MenuAction.OpenVoiceInput),
            MenuItem("😀", "Emoji",            MenuAction.OpenEmoji),
            MenuItem("📋", "Clipboard",        MenuAction.OpenClipboard),
            MenuItem("⚙",  "Keyboard settings", MenuAction.OpenSettings)
        )
        presentActionMenu(anchor, items)
    }

    /** Long-press chooser for the configurable slot right of Space: the
     *  same popup style (and slide-to-select behaviour) as the globe
     *  menu, with one row per assignable action. */
    private fun showRightOfSpaceMenu(anchor: KeyView) {
        dismissActionMenu()
        val current = prefs.rightOfSpaceAction
        val items = listOf(
            MenuItem(
                "😀", "Emoji",
                MenuAction.SetRightOfSpace(KeyboardPrefs.RIGHT_OF_SPACE_EMOJI),
                isCurrent = current == KeyboardPrefs.RIGHT_OF_SPACE_EMOJI
            ),
            MenuItem(
                "⌥", "Alt (AltGr)",
                MenuAction.SetRightOfSpace(KeyboardPrefs.RIGHT_OF_SPACE_ALT),
                isCurrent = current == KeyboardPrefs.RIGHT_OF_SPACE_ALT
            ),
            MenuItem(
                "🔣", "123 Symbols",
                MenuAction.SetRightOfSpace(KeyboardPrefs.RIGHT_OF_SPACE_SYMBOLS),
                isCurrent = current == KeyboardPrefs.RIGHT_OF_SPACE_SYMBOLS
            )
        )
        presentActionMenu(anchor, items)
    }

    /** Builds an [ActionMenuView] for [items] and mounts it anchored
     *  above [anchor], clamped to the keyboard bounds. */
    private fun presentActionMenu(anchor: KeyView, items: List<MenuItem>) {
        val theme = theme ?: return
        val menu = ActionMenuView(context, items, theme) { action ->
            dismissActionMenu()
            listener?.onMenuAction(action)
        }.apply { elevation = dp(8f).toFloat() }
        actionMenuView = menu

        val w = menu.menuWidth
        // Cap height to ~70% of the keyboard so the menu scrolls instead of
        // being clipped when more items are added.
        val maxH = (height * 0.7f).toInt().coerceAtLeast(dp(120f))
        menu.measure(
            MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(maxH, MeasureSpec.AT_MOST)
        )
        val h = menu.measuredHeight

        val anchorLoc = IntArray(2)
        val selfLoc = IntArray(2)
        anchor.getLocationInWindow(anchorLoc)
        getLocationInWindow(selfLoc)
        val anchorX = anchorLoc[0] - selfLoc[0]
        val anchorY = anchorLoc[1] - selfLoc[1]
        val centerX = anchorX + anchor.width / 2
        var x = centerX - w / 2
        if (x < 0) x = 0
        if (x + w > width) x = (width - w).coerceAtLeast(0)
        var y = anchorY - h - dp(4f)
        // Don't bleed into the transparent popup zone above the keys —
        // the action menu is opaque and should stay aligned with the
        // keyboard area, same as the picker / clipboard overlays.
        val keysTop = dp(POPUP_ZONE_DP)
        if (y < keysTop) y = keysTop

        val lp = LayoutParams(w, h).apply {
            leftMargin = x
            topMargin = y
        }
        addView(menu, lp)
    }

    private fun dismissActionMenu() {
        actionMenuView?.let { removeView(it) }
        actionMenuView = null
    }

    // --- Emoji picker -----------------------------------------------------

    private val emojiTracker = EmojiUsageTracker(context)

    fun showEmojiPicker() {
        if (emojiView != null) return
        val theme = theme ?: return
        val view = EmojiView(context, theme, emojiTracker).apply {
            listener = object : EmojiView.Listener {
                override fun onEmoji(emoji: String) {
                    this@KeyboardView.listener?.onText(emoji)
                }
                override fun onBack() = hideEmojiPicker()
                override fun onBackspace() {
                    this@KeyboardView.listener?.onKey(
                        Key(0, "", type = KeyType.BACKSPACE), modifiers
                    )
                }
                override fun onSearch() {
                    showEmojiSearchHeader()
                }
            }
        }
        emojiView = view
        rowsContainer.visibility = INVISIBLE
        // Offset the overlay below popupZone so it fills only the actual
        // keys area, not the empty transparent zone above the keyboard.
        addView(view, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT).apply {
            topMargin = dp(POPUP_ZONE_DP)
        })
    }

    fun hideEmojiPicker() {
        emojiView?.let { removeView(it) }
        emojiView = null
        rowsContainer.visibility = VISIBLE
    }

    fun isEmojiOpen(): Boolean = emojiView != null

    // --- Voice input overlay --------------------------------------------

    /**
     * Mounts a [VoiceInputView] over the keys area. The view drives
     * Android's [android.speech.SpeechRecognizer] in [recognitionLocale]
     * and commits the final transcript back via [Listener.onText].
     *
     * If RECORD_AUDIO isn't granted yet, the view shows a hint and the
     * mic button opens this app's permission page (so the user can
     * grant it from there — an IME service can't request runtime
     * permissions directly).
     */
    fun showVoiceInput(recognitionLocale: String) {
        if (voiceInputView != null) return
        val theme = theme ?: return
        val view = VoiceInputView(
            context, theme,
            recognitionLocale = recognitionLocale,
            onText = { transcript ->
                this@KeyboardView.listener?.onText(transcript)
                hideVoiceInput()
            },
            onClose = { hideVoiceInput() },
            onOpenAppSettings = {
                this@KeyboardView.listener?.onOpenAppSettings()
            }
        )
        voiceInputView = view
        rowsContainer.visibility = INVISIBLE
        addView(view, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT).apply {
            topMargin = dp(POPUP_ZONE_DP)
        })
    }

    fun hideVoiceInput() {
        voiceInputView?.let { removeView(it) }
        voiceInputView = null
        rowsContainer.visibility = VISIBLE
    }

    // --- Emoji search header (full-keyboard search) ----------------------

    /**
     * Replaces the emoji picker with a thin search bar pinned above the
     * normal keyboard rows. KeyboardView routes character / letter /
     * space / backspace presses into the header's query so the user can
     * type with the **full** keyboard (the regular layout stays in place).
     * Picking a result commits it through [Listener.onText] and tears
     * the header down so the user is back to normal typing.
     */
    private fun showEmojiSearchHeader() {
        if (emojiSearchHeader != null) return
        val theme = theme ?: return
        // The picker hides itself — search lives outside it.
        hideEmojiPicker()
        val header = EmojiSearchHeaderView(
            context, theme, emojiTracker,
            onClose = { hideEmojiSearchHeader() },
            onEmojiPicked = { emoji ->
                listener?.onText(emoji)
                hideEmojiSearchHeader()
            }
        )
        emojiSearchHeader = header
        // Insert at index 1 so the layout order stays:
        //   0: popupZone (top, transparent)
        //   1: emojiSearchHeader (when active)
        //   2: rowsContainer (always)
        mainContainer.addView(header, 1, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(SEARCH_HEADER_DP)
        ))
        rowsContainer.visibility = VISIBLE
        requestLayout()
    }

    fun hideEmojiSearchHeader() {
        emojiSearchHeader?.let { mainContainer.removeView(it) }
        emojiSearchHeader = null
        requestLayout()
    }

    // --- Autocorrect suggestion bar ---------------------------------------

    /**
     * Mounts / unmounts the suggestion strip. The IME service calls this
     * on every input session start with its per-editor verdict (text
     * fields yes; passwords, terminals, URL bars no). Remounting picks
     * up the current theme.
     */
    fun setSuggestionBarVisible(visible: Boolean) {
        if (!visible) {
            suggestionBar?.let { mainContainer.removeView(it) }
            suggestionBar = null
            requestLayout()
            return
        }
        val theme = theme ?: return
        // Recreate so a theme change since last session is applied.
        suggestionBar?.let { mainContainer.removeView(it) }
        val bar = SuggestionBarView(context, theme) { word ->
            listener?.onSuggestionPicked(word)
        }
        suggestionBar = bar
        // Directly above the keyboard rows, below popupZone (and below
        // the emoji search header if one is up).
        mainContainer.addView(bar, mainContainer.indexOfChild(rowsContainer),
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(SuggestionBarView.BAR_DP)
            ))
        requestLayout()
    }

    fun setSuggestions(words: List<String>) {
        suggestionBar?.setSuggestions(words)
    }

    // --- Clipboard manager -----------------------------------------------

    fun showClipboard() {
        if (clipboardView != null) return
        val theme = theme ?: return
        val view = com.pckeyboard.ime.clipboard.ClipboardView(context, theme, clipHistory).apply {
            listener = object : com.pckeyboard.ime.clipboard.ClipboardView.Listener {
                override fun onCommit(text: String) {
                    this@KeyboardView.listener?.onText(text)
                    hideClipboard()
                }
                override fun onEdit(text: String) {
                    this@KeyboardView.listener?.onClipboardEdit(text)
                    hideClipboard()
                }
                override fun onBack() = hideClipboard()
            }
        }
        clipboardView = view
        rowsContainer.visibility = INVISIBLE
        addView(view, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT).apply {
            topMargin = dp(POPUP_ZONE_DP)
        })
    }

    fun hideClipboard() {
        clipboardView?.let { removeView(it) }
        clipboardView = null
        rowsContainer.visibility = VISIBLE
    }

    fun isClipboardOpen(): Boolean = clipboardView != null

    /** Refresh the clipboard cards (called by the service after the OS
     *  primary-clip listener fires while the panel is open). */
    fun refreshClipboard() {
        clipboardView?.refresh()
    }

    // Dismiss the action menu when the user taps anywhere outside it. The
    // tap is consumed (return true) so the underlying key doesn't also
    // activate from the same down-event.
    override fun onInterceptTouchEvent(ev: android.view.MotionEvent): Boolean {
        val menu = actionMenuView
        if (menu != null && ev.actionMasked == android.view.MotionEvent.ACTION_DOWN) {
            val x = ev.x.toInt()
            val y = ev.y.toInt()
            val outside = x < menu.left || x > menu.right ||
                          y < menu.top  || y > menu.bottom
            if (outside) {
                dismissActionMenu()
                return true
            }
        }
        return super.onInterceptTouchEvent(ev)
    }

    // --- Alternate-char popup ---------------------------------------------

    private fun showPopup(anchor: KeyView, chars: String) {
        dismissPopup()
        val theme = theme ?: return

        // Arrange chars so the base character sits in the middle of the popup
        // — that way it can be aligned directly above the pressed key and a
        // straight release without sliding commits the base char.
        val base = anchor.key.label
        // For symbol / punctuation keys (KeyType.CHAR), expose the shift-label
        // (e.g. '<' on the comma key, '?' on the slash key) in the popup so
        // every character a key can produce is reachable without juggling
        // Shift first. Letter keys deliberately exclude it — their shift-label
        // is just the uppercase form, already accessible via Shift/Caps.
        val shiftLabel = if (anchor.key.type == KeyType.CHAR) {
            anchor.key.shiftLabel?.takeIf { it != base }
        } else null
        val rawOthers = listOfNotNull(shiftLabel) + chars.map { it.toString() }
        val others = rawOthers.filter { it != base }.distinct()
        val leftCount = others.size / 2
        val all = others.take(leftCount) + listOf(base) + others.drop(leftCount)
        val baseIndex = leftCount

        val popup = KeyPopupView(context, all, baseIndex, theme)
        popup.selectedIndex = baseIndex
        popup.elevation = dp(8f).toFloat()
        popupView = popup

        popup.measure(
            MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
            MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        )
        val popupW = popup.measuredWidth
        val popupH = popup.measuredHeight

        val anchorLoc = IntArray(2)
        val selfLoc = IntArray(2)
        anchor.getLocationInWindow(anchorLoc)
        getLocationInWindow(selfLoc)
        val anchorX = anchorLoc[0] - selfLoc[0]
        val anchorY = anchorLoc[1] - selfLoc[1]
        val anchorCenterX = anchorX + anchor.width / 2

        // Centre the BASE cell over the anchor, not the whole popup. That
        // keeps the resting selection on the base char and makes left/
        // right slides map cleanly to neighbouring options.
        val baseCellCenterInPopup = popup.pad + popup.cellWidth * baseIndex + popup.cellWidth / 2f
        var popupX = (anchorCenterX - baseCellCenterInPopup).toInt()
        var popupY = anchorY - popupH - dp(4f)

        // Clamp horizontally inside the keyboard.
        if (popupX < 0) popupX = 0
        if (popupX + popupW > width) popupX = (width - popupW).coerceAtLeast(0)

        // popupZone reserves dp(POPUP_ZONE_DP) of empty space at the top
        // of the IME view, so any popup for a key in the rows fits ABOVE
        // the row inside that zone — even for the top number row. The
        // app behind shows through it because the IME window is
        // transparent and PcKeyboardService.onComputeInsets pretends the
        // IME is only as tall as the rows themselves.
        if (popupY < 0) popupY = 0

        val lp = LayoutParams(popupW, popupH).apply {
            leftMargin = popupX
            topMargin = popupY
        }
        addView(popup, lp)
    }

    private fun dismissPopup() {
        popupView?.let { removeView(it) }
        popupView = null
    }

    // --- Space trackpad ---------------------------------------------------

    private fun showTrackpad() {
        endTrackpad()
        val theme = theme ?: return
        val tp = TrackpadView(
            context, theme,
            onCursorDelta = { dxPx, dyPx -> handleTrackpadDelta(dxPx, dyPx) },
            onClose = { endTrackpad() }
        )
        trackpadView = tp
        trackpadActive = true
        trackpadArmed = false
        trackpadAccumX = 0f
        trackpadAccumY = 0f
        // Capture the user's sensitivity preference once so the cursor
        // speed stays consistent across a single trackpad session even
        // if Settings is opened in another window mid-use.
        trackpadSensitivity =
            com.pckeyboard.ime.settings.KeyboardPrefs(context).trackpadSensitivity
        // Don't toggle rowsContainer visibility: the trackpad's opaque draw
        // covers the rows by itself, and toggling visibility can trigger
        // ACTION_CANCEL on the still-active space-press gesture, which would
        // immediately tear the trackpad back down.
        addView(tp, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT).apply {
            topMargin = dp(POPUP_ZONE_DP)
        })
    }

    /**
     * Arming-phase MOVE handler. While the user is still dragging from
     * Space (KeyView owns the gesture), check whether the finger has
     * crossed into the central indicator — when it has, flip the
     * trackpad into armed state. The actual cursor-driving touches start
     * later in [Phase.FREE] once the user releases and re-touches the
     * trackpad surface.
     */
    private fun handleTrackpadMove(rawX: Float, rawY: Float) {
        val tp = trackpadView ?: return
        if (tp.phase != TrackpadView.Phase.ARMING) return
        if (trackpadArmed) return
        val loc = IntArray(2)
        tp.getLocationOnScreen(loc)
        val localX = rawX - loc[0]
        val localY = rawY - loc[1]
        if (tp.isInsideIndicator(localX, localY)) {
            trackpadArmed = true
            tp.armed = true
            tp.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        }
    }

    /**
     * Free-phase handler invoked by [TrackpadView] whenever the user's
     * finger moves while on the touchpad surface. Converts pixel deltas
     * to character / line steps using the captured sensitivity, then
     * forwards whole-unit moves to the IME service.
     */
    private fun handleTrackpadDelta(dxPx: Float, dyPx: Float) {
        val pxPerChar = dp(TRACKPAD_PX_PER_CHAR_DP).toFloat()
        val pxPerLine = dp(TRACKPAD_PX_PER_LINE_DP).toFloat()
        val sens = trackpadSensitivity
        trackpadAccumX += dxPx / pxPerChar * sens
        trackpadAccumY += dyPx / pxPerLine * sens
        var dxSteps = 0
        var dySteps = 0
        while (trackpadAccumX >=  1f) { dxSteps++; trackpadAccumX -= 1f }
        while (trackpadAccumX <= -1f) { dxSteps--; trackpadAccumX += 1f }
        while (trackpadAccumY >=  1f) { dySteps++; trackpadAccumY -= 1f }
        while (trackpadAccumY <= -1f) { dySteps--; trackpadAccumY += 1f }
        if (dxSteps != 0 || dySteps != 0) {
            // Route cursor motion through a dedicated callback so the
            // service moves the cursor with InputConnection.setSelection
            // instead of DPAD KeyEvents — DPADs can move focus to a
            // sibling view (e.g. the Send button next to a chat input).
            listener?.onCursorMove(dxSteps, dySteps)
        }
    }

    private fun endTrackpad() {
        trackpadView?.let { removeView(it) }
        trackpadView = null
        trackpadActive = false
        trackpadArmed = false
        trackpadAccumX = 0f
        trackpadAccumY = 0f
    }

    companion object {
        /** Transparent reserved space at the top of the IME view for the
         *  long-press popup to draw into; matched by the service's
         *  contentTopInsets so the app keeps fitting above the rows. */
        const val POPUP_ZONE_DP = 90f
        /** Height of the emoji search overlay header (query + results strip). */
        const val SEARCH_HEADER_DP = 104f
        /** Finger pixels per one character of horizontal cursor movement
         *  at sensitivity 1.0. Lower = faster cursor, higher = more
         *  precise. */
        const val TRACKPAD_PX_PER_CHAR_DP = 18f
        /** Finger pixels per one line of vertical cursor movement at
         *  sensitivity 1.0 — intentionally larger than the horizontal
         *  ratio because a stray line-skip is more disruptive than a
         *  stray character skip. */
        const val TRACKPAD_PX_PER_LINE_DP = 36f
    }

    override fun onDetachedFromWindow() {
        dismissPopup()
        dismissActionMenu()
        endTrackpad()
        hideEmojiPicker()
        hideClipboard()
        super.onDetachedFromWindow()
    }

    private fun startRepeat(view: KeyView) {
        val initialDelay = 350L
        val interval = 45L
        val runnable = object : Runnable {
            override fun run() {
                listener?.onKey(view.key, modifiers)
                handler.postDelayed(this, interval)
            }
        }
        repeatRunnables[view] = runnable
        handler.postDelayed(runnable, initialDelay)
    }

    private fun stopRepeat(view: KeyView) {
        repeatRunnables.remove(view)?.also { handler.removeCallbacks(it) }
    }

    private fun handleKey(key: Key) {
        // While the emoji search header is mounted the keyboard's char,
        // letter, space and backspace keys steer the query instead of the
        // input field; modifier presses still toggle their state so visual
        // feedback stays consistent. Everything else (Enter, Tab, Esc,
        // arrows, function keys, layout switches, settings, …) is ignored
        // to keep the search session focused.
        emojiSearchHeader?.let { header ->
            when (key.type) {
                KeyType.LETTER, KeyType.CHAR -> {
                    header.appendQuery(key.label)
                    refresh()
                    return
                }
                KeyType.SPACE -> {
                    header.appendQuery(" ")
                    refresh()
                    return
                }
                KeyType.BACKSPACE -> {
                    header.deleteFromQuery()
                    refresh()
                    return
                }
                KeyType.SHIFT -> { modifiers.tapShift(); refresh(); return }
                KeyType.CTRL -> { modifiers.tapCtrl(); refresh(); return }
                KeyType.ALT -> { modifiers.tapAlt(); refresh(); return }
                KeyType.META -> { modifiers.tapMeta(); refresh(); return }
                KeyType.CAPS_LOCK -> { modifiers.toggleCapsLock(); refresh(); return }
                else -> { refresh(); return }
            }
        }

        val isModifierToggle = when (key.type) {
            KeyType.SHIFT -> { modifiers.tapShift(); true }
            KeyType.CTRL -> { modifiers.tapCtrl(); true }
            KeyType.ALT -> { modifiers.tapAlt(); true }
            KeyType.META -> { modifiers.tapMeta(); true }
            KeyType.CAPS_LOCK -> { modifiers.toggleCapsLock(); true }
            // Globe tap is intentionally a no-op — long-press shows the menu.
            KeyType.LANGUAGE_SWITCH -> true
            else -> false
        }
        if (!isModifierToggle) {
            listener?.onKey(key, modifiers)
            modifiers.consumeAfterChar()
        }
        refresh()
    }

    private fun forEachKeyView(action: (KeyView) -> Unit) {
        for (i in 0 until rowsContainer.childCount) {
            val row = rowsContainer.getChildAt(i) as? ViewGroup ?: continue
            for (j in 0 until row.childCount) {
                val kv = row.getChildAt(j) as? KeyView ?: continue
                action(kv)
            }
        }
    }

    private fun dp(v: Float): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics).toInt()

    interface Listener {
        fun onKey(key: Key, modifiers: ModifierState)
        /** Trackpad-driven cursor motion. dx / dy are character / line steps. */
        fun onCursorMove(dx: Int, dy: Int)
        /** Globe long-press action menu selection. */
        fun onMenuAction(action: MenuAction)
        /** Commit a literal string (used for multi-codepoint emoji + clip taps). */
        fun onText(text: String)
        /** A word was tapped on the suggestion bar. */
        fun onSuggestionPicked(word: String)
        /** Open the clipboard editor activity for the given existing entry. */
        fun onClipboardEdit(text: String)
        /** Open this app's system "App info" page so the user can grant
         *  RECORD_AUDIO from there. IME services can't request runtime
         *  permissions directly, hence the deep-link. */
        fun onOpenAppSettings()
    }
}
