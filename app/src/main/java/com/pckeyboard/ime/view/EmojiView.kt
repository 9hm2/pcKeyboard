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
import androidx.viewpager2.widget.ViewPager2
import com.pckeyboard.ime.theme.KeyboardTheme

/**
 * Full-keyboard emoji picker. Tab row at the top, a swipeable
 * [ViewPager2] of category pages (each an 8-column scrollable grid) in
 * the middle, and a control row at the bottom with ABC / backspace /
 * space / enter.
 *
 * First page is "Recents", populated from [EmojiUsageTracker]; each
 * tap on an emoji is recorded so frequently-used emojis float to the
 * top of the Recents page on the next open.
 */
class EmojiView(
    context: Context,
    private val theme: KeyboardTheme,
    private val tracker: EmojiUsageTracker
) : FrameLayout(context) {

    interface Listener {
        fun onEmoji(emoji: String)
        fun onBack()
        fun onBackspace()
        fun onSpace()
        fun onEnter()
    }

    var listener: Listener? = null

    private val pages: List<EmojiCategoryData> = EmojiCatalog.pages(tracker)
    private val tabButtons = mutableListOf<TextView>()
    private lateinit var pager: ViewPager2

    init {
        setBackgroundColor(theme.backgroundColor)

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }

        // 1. Category tab row.
        val tabBar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(theme.modifierKeyColor)
        }
        for ((i, cat) in pages.withIndex()) {
            val btn = TextView(context).apply {
                text = cat.icon
                gravity = Gravity.CENTER
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
                isClickable = true
                isFocusable = true
                layoutParams = LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.MATCH_PARENT, 1f
                )
                setOnClickListener { pager.setCurrentItem(i, true) }
            }
            tabButtons.add(btn)
            tabBar.addView(btn)
        }
        val tabScroller = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            addView(tabBar, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            ))
        }
        root.addView(tabScroller, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(44f)
        ))

        // 2. Swipeable pager — one RecyclerView per category page.
        pager = ViewPager2(context).apply {
            adapter = PagerAdapter()
            registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    highlightTab(position)
                }
            })
        }
        root.addView(pager, LinearLayout.LayoutParams(
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
        highlightTab(0)
    }

    private fun highlightTab(position: Int) {
        for ((i, btn) in tabButtons.withIndex()) {
            val active = i == position
            btn.setBackgroundColor(if (active) theme.accentColor else theme.modifierKeyColor)
            btn.setTextColor(if (active) theme.accentTextColor else theme.modifierTextColor)
        }
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

    // --- ViewPager2 adapter: one page == one category's emoji grid -------

    private inner class PagerAdapter : RecyclerView.Adapter<PageVH>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageVH {
            val rv = RecyclerView(parent.context).apply {
                layoutManager = GridLayoutManager(parent.context, COLUMNS)
                setBackgroundColor(theme.backgroundColor)
                overScrollMode = OVER_SCROLL_NEVER
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }
            return PageVH(rv)
        }

        override fun getItemCount(): Int = pages.size

        override fun onBindViewHolder(holder: PageVH, position: Int) {
            holder.rv.adapter = GridAdapter(pages[position].emojis)
        }
    }

    private class PageVH(val rv: RecyclerView) : RecyclerView.ViewHolder(rv)

    private inner class GridAdapter(private val emojis: List<String>) :
        RecyclerView.Adapter<EmojiVH>() {

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
                tracker.recordUse(emoji)
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
