package com.pckeyboard.ime.clipboard

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.pckeyboard.ime.theme.KeyboardTheme

/**
 * Full-keyboard clipboard manager. A vertical list of cards (one per
 * captured clip, newest first); tap a card to commit it, long-press to
 * open the edit-window. Bottom control row matches the emoji picker's
 * (ABC to dismiss).
 */
class ClipboardView(
    context: Context,
    private val theme: KeyboardTheme,
    private val history: ClipboardHistory
) : FrameLayout(context) {

    interface Listener {
        fun onCommit(text: String)
        fun onEdit(text: String)
        fun onBack()
    }

    var listener: Listener? = null

    private val adapter = ClipAdapter()
    private val emptyView: TextView

    init {
        setBackgroundColor(theme.backgroundColor)

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }

        // Title bar.
        val title = TextView(context).apply {
            text = "Clipboard"
            setPadding(dp(16f), dp(12f), dp(16f), dp(12f))
            setTextColor(theme.keyTextColor)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setBackgroundColor(theme.modifierKeyColor)
        }
        root.addView(title, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(44f)))

        // Either the recycler or the empty-state TextView depending on data.
        val listContainer = FrameLayout(context)
        emptyView = TextView(context).apply {
            text = "No clipboard items yet.\nCopy text in any app and it'll show up here."
            gravity = Gravity.CENTER
            setTextColor(theme.secondaryTextColor)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setPadding(dp(24f), dp(24f), dp(24f), dp(24f))
        }
        listContainer.addView(emptyView, LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT))
        val recycler = RecyclerView(context).apply {
            layoutManager = LinearLayoutManager(context)
            this.adapter = this@ClipboardView.adapter
            setPadding(dp(8f), dp(8f), dp(8f), dp(8f))
            clipToPadding = false
            overScrollMode = OVER_SCROLL_NEVER
        }
        listContainer.addView(recycler, LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT))
        root.addView(listContainer, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        // Control row mirrors EmojiView's layout.
        val controlRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(theme.modifierKeyColor)
        }
        controlRow.addView(controlButton("ABC", 1.0f) { listener?.onBack() })
        root.addView(controlRow, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(46f)))

        addView(root)
        refresh()
    }

    fun refresh() {
        val list = history.all()
        adapter.update(list)
        emptyView.visibility = if (list.isEmpty()) VISIBLE else GONE
    }

    private fun controlButton(label: String, weight: Float, onClick: () -> Unit): View {
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

    private inner class ClipAdapter : RecyclerView.Adapter<ClipVH>() {
        private var items: List<String> = emptyList()

        fun update(list: List<String>) {
            items = list
            notifyDataSetChanged()
        }

        override fun getItemCount(): Int = items.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ClipVH {
            val card = TextView(context).apply {
                setPadding(dp(14f), dp(12f), dp(14f), dp(12f))
                setTextColor(theme.keyTextColor)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                maxLines = 4
                ellipsize = TextUtils.TruncateAt.END
                isClickable = true
                isFocusable = true
                val bg = GradientDrawable().apply {
                    setColor(theme.keyBackgroundColor)
                    cornerRadius = dp(12f).toFloat()
                }
                background = bg
                layoutParams = RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = dp(4f)
                    bottomMargin = dp(4f)
                }
            }
            return ClipVH(card)
        }

        override fun onBindViewHolder(holder: ClipVH, position: Int) {
            val text = items[position]
            holder.text.text = text
            holder.text.setOnClickListener {
                holder.text.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                listener?.onCommit(text)
            }
            holder.text.setOnLongClickListener {
                holder.text.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                listener?.onEdit(text)
                true
            }
        }
    }

    private class ClipVH(val text: TextView) : RecyclerView.ViewHolder(text)

    private fun dp(v: Float): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics).toInt()
}
