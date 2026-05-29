package com.pckeyboard.ime.view

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.widget.NestedScrollView
import com.pckeyboard.ime.theme.KeyboardTheme

/**
 * Vertical action menu shown on long-press of the globe key.
 *
 * Built as a [NestedScrollView] wrapping a stack of TextView rows, so it
 * grows when extra items are added and scrolls inside the keyboard's
 * bounds instead of being clipped. Items are tapped, not slid — long-press
 * pops the menu, the user lifts their finger, then taps the desired row.
 * Tapping outside the menu dismisses it (handled in KeyboardView).
 */
class ActionMenuView(
    context: Context,
    val items: List<MenuItem>,
    private val theme: KeyboardTheme,
    private val onAction: (MenuAction) -> Unit
) : NestedScrollView(context) {

    val menuWidth: Int = dp(240f)

    init {
        val bg = GradientDrawable().apply {
            setColor(theme.modifierKeyColor)
            cornerRadius = dp(14f).toFloat()
        }
        background = bg
        clipToOutline = true
        isFillViewport = false

        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(6f), dp(6f), dp(6f), dp(6f))
        }
        for (item in items) {
            column.addView(buildRow(item))
        }
        addView(column, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
    }

    private fun buildRow(item: MenuItem): TextView {
        val row = TextView(context).apply {
            text = " ${item.icon}    ${item.label}"
            setPadding(dp(12f), dp(14f), dp(12f), dp(14f))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            gravity = Gravity.CENTER_VERTICAL
            isClickable = true
            isFocusable = true
            if (item.isCurrent) {
                setTextColor(theme.accentColor)
                val highlight = GradientDrawable().apply {
                    setColor(theme.keyBackgroundColor)
                    cornerRadius = dp(8f).toFloat()
                }
                background = highlight
            } else {
                setTextColor(theme.modifierTextColor)
            }
            setOnClickListener {
                performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                onAction(item.action)
            }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(2f); bottomMargin = dp(2f) }
        }
        return row
    }

    private fun dp(v: Float): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics).toInt()
}

data class MenuItem(
    val icon: String,
    val label: String,
    val action: MenuAction,
    /** If true, the item is rendered with a subtle highlight to show "current". */
    val isCurrent: Boolean = false
)

sealed class MenuAction {
    /** Paste the current primary clip. */
    object PasteClipboard : MenuAction()
    /** Show the emoji picker overlay. */
    object OpenEmoji : MenuAction()
    /** Launch the keyboard's Settings activity. */
    object OpenSettings : MenuAction()
}
