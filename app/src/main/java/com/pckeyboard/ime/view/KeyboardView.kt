package com.pckeyboard.ime.view

import android.content.Context
import android.graphics.Color
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
            // Pick a sensible default height: per-row size scaled to screen width.
            val rows = layoutData?.rows?.size ?: 5
            val widthDp = resources.displayMetrics.widthPixels / resources.displayMetrics.density
            // Foldable / tablet: smaller per-row height because we have more rows.
            val perRowDp = if (widthDp >= 600f) 46f else 52f
            val targetHeight = dp(perRowDp) * rows + dp((theme?.keySpacingDp ?: 3).toFloat()) * (rows + 1)
            val resolved = if (mode == MeasureSpec.AT_MOST) {
                minOf(MeasureSpec.getSize(heightMeasureSpec), targetHeight)
            } else targetHeight
            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(resolved, MeasureSpec.EXACTLY))
        } else {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        }
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
            val totalWeight = row.sumOf { it.widthWeight.toDouble() }.toFloat()
            for (key in row) {
                val kv = KeyView(context, key, theme, modifiers).apply {
                    listener = this@KeyboardView
                    layoutParams = LayoutParams(0, LayoutParams.MATCH_PARENT, key.widthWeight / totalWeight)
                }
                rowView.addView(kv)
            }
            addView(rowView)
        }
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
