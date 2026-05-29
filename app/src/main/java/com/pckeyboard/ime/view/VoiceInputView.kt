package com.pckeyboard.ime.view

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.pckeyboard.ime.theme.KeyboardTheme

/**
 * Full-keyboard voice-input overlay shown when the user picks "Voice
 * input" from the globe action menu. Drives the system [SpeechRecognizer]
 * with the supplied [recognitionLocale], shows live partial results in
 * the centre and commits the final transcript via [onText].
 *
 * The mic button on the left **toggles** listening (tap → start, tap →
 * stop). The ✕ button on the right closes the overlay without committing
 * the in-flight transcript. The view also draws a small "Open settings"
 * link when the RECORD_AUDIO permission isn't granted yet, because an
 * IME service can't request runtime permissions directly.
 */
class VoiceInputView(
    context: Context,
    private val theme: KeyboardTheme,
    /** BCP-47 locale tag for the recogniser (e.g. "hu-HU", "en-US"). */
    private val recognitionLocale: String,
    private val onText: (String) -> Unit,
    private val onClose: () -> Unit,
    private val onOpenAppSettings: () -> Unit
) : FrameLayout(context) {

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

    private var recognizer: SpeechRecognizer? = null
    private var listening: Boolean = false
    private var lastPartial: String = ""
    private var rmsDb: Float = 0f
    /** True if the user explicitly tapped Stop / ✕ — so a follow-up
     *  ERROR_CLIENT from the recogniser cancelling doesn't get
     *  misinterpreted as a real recognition failure. */
    private var userCanceled: Boolean = false

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
        if (hasRecordPermission()) {
            startListening()
        } else {
            statusText.text = context.getString(
                com.pckeyboard.ime.R.string.voice_permission_missing
            )
        }
    }

    override fun onDetachedFromWindow() {
        stopListening()
        recognizer?.destroy()
        recognizer = null
        super.onDetachedFromWindow()
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
            if (!hasRecordPermission()) {
                onOpenAppSettings()
                return true
            }
            if (listening) stopListening() else startListening()
            return true
        }
        return true
    }

    override fun onDraw(canvas: Canvas) {
        // Animated outer ring that pulses with mic input volume while
        // listening; static thin ring when idle.
        val baseRadius = (micRect.width() / 2f)
        if (listening) {
            // rmsDb is roughly -2..10. Map to 1.0..1.6× radius.
            val scale = 1f + ((rmsDb.coerceIn(-2f, 10f) + 2f) / 12f) * 0.6f
            pulsePaint.color = (theme.accentColor and 0x00FFFFFF) or 0x33000000
            canvas.drawCircle(
                micRect.centerX(),
                micRect.centerY(),
                baseRadius * scale,
                pulsePaint
            )
        }
        bgPaint.color = if (listening) theme.accentColor else theme.keyBackgroundColor
        canvas.drawCircle(micRect.centerX(), micRect.centerY(), baseRadius, bgPaint)
        ringPaint.color = if (listening) theme.accentTextColor else theme.accentColor
        canvas.drawCircle(micRect.centerX(), micRect.centerY(), baseRadius, ringPaint)
        drawMicIcon(canvas, micRect.centerX(), micRect.centerY(),
            if (listening) theme.accentTextColor else theme.keyTextColor)

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
        statusText.text = when {
            !hasRecordPermission() -> context.getString(
                com.pckeyboard.ime.R.string.voice_permission_missing
            )
            listening -> context.getString(com.pckeyboard.ime.R.string.voice_listening)
            else -> context.getString(com.pckeyboard.ime.R.string.voice_tap_to_speak)
        }
        invalidate()
    }

    private fun startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            statusText.text = context.getString(
                com.pckeyboard.ime.R.string.voice_not_available
            )
            return
        }
        userCanceled = false
        lastPartial = ""
        val rec = recognizer ?: SpeechRecognizer.createSpeechRecognizer(context).also {
            recognizer = it
        }
        rec.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                listening = true
                lastPartial = ""
                transcriptText.text = ""
                refreshUi()
            }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rms: Float) { rmsDb = rms; invalidate() }
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { listening = false; refreshUi() }
            override fun onError(error: Int) {
                listening = false
                // SpeechRecognizer routinely fires ERROR_CLIENT after the
                // user finishes speaking and the service shuts down —
                // even when partial results came through cleanly. If we
                // have a usable transcript and the user didn't cancel,
                // commit it instead of throwing an error in their face.
                if (!userCanceled && lastPartial.isNotBlank()) {
                    onText(lastPartial)
                    return
                }
                userCanceled = false
                statusText.text = when (error) {
                    SpeechRecognizer.ERROR_NETWORK,
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                        context.getString(com.pckeyboard.ime.R.string.voice_error_network)
                    SpeechRecognizer.ERROR_NO_MATCH,
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                        context.getString(com.pckeyboard.ime.R.string.voice_error_no_match)
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                        context.getString(com.pckeyboard.ime.R.string.voice_permission_missing)
                    else ->
                        context.getString(com.pckeyboard.ime.R.string.voice_error_generic, error)
                }
                invalidate()
            }
            override fun onPartialResults(partialResults: Bundle?) {
                val candidates = partialResults
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = candidates?.firstOrNull().orEmpty()
                if (text.isNotEmpty()) {
                    lastPartial = text
                    transcriptText.text = text
                }
            }
            override fun onResults(results: Bundle?) {
                val candidates = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = candidates?.firstOrNull().orEmpty()
                if (text.isNotEmpty()) {
                    onText(text)
                }
                listening = false
                refreshUi()
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, recognitionLocale)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, recognitionLocale)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        }
        try {
            rec.startListening(intent)
        } catch (_: Throwable) {
            statusText.text = context.getString(
                com.pckeyboard.ime.R.string.voice_not_available
            )
        }
    }

    private fun stopListening() {
        userCanceled = true
        recognizer?.stopListening()
        recognizer?.cancel()
        listening = false
        refreshUi()
    }

    private fun hasRecordPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    private fun dp(v: Float): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics).toInt()
}
