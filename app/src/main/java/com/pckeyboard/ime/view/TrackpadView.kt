package com.pckeyboard.ime.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.TypedValue
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import com.pckeyboard.ime.theme.KeyboardTheme

/**
 * Two-phase trackpad overlay shown when the user long-presses Space.
 *
 *  - [Phase.ARMING] — initial state. A circular indicator sits in the
 *    centre of the keyboard area and the user must slide their finger
 *    from Space *into* the indicator to arm the trackpad (so an
 *    accidental long-press can be undone by just releasing). KeyView
 *    routes its raw screen coords to [isInsideIndicator]; KeyboardView
 *    flips [armed] when the finger crosses into the ring.
 *
 *  - [Phase.FREE] — KeyboardView transitions the view into this phase
 *    on release (after arming). The user is now driving a real
 *    touchpad: each finger-down on the surface sets an origin and
 *    subsequent moves emit *relative* cursor deltas via
 *    [onCursorDelta]. Lifting the finger ends a drag but keeps the
 *    trackpad open; the user can re-touch as many times as they want
 *    until they tap the ✕ button (top-right), which fires [onClose].
 *    The ✕ button is hidden while a finger is on the surface so it
 *    doesn't catch a stray touch near the corner.
 */
class TrackpadView(
    context: Context,
    private val theme: KeyboardTheme,
    /** Pixel-delta callback used in [Phase.FREE]. KeyboardView converts
     *  these deltas to character / line steps with its own sensitivity. */
    private val onCursorDelta: (dxPx: Float, dyPx: Float) -> Unit,
    /** Fired when the user taps the ✕ button. KeyboardView tears the
     *  trackpad down. */
    private val onClose: () -> Unit
) : View(context) {

    enum class Phase { ARMING, FREE }

    var phase: Phase = Phase.ARMING
        set(value) {
            if (field != value) { field = value; invalidate() }
        }

    var armed: Boolean = false
        set(value) { if (field != value) { field = value; invalidate() } }

    /** While true the ✕ button is hidden so it can't catch the drag. */
    private var fingerDown: Boolean = false
    private var lastTouchX: Float = 0f
    private var lastTouchY: Float = 0f

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(2f).toFloat()
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        textSize = sp(14f)
    }
    private val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val xPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(2f).toFloat()
        strokeCap = Paint.Cap.ROUND
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    val indicatorRadius: Float = dp(56f).toFloat()
    private var indicatorCx: Float = 0f
    private var indicatorCy: Float = 0f

    private val closeRadius: Float = dp(22f).toFloat()
    private val closeRect = RectF()
    /** Slightly inflated hit-rect — fingers are bigger than the visual ✕. */
    private val closeHitRect = RectF()

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        indicatorCx = w / 2f
        indicatorCy = h / 2f
        val cx = w - dp(28f).toFloat()
        val cy = dp(28f).toFloat()
        closeRect.set(cx - closeRadius, cy - closeRadius, cx + closeRadius, cy + closeRadius)
        val pad = dp(8f).toFloat()
        closeHitRect.set(closeRect.left - pad, closeRect.top - pad,
            closeRect.right + pad, closeRect.bottom + pad)
    }

    /** Used by KeyboardView in [Phase.ARMING] to check the slide-target. */
    fun isInsideIndicator(localX: Float, localY: Float): Boolean {
        val dx = localX - indicatorCx
        val dy = localY - indicatorCy
        return dx * dx + dy * dy <= indicatorRadius * indicatorRadius
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        // During arming the gesture is still owned by the Space KeyView,
        // so we don't process touches here — the user's finger is on the
        // keyboard row below, not on the trackpad surface yet.
        if (phase != Phase.FREE) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (closeHitRect.contains(event.x, event.y)) {
                    performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    onClose()
                    return true
                }
                fingerDown = true
                lastTouchX = event.x
                lastTouchY = event.y
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (!fingerDown) return true
                val dx = event.x - lastTouchX
                val dy = event.y - lastTouchY
                lastTouchX = event.x
                lastTouchY = event.y
                if (dx != 0f || dy != 0f) {
                    onCursorDelta(dx, dy)
                    invalidate()
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                fingerDown = false
                invalidate()
                return true
            }
        }
        return false
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(theme.backgroundColor)

        // Common card backdrop in both phases for visual lift.
        val pad = dp(20f).toFloat()
        bgPaint.color = theme.modifierKeyColor
        canvas.drawRoundRect(
            pad, pad, width - pad, height - pad,
            dp(20f).toFloat(), dp(20f).toFloat(),
            bgPaint
        )

        when (phase) {
            Phase.ARMING -> drawArmingPhase(canvas)
            Phase.FREE -> drawFreePhase(canvas)
        }
    }

    private fun drawArmingPhase(canvas: Canvas) {
        // Centre indicator that the user must slide into.
        fillPaint.color = if (armed) theme.accentColor else theme.keyBackgroundColor
        canvas.drawCircle(indicatorCx, indicatorCy, indicatorRadius, fillPaint)
        ringPaint.color = if (armed) theme.accentTextColor else theme.accentColor
        canvas.drawCircle(indicatorCx, indicatorCy, indicatorRadius, ringPaint)

        // Cardinal arrows inside the indicator.
        arrowPaint.color = if (armed) theme.accentTextColor else theme.keyTextColor
        val a = dp(10f).toFloat()
        val o = dp(20f).toFloat()
        drawTriangle(canvas, indicatorCx,     indicatorCy - o, 0f,   -a)
        drawTriangle(canvas, indicatorCx,     indicatorCy + o, 0f,    a)
        drawTriangle(canvas, indicatorCx - o, indicatorCy,    -a,   0f)
        drawTriangle(canvas, indicatorCx + o, indicatorCy,     a,   0f)

        textPaint.color = theme.secondaryTextColor
        val caption = if (armed) "Release to start trackpad" else "Slide here to use as trackpad"
        canvas.drawText(
            caption,
            indicatorCx,
            indicatorCy + indicatorRadius + dp(28f),
            textPaint
        )
    }

    private fun drawFreePhase(canvas: Canvas) {
        // Live touch dot follows the finger so the user can see where
        // the trackpad is reading them from — useful precision feedback.
        if (fingerDown) {
            dotPaint.color = theme.accentColor
            canvas.drawCircle(lastTouchX, lastTouchY, dp(12f).toFloat(), dotPaint)
            dotPaint.color = (theme.accentTextColor and 0x00FFFFFF) or 0x66000000
            canvas.drawCircle(lastTouchX, lastTouchY, dp(20f).toFloat(), dotPaint)
        } else {
            // Idle hint: small instruction text in the middle of the
            // empty surface so the user knows they're now in touchpad
            // mode and can tap anywhere to start a drag.
            textPaint.color = theme.secondaryTextColor
            canvas.drawText(
                "Touch & drag anywhere",
                width / 2f, height / 2f + dp(6f),
                textPaint
            )
            // ✕ close button at top-right (hidden while a drag is on).
            fillPaint.color = theme.keyBackgroundColor
            canvas.drawCircle(closeRect.centerX(), closeRect.centerY(), closeRadius, fillPaint)
            ringPaint.color = theme.accentColor
            canvas.drawCircle(closeRect.centerX(), closeRect.centerY(), closeRadius, ringPaint)
            xPaint.color = theme.keyTextColor
            val cx = closeRect.centerX()
            val cy = closeRect.centerY()
            val xs = dp(9f).toFloat()
            canvas.drawLine(cx - xs, cy - xs, cx + xs, cy + xs, xPaint)
            canvas.drawLine(cx - xs, cy + xs, cx + xs, cy - xs, xPaint)
        }
    }

    private fun drawTriangle(canvas: Canvas, cx: Float, cy: Float, dx: Float, dy: Float) {
        val path = android.graphics.Path()
        if (dy != 0f) {
            path.moveTo(cx, cy + dy)
            path.lineTo(cx - dp(7f), cy - dy * 0.2f)
            path.lineTo(cx + dp(7f), cy - dy * 0.2f)
        } else {
            path.moveTo(cx + dx, cy)
            path.lineTo(cx - dx * 0.2f, cy - dp(7f))
            path.lineTo(cx - dx * 0.2f, cy + dp(7f))
        }
        path.close()
        canvas.drawPath(path, arrowPaint)
    }

    private fun dp(v: Float): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics).toInt()

    private fun sp(v: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v, resources.displayMetrics)
}
