package com.pckeyboard.ime.settings

import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding

/**
 * Adds the system-bar bottom inset on top of the view's existing
 * paddingBottom, leaving left/right/top untouched. Also adds the IME
 * inset so the view's content stays above the on-screen keyboard when
 * one is shown for an EditText inside.
 */
fun View.addSystemBarBottomPadding() {
    val initialBottom = paddingBottom
    ViewCompat.setOnApplyWindowInsetsListener(this) { v, insets ->
        val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
        val ime  = insets.getInsets(WindowInsetsCompat.Type.ime())
        v.updatePadding(bottom = initialBottom + maxOf(bars.bottom, ime.bottom))
        insets
    }
}

/** Adds the system-bar bottom & right inset onto the view's margin, plus
 *  the IME inset bottom so floating buttons (FABs) ride above the
 *  keyboard. */
fun View.addSystemBarBottomEndMargin() {
    val lp = layoutParams as? ViewGroup.MarginLayoutParams ?: return
    val initialBottom = lp.bottomMargin
    val initialEnd = lp.rightMargin
    ViewCompat.setOnApplyWindowInsetsListener(this) { v, insets ->
        val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
        val ime  = insets.getInsets(WindowInsetsCompat.Type.ime())
        v.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            bottomMargin = initialBottom + maxOf(bars.bottom, ime.bottom)
            rightMargin = initialEnd + bars.right
        }
        insets
    }
}

/**
 * Used on standalone (no-AppBar) screens like SetupActivity: adds the
 * system bar top/bottom/left/right insets onto the view's padding. The
 * bottom inset also folds in the IME so content stays visible when the
 * keyboard pops up.
 */
fun View.addSystemBarPadding() {
    val initialL = paddingLeft
    val initialT = paddingTop
    val initialR = paddingRight
    val initialB = paddingBottom
    ViewCompat.setOnApplyWindowInsetsListener(this) { v, insets ->
        val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
        val ime  = insets.getInsets(WindowInsetsCompat.Type.ime())
        v.updatePadding(
            left = initialL + bars.left,
            top = initialT + bars.top,
            right = initialR + bars.right,
            bottom = initialB + maxOf(bars.bottom, ime.bottom)
        )
        insets
    }
}
