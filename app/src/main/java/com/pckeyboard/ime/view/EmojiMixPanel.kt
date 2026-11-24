package com.pckeyboard.ime.view

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.pckeyboard.ime.R
import com.pckeyboard.ime.emoji.mix.EmojiRasterizer
import com.pckeyboard.ime.emoji.mix.MixInput
import com.pckeyboard.ime.emoji.mix.MixRecipes
import com.pckeyboard.ime.emoji.mix.MixVariant
import com.pckeyboard.ime.theme.KeyboardTheme
import java.io.File
import java.util.concurrent.Executors

/**
 * The emoji mixer: pick two emojis from the normal picker grid below,
 * and a strip of freshly composed sticker variants appears above it;
 * tap one to send it as an image. 🎲 re-rolls the random details.
 */
class EmojiMixPanel(
    context: Context,
    private val theme: KeyboardTheme,
    tracker: EmojiUsageTracker,
    private val cacheDir: File,
    /** A finished sticker PNG to insert / copy. */
    private val onSend: (File) -> Unit,
    /** Leave the mixer (back to the keyboard). */
    private val onClose: () -> Unit
) : LinearLayout(context) {

    private val main = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "emoji-mix").apply { isDaemon = true }
    }
    /** Bumped on every change so stale background renders are dropped. */
    @Volatile private var generation = 0
    private var salt = 0L
    private val selected = ArrayList<String>()

    private val slotA: TextView = slotView(0)
    private val slotB: TextView = slotView(1)
    private val strip = LinearLayout(context).apply { orientation = HORIZONTAL }
    private val hint = TextView(context).apply {
        text = context.getString(R.string.mix_hint)
        setTextColor(theme.secondaryTextColor)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(12f), 0, dp(12f), 0)
    }

    init {
        orientation = VERTICAL
        setBackgroundColor(theme.backgroundColor)

        val bar = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(6f), dp(4f), dp(6f), dp(4f))
        }
        bar.addView(slotA, LayoutParams(dp(52f), dp(52f)))
        bar.addView(TextView(context).apply {
            text = "+"
            setTextColor(theme.keyTextColor)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            gravity = Gravity.CENTER
        }, LayoutParams(dp(24f), dp(52f)))
        bar.addView(slotB, LayoutParams(dp(52f), dp(52f)))
        bar.addView(TextView(context).apply {
            text = "🎲"
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            isClickable = true
            setOnClickListener {
                performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                salt++
                regenerate()
            }
        }, LayoutParams(dp(48f), dp(52f)))
        val scroll = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            addView(strip, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT))
        }
        bar.addView(scroll, LayoutParams(0, dp(60f), 1f))
        addView(bar, LayoutParams(LayoutParams.MATCH_PARENT, dp(66f)))

        // The normal emoji grid does the picking.
        val picker = EmojiView(context, theme, tracker, showMixButton = false).apply {
            listener = object : EmojiView.Listener {
                override fun onEmoji(emoji: String) = select(emoji)
                override fun onBack() = onClose()
                override fun onBackspace() {
                    if (selected.isNotEmpty()) { selected.removeAt(selected.size - 1); refresh() }
                }
                override fun onSearch() {}
            }
        }
        addView(picker, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        refresh()
    }

    private fun slotView(index: Int): TextView = TextView(context).apply {
        gravity = Gravity.CENTER
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 26f)
        background = GradientDrawable().apply {
            setColor(theme.keyBackgroundColor)
            cornerRadius = dp(12f).toFloat()
            setStroke(dp(1.5f), theme.accentColor)
        }
        isClickable = true
        setOnClickListener {
            // Tapping a filled slot clears it.
            if (index < selected.size) { selected.removeAt(index); refresh() }
        }
    }

    private fun select(emoji: String) {
        if (selected.size >= 2) selected.removeAt(0)
        selected.add(emoji)
        salt = 0L
        refresh()
    }

    private fun refresh() {
        slotA.text = selected.getOrNull(0) ?: ""
        slotB.text = selected.getOrNull(1) ?: ""
        regenerate()
    }

    private fun regenerate() {
        generation++
        val gen = generation
        strip.removeAllViews()
        if (selected.size < 2) {
            strip.addView(hint, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT))
            return
        }
        val a = selected[0]
        val b = selected[1]
        val currentSalt = salt
        executor.execute {
            if (gen != generation) return@execute
            val inA = MixInput(a, EmojiRasterizer.render(a, THUMB_SRC), MixRecipes.isFaceEmoji(a))
            val inB = MixInput(b, EmojiRasterizer.render(b, THUMB_SRC), MixRecipes.isFaceEmoji(b))
            for (v in MixRecipes.variantsFor(inA, inB, currentSalt)) {
                if (gen != generation) return@execute
                val bmp = EmojiRasterizer.toBitmap(v.render(THUMB))
                main.post { if (gen == generation) strip.addView(thumbnail(bmp, v)) else bmp.recycle() }
            }
        }
    }

    private fun thumbnail(bmp: android.graphics.Bitmap, v: MixVariant): View =
        ImageView(context).apply {
            setImageBitmap(bmp)
            scaleType = ImageView.ScaleType.FIT_CENTER
            setPadding(dp(3f), dp(3f), dp(3f), dp(3f))
            background = GradientDrawable().apply {
                setColor(theme.keyBackgroundColor)
                cornerRadius = dp(12f).toFloat()
            }
            layoutParams = LayoutParams(dp(60f), dp(60f)).apply { marginStart = dp(4f) }
            isClickable = true
            setOnClickListener {
                performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                send(v)
            }
        }

    /** Re-renders the chosen variant at full quality off the UI thread. */
    private fun send(v: MixVariant) {
        executor.execute {
            val baseArt = EmojiRasterizer.render(v.base.text, FINAL_SRC)
            val otherArt = EmojiRasterizer.render(v.other.text, FINAL_SRC)
            val full = MixVariant(
                v.recipe,
                MixInput(v.base.text, baseArt, v.base.isFace),
                MixInput(v.other.text, otherArt, v.other.isFace),
                v.seed
            ).render(FINAL)
            val file = EmojiRasterizer.savePng(cacheDir, full)
            main.post { onSend(file) }
        }
    }

    override fun onDetachedFromWindow() {
        generation++
        executor.shutdownNow()
        super.onDetachedFromWindow()
    }

    private fun dp(v: Float): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics).toInt()

    companion object {
        const val THUMB = 144
        const val THUMB_SRC = 192
        const val FINAL = 512
        const val FINAL_SRC = 512
    }
}
