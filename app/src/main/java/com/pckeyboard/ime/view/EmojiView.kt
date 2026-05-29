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
 * Full-keyboard emoji picker. Tab row at the top with the category
 * icons (separated by faint divider lines) and a permanent 🔍 search
 * button pinned at the right edge; a swipeable [ViewPager2] of category
 * pages (each an 8-column scrollable grid) in the middle; and a slim
 * bottom control row with ABC + ⌫ pulled apart on the right so each
 * button has its own touch target.
 *
 * Tapping the search button fires [Listener.onSearch] — KeyboardView
 * responds by collapsing this picker, mounting a small search header
 * above the real keyboard rows, and routing the user's letter / space /
 * backspace key presses into the header's query field so the **full**
 * keyboard is available for typing (not a mini-pad).
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
        fun onSearch()
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

        // 1. Top row: category tabs (scrollable) + a permanent 🔍 button
        //    pinned at the right edge so search is reachable from where
        //    the user already is (the tab row), not down at the bottom.
        val topRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(theme.modifierKeyColor)
        }
        val tabBar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        for ((i, cat) in pages.withIndex()) {
            if (i > 0) tabBar.addView(verticalDivider())
            val btn = TextView(context).apply {
                text = cat.icon
                gravity = Gravity.CENTER
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
                isClickable = true
                isFocusable = true
                setPadding(dp(14f), 0, dp(14f), 0)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.MATCH_PARENT
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
        topRow.addView(tabScroller, LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.MATCH_PARENT, 1f
        ))
        // A divider between the last visible tab and the pinned 🔍
        // button so the search affordance reads as a separate slot.
        topRow.addView(verticalDivider())
        val searchBtn = TextView(context).apply {
            text = "🔍"
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            isClickable = true
            isFocusable = true
            setPadding(dp(16f), 0, dp(16f), 0)
            setOnClickListener {
                performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                listener?.onSearch()
            }
        }
        topRow.addView(searchBtn, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.MATCH_PARENT
        ))
        root.addView(topRow, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(44f)
        ))

        // 2. Swipeable pager.
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

        // 3. Bottom control row: ABC + ⌫ on the right, with a visible
        //    gap between them so each button reads as its own touch
        //    target. The left side is empty (just modifier-keyed
        //    background) — Space isn't needed inside the emoji picker.
        val controlRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(theme.modifierKeyColor)
            setPadding(0, dp(6f), dp(8f), dp(6f))
        }
        controlRow.addView(spacerView(1.0f))
        controlRow.addView(outlinedControlButton("ABC") { listener?.onBack() })
        controlRow.addView(View(context).apply {
            // Visible breathing room between the two buttons.
            layoutParams = LinearLayout.LayoutParams(dp(8f), LinearLayout.LayoutParams.MATCH_PARENT)
        })
        controlRow.addView(outlinedControlButton("⌫") { listener?.onBackspace() })
        root.addView(controlRow, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(48f)
        ))

        addView(root)
        highlightTab(0)
    }

    private fun spacerView(weight: Float): View = View(context).apply {
        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, weight)
    }

    /** Thin vertical line used between category tabs and around the
     *  pinned 🔍 button. Picks up the theme's [KeyboardTheme.dividerColor]
     *  so it never looks like a hard solid edge. */
    private fun verticalDivider(): View = View(context).apply {
        setBackgroundColor(theme.dividerColor)
        layoutParams = LinearLayout.LayoutParams(
            dp(1f), LinearLayout.LayoutParams.MATCH_PARENT
        ).apply {
            topMargin = dp(10f)
            bottomMargin = dp(10f)
        }
    }

    /** Bottom-row control with a visible rounded-rect background so the
     *  user can see exactly where to tap. */
    private fun outlinedControlButton(label: String, onClick: () -> Unit): View {
        val bg = android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.RECTANGLE
            cornerRadius = dp(8f).toFloat()
            setColor(theme.keyBackgroundColor)
        }
        return TextView(context).apply {
            text = label
            gravity = Gravity.CENTER
            setTextColor(theme.modifierTextColor)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            isClickable = true
            isFocusable = true
            background = bg
            setPadding(dp(20f), 0, dp(20f), 0)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
            setOnClickListener {
                performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                onClick()
            }
        }
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
