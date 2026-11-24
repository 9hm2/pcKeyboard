package com.pckeyboard.ime.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.pckeyboard.ime.theme.KeyboardTheme

/**
 * Full-keyboard voice-input overlay shown when the user picks "Voice
 * input" from the globe action menu. A pure RENDERER of a
 * [VoiceRecognitionController]: the recogniser lives in the service, so
 * this view can be destroyed and rebuilt (rotation, fold) while the
 * dictation keeps running underneath.
 *
 * The mic button toggles listening, ✕ closes the overlay (ending the
 * session). A hint + settings link shows when RECORD_AUDIO isn't
 * granted, because an IME service can't request runtime permissions.
 */
class VoiceInputView(
    context: Context,
    private val theme: KeyboardTheme,
    private val controller: VoiceRecognitionController,
    private val onClose: () -> Unit,
    private val onOpenAppSettings: () -> Unit
) : FrameLayout(context), VoiceRecognitionController.Ui {

    private val statusText: TextView
    private val transcriptText: TextView
    private val micRect = RectF()
    private val closeRect = RectF()
    private val pulsePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(2f).toFloat()
    }
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val xPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(2f).toFloat()
        strokeCap = Paint.Cap.ROUND
    }

    init {
        setBackgroundColor(theme.backgroundColor)

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(24f), dp(40f), dp(24f), dp(24f))
        }
        statusText = TextView(context).apply {
            text = ""
            setTextColor(theme.secondaryTextColor)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            gravity = Gravity.CENTER
        }
        root.addView(statusText, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        transcriptText = TextView(context).apply {
            text = ""
            setTextColor(theme.keyTextColor)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            gravity = Gravity.CENTER
            setPadding(0, dp(20f), 0, 0)
        }
        root.addView(transcriptText, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(20f) })
        addView(root, LayoutParams(
            LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT
        ))

        refreshUi()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        controller.attach(this)
        if (controller.hasRecordPermission()) {
            // No-op when a session is already live (post-rotation).
            controller.startIfIdle()
        }
        refreshUi()
    }

    override fun onDetachedFromWindow() {
        // Only detach the renderer — the session itself outlives the view.
        controller.attach(null)
        super.onDetachedFromWindow()
    }

    override fun onVoiceStateChanged() {
        transcriptText.text = controller.lastPartial
        refreshUi()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        val micRadius = dp(36f).toFloat()
        val micCx = w / 2f
        val micCy = h - dp(64f).toFloat()
        micRect.set(micCx - micRadius, micCy - micRadius, micCx + micRadius, micCy + micRadius)

        val closeRadius = dp(22f).toFloat()
        val closeCx = w - dp(28f).toFloat()
        val closeCy = dp(28f).toFloat()
        closeRect.set(closeCx - closeRadius, closeCy - closeRadius,
            closeCx + closeRadius, closeCy + closeRadius)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked != MotionEvent.ACTION_DOWN) return true
        val padX = event.x
        val padY = event.y
        val closePad = dp(8f).toFloat()
        // The ✕ has a slightly inflated hit-rect, fingers are bigger than the visual mark.
        if (padX >= closeRect.left - closePad && padX <= closeRect.right + closePad &&
            padY >= closeRect.top - closePad && padY <= closeRect.bottom + closePad
        ) {
            performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            onClose()
            return true
        }
        val micPad = dp(16f).toFloat()
        if (padX >= micRect.left - micPad && padX <= micRect.right + micPad &&
            padY >= micRect.top - micPad && padY <= micRect.bottom + micPad
        ) {
            performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            if (!controller.hasRecordPermission()) {
                onOpenAppSettings()
                return true
            }
            if (controller.listening) controller.stop() else controller.start()
            return true
        }
        return true
    }

    override fun onDraw(canvas: Canvas) {
        // Animated outer ring that pulses with mic input volume while
        // listening; static thin ring when idle.
        val baseRadius = (micRect.width() / 2f)
        if (controller.listening) {
            // rmsDb is roughly -2..10. Map to 1.0..1.6× radius.
            val scale = 1f + ((controller.rmsDb.coerceIn(-2f, 10f) + 2f) / 12f) * 0.6f
            pulsePaint.color = (theme.accentColor and 0x00FFFFFF) or 0x33000000
            canvas.drawCircle(
                micRect.centerX(),
                micRect.centerY(),
                baseRadius * scale,
                pulsePaint
            )
        }
        bgPaint.color = if (controller.listening) theme.accentColor else theme.keyBackgroundColor
        canvas.drawCircle(micRect.centerX(), micRect.centerY(), baseRadius, bgPaint)
        ringPaint.color = if (controller.listening) theme.accentTextColor else theme.accentColor
        canvas.drawCircle(micRect.centerX(), micRect.centerY(), baseRadius, ringPaint)
        drawMicIcon(canvas, micRect.centerX(), micRect.centerY(),
            if (controller.listening) theme.accentTextColor else theme.keyTextColor)

        // Close button (always visible — voice has no slide gesture
        // that would catch the corner the way the trackpad does).
        bgPaint.color = theme.keyBackgroundColor
        canvas.drawCircle(closeRect.centerX(), closeRect.centerY(),
            closeRect.width() / 2f, bgPaint)
        ringPaint.color = theme.accentColor
        canvas.drawCircle(closeRect.centerX(), closeRect.centerY(),
            closeRect.width() / 2f, ringPaint)
        xPaint.color = theme.keyTextColor
        val cx = closeRect.centerX()
        val cy = closeRect.centerY()
        val xs = dp(9f).toFloat()
        canvas.drawLine(cx - xs, cy - xs, cx + xs, cy + xs, xPaint)
        canvas.drawLine(cx - xs, cy + xs, cx + xs, cy - xs, xPaint)
    }

    private fun drawMicIcon(canvas: Canvas, cx: Float, cy: Float, tint: Int) {
        iconPaint.color = tint
        // Capsule body of the mic.
        val capW = dp(14f).toFloat()
        val capH = dp(22f).toFloat()
        val capRect = RectF(cx - capW / 2f, cy - capH / 2f - dp(4f),
            cx + capW / 2f, cy + capH / 2f - dp(4f))
        canvas.drawRoundRect(capRect, capW / 2f, capW / 2f, iconPaint)
        // Curved stand.
        val standY = capRect.bottom + dp(4f)
        val arcRect = RectF(cx - capW * 0.9f, capRect.bottom - capH * 0.4f,
            cx + capW * 0.9f, capRect.bottom + capH * 0.4f)
        val standPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = dp(2.5f).toFloat()
            color = tint
            strokeCap = Paint.Cap.ROUND
        }
        canvas.drawArc(arcRect, 30f, 120f, false, standPaint)
        // Vertical stem + base.
        canvas.drawLine(cx, standY, cx, standY + dp(6f), standPaint)
        canvas.drawLine(cx - dp(8f), standY + dp(6f), cx + dp(8f), standY + dp(6f), standPaint)
    }

    private fun refreshUi() {
        statusText.text = when (controller.status) {
            VoiceRecognitionController.Status.PERMISSION_MISSING ->
                context.getString(com.pckeyboard.ime.R.string.voice_permission_missing)
            VoiceRecognitionController.Status.NOT_AVAILABLE ->
                context.getString(com.pckeyboard.ime.R.string.voice_not_available)
            VoiceRecognitionController.Status.ERROR_NETWORK ->
                context.getString(com.pckeyboard.ime.R.string.voice_error_network)
            VoiceRecognitionController.Status.ERROR_NO_MATCH ->
                context.getString(com.pckeyboard.ime.R.string.voice_error_no_match)
            VoiceRecognitionController.Status.ERROR_OTHER ->
                context.getString(com.pckeyboard.ime.R.string.voice_error_generic, controller.errorCode)
            VoiceRecognitionController.Status.LISTENING ->
                context.getString(com.pckeyboard.ime.R.string.voice_listening)
            VoiceRecognitionController.Status.IDLE ->
                if (!controller.hasRecordPermission())
                    context.getString(com.pckeyboard.ime.R.string.voice_permission_missing)
                else context.getString(com.pckeyboard.ime.R.string.voice_tap_to_speak)
        }
        invalidate()
    }

    private fun dp(v: Float): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics).toInt()
}
