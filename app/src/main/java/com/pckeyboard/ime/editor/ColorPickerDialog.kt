package com.pckeyboard.ime.editor

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Small ARGB colour picker built from primitives — no library dependency.
 * Four sliders (A / R / G / B) drive a live preview swatch and a read-only
 * "#AARRGGBB" hex display. Tapping OK invokes [onPicked] with the chosen
 * ARGB int.
 */
object ColorPickerDialog {

    fun show(context: Context, initial: Int, onPicked: (Int) -> Unit) {
        val density = context.resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()
        fun sp(v: Float) = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP, v, context.resources.displayMetrics
        )

        var alpha = (initial ushr 24) and 0xFF
        var red = (initial ushr 16) and 0xFF
        var green = (initial ushr 8) and 0xFF
        var blue = initial and 0xFF

        // One reset function per slider — captured so "Default" can put
        // every slider back to its starting value without closing the
        // dialog.
        val resetActions = mutableListOf<() -> Unit>()

        fun currentColor(): Int =
            (alpha and 0xFF shl 24) or
                (red and 0xFF shl 16) or
                (green and 0xFF shl 8) or
                (blue and 0xFF)

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(16), dp(20), dp(8))
        }

        val previewBg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = density * 8f
            setColor(initial)
            setStroke(dp(1), Color.parseColor("#33888888"))
        }
        val preview = TextView(context).apply {
            background = previewBg
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(72)
            ).apply { bottomMargin = dp(8) }
        }
        root.addView(preview)

        val hexLabel = TextView(context).apply {
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            text = String.format("#%08X", initial)
            setPadding(0, 0, 0, dp(8))
        }
        root.addView(hexLabel)

        fun refresh() {
            val c = currentColor()
            previewBg.setColor(c)
            preview.invalidate()
            hexLabel.text = String.format("#%08X", c)
        }

        fun addSlider(label: String, value: Int, set: (Int) -> Unit) {
            val initialValue = value
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            val labelView = TextView(context).apply {
                text = label
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                layoutParams = LinearLayout.LayoutParams(dp(18), ViewGroup.LayoutParams.WRAP_CONTENT)
            }
            val valueView = TextView(context).apply {
                text = value.toString()
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                gravity = Gravity.END
                layoutParams = LinearLayout.LayoutParams(dp(36), ViewGroup.LayoutParams.WRAP_CONTENT)
            }
            val seek = SeekBar(context).apply {
                max = 255
                progress = value
                layoutParams = LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
                )
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                        set(progress)
                        valueView.text = progress.toString()
                        refresh()
                    }
                    override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                    override fun onStopTrackingTouch(seekBar: SeekBar?) {}
                })
            }
            row.addView(labelView)
            row.addView(seek)
            row.addView(valueView)
            root.addView(row)
            // Putting seek.progress back to the initial value fires the
            // listener above, which in turn calls set(), updates valueView
            // and refreshes the preview — so we only need to capture this
            // one call to fully reset the slider's state.
            resetActions.add { seek.progress = initialValue }
        }

        addSlider("A", alpha) { alpha = it }
        addSlider("R", red) { red = it }
        addSlider("G", green) { green = it }
        addSlider("B", blue) { blue = it }

        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle("Pick colour")
            .setView(root)
            .setPositiveButton(android.R.string.ok) { _, _ -> onPicked(currentColor()) }
            // null listener keeps the button visible but doesn't auto-
            // dismiss the dialog — onShow below wires the real handler
            // so "Default" rolls every slider back without closing.
            .setNeutralButton("Default", null)
            .setNegativeButton(android.R.string.cancel, null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(android.content.DialogInterface.BUTTON_NEUTRAL)
                ?.setOnClickListener { resetActions.forEach { it() } }
        }
        dialog.show()
    }
}
