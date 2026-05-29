package com.pckeyboard.ime.view

import android.content.Context
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.pckeyboard.ime.theme.KeyboardTheme

/**
 * Full-keyboard emoji picker. Tabs at the top (one per [EmojiCategory]),
 * an 8-column scrollable grid in the middle, and a control row at the
 * bottom with ABC (return to letters), backspace, space and enter.
 *
 * Tap an emoji to commit it via [Listener.onEmoji]; the picker stays
 * open so the user can pick several in a row, just like Gboard.
 */
class EmojiView(
    context: Context,
    private val theme: KeyboardTheme
) : FrameLayout(context) {

    interface Listener {
        fun onEmoji(emoji: String)
        fun onBack()
        fun onBackspace()
        fun onSpace()
        fun onEnter()
    }

    var listener: Listener? = null

    private val adapter = EmojiAdapter()
    private val tabButtons = mutableListOf<TextView>()
    private var currentTabIndex: Int = 0

    init {
        setBackgroundColor(theme.backgroundColor)

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }

        // 1. Category tab row.
        val tabRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(theme.modifierKeyColor)
        }
        for ((i, cat) in EmojiCategory.entries.withIndex()) {
            val btn = TextView(context).apply {
                text = cat.icon
                gravity = Gravity.CENTER
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
                isClickable = true
                isFocusable = true
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
                setOnClickListener { showCategory(i) }
            }
            tabButtons.add(btn)
            tabRow.addView(btn)
        }
        val tabScroller = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            addView(tabRow, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            ))
        }
        root.addView(tabScroller, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(44f)
        ))

        // 2. Scrollable emoji grid.
        val grid = RecyclerView(context).apply {
            layoutManager = GridLayoutManager(context, COLUMNS)
            this.adapter = this@EmojiView.adapter
            setBackgroundColor(theme.backgroundColor)
            overScrollMode = OVER_SCROLL_NEVER
        }
        root.addView(grid, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        ))

        // 3. Control row.
        val controlRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(theme.modifierKeyColor)
        }
        controlRow.addView(controlButton("ABC",    1.5f) { listener?.onBack() })
        controlRow.addView(controlButton("⌫",    1.0f) { listener?.onBackspace() })
        controlRow.addView(controlButton("space", 5.0f) { listener?.onSpace() })
        controlRow.addView(controlButton("⏎",    1.5f) { listener?.onEnter() })
        root.addView(controlRow, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(46f)
        ))

        addView(root)
        showCategory(0)
    }

    private fun showCategory(index: Int) {
        currentTabIndex = index
        for ((i, btn) in tabButtons.withIndex()) {
            val active = i == index
            btn.setBackgroundColor(if (active) theme.accentColor else theme.modifierKeyColor)
            btn.setTextColor(if (active) theme.accentTextColor else theme.modifierTextColor)
        }
        adapter.update(EmojiCategory.entries[index].emojis)
    }

    private fun controlButton(
        label: String,
        weight: Float,
        onClick: () -> Unit
    ): View {
        return TextView(context).apply {
            text = label
            gravity = Gravity.CENTER
            setTextColor(theme.modifierTextColor)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            isClickable = true
            isFocusable = true
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, weight)
            setOnClickListener {
                performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                onClick()
            }
        }
    }

    private inner class EmojiAdapter : RecyclerView.Adapter<EmojiVH>() {
        private var emojis: List<String> = emptyList()

        fun update(list: List<String>) {
            emojis = list
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EmojiVH {
            val tv = TextView(context).apply {
                gravity = Gravity.CENTER
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
                isClickable = true
                isFocusable = true
                setPadding(dp(2f), dp(8f), dp(2f), dp(8f))
            }
            return EmojiVH(tv)
        }

        override fun getItemCount(): Int = emojis.size

        override fun onBindViewHolder(holder: EmojiVH, position: Int) {
            val emoji = emojis[position]
            holder.text.text = emoji
            holder.text.setOnClickListener {
                holder.text.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                listener?.onEmoji(emoji)
            }
        }
    }

    private class EmojiVH(val text: TextView) : RecyclerView.ViewHolder(text)

    private fun dp(v: Float): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics).toInt()

    companion object {
        private const val COLUMNS = 8
    }
}
