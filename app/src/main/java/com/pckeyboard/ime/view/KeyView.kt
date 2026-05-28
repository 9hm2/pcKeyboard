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

    var pressed: Boolean = false
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

        bgPaint.color = backgroundColorForState()
        canvas.drawRoundRect(rect, radius, radius, bgPaint)

        // accent highlight when modifier is locked or sticky-on
        val highlight = highlightColorForState()
        if (highlight != null) {
            bgPaint.color = highlight
            val border = dp(2f)
            val r = RectF(rect)
            r.inset(dp(1.5f), dp(1.5f))
            bgPaint.style = Paint.Style.STROKE
            bgPaint.strokeWidth = border
            canvas.drawRoundRect(r, radius, radius, bgPaint)
            bgPaint.style = Paint.Style.FILL
        }

        val isShifted = modifiers.isShiftActive()
        val mainText = when {
            key.type == KeyType.LETTER && isShifted -> key.label.uppercase()
            key.type == KeyType.LETTER -> key.label
            key.type == KeyType.CHAR && isShifted && key.shiftLabel != null -> key.shiftLabel
            else -> key.label
        }

        val mainSize = preferredTextSize()
        textPaint.color = textColorForState()
        textPaint.textSize = mainSize
        val cy = rect.centerY() - (textPaint.descent() + textPaint.ascent()) / 2
        canvas.drawText(mainText, rect.centerX(), cy, textPaint)

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

    private fun backgroundColorForState(): Int {
        if (pressed) return theme.keyPressedColor
        return when (key.type) {
            KeyType.SPACE, KeyType.ENTER -> theme.accentColor
            KeyType.LETTER, KeyType.CHAR -> theme.keyBackgroundColor
            else -> theme.modifierKeyColor
        }
    }

    private fun textColorForState(): Int = when (key.type) {
        KeyType.SPACE, KeyType.ENTER -> theme.accentTextColor
        KeyType.LETTER, KeyType.CHAR -> theme.keyTextColor
        else -> theme.modifierTextColor
    }

    private fun highlightColorForState(): Int? {
        val state = when (key.type) {
            KeyType.SHIFT -> modifiers.shift
            KeyType.CTRL -> modifiers.ctrl
            KeyType.ALT -> modifiers.alt
            KeyType.META -> modifiers.meta
            KeyType.FN -> modifiers.fn
            KeyType.CAPS_LOCK -> if (modifiers.capsLock) ModifierState.State.LOCKED else ModifierState.State.OFF
            else -> return null
        }
        return when (state) {
            ModifierState.State.OFF -> null
            ModifierState.State.ONCE -> theme.accentColor
            ModifierState.State.LOCKED -> theme.accentColor
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

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pressed = true
                listener?.onKeyDown(this)
                return true
            }
            MotionEvent.ACTION_UP -> {
                pressed = false
                listener?.onKeyUp(this)
                performClick()
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                pressed = false
                listener?.onKeyCancel(this)
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    interface Listener {
        fun onKeyDown(view: KeyView)
        fun onKeyUp(view: KeyView)
        fun onKeyCancel(view: KeyView)
    }
}
