package com.pckeyboard.ime.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.TypedValue
import android.view.View
import com.pckeyboard.ime.theme.KeyboardTheme

/**
 * Vertical action menu shown on long-press of the globe key. Each item
 * has an icon + label and represents one [MenuAction] the service runs on
 * release.
 *
 * Touch is NOT handled here — the underlying KeyView keeps capturing the
 * gesture and forwards rawY via [findIndexForY], same pattern as
 * [KeyPopupView].
 */
class ActionMenuView(
    context: Context,
    val items: List<MenuItem>,
    private val theme: KeyboardTheme
) : View(context) {

    var selectedIndex: Int = -1
        set(value) {
            if (field != value) { field = value; invalidate() }
        }

    val itemHeight: Int = dp(46f)
    val pad: Int = dp(6f)
    val menuWidth: Int = dp(220f)
    private val cornerRadius: Float = dp(14f).toFloat()
    private val itemCornerRadius: Float = dp(8f).toFloat()

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.LEFT
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        textSize = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP, 15f, resources.displayMetrics
        )
    }
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP, 18f, resources.displayMetrics
        )
    }
    private val rect = RectF()

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(menuWidth, itemHeight * items.size + pad * 2)
    }

    fun findIndexForY(y: Float): Int {
        if (items.isEmpty()) return -1
        val inside = y - pad
        return (inside / itemHeight).toInt().coerceIn(0, items.size - 1)
    }

    override fun onDraw(canvas: Canvas) {
        // Outer card.
        bgPaint.color = theme.modifierKeyColor
        rect.set(0f, 0f, width.toFloat(), height.toFloat())
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, bgPaint)

        for ((i, item) in items.withIndex()) {
            val top = pad + i * itemHeight
            val itemRect = RectF(
                pad.toFloat(), top.toFloat(),
                (width - pad).toFloat(), (top + itemHeight).toFloat()
            )

            if (i == selectedIndex) {
                bgPaint.color = theme.accentColor
                canvas.drawRoundRect(itemRect, itemCornerRadius, itemCornerRadius, bgPaint)
                labelPaint.color = theme.accentTextColor
                iconPaint.color = theme.accentTextColor
            } else if (item.isCurrent) {
                bgPaint.color = theme.keyBackgroundColor
                canvas.drawRoundRect(itemRect, itemCornerRadius, itemCornerRadius, bgPaint)
                labelPaint.color = theme.keyTextColor
                iconPaint.color = theme.accentColor
            } else {
                labelPaint.color = theme.modifierTextColor
                iconPaint.color = theme.modifierTextColor
            }

            val iconCx = itemRect.left + dp(18f)
            val cy = itemRect.centerY() - (labelPaint.descent() + labelPaint.ascent()) / 2
            canvas.drawText(item.icon, iconCx, cy, iconPaint)
            canvas.drawText(item.label, itemRect.left + dp(40f), cy, labelPaint)
        }
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
    /** Switch to a specific language pack id (e.g. "en_US"). */
    data class SwitchLanguage(val packId: String) : MenuAction()
    /** Paste the current primary clip. */
    object PasteClipboard : MenuAction()
    /** Show the emoji picker overlay. */
    object OpenEmoji : MenuAction()
    /** Launch the keyboard's Settings activity. */
    object OpenSettings : MenuAction()
}
