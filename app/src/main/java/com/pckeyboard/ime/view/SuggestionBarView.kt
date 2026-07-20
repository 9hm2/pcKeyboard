package com.pckeyboard.ime.view

import android.content.Context
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import com.pckeyboard.ime.theme.KeyboardTheme

/**
 * Suggestion strip pinned between the popup zone and the keyboard rows.
 * Horizontally scrollable: the best candidate sits leftmost (rendered
 * bolder), and swiping the strip reveals the longer tail of variants.
 * Tapping a chip commits it via [onPick]. The bar keeps its height when
 * there are no candidates so the keyboard doesn't jump while typing.
 */
class SuggestionBarView(
    context: Context,
    private val theme: KeyboardTheme,
    private val onPick: (String) -> Unit
) : HorizontalScrollView(context) {

    private val row = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
    }

    init {
        setBackgroundColor(theme.backgroundColor)
        isHorizontalScrollBarEnabled = false
        isFillViewport = true
        addView(row, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT))
    }

    fun setSuggestions(words: List<String>) {
        row.removeAllViews()
        for ((i, word) in words.withIndex()) {
            if (i > 0) row.addView(makeDivider())
            row.addView(makeChip(word, best = i == 0))
        }
        scrollTo(0, 0)
    }

    private fun makeChip(word: String, best: Boolean): TextView =
        TextView(context).apply {
            text = word
            gravity = Gravity.CENTER
            maxLines = 1
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTextColor(if (best) theme.accentColor else theme.keyTextColor)
            typeface = if (best) Typeface.create("sans-serif-medium", Typeface.NORMAL)
                       else Typeface.create("sans-serif", Typeface.NORMAL)
            minimumWidth = dp(88f)
            setPadding(dp(14f), 0, dp(14f), 0)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.MATCH_PARENT
            )
            isClickable = true
            setOnClickListener {
                performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                onPick(word)
            }
        }

    private fun makeDivider(): View = View(context).apply {
        setBackgroundColor(theme.modifierKeyColor)
        layoutParams = LinearLayout.LayoutParams(dp(1f), LinearLayout.LayoutParams.MATCH_PARENT)
            .apply { topMargin = dp(10f); bottomMargin = dp(10f) }
    }

    private fun dp(v: Float): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics).toInt()

    companion object {
        /** Bar height in dp — added to the IME view height while mounted. */
        const val BAR_DP = 42f
        /** How many candidates the strip requests from the engine. */
        const val MAX_SUGGESTIONS = 8
    }
}
