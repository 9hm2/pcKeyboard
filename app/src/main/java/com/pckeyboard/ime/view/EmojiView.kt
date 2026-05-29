package com.pckeyboard.ime.view

import android.content.Context
import android.graphics.drawable.GradientDrawable
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
 * Full-keyboard emoji picker. The "normal" mode shows a tab row, a
 * swipeable [ViewPager2] of category pages (each an 8-column scrollable
 * grid), and a control row with ABC / 🔍 / backspace / space / enter.
 *
 * Tapping the 🔍 button switches into a self-contained "search" mode
 * inside the picker — no separate activity. The search mode displays:
 *   - a query bar at the top (magnifier + the current text + ✕ to leave),
 *   - a results grid driven by [EmojiKeywords.search],
 *   - a compact 3-row QWERTY pad at the bottom so the user can type into
 *     the query without ever leaving the IME view.
 *
 * Every emoji commit (page tap, recents tap, or search-result tap) goes
 * through [EmojiUsageTracker.recordUse], so emojis picked via search end
 * up in the Recents page on the next open.
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

    // Two sibling roots — only one is visible at a time.
    private lateinit var normalRoot: LinearLayout
    private lateinit var searchRoot: LinearLayout

    // Search state.
    private var query: String = ""
    private val searchResults = mutableListOf<String>()
    private val searchAdapter = SearchAdapter()
    private lateinit var queryView: TextView
    private lateinit var searchEmpty: TextView

    init {
        setBackgroundColor(theme.backgroundColor)
        buildNormalRoot()
        buildSearchRoot()
        addView(normalRoot, LayoutParams(
            LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT
        ))
        addView(searchRoot, LayoutParams(
            LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT
        ))
        searchRoot.visibility = GONE
        highlightTab(0)
    }

    // --- Normal mode ------------------------------------------------------

    private fun buildNormalRoot() {
        normalRoot = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }

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
        normalRoot.addView(tabScroller, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(44f)
        ))

        pager = ViewPager2(context).apply {
            adapter = PagerAdapter()
            registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    highlightTab(position)
                }
            })
        }
        normalRoot.addView(pager, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        ))

        val controlRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(theme.modifierKeyColor)
        }
        controlRow.addView(controlButton("ABC",    1.5f) { listener?.onBack() })
        controlRow.addView(controlButton("🔍",    1.0f) { enterSearchMode() })
        controlRow.addView(controlButton("⌫",    1.0f) { listener?.onBackspace() })
        controlRow.addView(controlButton("space", 4.0f) { listener?.onSpace() })
        controlRow.addView(controlButton("⏎",    1.5f) { listener?.onEnter() })
        normalRoot.addView(controlRow, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(46f)
        ))
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

    // --- Search mode ------------------------------------------------------

    private fun buildSearchRoot() {
        searchRoot = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }

        val queryBar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(theme.modifierKeyColor)
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
            text = ""
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, dp(8f), 0)
            setTextColor(theme.keyTextColor)
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
                exitSearchMode()
            }
        }
        queryBar.addView(close, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.MATCH_PARENT
        ))
        searchRoot.addView(queryBar, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(44f)
        ))

        // Results grid + empty-state overlay sharing the same slot.
        val resultsContainer = FrameLayout(context)
        val resultsRecycler = RecyclerView(context).apply {
            layoutManager = GridLayoutManager(context, COLUMNS)
            adapter = searchAdapter
            setBackgroundColor(theme.backgroundColor)
            overScrollMode = OVER_SCROLL_NEVER
        }
        resultsContainer.addView(resultsRecycler, LayoutParams(
            LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT
        ))
        searchEmpty = TextView(context).apply {
            gravity = Gravity.CENTER
            setTextColor(theme.secondaryTextColor)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setPadding(dp(24f), dp(24f), dp(24f), dp(24f))
        }
        resultsContainer.addView(searchEmpty, LayoutParams(
            LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT
        ))
        searchRoot.addView(resultsContainer, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        ))

        // Mini QWERTY pad — 3 rows, lowercase only.
        val qwertyContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(theme.modifierKeyColor)
        }
        qwertyContainer.addView(buildTopLetterRow("qwertyuiop"))
        qwertyContainer.addView(buildHomeLetterRow("asdfghjkl"))
        qwertyContainer.addView(buildBottomLetterRow("zxcvbnm"))
        searchRoot.addView(qwertyContainer, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(132f)
        ))
    }

    private fun buildTopLetterRow(letters: String): LinearLayout {
        val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        for (c in letters) row.addView(letterKey(c.toString(), 1f))
        row.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        )
        return row
    }

    /** Home row centered with 0.5 weight margins so it offsets like a real QWERTY. */
    private fun buildHomeLetterRow(letters: String): LinearLayout {
        val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        row.addView(spacer(0.5f))
        for (c in letters) row.addView(letterKey(c.toString(), 1f))
        row.addView(spacer(0.5f))
        row.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        )
        return row
    }

    /** Backspace + 7 letters + space + clear. Total weight 10. */
    private fun buildBottomLetterRow(letters: String): LinearLayout {
        val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        row.addView(controlKey("⌫", 1f) { onSearchBackspace() })
        for (c in letters) row.addView(letterKey(c.toString(), 1f))
        row.addView(controlKey("space", 1f) { onSearchTextInput(" ") })
        row.addView(controlKey("Clear", 1f) { onSearchClear() })
        row.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        )
        return row
    }

    private fun spacer(weight: Float): View {
        val v = View(context)
        v.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, weight)
        return v
    }

    private fun letterKey(label: String, weight: Float): View {
        return TextView(context).apply {
            text = label
            gravity = Gravity.CENTER
            setTextColor(theme.keyTextColor)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            isClickable = true
            isFocusable = true
            val bg = GradientDrawable().apply {
                setColor(theme.keyBackgroundColor)
                cornerRadius = dp(6f).toFloat()
            }
            background = bg
            val pad = dp(2f)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, weight)
                .apply { setMargins(pad, pad, pad, pad) }
            setOnClickListener {
                performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                onSearchTextInput(label)
            }
        }
    }

    private fun controlKey(label: String, weight: Float, onClick: () -> Unit): View {
        return TextView(context).apply {
            text = label
            gravity = Gravity.CENTER
            setTextColor(theme.modifierTextColor)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            isClickable = true
            isFocusable = true
            val pad = dp(2f)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, weight)
                .apply { setMargins(pad, pad, pad, pad) }
            setOnClickListener {
                performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                onClick()
            }
        }
    }

    private fun enterSearchMode() {
        query = ""
        refreshSearch()
        normalRoot.visibility = GONE
        searchRoot.visibility = VISIBLE
    }

    private fun exitSearchMode() {
        query = ""
        searchRoot.visibility = GONE
        normalRoot.visibility = VISIBLE
    }

    private fun onSearchTextInput(text: String) {
        query += text
        refreshSearch()
    }

    private fun onSearchBackspace() {
        if (query.isNotEmpty()) {
            query = query.dropLast(1)
            refreshSearch()
        }
    }

    private fun onSearchClear() {
        if (query.isNotEmpty()) {
            query = ""
            refreshSearch()
        }
    }

    private fun refreshSearch() {
        queryView.text = query
        searchResults.clear()
        searchResults.addAll(EmojiKeywords.search(query))
        searchAdapter.notifyDataSetChanged()
        when {
            query.isBlank() -> {
                searchEmpty.visibility = VISIBLE
                searchEmpty.text = context.getString(
                    com.pckeyboard.ime.R.string.emoji_search_hint
                )
            }
            searchResults.isEmpty() -> {
                searchEmpty.visibility = VISIBLE
                searchEmpty.text = context.getString(
                    com.pckeyboard.ime.R.string.emoji_search_no_results, query
                )
            }
            else -> searchEmpty.visibility = GONE
        }
    }

    // --- Adapters ---------------------------------------------------------

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
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EmojiVH =
            EmojiVH(emojiCell())
        override fun getItemCount(): Int = emojis.size
        override fun onBindViewHolder(holder: EmojiVH, position: Int) {
            bindEmoji(holder, emojis[position])
        }
    }

    private inner class SearchAdapter : RecyclerView.Adapter<EmojiVH>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EmojiVH =
            EmojiVH(emojiCell())
        override fun getItemCount(): Int = searchResults.size
        override fun onBindViewHolder(holder: EmojiVH, position: Int) {
            bindEmoji(holder, searchResults[position])
        }
    }

    private fun bindEmoji(holder: EmojiVH, emoji: String) {
        holder.text.text = emoji
        holder.text.setOnClickListener {
            holder.text.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            tracker.recordUse(emoji)
            listener?.onEmoji(emoji)
        }
    }

    private fun emojiCell(): TextView = TextView(context).apply {
        gravity = Gravity.CENTER
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
        isClickable = true
        isFocusable = true
        setPadding(dp(2f), dp(8f), dp(2f), dp(8f))
    }

    private class EmojiVH(val text: TextView) : RecyclerView.ViewHolder(text)

    private fun dp(v: Float): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics).toInt()

    companion object {
        private const val COLUMNS = 8
    }
}
