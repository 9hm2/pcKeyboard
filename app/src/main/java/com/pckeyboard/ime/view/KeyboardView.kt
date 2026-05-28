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

    // Long-press popup state — rendered as a child of this FrameLayout so it
    // is always visible on top of the keys (a PopupWindow is unreliable in
    // an IME's input window: the system layer often puts it behind the IME).
    private var popupView: KeyPopupView? = null

    // Space-trackpad state.
    private var trackpadView: TrackpadView? = null
    private var trackpadActive: Boolean = false
    private var trackpadArmed: Boolean = false
    private var trackpadLastX: Float = 0f
    private var trackpadLastY: Float = 0f
    private var trackpadDxAccum: Float = 0f
    private var trackpadDyAccum: Float = 0f

    init {
        addView(
            rowsContainer,
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
            val targetHeight = (base * prefs.heightScale).toInt()
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
                ).apply {
                    topMargin = spacing
                    bottomMargin = spacing
                    leftMargin = spacing
                    rightMargin = spacing
                }
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
        if (view.key.type == KeyType.SPACE) {
            showTrackpad()
            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            return
        }
        val chars = view.key.popupChars ?: return
        showPopup(view, chars)
        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
    }

    override fun onKeyPopupMove(view: KeyView, rawX: Float, rawY: Float) {
        if (trackpadActive) {
            handleTrackpadMove(rawX, rawY)
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
        if (trackpadActive) {
            endTrackpad()
            return
        }
        dismissPopup()
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
        trackpadDxAccum = 0f
        trackpadDyAccum = 0f
        // Don't toggle rowsContainer visibility: the trackpad's opaque draw
        // covers the rows by itself, and toggling visibility can trigger
        // ACTION_CANCEL on the still-active space-press gesture, which would
        // immediately tear the trackpad back down.
        addView(tp, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

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
                trackpadLastX = rawX
                trackpadLastY = rawY
                tp.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            }
            return
        }

        val stepPx = dp(10f).toFloat()
        trackpadDxAccum += rawX - trackpadLastX
        trackpadDyAccum += rawY - trackpadLastY
        trackpadLastX = rawX
        trackpadLastY = rawY

        var dxSteps = 0
        var dySteps = 0
        while (abs(trackpadDxAccum) >= stepPx) {
            if (trackpadDxAccum > 0) { dxSteps++; trackpadDxAccum -= stepPx }
            else { dxSteps--; trackpadDxAccum += stepPx }
        }
        while (abs(trackpadDyAccum) >= stepPx) {
            if (trackpadDyAccum > 0) { dySteps++; trackpadDyAccum -= stepPx }
            else { dySteps--; trackpadDyAccum += stepPx }
        }
        // Route cursor motion through a dedicated callback so the service
        // can move the cursor with InputConnection.setSelection instead of
        // sending DPAD KeyEvents — DPADs can move focus to a sibling view
        // (e.g. the Send button next to a chat input), which fires
        // onFinishInput and looks to the user like the keyboard vanishing.
        if (dxSteps != 0 || dySteps != 0) {
            listener?.onCursorMove(dxSteps, dySteps)
        }
    }

    private fun endTrackpad() {
        trackpadView?.let { removeView(it) }
        trackpadView = null
        trackpadActive = false
        trackpadArmed = false
    }

    override fun onDetachedFromWindow() {
        dismissPopup()
        endTrackpad()
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
        val isModifierToggle = when (key.type) {
            KeyType.SHIFT -> { modifiers.tapShift(); true }
            KeyType.CTRL -> { modifiers.tapCtrl(); true }
            KeyType.ALT -> { modifiers.tapAlt(); true }
            KeyType.META -> { modifiers.tapMeta(); true }
            KeyType.CAPS_LOCK -> { modifiers.toggleCapsLock(); true }
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
    }
}
