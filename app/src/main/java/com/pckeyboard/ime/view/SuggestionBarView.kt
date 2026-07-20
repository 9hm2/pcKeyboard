package com.pckeyboard.ime.view

import android.content.Context
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.pckeyboard.ime.theme.KeyboardTheme

/**
 * Thin suggestion strip pinned between the popup zone and the keyboard
 * rows. Shows up to [SLOTS] candidates; tapping one commits it via
 * [onPick]. The bar keeps its height when there are no candidates so
 * the keyboard doesn't jump while typing — the slots just go blank.
 */
class SuggestionBarView(
    context: Context,
    private val theme: KeyboardTheme,
    private val onPick: (String) -> Unit
) : LinearLayout(context) {

    private val slots = mutableListOf<TextView>()
    private val dividers = mutableListOf<View>()

    init {
        orientation = HORIZONTAL
        setBackgroundColor(theme.backgroundColor)
        for (i in 0 until SLOTS) {
            if (i > 0) {
                val divider = View(context).apply {
                    setBackgroundColor(theme.modifierKeyColor)
                    layoutParams = LayoutParams(dp(1f), LayoutParams.MATCH_PARENT).apply {
                        topMargin = dp(10f); bottomMargin = dp(10f)
                    }
                    visibility = INVISIBLE
                }
                dividers.add(divider)
                addView(divider)
            }
            val slot = TextView(context).apply {
                gravity = Gravity.CENTER
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                setTextColor(theme.keyTextColor)
                typeface = if (i == 1) Typeface.create("sans-serif-medium", Typeface.NORMAL)
                           else Typeface.create("sans-serif", Typeface.NORMAL)
                layoutParams = LayoutParams(0, LayoutParams.MATCH_PARENT, 1f)
                isClickable = true
                setOnClickListener {
                    performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    (tag as? String)?.let(onPick)
                }
            }
            slots.add(slot)
            addView(slot)
        }
    }

    /** Updates the strip. Best candidate goes to the middle slot, second
     *  to the left, third to the right — the Gboard convention users'
     *  thumbs already know. */
    fun setSuggestions(words: List<String>) {
        val bySlot = arrayOfNulls<String>(SLOTS)
        words.getOrNull(0)?.let { bySlot[1] = it }
        words.getOrNull(1)?.let { bySlot[0] = it }
        words.getOrNull(2)?.let { bySlot[2] = it }
        for (i in 0 until SLOTS) {
            slots[i].text = bySlot[i] ?: ""
            slots[i].tag = bySlot[i]
        }
        for ((i, divider) in dividers.withIndex()) {
            // Divider i sits before slot i+1 — show it only when both
            // neighbours have content.
            divider.visibility =
                if (bySlot[i] != null && bySlot[i + 1] != null) VISIBLE else INVISIBLE
        }
    }

    private fun dp(v: Float): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics).toInt()

    companion object {
        const val SLOTS = 3
        /** Bar height in dp — added to the IME view height while mounted. */
        const val BAR_DP = 42f
    }
}
