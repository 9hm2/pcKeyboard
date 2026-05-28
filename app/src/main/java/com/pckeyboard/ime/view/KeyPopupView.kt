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
 * Long-press popup. Renders a horizontal row of alternate characters and
 * tracks which one is currently under the user's finger via [selectedIndex].
 *
 * Touch is NOT handled here — the underlying KeyView keeps capturing the
 * gesture and forwards x-coordinates to [findIndexForX] so we don't fight
 * Android's touch-routing rules in a separate PopupWindow.
 */
class KeyPopupView(
    context: Context,
    val chars: List<String>,
    private val theme: KeyboardTheme
) : View(context) {

    var selectedIndex: Int = -1
        set(value) {
            if (field != value) { field = value; invalidate() }
        }

    val cellWidth: Int = dp(44f)
    val cellHeight: Int = dp(56f)
    private val pad: Float = dp(4f).toFloat()
    private val cornerRadius: Float = dp(10f).toFloat()

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        textSize = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP, 22f, resources.displayMetrics
        )
    }
    private val rect = RectF()

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(
            cellWidth * chars.size + (pad * 2).toInt(),
            cellHeight + (pad * 2).toInt()
        )
    }

    /** Returns popup char index for a touch x-coord local to this view. */
    fun findIndexForX(x: Float): Int {
        val inside = x - pad
        if (inside < 0f || inside > cellWidth * chars.size) return -1
        return (inside / cellWidth).toInt().coerceIn(0, chars.size - 1)
    }

    override fun onDraw(canvas: Canvas) {
        // overall background card
        bgPaint.color = theme.modifierKeyColor
        rect.set(0f, 0f, width.toFloat(), height.toFloat())
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, bgPaint)

        for (i in chars.indices) {
            val cellLeft = pad + i * cellWidth
            rect.set(cellLeft, pad, cellLeft + cellWidth, pad + cellHeight)
            if (i == selectedIndex) {
                bgPaint.color = theme.accentColor
                canvas.drawRoundRect(rect, cornerRadius, cornerRadius, bgPaint)
                textPaint.color = theme.accentTextColor
            } else {
                textPaint.color = theme.keyTextColor
            }
            val cy = rect.centerY() - (textPaint.descent() + textPaint.ascent()) / 2
            canvas.drawText(chars[i], rect.centerX(), cy, textPaint)
        }
    }

    private fun dp(v: Float): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics).toInt()
}
