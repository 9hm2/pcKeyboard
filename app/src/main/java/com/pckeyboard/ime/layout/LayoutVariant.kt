package com.pckeyboard.ime.layout

import com.pckeyboard.ime.model.KeyboardLayout

/**
 * Two flavours of the same locale layout:
 *  - COMPACT: drops the F-key row to save vertical space on narrow phones.
 *  - FULL:    Samsung-style PC layout, used on tablets and unfolded foldables.
 *
 * Decided in [PcKeyboardService] based on screen width.
 */
enum class LayoutVariant { COMPACT, FULL }

object LayoutSelector {
    /** Threshold above which we show the full PC keyboard. */
    const val FULL_WIDTH_DP_THRESHOLD = 600

    fun pick(widthDp: Int): LayoutVariant =
        if (widthDp >= FULL_WIDTH_DP_THRESHOLD) LayoutVariant.FULL else LayoutVariant.COMPACT

    fun apply(base: KeyboardLayout, variant: LayoutVariant): KeyboardLayout {
        if (variant == LayoutVariant.FULL) return base
        // Compact: drop the function key row (assumed to be the first row).
        if (base.rows.isEmpty()) return base
        val first = base.rows.first()
        val firstIsFnRow = first.any { it.label.matches(Regex("F\\d{1,2}")) }
        val rows = if (firstIsFnRow) base.rows.drop(1) else base.rows
        return base.copy(rows = rows)
    }
}
