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
 * Built as a [NestedScrollView] wrapping a stack of TextView rows so it
 * grows when extra items are added and scrolls inside the keyboard's
 * bounds. Supports two interaction styles:
 *
 *  - Tap: release the long-press, then tap a row.
 *  - Slide: keep the finger pressed; [highlightAt] tracks which row the
 *    finger is over, and [commitHover] dispatches the highlighted row's
 *    action when the finger lifts.
 */
class ActionMenuView(
    context: Context,
    val items: List<MenuItem>,
    private val theme: KeyboardTheme,
    private val onAction: (MenuAction) -> Unit
) : NestedScrollView(context) {

    val menuWidth: Int = dp(240f)

    private val rows = mutableListOf<TextView>()
    private var hoveredIndex: Int = -1

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
            val row = buildRow(item)
            rows.add(row)
            column.addView(row)
        }
        addView(column, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        // The menu is anchored above the globe key; the user lifts their
        // finger from the globe onto the menu's BOTTOM edge. Start the
        // content scrolled to the bottom so the very last item sits at
        // the bottom — that's the row the finger lands on first, and
        // sliding up reveals everything above it (auto-scroll in
        // highlightAt keeps going past the top of the viewport when
        // there are more items than fit).
        post { fullScroll(FOCUS_DOWN) }
    }

    private fun buildRow(item: MenuItem): TextView {
        val row = TextView(context).apply {
            text = " ${item.icon}    ${item.label}"
            setPadding(dp(12f), dp(14f), dp(12f), dp(14f))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            gravity = Gravity.CENTER_VERTICAL
            isClickable = true
            isFocusable = true
            setOnClickListener {
                performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                onAction(item.action)
            }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(2f); bottomMargin = dp(2f) }
        }
        applyDefaultStyle(row, item)
        return row
    }

    private fun applyDefaultStyle(row: TextView, item: MenuItem) {
        if (item.isCurrent) {
            row.setTextColor(theme.accentColor)
            val highlight = GradientDrawable().apply {
                setColor(theme.keyBackgroundColor)
                cornerRadius = dp(8f).toFloat()
            }
            row.background = highlight
        } else {
            row.setTextColor(theme.modifierTextColor)
            row.background = null
        }
    }

    private fun applyHoverStyle(row: TextView) {
        row.setTextColor(theme.accentTextColor)
        val hover = GradientDrawable().apply {
            setColor(theme.accentColor)
            cornerRadius = dp(8f).toFloat()
        }
        row.background = hover
    }

    /**
     * Highlight the row whose vertical extent contains the given screen
     * point. Pass coordinates outside the menu bounds to clear the
     * highlight (e.g. while the finger is still on the globe key).
     *
     * When the finger lingers near the top or bottom edge of the
     * viewport, the menu auto-scrolls in that direction so the user can
     * walk through a list that's longer than what fits on-screen.
     */
    fun highlightAt(rawX: Float, rawY: Float) {
        autoScrollAt(rawX, rawY)
        val newIdx = rowIndexAt(rawX, rawY)
        if (newIdx == hoveredIndex) return
        if (hoveredIndex in rows.indices) {
            applyDefaultStyle(rows[hoveredIndex], items[hoveredIndex])
        }
        hoveredIndex = newIdx
        if (hoveredIndex in rows.indices) {
            applyHoverStyle(rows[hoveredIndex])
            performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        }
    }

    private fun autoScrollAt(rawX: Float, rawY: Float) {
        if (width == 0 || height == 0) return
        val loc = IntArray(2)
        getLocationOnScreen(loc)
        val localX = rawX - loc[0]
        val localY = rawY - loc[1]
        // Only auto-scroll when the finger is inside the menu's
        // horizontal extent — otherwise the user is still on the globe.
        if (localX < 0 || localX > width) return
        val edge = dp(36f).toFloat()
        val step = dp(8f)
        val column = getChildAt(0) ?: return
        val maxScroll = (column.height - height).coerceAtLeast(0)
        when {
            localY in 0f..edge && scrollY > 0 -> {
                // Near top edge → scroll up (toward beginning of list).
                scrollBy(0, -step.coerceAtMost(scrollY))
            }
            localY in (height - edge)..height.toFloat() && scrollY < maxScroll -> {
                // Near bottom edge → scroll down (toward end of list).
                scrollBy(0, step.coerceAtMost(maxScroll - scrollY))
            }
        }
    }

    /**
     * If a row is currently highlighted (via [highlightAt]), dispatch its
     * action and return `true`. Otherwise return `false` so the caller
     * knows to leave the menu open for tap selection.
     */
    fun commitHover(): Boolean {
        val idx = hoveredIndex
        if (idx in rows.indices) {
            performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            onAction(items[idx].action)
            return true
        }
        return false
    }

    private fun rowIndexAt(rawX: Float, rawY: Float): Int {
        val loc = IntArray(2)
        getLocationOnScreen(loc)
        val localX = rawX - loc[0]
        val localY = rawY - loc[1]
        if (localX < 0 || localX > width || localY < 0 || localY > height) return -1
        val contentY = localY + scrollY
        for ((i, row) in rows.withIndex()) {
            if (contentY >= row.top && contentY < row.bottom) return i
        }
        return -1
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
    /** Switch to the given language pack id. */
    data class SwitchLanguage(val packId: String) : MenuAction()
    /** Show the emoji picker overlay. */
    object OpenEmoji : MenuAction()
    /** Show the clipboard manager overlay. */
    object OpenClipboard : MenuAction()
    /** Launch the keyboard's Settings activity. */
    object OpenSettings : MenuAction()
    /** Flip the "show function row on narrow screens" preference. */
    object ToggleFunctionRow : MenuAction()
    /** Flip the "side-split" (left half + gap + right half) preference. */
    object ToggleSideSplit : MenuAction()
    /** Open the system speech-recognition overlay anchored to the IME. */
    object OpenVoiceInput : MenuAction()
    /** Reassign the right-of-Space slot ("symbols" / "emoji" / "alt"). */
    data class SetRightOfSpace(val action: String) : MenuAction()
}
