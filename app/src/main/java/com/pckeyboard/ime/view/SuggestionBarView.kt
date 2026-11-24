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

/** What a strip chip means — drives both its look and what a tap does. */
enum class ChipKind {
    /** An ordinary candidate. */
    NORMAL,
    /** The candidate the next Space will auto-commit (accent pill). */
    AUTO,
    /** The word exactly as typed — tapping keeps it and teaches it. */
    VERBATIM,
    /** Takes back the last auto-correction (carries the original word). */
    REVERT,
    /** Takes back the last Proofread. */
    UNDO_PROOFREAD
}

/** [text] is what a tap acts on; [label] is what's drawn (defaults to it). */
data class SuggestionChip(
    val text: String,
    val kind: ChipKind = ChipKind.NORMAL,
    val label: String = text
)

/**
 * Suggestion strip pinned between the popup zone and the keyboard rows.
 * Horizontally scrollable: the best candidate sits leftmost (rendered
 * as an accent pill), and swiping the strip reveals the longer tail of
 * variants. Tapping a chip commits it via [onPick]. KeyboardView only
 * mounts this while there is content — no candidates, no strip.
 */
class SuggestionBarView(
    context: Context,
    private val theme: KeyboardTheme,
    private val onPick: (SuggestionChip) -> Unit
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

    fun setSuggestions(chips: List<SuggestionChip>) {
        row.removeAllViews()
        for ((i, chip) in chips.withIndex()) {
            if (i > 0) row.addView(makeDivider())
            row.addView(makeChip(chip))
        }
        scrollTo(0, 0)
    }

    private fun makeChip(chip: SuggestionChip): TextView {
        val best = chip.kind == ChipKind.AUTO
        return TextView(context).apply {
            text = when (chip.kind) {
                // Quotes mark "this exactly as typed".
                ChipKind.VERBATIM -> "\u201C${chip.label}\u201D"
                else -> chip.label
            }
            gravity = Gravity.CENTER
            maxLines = 1
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            // The best candidate is rendered as an accent-coloured pill
            // with the theme's accent TEXT colour — the same pair the
            // Space/Enter keys use, so it stays legible even when the
            // accent colour itself is pale against the bar background
            // (colouring just the text with the accent was unreadable
            // on low-contrast themes).
            if (best) {
                setTextColor(theme.accentTextColor)
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                background = android.graphics.drawable.GradientDrawable().apply {
                    setColor(theme.accentColor)
                    cornerRadius = dp(10f).toFloat()
                }
            } else {
                val special = chip.kind == ChipKind.REVERT ||
                    chip.kind == ChipKind.UNDO_PROOFREAD
                setTextColor(
                    when {
                        chip.kind == ChipKind.VERBATIM -> theme.secondaryTextColor
                        special -> theme.accentColor
                        else -> theme.keyTextColor
                    }
                )
                typeface = Typeface.create(
                    "sans-serif",
                    if (chip.kind == ChipKind.VERBATIM) Typeface.ITALIC else Typeface.NORMAL
                )
            }
            minimumWidth = dp(88f)
            setPadding(dp(16f), 0, dp(16f), 0)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.MATCH_PARENT
            ).apply {
                if (best) {
                    topMargin = dp(6f); bottomMargin = dp(6f)
                    leftMargin = dp(6f); rightMargin = dp(2f)
                }
            }
            isClickable = true
            setOnClickListener {
                performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                onPick(chip)
            }
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
