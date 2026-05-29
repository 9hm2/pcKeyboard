package com.pckeyboard.ime.view

import android.content.Context
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.pckeyboard.ime.R
import com.pckeyboard.ime.theme.KeyboardTheme

/**
 * Slim search bar shown above the regular keyboard rows while the user
 * is searching emojis. The full keyboard is left in place; KeyboardView
 * routes char / letter / space / backspace key presses into this view's
 * [appendQuery] / [deleteFromQuery] instead of committing them to the
 * input field.
 *
 * Layout: 44 dp query bar (🔍 + live query + ✕) on top, 60 dp
 * horizontally-scrolling results strip underneath. Tapping a result
 * commits it via [onEmojiPicked] (which also records it as a Recents
 * entry through [EmojiUsageTracker]) and dismisses the search.
 */
class EmojiSearchHeaderView(
    context: Context,
    private val theme: KeyboardTheme,
    private val tracker: EmojiUsageTracker,
    private val onClose: () -> Unit,
    private val onEmojiPicked: (String) -> Unit
) : FrameLayout(context) {

    private var query: String = ""
    private val results = mutableListOf<String>()
    private val adapter = ResultAdapter()
    private val queryView: TextView

    init {
        setBackgroundColor(theme.modifierKeyColor)

        val root = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }

        // Query bar.
        val queryBar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val magnifier = TextView(context).apply {
            text = "🔍"
            gravity = Gravity.CENTER
            setPadding(dp(14f), 0, dp(8f), 0)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        }
        queryBar.addView(magnifier, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.MATCH_PARENT
        ))
        queryView = TextView(context).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, dp(8f), 0)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        }
        queryBar.addView(queryView, LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.MATCH_PARENT, 1f
        ))
        val close = TextView(context).apply {
            text = "✕"
            gravity = Gravity.CENTER
            setPadding(dp(14f), 0, dp(14f), 0)
            setTextColor(theme.modifierTextColor)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            isClickable = true
            isFocusable = true
            setOnClickListener {
                performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                onClose()
            }
        }
        queryBar.addView(close, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.MATCH_PARENT
        ))
        root.addView(queryBar, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(44f)
        ))

        // Results strip.
        val recycler = RecyclerView(context).apply {
            layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
            setBackgroundColor(theme.backgroundColor)
            adapter = this@EmojiSearchHeaderView.adapter
            overScrollMode = OVER_SCROLL_NEVER
        }
        root.addView(recycler, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(60f)
        ))

        addView(root, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        refreshResults()
    }

    fun appendQuery(text: String) {
        if (text.isEmpty()) return
        query += text.lowercase()
        refreshResults()
    }

    fun deleteFromQuery() {
        if (query.isNotEmpty()) {
            query = query.dropLast(1)
            refreshResults()
        }
    }

    private fun refreshResults() {
        if (query.isEmpty()) {
            queryView.text = context.getString(R.string.emoji_search_hint)
            queryView.setTextColor(theme.secondaryTextColor)
        } else {
            queryView.text = query
            queryView.setTextColor(theme.keyTextColor)
        }
        results.clear()
        if (query.isEmpty()) {
            results.addAll(tracker.recents(40))
        } else {
            results.addAll(EmojiKeywords.search(query))
        }
        adapter.notifyDataSetChanged()
    }

    private inner class ResultAdapter : RecyclerView.Adapter<VH>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val tv = TextView(context).apply {
                gravity = Gravity.CENTER
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
                isClickable = true
                isFocusable = true
                setPadding(dp(10f), 0, dp(10f), 0)
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }
            return VH(tv)
        }

        override fun getItemCount(): Int = results.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val emoji = results[position]
            holder.text.text = emoji
            holder.text.setOnClickListener {
                holder.text.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                tracker.recordUse(emoji)
                onEmojiPicked(emoji)
            }
        }
    }

    private class VH(val text: TextView) : RecyclerView.ViewHolder(text)

    private fun dp(v: Float): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics).toInt()
}
