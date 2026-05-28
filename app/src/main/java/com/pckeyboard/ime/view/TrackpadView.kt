package com.pckeyboard.ime.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.util.TypedValue
import android.view.View
import com.pckeyboard.ime.theme.KeyboardTheme

/**
 * Full-keyboard overlay shown when the user long-presses Space.
 *
 * Behaviour intent (matches Samsung's hidden trackpad gesture):
 *  - A circular indicator is drawn in the centre of the keyboard area.
 *  - The user must slide their finger from Space *into* the indicator to
 *    "arm" the trackpad. Until armed nothing moves and a stray long-press
 *    can be undone simply by releasing.
 *  - Once armed, the indicator changes colour and any further finger
 *    movement is consumed by [KeyboardView] as cursor motion.
 *
 * The view itself only draws — it does not handle touch. The parent
 * KeyboardView routes raw screen coordinates here via [isInsideIndicator]
 * and flips [armed] when appropriate.
 */
class TrackpadView(
    context: Context,
    private val theme: KeyboardTheme
) : View(context) {

    var armed: Boolean = false
        set(value) { if (field != value) { field = value; invalidate() } }

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = theme.modifierKeyColor
    }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(2f).toFloat()
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        textSize = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP, 14f, resources.displayMetrics
        )
    }
    private val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    val indicatorRadius: Float = dp(42f).toFloat()

    private var indicatorCx: Float = 0f
    private var indicatorCy: Float = 0f

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        indicatorCx = w / 2f
        indicatorCy = h / 2f
    }

    fun isInsideIndicator(localX: Float, localY: Float): Boolean {
        val dx = localX - indicatorCx
        val dy = localY - indicatorCy
        return dx * dx + dy * dy <= indicatorRadius * indicatorRadius
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(theme.backgroundColor)

        // Card backdrop behind the indicator for visual lift.
        val pad = dp(20f).toFloat()
        bgPaint.color = theme.modifierKeyColor
        canvas.drawRoundRect(
            pad, pad,
            width - pad, height - pad,
            dp(20f).toFloat(), dp(20f).toFloat(),
            bgPaint
        )

        // Centre indicator.
        fillPaint.color = if (armed) theme.accentColor else theme.keyBackgroundColor
        canvas.drawCircle(indicatorCx, indicatorCy, indicatorRadius, fillPaint)
        ringPaint.color = if (armed) theme.accentTextColor else theme.accentColor
        canvas.drawCircle(indicatorCx, indicatorCy, indicatorRadius, ringPaint)

        // Cardinal arrows inside the indicator to hint at cursor control.
        arrowPaint.color = if (armed) theme.accentTextColor else theme.keyTextColor
        val a = dp(10f).toFloat()
        val o = dp(20f).toFloat() // distance from centre
        drawTriangle(canvas, indicatorCx,     indicatorCy - o, 0f,   -a) // up
        drawTriangle(canvas, indicatorCx,     indicatorCy + o, 0f,    a) // down
        drawTriangle(canvas, indicatorCx - o, indicatorCy,    -a,   0f) // left
        drawTriangle(canvas, indicatorCx + o, indicatorCy,     a,   0f) // right

        // Caption.
        textPaint.color = theme.secondaryTextColor
        val caption = if (armed) "Move to control cursor" else "Slide here to use as trackpad"
        canvas.drawText(
            caption,
            indicatorCx,
            indicatorCy + indicatorRadius + dp(28f),
            textPaint
        )
    }

    private fun drawTriangle(canvas: Canvas, cx: Float, cy: Float, dx: Float, dy: Float) {
        // Tiny isosceles triangle pointing in direction (dx, dy).
        val path = android.graphics.Path()
        if (dy != 0f) { // vertical arrow
            path.moveTo(cx, cy + dy)
            path.lineTo(cx - dp(7f), cy - dy * 0.2f)
            path.lineTo(cx + dp(7f), cy - dy * 0.2f)
        } else { // horizontal arrow
            path.moveTo(cx + dx, cy)
            path.lineTo(cx - dx * 0.2f, cy - dp(7f))
            path.lineTo(cx - dx * 0.2f, cy + dp(7f))
        }
        path.close()
        canvas.drawPath(path, arrowPaint)
    }

    private fun dp(v: Float): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics).toInt()
}
