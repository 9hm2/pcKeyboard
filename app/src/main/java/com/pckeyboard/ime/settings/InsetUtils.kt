package com.pckeyboard.ime.settings

import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding

/**
 * Adds the system-bar bottom inset on top of the view's existing
 * paddingBottom, leaving left/right/top untouched. Use on scrollables so
 * their content doesn't sit behind the nav bar.
 */
fun View.addSystemBarBottomPadding() {
    val initialBottom = paddingBottom
    ViewCompat.setOnApplyWindowInsetsListener(this) { v, insets ->
        val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
        v.updatePadding(bottom = initialBottom + bars.bottom)
        insets
    }
}

/** Adds the system-bar bottom & right inset onto the view's margin. */
fun View.addSystemBarBottomEndMargin() {
    val lp = layoutParams as? ViewGroup.MarginLayoutParams ?: return
    val initialBottom = lp.bottomMargin
    val initialEnd = lp.rightMargin
    ViewCompat.setOnApplyWindowInsetsListener(this) { v, insets ->
        val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
        v.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            bottomMargin = initialBottom + bars.bottom
            rightMargin = initialEnd + bars.right
        }
        insets
    }
}

/**
 * Used on standalone (no-AppBar) screens like SetupActivity: adds the
 * system bar top/bottom/left/right insets onto the view's padding.
 */
fun View.addSystemBarPadding() {
    val initialL = paddingLeft
    val initialT = paddingTop
    val initialR = paddingRight
    val initialB = paddingBottom
    ViewCompat.setOnApplyWindowInsetsListener(this) { v, insets ->
        val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
        v.updatePadding(
            left = initialL + bars.left,
            top = initialT + bars.top,
            right = initialR + bars.right,
            bottom = initialB + bars.bottom
        )
        insets
    }
}
