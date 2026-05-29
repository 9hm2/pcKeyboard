package com.pckeyboard.ime.view

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.pckeyboard.ime.R

/**
 * Small full-screen emoji search. The EditText auto-focuses so pcKeyboard
 * (the user's active IME) pops up under it; as the user types, the grid
 * filters via [EmojiKeywords.search]. Tap an emoji to commit it to the
 * original input via [EmojiSearchBridge]; X dismisses without committing.
 */
class EmojiSearchActivity : AppCompatActivity() {

    private val results = mutableListOf<String>()
    private val adapter = Adapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_emoji_search)

        val root = findViewById<View>(R.id.root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            v.updatePadding(
                top = bars.top,
                left = bars.left,
                right = bars.right,
                bottom = maxOf(bars.bottom, ime.bottom)
            )
            insets
        }

        EmojiSearchBridge.pending = null

        findViewById<ImageButton>(R.id.btnClose).setOnClickListener { finishAndRemoveTask() }

        val recycler = findViewById<RecyclerView>(R.id.results).apply {
            layoutManager = GridLayoutManager(this@EmojiSearchActivity, 8)
            adapter = this@EmojiSearchActivity.adapter
        }

        val empty = findViewById<TextView>(R.id.emptyHint)
        findViewById<EditText>(R.id.query).apply {
            requestFocus()
            addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable?) {
                    val q = s?.toString() ?: ""
                    results.clear()
                    results.addAll(EmojiKeywords.search(q))
                    adapter.notifyDataSetChanged()
                    empty.visibility = if (q.isBlank() || results.isEmpty()) View.VISIBLE else View.GONE
                    empty.text = if (q.isBlank())
                        getString(R.string.emoji_search_hint)
                    else
                        getString(R.string.emoji_search_no_results, q)
                }
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            })
        }
    }

    private inner class Adapter : RecyclerView.Adapter<VH>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val tv = TextView(this@EmojiSearchActivity).apply {
                gravity = Gravity.CENTER
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 26f)
                isClickable = true
                isFocusable = true
                val padPx = (resources.displayMetrics.density * 12).toInt()
                setPadding(padPx, padPx, padPx, padPx)
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }
            return VH(tv)
        }
        override fun getItemCount(): Int = results.size
        override fun onBindViewHolder(holder: VH, position: Int) {
            val emoji = results[position]
            holder.text.text = emoji
            holder.text.setOnClickListener {
                EmojiSearchBridge.pending = emoji
                EmojiSearchBridge.timestamp = System.currentTimeMillis()
                finishAndRemoveTask()
            }
        }
    }

    private class VH(val text: TextView) : RecyclerView.ViewHolder(text)
}
