package com.pckeyboard.ime.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Build
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import com.pckeyboard.ime.model.Key
import com.pckeyboard.ime.model.KeyType
import com.pckeyboard.ime.model.ModifierState
import com.pckeyboard.ime.settings.KeyboardPrefs
import com.pckeyboard.ime.theme.KeyboardTheme

/**
 * Renders a single key. Handles its own touch (pressed) feedback so the
 * parent row only has to do hit-testing.
 */
class KeyView(
    context: Context,
    val key: Key,
    var theme: KeyboardTheme,
    var modifiers: ModifierState
) : View(context) {

    var isDown: Boolean = false
        set(value) { field = value; invalidate() }

    var listener: Listener? = null

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
    private val secondaryPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
    }
    private val rect = RectF()

    init {
        isClickable = true
        isFocusable = true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            tooltipText = key.label
        }
    }

    override fun onDraw(canvas: Canvas) {
        val pad = dp(theme.keySpacingDp / 2f)
        rect.set(pad, pad, width - pad, height - pad)
        val radius = dp(theme.keyCornerRadiusDp.toFloat())
        val modState = modifierState()

        bgPaint.color = backgroundColorForState(modState)
        bgPaint.style = Paint.Style.FILL
        canvas.drawRoundRect(rect, radius, radius, bgPaint)

        // ONCE → bold accent border so the user can see a modifier is armed
        // for the next keypress without being confused with the LOCKED state.
        if (modState == ModifierState.State.ONCE) {
            val border = dp(3f)
            val r = RectF(rect)
            r.inset(border / 2f, border / 2f)
            bgPaint.color = theme.accentColor
            bgPaint.style = Paint.Style.STROKE
            bgPaint.strokeWidth = border
            canvas.drawRoundRect(r, radius, radius, bgPaint)
            bgPaint.style = Paint.Style.FILL
        }

        val isShifted = modifiers.isShiftActive()
        val isAltActive = modifiers.isAltActive()
        val altChar = key.altLabel?.takeIf {
            isAltActive && (key.type == KeyType.LETTER || key.type == KeyType.CHAR)
        }
        val mainText = when {
            altChar != null -> if (isShifted) altChar.uppercase() else altChar
            key.type == KeyType.LETTER && isShifted -> key.label.uppercase()
            key.type == KeyType.LETTER -> key.label
            key.type == KeyType.CHAR && isShifted && key.shiftLabel != null -> key.shiftLabel
            else -> key.label
        }

        val mainSize = preferredTextSize()
        textPaint.color = textColorForState(modState)
        textPaint.textSize = mainSize
        val cy = rect.centerY() - (textPaint.descent() + textPaint.ascent()) / 2
        canvas.drawText(mainText, rect.centerX(), cy, textPaint)

        // LOCKED → underline under the label to mark "stays on until tapped
        // again", visually distinct from the ONCE border.
        if (modState == ModifierState.State.LOCKED) {
            val lineW = mainSize * 0.9f
            val lineY = cy + textPaint.descent() + dp(2f)
            bgPaint.color = textColorForState(modState)
            canvas.drawRoundRect(
                rect.centerX() - lineW / 2f, lineY,
                rect.centerX() + lineW / 2f, lineY + dp(2.5f),
                dp(1.5f), dp(1.5f),
                bgPaint
            )
        }

        // ONCE → small accent dot in the top-right corner so even without the
        // border (e.g. on themes with similar accent / modifier colours) the
        // armed state is obvious.
        if (modState == ModifierState.State.ONCE) {
            val dotR = dp(3f)
            bgPaint.color = theme.accentColor
            canvas.drawCircle(rect.right - dp(8f), rect.top + dp(8f), dotR, bgPaint)
        }

        // secondary (shifted) hint for char keys (e.g. "!" above "1")
        if (key.type == KeyType.CHAR && !isShifted && key.shiftLabel != null) {
            secondaryPaint.color = theme.secondaryTextColor
            secondaryPaint.textSize = mainSize * 0.55f
            canvas.drawText(
                key.shiftLabel,
                rect.centerX() + rect.width() * 0.28f,
                rect.top + secondaryPaint.textSize * 1.1f,
                secondaryPaint
            )
        }
    }

    private fun modifierState(): ModifierState.State = when (key.type) {
        KeyType.SHIFT -> modifiers.shift
        KeyType.CTRL -> modifiers.ctrl
        KeyType.ALT -> modifiers.alt
        KeyType.META -> modifiers.meta
        KeyType.FN -> modifiers.fn
        KeyType.CAPS_LOCK -> if (modifiers.capsLock) ModifierState.State.LOCKED else ModifierState.State.OFF
        else -> ModifierState.State.OFF
    }

    private fun backgroundColorForState(modState: ModifierState.State): Int {
        if (isDown) return theme.keyPressedColor
        if (modState == ModifierState.State.LOCKED) return theme.accentColor
        return when (key.type) {
            KeyType.SPACE, KeyType.ENTER -> theme.accentColor
            KeyType.LETTER, KeyType.CHAR -> theme.keyBackgroundColor
            else -> theme.modifierKeyColor
        }
    }

    private fun textColorForState(modState: ModifierState.State): Int {
        if (modState == ModifierState.State.LOCKED) return theme.accentTextColor
        return when (key.type) {
            KeyType.SPACE, KeyType.ENTER -> theme.accentTextColor
            KeyType.LETTER, KeyType.CHAR -> theme.keyTextColor
            else -> theme.modifierTextColor
        }
    }

    private fun preferredTextSize(): Float {
        val sz = when (key.type) {
            KeyType.SPACE -> 14f
            KeyType.FN -> 12f
            KeyType.LETTER, KeyType.CHAR -> 18f
            else -> 13f
        }
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sz, resources.displayMetrics)
    }

    private fun dp(v: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics)

    private var popupActive: Boolean = false
    private var longPressRunnable: Runnable? = null
    private val prefs by lazy { KeyboardPrefs(context) }

    private fun canLongPress(): Boolean =
        key.popupChars != null || key.type == KeyType.SPACE ||
            key.type == KeyType.LANGUAGE_SWITCH

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                isDown = true
                listener?.onKeyDown(this)
                if (canLongPress()) scheduleLongPress()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (popupActive) {
                    listener?.onKeyPopupMove(this, event.rawX, event.rawY)
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                isDown = false
                cancelLongPressTimer()
                if (popupActive) {
                    popupActive = false
                    listener?.onKeyPopupRelease(this)
                    return true
                }
                listener?.onKeyUp(this)
                performClick()
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                isDown = false
                cancelLongPressTimer()
                if (popupActive) {
                    popupActive = false
                    listener?.onKeyPopupCancel(this)
                } else {
                    listener?.onKeyCancel(this)
                }
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun scheduleLongPress() {
        val r = Runnable {
            if (isDown && canLongPress()) {
                popupActive = true
                listener?.onKeyLongPress(this)
            }
        }
        longPressRunnable = r
        postDelayed(r, prefs.longPressDelayMs.toLong())
    }

    private fun cancelLongPressTimer() {
        longPressRunnable?.let { removeCallbacks(it) }
        longPressRunnable = null
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    interface Listener {
        fun onKeyDown(view: KeyView)
        fun onKeyUp(view: KeyView)
        fun onKeyCancel(view: KeyView)
        fun onKeyLongPress(view: KeyView)
        fun onKeyPopupMove(view: KeyView, rawX: Float, rawY: Float)
        fun onKeyPopupRelease(view: KeyView)
        fun onKeyPopupCancel(view: KeyView)
    }
}
