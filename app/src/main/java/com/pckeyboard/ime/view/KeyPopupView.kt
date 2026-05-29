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
    val baseIndex: Int,
    private val theme: KeyboardTheme
) : View(context) {

    var selectedIndex: Int = -1
        set(value) {
            if (field != value) { field = value; invalidate() }
        }

    val pad: Float = dp(6f).toFloat()
    /** Cell width scales with screen + char count so the popup never spills
     *  off the screen — for letters like 'o' with 8+ alternates the cells
     *  shrink towards the floor instead of overflowing the device width. */
    val cellWidth: Int = run {
        val screenW = resources.displayMetrics.widthPixels
        val sideMargin = dp(16f)
        val available = (screenW - (pad.toInt() * 2) - sideMargin).coerceAtLeast(dp(64f))
        val ideal = available / chars.size.coerceAtLeast(1)
        ideal.coerceIn(dp(28f), dp(46f))
    }
    val cellHeight: Int = dp(58f)
    private val cornerRadius: Float = dp(12f).toFloat()
    private val cellCornerRadius: Float = dp(8f).toFloat()

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        textSize = cellWidth * 0.5f
    }
    private val selectedTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif", Typeface.BOLD)
        textSize = cellWidth * 0.58f
    }
    private val rect = RectF()

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(
            cellWidth * chars.size + (pad * 2).toInt(),
            cellHeight + (pad * 2).toInt()
        )
    }

    /** Returns popup char index for a touch x-coord local to this view.
     *  Clamps to the nearest end cell so the user can never "fall off" the
     *  popup and lose their selection while still holding the gesture. */
    fun findIndexForX(x: Float): Int {
        if (chars.isEmpty()) return -1
        val inside = x - pad
        return (inside / cellWidth).toInt().coerceIn(0, chars.size - 1)
    }

    override fun onDraw(canvas: Canvas) {
        // Outer card.
        bgPaint.color = theme.modifierKeyColor
        rect.set(0f, 0f, width.toFloat(), height.toFloat())
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, bgPaint)

        // Faint dividers between cells so each character has its own slot.
        bgPaint.color = theme.dividerColor
        for (i in 1 until chars.size) {
            val x = pad + i * cellWidth
            canvas.drawRect(x - dp(0.5f), pad + dp(8f), x + dp(0.5f), pad + cellHeight - dp(8f), bgPaint)
        }

        for (i in chars.indices) {
            val cellLeft = pad + i * cellWidth
            val isSelected = i == selectedIndex

            if (isSelected) {
                // Selected cell grows into the surrounding padding (left,
                // right, top, bottom) — staying INSIDE the popup view's
                // own bounds so the parent FrameLayout doesn't clip the
                // top corners. Result: a fully-rounded accent pill.
                val grow = dp(4f)
                rect.set(
                    (cellLeft - grow).coerceAtLeast(0f),
                    (pad - grow).coerceAtLeast(0f),
                    (cellLeft + cellWidth + grow).coerceAtMost(width.toFloat()),
                    (pad + cellHeight + grow).coerceAtMost(height.toFloat())
                )
                bgPaint.color = theme.accentColor
                canvas.drawRoundRect(rect, cellCornerRadius, cellCornerRadius, bgPaint)
                selectedTextPaint.color = theme.accentTextColor
                val cy = rect.centerY() - (selectedTextPaint.descent() + selectedTextPaint.ascent()) / 2
                canvas.drawText(chars[i], rect.centerX(), cy, selectedTextPaint)
            } else {
                // Mark the "home" cell (the base character) with a soft tint
                // so the user knows where the popup will commit if they
                // release without sliding.
                if (i == baseIndex) {
                    rect.set(cellLeft, pad, cellLeft + cellWidth, pad + cellHeight)
                    bgPaint.color = theme.keyBackgroundColor
                    canvas.drawRoundRect(rect, cellCornerRadius, cellCornerRadius, bgPaint)
                }
                textPaint.color = theme.keyTextColor
                rect.set(cellLeft, pad, cellLeft + cellWidth, pad + cellHeight)
                val cy = rect.centerY() - (textPaint.descent() + textPaint.ascent()) / 2
                canvas.drawText(chars[i], rect.centerX(), cy, textPaint)
            }
        }
    }

    private fun dp(v: Float): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics).toInt()
}
