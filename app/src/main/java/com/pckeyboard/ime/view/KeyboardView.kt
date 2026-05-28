package com.pckeyboard.ime.view

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.util.TypedValue
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import com.pckeyboard.ime.model.Key
import com.pckeyboard.ime.model.KeyType
import com.pckeyboard.ime.model.KeyboardLayout
import com.pckeyboard.ime.model.ModifierState
import com.pckeyboard.ime.settings.KeyboardPrefs
import com.pckeyboard.ime.theme.KeyboardTheme

/**
 * Renders a KeyboardLayout into a vertical stack of rows. Each row is a
 * horizontal LinearLayout of KeyViews weighted by Key.widthWeight, so the
 * whole keyboard automatically resizes on foldable / unfolded screens.
 */
class KeyboardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : LinearLayout(context, attrs), KeyView.Listener {

    var listener: Listener? = null
    private var layoutData: KeyboardLayout? = null
    private var theme: KeyboardTheme? = null
    private val modifiers = ModifierState()
    private val handler = Handler(Looper.getMainLooper())
    private val repeatRunnables = mutableMapOf<KeyView, Runnable>()
    private val prefs = KeyboardPrefs(context)

    init {
        orientation = VERTICAL
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

    /** Re-applies sizing prefs (height scale, padding, split). Call after the
     *  user changes one of them, or after a configuration change. */
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
        removeAllViews()
        val layout = layoutData ?: return
        val theme = theme ?: return
        val spacing = dp(theme.keySpacingDp.toFloat())

        val widthDp = (resources.displayMetrics.widthPixels /
                resources.displayMetrics.density).toInt()
        val side = (resources.displayMetrics.widthPixels * prefs.horizontalPadding).toInt()
        setPadding(side, 0, side, 0)

        val splitGap = if (prefs.splitEnabled && widthDp >= KeyboardPrefs.SPLIT_MIN_WIDTH_DP)
            prefs.splitGapWeight else 0f

        for (row in layout.rows) {
            val rowView = LinearLayout(context).apply {
                orientation = HORIZONTAL
                layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f).apply {
                    topMargin = spacing
                    bottomMargin = spacing
                    leftMargin = spacing
                    rightMargin = spacing
                }
            }
            val splitIndex = if (splitGap > 0f) findSplitIndex(row) else -1
            val totalWeight = row.sumOf { it.widthWeight.toDouble() }.toFloat() + splitGap
            for ((index, key) in row.withIndex()) {
                val kv = KeyView(context, key, theme, modifiers).apply {
                    listener = this@KeyboardView
                    layoutParams = LayoutParams(0, LayoutParams.MATCH_PARENT, key.widthWeight / totalWeight)
                }
                rowView.addView(kv)
                if (index == splitIndex) {
                    val spacer = View(context).apply {
                        layoutParams = LayoutParams(0, LayoutParams.MATCH_PARENT, splitGap / totalWeight)
                    }
                    rowView.addView(spacer)
                }
            }
            addView(rowView)
        }
    }

    /** Returns the index of the key after which a centre gap should be
     *  inserted: roughly the half-weight split point of the row. */
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
        for (i in 0 until childCount) {
            val row = getChildAt(i) as? ViewGroup ?: continue
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
    }
}
