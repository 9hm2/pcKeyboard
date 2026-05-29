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
import kotlin.math.abs

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

    /** Vertical stack holding [emojiSearchHeader] (optional, on top) and
     *  [rowsContainer]. Wraps both so the header can push the keyboard rows
     *  down without overlapping them while emoji search is active. */
    private val mainContainer = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
    }

    // Long-press popup state — rendered as a child of this FrameLayout so it
    // is always visible on top of the keys (a PopupWindow is unreliable in
    // an IME's input window: the system layer often puts it behind the IME).
    private var popupView: KeyPopupView? = null

    // Globe long-press: vertical action menu, same touch routing pattern.
    private var actionMenuView: ActionMenuView? = null

    // Emoji picker overlay shown via the action menu "Emoji" item.
    private var emojiView: EmojiView? = null

    // Emoji search slim header — when present, sits inside mainContainer
    // above the keyboard rows; KeyboardView funnels char/letter/space/
    // backspace presses into its query while it's mounted.
    private var emojiSearchHeader: EmojiSearchHeaderView? = null

    // Clipboard manager overlay.
    private var clipboardView: com.pckeyboard.ime.clipboard.ClipboardView? = null
    private val clipHistory = com.pckeyboard.ime.clipboard.ClipboardHistory(context)

    // Space-trackpad state. After the user slides their finger to the
    // indicator and "arms" the trackpad, we capture the anchor position;
    // every tick the deflection from that anchor drives an analog-stick
    // style speed (with a small dead zone in the centre and a quadratic
    // ramp so fine control is possible near the anchor without giving up
    // top speed at the edges).
    private var trackpadView: TrackpadView? = null
    private var trackpadActive: Boolean = false
    private var trackpadArmed: Boolean = false
    private var trackpadAnchorX: Float = 0f
    private var trackpadAnchorY: Float = 0f
    private var trackpadCurrentX: Float = 0f
    private var trackpadCurrentY: Float = 0f
    private var trackpadAccumX: Float = 0f
    private var trackpadAccumY: Float = 0f
    private var trackpadTickRunnable: Runnable? = null
    /** Sensitivity multiplier captured at arming time so the cursor speed
     *  stays consistent across a single drag even if the user changes the
     *  preference in another session. */
    private var trackpadSensitivity: Float = 1f

    init {
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
        setBackgroundColor(theme.backgroundColor)
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
            // Grow the IME view by the search header height so the full
            // keyboard stays at its normal size while the user is typing
            // into the emoji search query above it.
            val extra = if (emojiSearchHeader != null) dp(SEARCH_HEADER_DP) else 0
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
            // Per spec: don't commit Space whether the user armed the trackpad
            // or not — long-press alone is intent enough to suppress the tap.
            endTrackpad()
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
        val theme = theme ?: return
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
        val items = langItems + listOf(
            MenuItem("😀", "Emoji",            MenuAction.OpenEmoji),
            MenuItem("📋", "Clipboard",        MenuAction.OpenClipboard),
            MenuItem("⚙",  "Keyboard settings", MenuAction.OpenSettings)
        )
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
        if (y < 0) y = 0

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
                override fun onSpace() {
                    this@KeyboardView.listener?.onKey(
                        Key(' '.code, " ", type = KeyType.SPACE), modifiers
                    )
                }
                override fun onEnter() {
                    this@KeyboardView.listener?.onKey(
                        Key(0, "", type = KeyType.ENTER), modifiers
                    )
                }
                override fun onSearch() {
                    showEmojiSearchHeader()
                }
            }
        }
        emojiView = view
        rowsContainer.visibility = INVISIBLE
        addView(view, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    fun hideEmojiPicker() {
        emojiView?.let { removeView(it) }
        emojiView = null
        rowsContainer.visibility = VISIBLE
    }

    fun isEmojiOpen(): Boolean = emojiView != null

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
        mainContainer.addView(header, 0, LinearLayout.LayoutParams(
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
        addView(view, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
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

        // Centre the BASE cell over the anchor, not the whole popup. That keeps
        // the resting selection on the base char and makes left/right slides
        // map cleanly to neighbouring options.
        val baseCellCenterInPopup = popup.pad + popup.cellWidth * baseIndex + popup.cellWidth / 2f
        var popupX = (anchorCenterX - baseCellCenterInPopup).toInt()
        var popupY = anchorY - popupH - dp(4f)

        // Clamp horizontally inside the keyboard.
        if (popupX < 0) popupX = 0
        if (popupX + popupW > width) popupX = (width - popupW).coerceAtLeast(0)

        // Never fall below the anchor: clamp to the top of the keyboard if
        // there isn't enough room above. Worst case the popup overlaps the
        // anchor visually, but the user is looking at the popup at that point.
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
        val tp = TrackpadView(context, theme)
        trackpadView = tp
        trackpadActive = true
        trackpadArmed = false
        trackpadAccumX = 0f
        trackpadAccumY = 0f
        // Don't toggle rowsContainer visibility: the trackpad's opaque draw
        // covers the rows by itself, and toggling visibility can trigger
        // ACTION_CANCEL on the still-active space-press gesture, which would
        // immediately tear the trackpad back down.
        addView(tp, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    /**
     * After arming, MOVE events just store the latest position; the actual
     * cursor motion is driven by [trackpadTickRunnable], which samples the
     * deflection from the armed anchor every [TRACKPAD_TICK_MS] and adds
     * speed-from-deflection to the accumulators.
     */
    private fun handleTrackpadMove(rawX: Float, rawY: Float) {
        val tp = trackpadView ?: return
        val loc = IntArray(2)
        tp.getLocationOnScreen(loc)
        val localX = rawX - loc[0]
        val localY = rawY - loc[1]

        if (!trackpadArmed) {
            if (tp.isInsideIndicator(localX, localY)) {
                trackpadArmed = true
                tp.armed = true
                trackpadAnchorX = rawX
                trackpadAnchorY = rawY
                trackpadCurrentX = rawX
                trackpadCurrentY = rawY
                trackpadSensitivity =
                    com.pckeyboard.ime.settings.KeyboardPrefs(context).trackpadSensitivity
                tp.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                startTrackpadTick()
            }
            return
        }
        trackpadCurrentX = rawX
        trackpadCurrentY = rawY
    }

    private fun startTrackpadTick() {
        stopTrackpadTick()
        val deadZonePx = dp(TRACKPAD_DEAD_ZONE_DP).toFloat()
        val maxDeflectionPx = dp(TRACKPAD_MAX_DEFLECTION_DP).toFloat()
        val dtSec = TRACKPAD_TICK_MS / 1000f
        val runnable = object : Runnable {
            override fun run() {
                val dx = trackpadCurrentX - trackpadAnchorX
                val dy = trackpadCurrentY - trackpadAnchorY
                trackpadAccumX += analogSpeed(dx, deadZonePx, maxDeflectionPx,
                    TRACKPAD_MAX_HORIZONTAL_CHARS_PER_SEC * trackpadSensitivity) * dtSec
                trackpadAccumY += analogSpeed(dy, deadZonePx, maxDeflectionPx,
                    TRACKPAD_MAX_VERTICAL_LINES_PER_SEC * trackpadSensitivity) * dtSec
                var dxSteps = 0
                var dySteps = 0
                while (trackpadAccumX >=  1f) { dxSteps++; trackpadAccumX -= 1f }
                while (trackpadAccumX <= -1f) { dxSteps--; trackpadAccumX += 1f }
                while (trackpadAccumY >=  1f) { dySteps++; trackpadAccumY -= 1f }
                while (trackpadAccumY <= -1f) { dySteps--; trackpadAccumY += 1f }
                // Route cursor motion through a dedicated callback so the
                // service moves the cursor with InputConnection.setSelection
                // instead of DPAD KeyEvents — DPADs can move focus to a
                // sibling view (e.g. the Send button next to a chat input).
                if (dxSteps != 0 || dySteps != 0) {
                    listener?.onCursorMove(dxSteps, dySteps)
                }
                handler.postDelayed(this, TRACKPAD_TICK_MS)
            }
        }
        trackpadTickRunnable = runnable
        handler.postDelayed(runnable, TRACKPAD_TICK_MS)
    }

    private fun stopTrackpadTick() {
        trackpadTickRunnable?.let { handler.removeCallbacks(it) }
        trackpadTickRunnable = null
    }

    /**
     * Maps a signed deflection (px) to a signed speed (units / sec) along
     * the same axis. Inside the dead zone the speed is zero; outside it
     * the curve is quadratic, so the first millimetres past the dead zone
     * produce gentle motion and the cursor only races when the finger is
     * pushed near the indicator's edge — exactly like an analog stick.
     */
    private fun analogSpeed(deflection: Float, deadZone: Float, maxDef: Float, maxSpeed: Float): Float {
        val absDef = abs(deflection)
        if (absDef <= deadZone) return 0f
        val effective = (absDef - deadZone).coerceAtMost(maxDef - deadZone)
        val maxEffective = (maxDef - deadZone).coerceAtLeast(1f)
        val normalized = effective / maxEffective
        val curved = normalized * normalized
        return if (deflection > 0) curved * maxSpeed else -curved * maxSpeed
    }

    private fun endTrackpad() {
        stopTrackpadTick()
        trackpadView?.let { removeView(it) }
        trackpadView = null
        trackpadActive = false
        trackpadArmed = false
        trackpadAccumX = 0f
        trackpadAccumY = 0f
    }

    private companion object {
        /** Height of the emoji search overlay header (query + results strip). */
        const val SEARCH_HEADER_DP = 104f
        /** Sampling interval for the analog-stick tick. 50 ms ≈ 20 Hz. */
        const val TRACKPAD_TICK_MS = 50L
        /** Radius inside which the cursor doesn't move at all (px-equiv in dp). */
        const val TRACKPAD_DEAD_ZONE_DP = 4f
        /** Distance from the anchor where the analog curve hits max speed. */
        const val TRACKPAD_MAX_DEFLECTION_DP = 70f
        /** Cursor velocity ceiling on the horizontal axis. */
        const val TRACKPAD_MAX_HORIZONTAL_CHARS_PER_SEC = 14f
        /** Cursor velocity ceiling on the vertical axis — kept noticeably
         *  lower than horizontal because a stray line-skip is worse than a
         *  stray character skip. */
        const val TRACKPAD_MAX_VERTICAL_LINES_PER_SEC = 4f
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
        /** Open the clipboard editor activity for the given existing entry. */
        fun onClipboardEdit(text: String)
    }
}
