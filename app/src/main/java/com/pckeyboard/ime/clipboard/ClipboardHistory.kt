package com.pckeyboard.ime.clipboard

import android.content.Context
import androidx.preference.PreferenceManager
import org.json.JSONArray

/**
 * Last-N clipboard texts captured by the IME's primary-clip listener.
 * Persisted as a JSON array in SharedPreferences so the cards survive
 * device reboots / process death.
 */
class ClipboardHistory(context: Context) {

    private val prefs =
        PreferenceManager.getDefaultSharedPreferences(context.applicationContext)

    /** Newest first. */
    fun all(): List<String> {
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { arr.getString(it) }
        } catch (_: Throwable) { emptyList() }
    }

    fun add(text: String) {
        if (text.isBlank()) return
        val list = all().toMutableList()
        list.remove(text)
        list.add(0, text)
        while (list.size > MAX) list.removeAt(list.size - 1)
        save(list)
    }

    fun remove(text: String) {
        val filtered = all().filter { it != text }
        save(filtered)
    }

    fun replace(old: String, new: String) {
        if (new.isBlank()) { remove(old); return }
        val list = all().toMutableList()
        val idx = list.indexOf(old)
        if (idx < 0) { add(new); return }
        list[idx] = new
        // Dedupe in case the new value collides with another entry.
        val seen = mutableSetOf<String>()
        save(list.filter { seen.add(it) })
    }

    private fun save(list: List<String>) {
        val arr = JSONArray()
        list.forEach { arr.put(it) }
        prefs.edit().putString(KEY, arr.toString()).apply()
    }

    companion object {
        private const val KEY = "clip_history_json"
        const val MAX = 20
    }
}
